package com.deepdimen.matrix.knowledge.support.provider;

import cn.hutool.core.lang.Pair;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.service.AiAssistantArkService;
import com.deepdimen.matrix.knowledge.service.AiAssistantGiteeService;
import com.deepdimen.matrix.knowledge.service.AiAssistantOpenRouterService;
import com.deepdimen.matrix.knowledge.service.AiAssistantService;
import com.deepdimen.matrix.knowledge.service.AiAssistantSiliconflowService;
import com.deepdimen.matrix.knowledge.service.AiFunctionAssistantService;
import com.deepdimen.matrix.knowledge.service.AiNoMemoryStreamAssistantService;
import com.deepdimen.matrix.knowledge.service.AiStreamAssistantService;
import com.deepdimen.matrix.knowledge.service.*;
import com.deepdimen.matrix.knowledge.support.constant.ModelTypeEnums;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import org.apache.commons.lang3.tuple.Triple;

/**
 * AI模型提供者接口
 * <p>
 * 定义了AI模型的构建、获取和管理功能 支持多种模型类型：聊天模型、流式模型、嵌入模型、评分模型等 提供模型的生命周期管理和缓存机制
 *
 * @author caoyuan
 * @date 2024/09/29
 */
public interface ModelProvider {

	/**
	 * 清除所有缓存的模型实例
	 */
	void clearModels();

	/**
	 * 判断是否支持指定的模型类型
	 * @param aiModel 模型实体
	 * @return 是否支持
	 */
	boolean support(AiModelEntity aiModel);

	/**
	 * 校验模型参数是否合法
	 * @param aiModel 模型实体
	 * @return 参数是否有效
	 */
	default boolean checkParams(AiModelEntity aiModel) {
		return true;
	}

	/**
	 * 构建同步聊天助手
	 * @param aiModel AI模型配置
	 * @return 聊天模型和助手服务的组合
	 */
	Pair<ChatModel, AiAssistantService> buildAiAssistant(AiModelEntity aiModel);

	/**
	 * 构建JSON格式输出的聊天助手
	 * @param aiModel AI模型配置
	 * @return 聊天模型、助手服务和JSON格式类型的三元组
	 */
	Triple<ChatModel, AiAssistantService, String> buildAiJSONAssistant(AiModelEntity aiModel);

	/**
	 * 构建流式聊天助手
	 * @param aiModel AI模型配置
	 * @return 流式聊天模型和助手服务的组合
	 */
	Pair<StreamingChatModel, AiStreamAssistantService> buildAiStreamAssistant(AiModelEntity aiModel);

	/**
	 * 构建无记忆的流式聊天助手
	 * <p>
	 * 适用于不需要会话历史的场景
	 * @param aiModel AI模型配置
	 * @return 流式聊天模型和无记忆助手服务的组合
	 */
	Pair<StreamingChatModel, AiNoMemoryStreamAssistantService> buildAiNoMemoryStreamAssistant(AiModelEntity aiModel);

	/**
	 * Builds an assistant only from a model that has already been resolved inside the
	 * caller's tenant boundary. Native assistant code must use this method (or the
	 * equivalent build method), never a name/default lookup that can fall back to a
	 * system model.
	 */
	default Pair<StreamingChatModel, AiNoMemoryStreamAssistantService> buildResolvedAiNoMemoryStreamAssistant(
			AiModelEntity resolvedModel) {
		if (resolvedModel == null || resolvedModel.getId() == null || resolvedModel.getName() == null
				|| resolvedModel.getName().isBlank()) {
			throw new IllegalArgumentException("MODEL_NOT_AVAILABLE");
		}
		return buildAiNoMemoryStreamAssistant(resolvedModel);
	}

	default Pair<ChatModel, AiAssistantService> buildResolvedAiAssistant(AiModelEntity resolvedModel) {
		if (resolvedModel == null || resolvedModel.getId() == null || resolvedModel.getName() == null
				|| resolvedModel.getName().isBlank()) {
			throw new IllegalArgumentException("MODEL_NOT_AVAILABLE");
		}
		return buildAiAssistant(resolvedModel);
	}

	/**
	 * 构建函数调用助手
	 * <p>
	 * 支持Function Calling功能的助手
	 * @param aiModel AI模型配置
	 * @return 流式聊天模型和函数助手服务的组合
	 */
	Pair<StreamingChatModel, AiFunctionAssistantService> buildAiFunctionAssistant(AiModelEntity aiModel);

	/**
	 * 构建向量嵌入模型
	 * @param model 模型配置
	 * @return 嵌入模型实例
	 */
	DimensionAwareEmbeddingModel buildEmbeddingModel(AiModelEntity model);

