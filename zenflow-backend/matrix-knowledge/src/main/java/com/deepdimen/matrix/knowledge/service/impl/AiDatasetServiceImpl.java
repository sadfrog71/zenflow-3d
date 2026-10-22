package com.deepdimen.matrix.knowledge.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.core.util.SpringContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.config.AiEmbedInitRunner;
import com.deepdimen.matrix.knowledge.dto.AiDatasetEntityDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDatasetPermissionEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDatasetMapper;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.service.AiDatasetPermissionService;
import com.deepdimen.matrix.knowledge.service.AiDatasetService;
import com.deepdimen.matrix.knowledge.support.provider.ChatMemoryAdvisorProvider;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;

/**
 * AI知识库服务实现类
 * <p>
 * 管理知识库的创建、查询和权限控制 支持公开和私有知识库的访问管理 提供用户级别的可见性设置
 *
 * @author matrix
 * @date 2024-03-14 13:39:21
 */
@Service
@RequiredArgsConstructor
public class AiDatasetServiceImpl extends ServiceImpl<AiDatasetMapper, AiDatasetEntity> implements AiDatasetService {

	/**
	 * 知识库权限服务
	 */
	private final AiDatasetPermissionService aiDatasetPermissionService;

	/**
	 *
	 */
	private final ModelProvider modelProvider;

	/**
	 *
	 */
	private final ChatMemoryAdvisorProvider chatMemoryAdvisorProvider;

	/**
	 * 分页查询知识库列表
	 * <p>
	 * 支持按条件筛选和分页
	 * @param page 分页参数
	 * @return 知识库分页数据
	 */
	@Override
	public Page<AiDatasetEntity> getAiDatasetPage(Page<AiDatasetEntity> page) {
		return baseMapper.getAiDatasetPage(page);
	}

	/**
	 * 根据ID查询公开知识库
	 * <p>
	 * 仅返回公开的知识库信息 私有知识库将返回访问失败
	 * @param datasetId 知识库ID
	 * @return 知识库信息或错误提示
	 */
	@Override
	public R getDatasetById(Long datasetId) {
		if (datasetId == null) {
			return R.failed("目标知识库ID不能为空");
		}

		AiDatasetEntity dataset = baseMapper.selectById(datasetId);
		if (Objects.isNull(dataset)) {
			return R.failed("知识库不存在");
		}
		if ("private".equals(dataset.getVisibility())) {
			return R.failed("私有知识库，无法查看");
		}

		return R.ok(dataset);
	}

	/**
	 * 创建新知识库
	 * <p>
	 * 自动生成集合名称 支持设置可见用户列表 使用事务确保数据一致性
	 * @param aiDatasetEntityDTO 知识库创建参数
	 * @return 创建结果
	 * @throws Exception 创建失败时抛出
	 */
	@SneakyThrows
	@Override
	@Transactional(rollbackFor = Exception.class)
	public R saveDataset(AiDatasetEntityDTO aiDatasetEntityDTO) {
		AiDatasetEntity aiDataset = new AiDatasetEntity();
		BeanUtils.copyProperties(aiDatasetEntityDTO, aiDataset);
		// 新建知识库默认私有，防止前端未传 visibility 时被当作公开知识库
		if (aiDataset.getVisibility() == null) {
			aiDataset.setVisibility("private");
		}
		this.save(aiDataset);
		aiDataset.setCollectionName(SecurityConstants.MATRIX_PREFIX + aiDataset.getId());
		baseMapper.updateById(aiDataset);

		// 创建默认权限（permission_type=all）
		aiDatasetPermissionService.createDefaultPermission(aiDataset.getId());

		// 发布知识库初始化事件，异步触发向量数据初始化
		SpringContextHolder.publishEvent(new AiEmbedInitRunner.EmbedInitEvent(this));
		return R.ok();
	}

	/**
	 * 更新知识库信息
	 * <p>
	 * 更新基本信息和可见用户列表
	 * @param aiDatasetEntityDTO 知识库更新参数
	 * @return 更新结果
	 */
	@Override
	public R updateDataset(AiDatasetEntityDTO aiDatasetEntityDTO) {
		AiDatasetEntity aiDataset = new AiDatasetEntity();
		BeanUtils.copyProperties(aiDatasetEntityDTO, aiDataset);
		this.updateById(aiDataset);

		SpringContextHolder.publishEvent(new AiEmbedInitRunner.EmbedInitEvent(this));
		return R.ok();
	}

