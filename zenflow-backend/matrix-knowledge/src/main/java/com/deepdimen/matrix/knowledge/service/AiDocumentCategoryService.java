package com.deepdimen.matrix.knowledge.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.knowledge.dto.AiDocumentCategoryDTO;
import com.deepdimen.matrix.knowledge.dto.AiDocumentCategoryMoveDTO;
import com.deepdimen.matrix.knowledge.dto.AiDocumentDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDatasetPermissionEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentCategoryEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetPermissionMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentCategoryMapper;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentMapper;
import com.deepdimen.matrix.knowledge.support.handler.source.FileSourceTypeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 文档分类的唯一写入入口。所有写操作先锁定知识库，避免删除、移动、上传相互竞争。 */
@Service
@RequiredArgsConstructor
public class AiDocumentCategoryService {

	private final AiDatasetMapper datasetMapper;

	private final AiDatasetPermissionMapper permissionMapper;

	private final AiDocumentCategoryMapper categoryMapper;

	private final AiDocumentMapper documentMapper;

	public List<AiDocumentCategoryEntity> list(Long datasetId) {
		requireDataset(datasetId, false);
		return scopedCategories(datasetId);
	}

	@Transactional(rollbackFor = Exception.class)
	public AiDocumentCategoryEntity create(AiDocumentCategoryDTO request) {
		requireDataset(request.getDatasetId(), true);
		if (request.getId() != null) {
			throw new IllegalArgumentException("新增分类不能指定编号");
		}
		AiDocumentCategoryEntity category = validateCategory(request);
		categoryMapper.insert(category);
		return category;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean update(AiDocumentCategoryDTO request) {
		requireDataset(request.getDatasetId(), true);
		requireCategory(scopedCategories(request.getDatasetId()), request.getId());
		AiDocumentCategoryEntity category = validateCategory(request);
		return categoryMapper.update(category,
				Wrappers.<AiDocumentCategoryEntity>lambdaQuery()
					.eq(AiDocumentCategoryEntity::getId, request.getId())
					.eq(AiDocumentCategoryEntity::getTenantId, tenantId())
					.eq(AiDocumentCategoryEntity::getDatasetId, request.getDatasetId())) == 1;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean delete(Long datasetId, Long id) {
		requireDataset(datasetId, true);
		List<AiDocumentCategoryEntity> categories = scopedCategories(datasetId);
		requireCategory(categories, id);
		if (categories.stream().anyMatch(category -> Objects.equals(category.getParentId(), id))) {
			throw new IllegalArgumentException("分类下还有子分类，请先移动或删除子分类");
		}
		if (documentMapper.selectCount(Wrappers.<AiDocumentEntity>query()
			.eq("tenant_id", tenantId())
			.eq("dataset_id", datasetId)
			.eq("category_id", id)) > 0) {
			throw new IllegalArgumentException("分类下还有文档，请先移动文档");
		}
		return categoryMapper.delete(Wrappers.<AiDocumentCategoryEntity>lambdaQuery()
			.eq(AiDocumentCategoryEntity::getId, id)
			.eq(AiDocumentCategoryEntity::getDatasetId, datasetId)
			.eq(AiDocumentCategoryEntity::getTenantId, tenantId())) == 1;
	}

	@Transactional(rollbackFor = Exception.class)
	public boolean moveDocuments(AiDocumentCategoryMoveDTO request) {
		requireDataset(request.getDatasetId(), true);
		List<Long> ids = request.getDocumentIds();
		if (ids == null || ids.isEmpty() || ids.size() > 500 || ids.stream().anyMatch(id -> id == null || id <= 0)
				|| new HashSet<>(ids).size() != ids.size()) {
			throw new IllegalArgumentException("请选择1至500个不重复的文档");
		}
		if (request.getCategoryId() != null) {
			requireCategory(scopedCategories(request.getDatasetId()), request.getCategoryId());
		}
		List<AiDocumentEntity> documents = documentMapper.selectList(Wrappers.<AiDocumentEntity>query()
			.select("id")
			.eq("tenant_id", tenantId())
			.eq("dataset_id", request.getDatasetId())
			.in("id", ids)
			.last("FOR UPDATE"));
		if (documents.size() != ids.size()) {
			throw new IllegalArgumentException("部分文档不存在或不属于当前知识库，未移动任何文档");
		}
		// 只更新分类，不触发重新切片或向量化；显式 set 才能写入 NULL。
		documentMapper.update(null,
				Wrappers.<AiDocumentEntity>update()
					.eq("tenant_id", tenantId())
					.eq("dataset_id", request.getDatasetId())
					.in("id", ids)
					.set("category_id", request.getCategoryId()));
		return true;
	}

	/** HTTP线程同步保存文档元数据，提交后才启动耗时解析，防止分类删除与异步插入竞争。 */
	@Transactional(rollbackFor = Exception.class)
	public void prepareUpload(FileSourceTypeHandler handler, AiDocumentDTO request) {
		requireDataset(request.getDatasetId(), true);
		if (request.getId() != null) {
			throw new IllegalArgumentException("新增文档不能指定已有文档编号");
		}
		if (request.getCategoryId() != null) {
			requireCategory(scopedCategories(request.getDatasetId()), request.getCategoryId());
		}
		request.setPreparedDocument(handler.saveDocument(request));
	}

	public void applyFilter(AiDocumentEntity query) {
		// 此字段只接受服务端解析结果，不能接受请求注入的分类ID列表。
		query.setCategoryIds(null);
		if (query.getCategoryId() != null && Boolean.TRUE.equals(query.getUncategorized())) {
			throw new IllegalArgumentException("不能同时查询指定分类和未分类");
		}
		if (query.getDatasetId() != null) {
			requireDataset(query.getDatasetId(), false);
		}
		if (query.getCategoryId() != null || Boolean.TRUE.equals(query.getUncategorized())) {
			if (query.getDatasetId() == null) {
				throw new IllegalArgumentException("按分类查询时必须选择知识库");
			}
			if (query.getCategoryId() != null) {
				List<AiDocumentCategoryEntity> categories = scopedCategories(query.getDatasetId());
				requireCategory(categories, query.getCategoryId());
				query.setCategoryIds(AiDocumentCategoryTree.descendants(categories, query.getCategoryId()));
			}
		}
	}

	/** 旧通用文档编辑不得绕过分类移动校验，也不得变更知识库。 */
	public void validateDocumentUpdate(AiDocumentEntity request) {
		AiDocumentEntity existing = documentMapper
			.selectOne(Wrappers.<AiDocumentEntity>query().eq("tenant_id", tenantId()).eq("id", request.getId()));
		if (existing == null) {
			throw new IllegalArgumentException("文档不存在或无权限访问");
		}
		requireDataset(existing.getDatasetId(), false);
		if (request.getDatasetId() != null && !Objects.equals(request.getDatasetId(), existing.getDatasetId())) {
			throw new IllegalArgumentException("不能通过编辑文档修改所属知识库");
		}
		if (request.getCategoryId() != null && !Objects.equals(request.getCategoryId(), existing.getCategoryId())) {
			throw new IllegalArgumentException("请使用文档分类移动功能");
		}
		request.setDatasetId(null);
		request.setCategoryId(null);
	}

	private AiDocumentCategoryEntity validateCategory(AiDocumentCategoryDTO request) {
		String name = request.getName() == null ? "" : request.getName().strip();
		if (name.isEmpty() || name.length() > 100 || request.getSort() == null || request.getSort() < 0) {
			throw new IllegalArgumentException("分类名称须为1至100个字符，排序不能为负数");
		}
		List<AiDocumentCategoryEntity> nodes = scopedCategories(request.getDatasetId());
		AiDocumentCategoryTree.validateParent(nodes, request.getId(), request.getParentId());
		if (nodes.stream()
			.anyMatch(node -> !Objects.equals(node.getId(), request.getId())
					&& Objects.equals(node.getParentId(), request.getParentId())
					&& node.getName().equalsIgnoreCase(name))) {
			throw new IllegalArgumentException("同一层级已存在同名分类");
		}
		AiDocumentCategoryEntity result = new AiDocumentCategoryEntity();
		result.setId(request.getId());
		result.setTenantId(tenantId());
		result.setDatasetId(request.getDatasetId());
		result.setParentId(request.getParentId());
		result.setName(name);
		result.setSort(request.getSort());
		return result;
	}

	private List<AiDocumentCategoryEntity> scopedCategories(Long datasetId) {
		return categoryMapper.selectList(Wrappers.<AiDocumentCategoryEntity>lambdaQuery()
			.eq(AiDocumentCategoryEntity::getTenantId, tenantId())
			.eq(AiDocumentCategoryEntity::getDatasetId, datasetId)
			.orderByAsc(AiDocumentCategoryEntity::getSort)
			.orderByAsc(AiDocumentCategoryEntity::getId));
	}

	private void requireCategory(List<AiDocumentCategoryEntity> categories, Long id) {
		if (id == null || categories.stream().noneMatch(category -> Objects.equals(category.getId(), id))) {
			throw new IllegalArgumentException("分类不存在或不属于当前知识库");
		}
	}

	private void requireDataset(Long datasetId, boolean lock) {
		if (datasetId == null || datasetId <= 0) {
			throw new IllegalArgumentException("请选择知识库");
		}
		AiDatasetEntity dataset = datasetMapper.selectOne(Wrappers.<AiDatasetEntity>query()
			.eq("id", datasetId)
			.eq("tenant_id", tenantId())
			.last(lock ? "FOR UPDATE" : ""));
		if (dataset == null) {
			throw new IllegalArgumentException("知识库不存在或无权限访问");
		}
		// 与 AiDatasetService.listDataset 一致：public，或 all/user/role 授权。
		if ("public".equals(dataset.getVisibility())) {
			return;
		}
		Long userId = SecurityUtils.getUser() == null ? null : SecurityUtils.getUser().getId();
		List<Long> roles = SecurityUtils.getRoleIds();
		List<AiDatasetPermissionEntity> permissions = permissionMapper.selectList(
				Wrappers.<AiDatasetPermissionEntity>query().eq("tenant_id", tenantId()).eq("dataset_id", datasetId));
		boolean allowed = permissions.stream()
			.anyMatch(permission -> "all".equals(permission.getPermissionType())
					|| ("user".equals(permission.getPermissionType()) && userId != null
							&& userId.equals(permission.getTargetId()))
					|| ("role".equals(permission.getPermissionType()) && roles != null
							&& roles.contains(permission.getTargetId())));
		if (!allowed) {
			throw new IllegalArgumentException("知识库不存在或无权限访问");
		}
	}

	private Long tenantId() {
		Long tenant = TenantContextHolder.getTenantId();
		if (tenant == null || tenant <= 0 || TenantContextHolder.getTenantSkip()) {
			throw new IllegalArgumentException("缺少有效租户上下文");
		}
		return tenant;
	}

}
