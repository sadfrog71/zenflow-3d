package com.deepdimen.matrix.knowledge.support.handler.model;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.core.exception.CheckedException;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.config.properties.DefaultModelProperties;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.service.AiAssistantArkService;
import com.deepdimen.matrix.knowledge.service.AiAssistantGiteeService;
import com.deepdimen.matrix.knowledge.service.AiAssistantOpenRouterService;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.service.AiAssistantSiliconflowService;
import com.deepdimen.matrix.knowledge.service.AiFunctionAssistantService;
import com.deepdimen.matrix.knowledge.service.AiNoMemoryStreamAssistantService;
import com.deepdimen.matrix.knowledge.service.AiStreamAssistantService;
import com.deepdimen.matrix.knowledge.service.*;
import com.deepdimen.matrix.knowledge.support.constant.ModelSupportEnums;
import com.deepdimen.matrix.knowledge.support.constant.ModelTypeEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;

import java.util.Objects;

/**
 * 抽象模型构建处理器
 * <p>
 * 这是所有 AI 模型构建处理器的基类，提供了模型管理的核心功能。 主要职责包括： 1. 缓存各种类型的 AI 模型实例（聊天模型、嵌入模型、评分模型等） 2.
 * 提供统一的模型获取接口 3. 管理模型的生命周期（创建、缓存、删除） 4. 处理默认模型和模型配置
 * </p>
 * <p>
 * 使用 LRU 缓存策略，最大缓存 1024 个模型实例。
 * </p>
 *
 * @author caoyuan
 * @date 2024/09/29
 */
@Slf4j
public abstract class AbstractModelBuildHandler implements ModelProvider {

	/**
	 * AI 助手服务缓存
	 * <p>
	 * 缓存同步调用的聊天模型和对应的助手服务实例。 Key: 模型名称，Value: Pair<聊天模型, 助手服务>
	 * </p>
	 */
	final static Cache<String, Pair<ChatModel, AiAssistantService>> AI_ASSISTANT_SERVICE_CACHE = CacheUtil
		.newLRUCache(1024);

	/**
	 * AI JSON 助手服务缓存
	 * <p>
	 * 缓存支持 JSON 输出模式的聊天模型和对应的助手服务实例。 Key: 模型名称，Value: Triple<聊天模型, 助手服务, 响应格式类型>
	 * </p>
	 */
	final static Cache<String, Triple<ChatModel, AiAssistantService, String>> AI_JSON_ASSISTANT_SERVICE_CACHE = CacheUtil
		.newLRUCache(1024);

	/**
	 * AI 流式助手服务缓存
	 * <p>
	 * 缓存流式响应的聊天模型和对应的助手服务实例。 Key: 模型名称，Value: Pair<流式聊天模型, 流式助手服务>
	 * </p>
	 */
	final static Cache<String, Pair<StreamingChatModel, AiStreamAssistantService>> AI_STREAM_ASSISTANT_SERVICE_CACHE = CacheUtil
		.newLRUCache(1024);

	/**
	 * AI 无记忆流式助手服务缓存
	 * <p>
	 * 缓存不保存对话历史的流式聊天模型和对应的助手服务实例。 Key: 模型名称，Value: Pair<流式聊天模型, 无记忆流式助手服务>
	 * </p>
	 */
	final static Cache<String, Pair<StreamingChatModel, AiNoMemoryStreamAssistantService>> AI_NOMEMORY_STREAM_ASSISTANT_SERVICE_CACHE = CacheUtil
		.newLRUCache(1024);

	/**
	 * AI 函数助手服务缓存
	 * <p>
	 * 缓存支持 Function Calling 的流式聊天模型和对应的助手服务实例。 Key: 模型名称，Value: Pair<流式聊天模型, 函数助手服务>
	 * </p>
	 */
	final static Cache<String, Pair<StreamingChatModel, AiFunctionAssistantService>> AI_FUNCTION_ASSISTANT_SERVICE_CACHE = CacheUtil
		.newLRUCache(1024);

