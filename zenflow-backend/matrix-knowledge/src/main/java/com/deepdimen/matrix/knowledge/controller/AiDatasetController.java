package com.deepdimen.matrix.knowledge.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.core.util.SpringContextHolder;
import com.deepdimen.matrix.common.excel.annotation.ResponseExcel;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.deepdimen.matrix.knowledge.config.AiEmbedInitRunner;
import com.deepdimen.matrix.knowledge.dto.AiDatasetEntityDTO;
import com.deepdimen.matrix.knowledge.dto.AiDatasetPermissionDTO;
import com.deepdimen.matrix.knowledge.dto.AiDatasetVerifyDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDatasetPermissionEntity;
import com.deepdimen.matrix.knowledge.service.AiDatasetPermissionService;
import com.deepdimen.matrix.knowledge.service.AiDatasetService;
import com.deepdimen.matrix.knowledge.vo.AiDatasetPermissionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 知识库管理控制器 提供知识库的创建、查询、修改和删除等功能，支持知识库的公开访问验证
 *
 * @author matrix
 * @date 2024-03-14 13:39:21
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/aiDataset")
@Tag(description = "aiDataset", name = "知识库管理")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class AiDatasetController {

	private final AiDatasetService aiDatasetService;

	private final AiDatasetPermissionService aiDatasetPermissionService;

	/**
	 * 获取所有AI数据集列表 返回按排序字段降序排列的数据集列表
	 * @return 包含AI数据集列表的响应结果
	 */
	@Operation(summary = "批量查询", description = "批量查询")
	@GetMapping("/list")
	public R getAiDatasetList() {
		return aiDatasetService.listDataset();
	}

	/**
	 * 分页查询知识库列表
	 * @param page 分页对象
	 * @param aiDataset 知识库查询条件
	 * @return 分页后的知识库列表
	 */
	@Operation(summary = "分页查询", description = "分页查询")
	@GetMapping("/page")
	@HasPermission("knowledge_aiDataset_view")
	public R getAiDatasetPage(@ParameterObject Page page, @ParameterObject AiDatasetEntity aiDataset) {
		return R.ok(aiDatasetService.getAiDatasetPage(page));
	}

	/**
	 * 查询知识库详细信息 根据查询条件获取知识库的详细信息
	 * @param dataset 查询条件
	 * @return 知识库详细信息
	 */
	@Operation(summary = "通过id查询", description = "通过id查询")
	@GetMapping("/details")
	@HasPermission("knowledge_aiDataset_view")
	public R details(@ParameterObject AiDatasetEntity dataset) {
		return aiDatasetService.getAiDataset(dataset);
	}

	/**
	 * 新增知识库 创建新的知识库，包括基本信息和配置参数
	 * @param aiDatasetEntityDTO 知识库实体DTO
	 * @return 操作结果
	 */
	@Operation(summary = "新增知识库", description = "新增知识库")
	@SysLog("新增知识库")
	@PostMapping
	@HasPermission("knowledge_aiDataset_add")
	public R save(@RequestBody AiDatasetEntityDTO aiDatasetEntityDTO) {
		return aiDatasetService.saveDataset(aiDatasetEntityDTO);
	}

	/**
	 * 修改知识库信息 更新知识库的基本信息和配置参数
	 * @param aiDatasetEntityDTO 知识库实体DTO
	 * @return 操作结果
	 */
	@Operation(summary = "修改知识库", description = "修改知识库")
	@SysLog("修改知识库")
	@PutMapping
	@HasPermission("knowledge_aiDataset_edit")
	public R updateById(@RequestBody AiDatasetEntityDTO aiDatasetEntityDTO) {
		return aiDatasetService.updateDataset(aiDatasetEntityDTO);
	}

	/**
	 * 批量删除知识库
	 * @param ids 知识库ID数组
	 * @return 操作结果
	 */
	@Operation(summary = "通过id删除知识库", description = "通过id删除知识库")
	@SysLog("通过id删除知识库")
	@DeleteMapping
	@HasPermission("knowledge_aiDataset_del")
	public R removeById(@RequestBody Long[] ids) {
		aiDatasetService.removeBatchByIds(CollUtil.toList(ids));
		// 级联清理权限记录，防止孤儿数据积累
		aiDatasetPermissionService.remove(Wrappers.<AiDatasetPermissionEntity>lambdaQuery()
			.in(AiDatasetPermissionEntity::getDatasetId, Arrays.asList(ids)));
		SpringContextHolder.publishEvent(new AiEmbedInitRunner.EmbedInitEvent(this));
		return R.ok();
	}

	/**
	 * 导出知识库信息到Excel
	 * @param aiDataset 查询条件
	 * @param ids 指定导出的知识库ID数组
	 * @return Excel文件流
	 */
	@ResponseExcel
	@GetMapping("/export")
	@HasPermission("knowledge_aiDataset_export")
	public List<AiDatasetEntity> export(AiDatasetEntity aiDataset, Long[] ids) {
		return aiDatasetService
			.list(Wrappers.lambdaQuery(aiDataset)
				.in(ArrayUtil.isNotEmpty(ids), AiDatasetEntity::getId, CollUtil.toList(ids)));
	}

	/**
	 * 查询知识库公开信息 用于公开访问时获取知识库的基本信息
	 * @param datasetId 知识库ID
	 * @return 知识库公开信息
	 */
	@Inner(value = false)
	@GetMapping("/info")
	public R info(@RequestParam(required = false) Long datasetId) {
		return aiDatasetService.getDatasetById(datasetId);
	}

	/**
	 * 验证知识库访问密码 用于验证公开知识库的访问权限
	 * @param aiDatasetVerifyDTO 包含知识库ID和访问密码的请求对象
	 * @return 验证结果，成功返回true，失败返回false
	 */
	@Inner(value = false)
	@PostMapping("/verify")
	public R verify(@RequestBody AiDatasetVerifyDTO aiDatasetVerifyDTO) {
		Optional<AiDatasetEntity> opt = aiDatasetService.getOptById(aiDatasetVerifyDTO.getDatasetId());
		if (opt.isEmpty()) {
			return R.failed();
		}

		if (aiDatasetVerifyDTO.getToken().equals(opt.get().getPublicPassword())) {
			return R.ok(true);
		}

		return R.failed();
	}

	/**
	 * 自动生成知识库
	 * @param title 标题
	 * @return 创建结果
	 */
	@GetMapping(value = "/autoGen")
	public R autoGen(@RequestParam String title) {
		return aiDatasetService.autoGen(title);
	}

	/**
	 * 查询知识库权限配置
	 * @param id 知识库ID
	 * @return 权限配置
	 */
	@Operation(summary = "查询知识库权限配置", description = "查询知识库权限配置")
	@GetMapping("/{id}/permission")
	@HasPermission("knowledge_aiDataset_view")
	public R<AiDatasetPermissionVO> getPermission(@PathVariable Long id) {
		if (aiDatasetService.getById(id) == null) {
			return R.failed("知识库不存在");
		}
		return R.ok(aiDatasetPermissionService.getPermission(id));
	}

	/**
	 * 设置知识库权限
	 * @param id 知识库ID
	 * @param dto 权限设置请求
	 * @return 操作结果
	 */
	@Operation(summary = "设置知识库权限", description = "设置知识库权限")
	@SysLog("设置知识库权限")
	@PostMapping("/{id}/permission")
	@HasPermission("knowledge_aiDataset_edit")
	public R<Boolean> setPermission(@PathVariable Long id, @Valid @RequestBody AiDatasetPermissionDTO dto) {
		if (aiDatasetService.getById(id) == null) {
			return R.failed("知识库不存在");
		}
		aiDatasetPermissionService.setPermission(id, dto);
		return R.ok(Boolean.TRUE);
	}

	/**
	 * 获取公开知识库列表
	 * @return 公开知识库列表
	 */
	@Inner(value = false)
	@GetMapping("/share/list")
	public R getShareDataset() {
		return R.ok(aiDatasetService.list(Wrappers.<AiDatasetEntity>lambdaQuery()
			.eq(AiDatasetEntity::getVisibility, "public")
			.orderByDesc(AiDatasetEntity::getSortOrder)));
	}

	/**
	 * 获取公开知识库详细信息
	 * @param dataset 知识库查询条件
	 * @return 详细信息
	 */
	@Inner(value = false)
	@GetMapping("/share/info")
	public R shareInfo(@ParameterObject AiDatasetEntity dataset) {
		return R.ok(aiDatasetService.getOne(Wrappers.<AiDatasetEntity>lambdaQuery()
			.eq(Objects.nonNull(dataset.getId()), AiDatasetEntity::getId, dataset.getId())
			.eq(StrUtil.isNotBlank(dataset.getName()), AiDatasetEntity::getName, dataset.getName()), false));
	}

}