	/**
	 * 构建重排序评分模型
	 * @param model 模型配置
	 * @return 评分模型实例
	 */
	ScoringModel buildScoringModel(AiModelEntity model);

	/**
	 * 构建文件处理助手
	 * <p>
	 * 用于处理图像、音频、视频等文件
	 * @param model 模型配置
	 * @return 文件处理助手服务
	 */
	AiAssistantSiliconflowService buildFileAssistant(AiModelEntity model);

	/**
	 * 构建OpenRouter助手服务
	 * @param model AI模型实体
	 * @return OpenRouter助手服务实例
	 */
	AiAssistantOpenRouterService buildOpenRouterAssistant(AiModelEntity model);

	/**
	 * 构建ARK助手服务
	 * @param model AI模型实体
	 * @return ARK助手服务实例
	 */
	AiAssistantArkService buildArkAssistant(AiModelEntity model);

	/**
	 * 构建Gitee AI助手服务
	 * @param model AI模型实体
	 * @return Gitee AI助手服务实例
	 */
	AiAssistantGiteeService buildGiteeAssistant(AiModelEntity model);

	/**
	 * 获取文件处理助手
	 * @param model 模型名称
	 * @return 文件处理助手服务
	 */
	AiAssistantSiliconflowService getSiliconflowAssistant(String model);

	/**
	 * 获取OpenRouter助手服务
	 * @param model 模型名称
	 * @return OpenRouter助手服务实例
	 */
	AiAssistantOpenRouterService getOpenRouterAssistant(String model);

	/**
	 * 获取指定模型的AI助手服务
	 * @param model 模型名称
	 * @return 对应的AI助手服务实例
	 */
	AiAssistantArkService getArkAssistant(String model);

	/**
	 * 获取Gitee AI助手服务
	 * @param model 模型名称
	 * @return Gitee AI助手服务实例
	 */
	AiAssistantGiteeService getGiteeAssistant(String model);

	/**
	 * 获取指定名称的AI助手服务
	 * @param modelName 模型名称
	 * @return 聊天模型和助手服务的组合
	 */
	Pair<ChatModel, AiAssistantService> getAiAssistant(String modelName);

	/**
	 * 获取默认的AI助手
	 * @return 默认聊天模型和助手服务的组合
	 */
	Pair<ChatModel, AiAssistantService> getAiAssistant();

	/**
	 * 获取视觉理解AI助手
	 * @return 视觉模型和助手服务的组合
	 */
	Pair<ChatModel, AiAssistantService> getAiVisionAssistant();

	/**
	 * 获取视觉理解流式AI助手
	 * @return 视觉流式模型和助手服务的组合
	 */
	Pair<StreamingChatModel, AiStreamAssistantService> getAiVisionStreamAssistant();

	/**
	 * 获取JSON格式输出的AI助手
	 * @param modelName 模型名称
	 * @return 聊天模型、助手服务和JSON格式类型的三元组
	 */
	Triple<ChatModel, AiAssistantService, String> getAiJSONAssistant(String modelName);

	/**
	 * 获取流式AI助手
	 * @param modelName 模型名称
	 * @return 流式聊天模型和助手服务的组合
	 */
	Pair<StreamingChatModel, AiStreamAssistantService> getAiStreamAssistant(String modelName);

	/**
	 * 获取无记忆的流式AI助手
	 * @param modelName 模型名称
	 * @return 流式聊天模型和无记忆助手服务的组合
	 */
	Pair<StreamingChatModel, AiNoMemoryStreamAssistantService> getAiNoMemoryStreamAssistant(String modelName);

	/**
	 * 获取函数调用AI助手
	 * @param modelName 模型名称
	 * @return 流式聊天模型和函数助手服务的组合
	 */
	Pair<StreamingChatModel, AiFunctionAssistantService> getAiFunctionAssistant(String modelName);

	/**
	 * 获取向量嵌入模型
	 * @param modelName 模型名称
	 * @return 嵌入模型实例
	 */
	DimensionAwareEmbeddingModel getEmbeddingModel(String modelName);

	/**
	 * 获取重排序评分模型
	 * @param modelName 模型名称
	 * @return 评分模型实例
	 */
	ScoringModel getRerankerModel(String modelName);

	/**
	 * 删除指定的模型缓存
	 * @param modelName 模型名称
	 */
	void delete(String modelName);

	/**
	 * 获取AI流式对话助手服务
	 * @param modelName 模型名称
	 * @param modelTypeEnums 模型类型枚举
	 * @return 包含流式对话模型和助手服务的配对对象
	 */
	Pair<StreamingChatModel, AiStreamAssistantService> getAiStreamAssistant(String modelName,
			ModelTypeEnums modelTypeEnums);

}