	/**
	 * AI 嵌入模型缓存
	 * <p>
	 * 缓存文本嵌入模型，用于将文本转换为向量表示。 Key: 模型名称，Value: 嵌入模型实例
	 * </p>
	 */
	final static Cache<String, DimensionAwareEmbeddingModel> AI_EMBEDDING_CACHE = CacheUtil.newLRUCache(1024);

	/**
	 * AI 图像/文件处理服务缓存
	 * <p>
	 * 缓存图像生成或文件处理相关的服务实例。 Key: 模型名称，Value: Siliconflow 助手服务实例
	 * </p>
	 */
	final static Cache<String, AiAssistantSiliconflowService> AI_IMAGE_CACHE = CacheUtil.newLRUCache(1024);

	/**
	 * AI OpenRouter助手服务缓存，使用LRU算法，最大容量为1024
	 */
	final static Cache<String, AiAssistantOpenRouterService> AI_OPENROUTER_CACHE = CacheUtil.newLRUCache(1024);

	/**
	 * AI助手方舟服务缓存，使用LRU算法，最大容量为1024
	 */
	final static Cache<String, AiAssistantArkService> AI_ARK_CACHE = CacheUtil.newLRUCache(1024);

	/**
	 * Gitee AI助手服务缓存，使用LRU算法，最大容量为1024
	 */
	final static Cache<String, AiAssistantGiteeService> AI_GITEE_CACHE = CacheUtil.newLRUCache(1024);

	/**
	 * AI 重排序模型缓存
	 * <p>
	 * 缓存重排序评分模型，用于对检索结果进行重新排序。 Key: 模型名称，Value: 评分模型实例
	 * </p>
	 */
	final static Cache<String, ScoringModel> AI_RERANKER_CACHE = CacheUtil.newLRUCache(1024);

	/**
	 * 删除指定模型的所有缓存实例
	 * <p>
	 * 从所有缓存中移除指定模型名称的实例，通常在模型配置更新或删除时调用。
	 * </p>
	 * @param modelName 要删除的模型名称
	 */
	@Override
	public void delete(String modelName) {
		for (ModelTypeEnums modelType : ModelTypeEnums.values()) {
			String key = tenantCacheKey(modelType.getType(), modelName);
			AI_ASSISTANT_SERVICE_CACHE.remove(key);
			AI_JSON_ASSISTANT_SERVICE_CACHE.remove(key);
			AI_STREAM_ASSISTANT_SERVICE_CACHE.remove(key);
			AI_NOMEMORY_STREAM_ASSISTANT_SERVICE_CACHE.remove(key);
			AI_FUNCTION_ASSISTANT_SERVICE_CACHE.remove(key);
			AI_EMBEDDING_CACHE.remove(key);
			AI_RERANKER_CACHE.remove(key);
			AI_IMAGE_CACHE.remove(key);
		}
		AI_OPENROUTER_CACHE.remove(tenantCacheKey("OPENROUTER", modelName));
		AI_ARK_CACHE.remove(tenantCacheKey("ARK", modelName));
		AI_GITEE_CACHE.remove(tenantCacheKey("GITEE", modelName));
	}

	/**
	 * Model instances contain tenant credentials, endpoints and provider clients. The
	 * tenant must therefore be part of every cache key even when different tenants
	 * intentionally reuse the same display name.
	 */
	static String tenantCacheKey(String category, String modelName) {
		Long tenantId = TenantContextHolder.getTenantId();
		if (tenantId == null) {
			throw new IllegalStateException("模型调用缺少租户上下文");
		}
		return tenantId + ":" + category + ':'
				+ Objects.toString(modelName, "<default>");
	}