	/**
	 * 查询知识库详细信息
	 * <p>
	 * 包含基本信息和可见用户列表 支持按ID或名称查询
	 * @param dataset 查询条件（ID或名称）
	 * @return 知识库详细信息
	 */
	@Override
	public R getAiDataset(AiDatasetEntity dataset) {
		AiDatasetEntityDTO aiDatasetEntityDTO = new AiDatasetEntityDTO();
		AiDatasetEntity aiDatasetEntity = baseMapper.selectOne(Wrappers.<AiDatasetEntity>lambdaQuery()
			.eq(Objects.nonNull(dataset.getId()), AiDatasetEntity::getId, dataset.getId())
			.eq(StrUtil.isNotBlank(dataset.getName()), AiDatasetEntity::getName, dataset.getName()), false);

		if (Objects.isNull(aiDatasetEntity)) {
			return R.ok();
		}

		BeanUtils.copyProperties(aiDatasetEntity, aiDatasetEntityDTO);
		// 返回新权限配置
		aiDatasetEntityDTO.setPermissionConfig(aiDatasetPermissionService.getPermission(aiDatasetEntity.getId()));
		return R.ok(aiDatasetEntityDTO);
	}

	/**
	 * 获取当前用户可访问的知识库列表
	 * <p>
	 * visibility=public 的知识库对所有人可见； visibility=private 的知识库根据 ai_dataset_permission 表过滤：
	 * permission_type=all 全部可见，permission_type=user/role 按目标ID匹配
	 * @return 可访问的知识库列表
	 */
	@Override
	public R listDataset() {
		Long userId = SecurityUtils.getUser().getId();
		List<Long> roleIds = SecurityUtils.getRoleIds();

		// 1. visibility=public 的知识库
		List<Long> publicIds = baseMapper
			.selectList(Wrappers.<AiDatasetEntity>lambdaQuery().eq(AiDatasetEntity::getVisibility, "public"))
			.stream()
			.map(AiDatasetEntity::getId)
			.toList();

		// 2. ai_dataset_permission 中 permission_type=all 的知识库
		List<Long> allPermIds = aiDatasetPermissionService
			.list(Wrappers.<AiDatasetPermissionEntity>lambdaQuery()
				.eq(AiDatasetPermissionEntity::getPermissionType, "all"))
			.stream()
			.map(AiDatasetPermissionEntity::getDatasetId)
			.toList();

		// 3. permission_type=user 且 target_id=当前用户ID
		List<Long> userPermIds = aiDatasetPermissionService
			.list(Wrappers.<AiDatasetPermissionEntity>lambdaQuery()
				.eq(AiDatasetPermissionEntity::getPermissionType, "user")
				.eq(AiDatasetPermissionEntity::getTargetId, userId))
			.stream()
			.map(AiDatasetPermissionEntity::getDatasetId)
			.toList();

		// 4. permission_type=role 且 target_id in 当前用户角色ID列表
		List<Long> rolePermIds = new ArrayList<>();
		if (CollUtil.isNotEmpty(roleIds)) {
			rolePermIds = aiDatasetPermissionService
				.list(Wrappers.<AiDatasetPermissionEntity>lambdaQuery()
					.eq(AiDatasetPermissionEntity::getPermissionType, "role")
					.in(AiDatasetPermissionEntity::getTargetId, roleIds))
				.stream()
				.map(AiDatasetPermissionEntity::getDatasetId)
				.toList();
		}

		Set<Long> idList = new HashSet<>();
		idList.addAll(publicIds);
		idList.addAll(allPermIds);
		idList.addAll(userPermIds);
		idList.addAll(rolePermIds);

		if (CollUtil.isEmpty(idList)) {
			return R.ok(new ArrayList<>());
		}

		return R.ok(baseMapper.listAccessibleWithUnits(idList, TenantContextHolder.getTenantId()));
	}

	/**
	 * @param title AI数据集实体
	 * @return 生成结果
	 */
	@Override
	public R autoGen(String title) {
		Triple<ChatModel, AiAssistantService, String> jsonAssistantTriple = modelProvider
			.getAiJSONAssistant("qwen-chat");
		ChatRequest.Builder builder = ChatRequest.builder();
		ChatMemory chatMemory = chatMemoryAdvisorProvider.get(IdUtil.simpleUUID());
		chatMemory.add(userMessage(PromptBuilder.render("user-json.st", Map.of("userInput", title, "jsonSchema", ""))));
		List<ChatMessage> chatMessages = new ArrayList<>();
		chatMessages.add(systemMessage(PromptBuilder.render("agent-system-json.st")));
		chatMessages.addAll(chatMemory.messages());
		builder.messages(chatMessages);
		ChatResponse chatResponse = jsonAssistantTriple.getLeft().chat(builder.build());
		return R.ok(JSONUtil.parseObj(chatResponse.aiMessage().text()));
	}

}