	/**
	 * 获取 AI 助手服务（同步调用）
	 * <p>
	 * 返回指定模型的同步聊天模型和助手服务实例。 如果未指定模型名称，则使用默认模型（GLM_4V_FLASH）。 优先从缓存中获取，如果不存在则创建新实例并缓存。
	 * </p>
	 * @param modelName AI 模型名称，可以为空
	 * @return 包含 ChatModel 和 AiAssistantService 的 Pair 对象
	 */
	@Override
	public Pair<ChatModel, AiAssistantService> getAiAssistant(String modelName) {
		String cacheKey = tenantCacheKey(ModelTypeEnums.CHAT.getType(), modelName);
		Pair<ChatModel, AiAssistantService> aiAssistantServicePair = AI_ASSISTANT_SERVICE_CACHE
			.get(cacheKey);

		if (Objects.nonNull(aiAssistantServicePair)) {
			return aiAssistantServicePair;
		}

		aiAssistantServicePair = buildAiAssistant(getModelByName(modelName, ModelTypeEnums.CHAT));
		AI_ASSISTANT_SERVICE_CACHE.put(cacheKey, aiAssistantServicePair);
		return aiAssistantServicePair;
	}

	/**
	 * 获取 AI 流式助手服务
	 * <p>
	 * 返回指定模型的流式聊天模型和助手服务实例。 流式响应允许实时接收 AI 生成的文本，提供更好的用户体验。
	 * </p>
	 * @param modelName AI 模型名称
	 * @return 包含 StreamingChatModel 和 AiStreamAssistantService 的 Pair 对象
	 */
	@Override
	public Pair<StreamingChatModel, AiStreamAssistantService> getAiStreamAssistant(String modelName) {
		return this.getAiStreamAssistant(modelName, ModelTypeEnums.CHAT);
	}

	/**
	 * 获取AI流式助手服务
	 * @param modelName 模型名称
	 * @param modelTypeEnums 模型类型枚举
	 * @return AI流式助手模型和服务的配对对象
	 */
	@Override
	public Pair<StreamingChatModel, AiStreamAssistantService> getAiStreamAssistant(String modelName,
			ModelTypeEnums modelTypeEnums) {
		String cacheKey = tenantCacheKey(modelTypeEnums.getType(), modelName);
		Pair<StreamingChatModel, AiStreamAssistantService> aiStreamAssistantServicePair = AI_STREAM_ASSISTANT_SERVICE_CACHE
			.get(cacheKey);

		if (Objects.nonNull(aiStreamAssistantServicePair)) {
			return aiStreamAssistantServicePair;
		}

		aiStreamAssistantServicePair = buildAiStreamAssistant(getModelByName(modelName, modelTypeEnums));
		AI_STREAM_ASSISTANT_SERVICE_CACHE.put(cacheKey, aiStreamAssistantServicePair);
		return aiStreamAssistantServicePair;
	}

	/**
	 * 获取 AI 函数助手服务
	 * <p>
	 * 返回支持 Function Calling 功能的流式聊天模型和助手服务实例。 Function Calling 允许 AI
	 * 调用预定义的函数/工具，实现更复杂的交互逻辑。
	 * </p>
	 * @param modelName AI 模型名称
	 * @return 包含 StreamingChatModel 和 AiFunctionAssistantService 的 Pair 对象
	 */
	@Override
	public Pair<StreamingChatModel, AiFunctionAssistantService> getAiFunctionAssistant(String modelName) {
		String cacheKey = tenantCacheKey(ModelTypeEnums.CHAT.getType(), modelName);
		Pair<StreamingChatModel, AiFunctionAssistantService> aiFunctionAssistantServicePair = AI_FUNCTION_ASSISTANT_SERVICE_CACHE
			.get(cacheKey);

		if (Objects.nonNull(aiFunctionAssistantServicePair)) {
			return aiFunctionAssistantServicePair;
		}

		aiFunctionAssistantServicePair = buildAiFunctionAssistant(getModelByName(modelName, ModelTypeEnums.CHAT));
		AI_FUNCTION_ASSISTANT_SERVICE_CACHE.put(cacheKey, aiFunctionAssistantServicePair);
		return aiFunctionAssistantServicePair;
	}

	/**
	 * 获取文本嵌入模型
	 * <p>
	 * 返回指定的文本嵌入模型实例，用于将文本转换为向量表示。 这些向量可以用于语义搜索、文本相似度计算等任务。
	 * </p>
	 * @param modelName 嵌入模型名称
	 * @return 支持维度感知的嵌入模型实例
	 */
	@Override
	public DimensionAwareEmbeddingModel getEmbeddingModel(String modelName) {
		String cacheKey = tenantCacheKey(ModelTypeEnums.EMBEDDING.getType(), modelName);
		DimensionAwareEmbeddingModel embeddingModel = AI_EMBEDDING_CACHE.get(cacheKey);

		if (Objects.nonNull(embeddingModel)) {
			return embeddingModel;
		}

		embeddingModel = buildEmbeddingModel(getModelByName(modelName, ModelTypeEnums.EMBEDDING));
		AI_EMBEDDING_CACHE.put(cacheKey, embeddingModel);
		return embeddingModel;
	}

	/**
	 * 获取重排序模型
	 * <p>
	 * 返回指定的重排序评分模型实例，用于对检索结果进行重新排序。 重排序模型可以根据查询和文档的相关性重新评分，提高检索质量。 如果模型不存在，返回 null。
	 * </p>
	 * @param modelName 重排序模型名称
	 * @return 评分模型实例，如果不存在则返回 null
	 */
	@Override
	public ScoringModel getRerankerModel(String modelName) {
		String cacheKey = tenantCacheKey(ModelTypeEnums.RERANKER.getType(), modelName);
		ScoringModel scoringModel = AI_RERANKER_CACHE.get(cacheKey);

		if (Objects.nonNull(scoringModel)) {
			return scoringModel;
		}

		AiModelMapper modelMapper = SpringUtil.getBean(AiModelMapper.class);
		AiModelEntity aiModelEntity = modelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getName, modelName)
			.eq(AiModelEntity::getModelType, ModelTypeEnums.RERANKER.getType()), false);

		if (Objects.isNull(aiModelEntity)) {
			return null;
		}

		scoringModel = buildScoringModel(aiModelEntity);
		AI_RERANKER_CACHE.put(cacheKey, scoringModel);
		return scoringModel;
	}

	/**
	 * 获取 AI JSON 助手服务
	 * <p>
	 * 返回支持 JSON 格式输出的聊天模型和助手服务实例。 这种模式确保 AI 输出的内容是结构化的 JSON 格式， 方便程序解析和处理。
	 * </p>
	 * @param modelName AI 模型名称，可以为空
	 * @return Triple 对象，包含：ChatModel、AiAssistantService 和响应格式类型
	 */
	@Override
	public Triple<ChatModel, AiAssistantService, String> getAiJSONAssistant(String modelName) {
		String cacheKey = tenantCacheKey(ModelTypeEnums.CHAT.getType(), modelName);
		Triple<ChatModel, AiAssistantService, String> aiAssistantServicePair = null;
		if (StrUtil.isNotBlank(modelName)) {
			aiAssistantServicePair = AI_JSON_ASSISTANT_SERVICE_CACHE.get(cacheKey);

			if (Objects.nonNull(aiAssistantServicePair)) {
				return aiAssistantServicePair;
			}
		}

		aiAssistantServicePair = buildAiJSONAssistant(getModelByName(modelName, ModelTypeEnums.CHAT));
		AI_JSON_ASSISTANT_SERVICE_CACHE.put(cacheKey, aiAssistantServicePair);
		return aiAssistantServicePair;
	}

	/**
	 * 获取文件/图像处理助手服务
	 * <p>
	 * 返回用于处理文件或图像的服务实例。 目前主要用于 Siliconflow 等服务的图像生成或文件处理。
	 * </p>
	 * @param modelName 模型名称
	 * @return Siliconflow 助手服务实例
	 */
	@Override
	public AiAssistantSiliconflowService getSiliconflowAssistant(String modelName) {
		String cacheKey = tenantCacheKey(ModelTypeEnums.IMAGE.getType(), modelName);
		AiAssistantSiliconflowService imageModel = AI_IMAGE_CACHE.get(cacheKey);

		if (Objects.nonNull(imageModel)) {
			return imageModel;
		}

		imageModel = buildFileAssistant(getModelByName(modelName, ModelTypeEnums.IMAGE));
		AI_IMAGE_CACHE.put(cacheKey, imageModel);
		return imageModel;
	}

	/**
	 * 获取OpenRouter助手服务
	 * @param model 模型名称
	 * @return OpenRouter助手服务实例
	 */
	@Override
	public AiAssistantOpenRouterService getOpenRouterAssistant(String model) {
		String cacheKey = tenantCacheKey("OPENROUTER", model);
		AiAssistantOpenRouterService routerAssistantService = AI_OPENROUTER_CACHE.get(cacheKey);

		if (Objects.nonNull(routerAssistantService)) {
			return routerAssistantService;
		}

		routerAssistantService = buildOpenRouterAssistant(getModelByName(model, ModelTypeEnums.IMAGE));
		AI_OPENROUTER_CACHE.put(cacheKey, routerAssistantService);
		return routerAssistantService;
	}

	/**
	 * 获取ARK助手服务
	 * @param model 模型名称
	 * @return ARK助手服务实例
	 */
	@Override
	public AiAssistantArkService getArkAssistant(String model) {
		String cacheKey = tenantCacheKey("ARK", model);
		AiAssistantArkService arkAssistantService = AI_ARK_CACHE.get(cacheKey);

		if (Objects.nonNull(arkAssistantService)) {
			return arkAssistantService;
		}

		arkAssistantService = buildArkAssistant(getModelByName(model, ModelTypeEnums.IMAGE));
		AI_ARK_CACHE.put(cacheKey, arkAssistantService);
		return arkAssistantService;
	}

	/**
	 * 获取Gitee AI助手服务
	 * @param model 模型名称
	 * @return Gitee AI助手服务实例
	 */
	@Override
	public AiAssistantGiteeService getGiteeAssistant(String model) {
		String cacheKey = tenantCacheKey("GITEE", model);
		AiAssistantGiteeService giteeAssistantService = AI_GITEE_CACHE.get(cacheKey);

		if (Objects.nonNull(giteeAssistantService)) {
			return giteeAssistantService;
		}

		giteeAssistantService = buildGiteeAssistant(getModelByName(model, ModelTypeEnums.VOICE));
		AI_GITEE_CACHE.put(cacheKey, giteeAssistantService);
		return giteeAssistantService;
	}

	/**
	 * 获取默认的 AI 助手服务
	 * <p>
	 * 返回系统配置的默认聊天模型和助手服务实例。 默认模型是在数据库中标记为 default_model=1 的 CHAT 类型模型。 如果没有配置默认模型，将抛出异常。
	 * </p>
	 * @return 包含默认 ChatModel 和 AiAssistantService 的 Pair 对象
	 * @throws CheckedException 当默认模型不存在时抛出
	 */
	@Override
	public Pair<ChatModel, AiAssistantService> getAiAssistant() {
		AiModelMapper modelMapper = SpringUtil.getBean(AiModelMapper.class);
		AiModelEntity aiModelEntity = modelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getDefaultModel, YesNoEnum.YES.getCode())
			.eq(AiModelEntity::getModelType, ModelTypeEnums.CHAT.getType()), false);
		if (Objects.isNull(aiModelEntity)) {
			// 默认使用系统内置模型
			return getAiAssistant(ModelSupportEnums.GLM_4V_FLASH.name());
		}

		return getAiAssistant(aiModelEntity.getName());
	}

	/**
	 * 获取 AI 视觉助手服务
	 * <p>
	 * 返回支持图像理解的聊天模型和助手服务实例。 优先使用系统配置的默认 VISION 类型模型， 如果没有配置，则回退到使用系统内置模型。
	 * </p>
	 * @return 包含 ChatModel 和 AiAssistantService 的 Pair 对象
	 */
	@Override
	public Pair<ChatModel, AiAssistantService> getAiVisionAssistant() {
		AiModelMapper modelMapper = SpringUtil.getBean(AiModelMapper.class);
		AiModelEntity aiModelEntity = modelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getDefaultModel, YesNoEnum.YES.getCode())
			.eq(AiModelEntity::getModelType, ModelTypeEnums.VISION.getType()), false);
		if (Objects.isNull(aiModelEntity)) {
			// 默认使用系统内置模型
			return getAiAssistant(ModelSupportEnums.GLM_4V_FLASH.name());
		}

		return getAiAssistant(aiModelEntity.getName());
	}

	/**
	 * 获取 AI 视觉流式助手服务
	 * <p>
	 * 返回支持图像理解的流式聊天模型和助手服务实例。 优先使用系统配置的默认 VISION 类型模型， 如果没有配置，则使用默认的 GLM_4V_FLASH 模型。
	 * </p>
	 * @return 包含 StreamingChatModel 和 AiStreamAssistantService 的 Pair 对象
	 */
	@Override
	public Pair<StreamingChatModel, AiStreamAssistantService> getAiVisionStreamAssistant() {
		AiModelMapper modelMapper = SpringUtil.getBean(AiModelMapper.class);
		AiModelEntity aiModelEntity = modelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getDefaultModel, YesNoEnum.YES.getCode())
			.eq(AiModelEntity::getModelType, ModelTypeEnums.VISION.getType()), false);
		if (Objects.isNull(aiModelEntity)) {
			// 默认使用系统内置模型
			return getAiStreamAssistant(ModelSupportEnums.GLM_4V_FLASH.name());
		}

		return getAiStreamAssistant(aiModelEntity.getName());
	}

	/**
	 * 根据模型名称获取模型配置
	 * <p>
	 * 从数据库中查询指定名称的模型配置。 如果没有指定模型名称，则返回指定类型的默认模型。 如果模型不存在，则使用系统内置的默认模型配置。
	 * </p>
	 * @param modelName 模型名称，可以为空
	 * @param modelTypeEnum 模型类型枚举
	 * @return 模型配置实体
	 */
	public AiModelEntity getModelByName(String modelName, ModelTypeEnums modelTypeEnum) {
		AiModelMapper modelMapper = SpringUtil.getBean(AiModelMapper.class);

		AiModelEntity aiModelEntity;
		if (StrUtil.isNotBlank(modelName)) {
			aiModelEntity = modelMapper
				.selectOne(Wrappers.<AiModelEntity>lambdaQuery().eq(AiModelEntity::getName, modelName), false);
		}
		else {
			aiModelEntity = modelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
				.eq(Objects.nonNull(modelTypeEnum), AiModelEntity::getModelType, modelTypeEnum.getType())
				.eq(AiModelEntity::getDefaultModel, YesNoEnum.YES.getCode()), false);
		}

		// 视觉模型和文本模型
		if (Objects.isNull(aiModelEntity) && StrUtil.equalsAnyIgnoreCase(modelTypeEnum.getType(),
				ModelTypeEnums.CHAT.getType(), ModelTypeEnums.VISION.getType())) {
			log.error("模型不存在:{}，请先在模型配置中添加，默认使用系统内置模型", modelName);
			AiModelEntity defaultModel = new AiModelEntity();
			AiKnowledgeProperties aiKnowledgeProperties = SpringUtil.getBean(AiKnowledgeProperties.class);
			DefaultModelProperties modelProperties = aiKnowledgeProperties.getDefaultModel();
			defaultModel.setName(modelProperties.getModel().getCode());
			defaultModel.setApiKey(modelProperties.getApiKey());
			defaultModel.setBaseUrl(modelProperties.getBaseUrl());
			defaultModel.setModelName(modelProperties.getModel().getCode());
			return defaultModel;
		}

		// 向量模型
		if (Objects.isNull(aiModelEntity)
				&& StrUtil.equalsAnyIgnoreCase(modelTypeEnum.getType(), ModelTypeEnums.EMBEDDING.getType())) {
			log.error("模型不存在:{}，请先在模型配置中添加，默认使用系统内置模型", modelName);
			AiModelEntity defaultModel = new AiModelEntity();
			AiKnowledgeProperties aiKnowledgeProperties = SpringUtil.getBean(AiKnowledgeProperties.class);
			DefaultModelProperties modelProperties = aiKnowledgeProperties.getDefaultModel();
			defaultModel.setName(modelProperties.getEmbedModel().getCode());
			defaultModel.setApiKey(modelProperties.getEmbedApiKey());
			defaultModel.setBaseUrl(modelProperties.getEmbedBaseUrl());
			defaultModel.setModelName(modelProperties.getEmbedModel().getCode());
			return defaultModel;
		}
		return aiModelEntity;
	}

	/**
	 * 获取 AI 无记忆流式助手服务
	 * <p>
	 * 返回不保存对话历史的流式聊天模型和助手服务实例。 适用于不需要上下文记忆的场景，如一次性问答、单轮对话等。
	 * </p>
	 * @param modelName AI 模型名称
	 * @return 包含 StreamingChatModel 和 AiNoMemoryStreamAssistantService 的 Pair 对象
	 */
	@Override
	public Pair<StreamingChatModel, AiNoMemoryStreamAssistantService> getAiNoMemoryStreamAssistant(String modelName) {
		String cacheKey = tenantCacheKey(ModelTypeEnums.CHAT.getType(), modelName);
		Pair<StreamingChatModel, AiNoMemoryStreamAssistantService> aiStreamAssistantServicePair = AI_NOMEMORY_STREAM_ASSISTANT_SERVICE_CACHE
			.get(cacheKey);

		if (Objects.nonNull(aiStreamAssistantServicePair)) {
			return aiStreamAssistantServicePair;
		}

		aiStreamAssistantServicePair = buildAiNoMemoryStreamAssistant(getModelByName(modelName, ModelTypeEnums.CHAT));
		AI_NOMEMORY_STREAM_ASSISTANT_SERVICE_CACHE.put(cacheKey, aiStreamAssistantServicePair);
		return aiStreamAssistantServicePair;
	}

	/**
	 * 清除所有模型缓存
	 * <p>
	 * 清空所有类型的模型缓存，通常在系统重置或配置大规模更新时使用。 清除后，下次调用时将重新创建模型实例。
	 * </p>
	 */
	@Override
	public void clearModels() {
		AI_ASSISTANT_SERVICE_CACHE.clear();
		AI_JSON_ASSISTANT_SERVICE_CACHE.clear();
		AI_STREAM_ASSISTANT_SERVICE_CACHE.clear();
		AI_FUNCTION_ASSISTANT_SERVICE_CACHE.clear();
		AI_NOMEMORY_STREAM_ASSISTANT_SERVICE_CACHE.clear();
		AI_EMBEDDING_CACHE.clear();
		AI_RERANKER_CACHE.clear();
		AI_IMAGE_CACHE.clear();
		AI_OPENROUTER_CACHE.clear();
		AI_ARK_CACHE.clear();
		AI_GITEE_CACHE.clear();
	}

}
