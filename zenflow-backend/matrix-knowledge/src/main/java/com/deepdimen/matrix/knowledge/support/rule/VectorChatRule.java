package com.deepdimen.matrix.knowledge.support.rule;

import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.service.AiDatasetService;
import com.deepdimen.matrix.knowledge.support.constant.EmbedBizTypeEnums;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.UnifiedRagStrategy;
import com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Objects;

import static com.deepdimen.matrix.knowledge.support.provider.EmbeddingProvider.TEMP_ID;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 基于向量知识库的聊天规则
 * <p>
 * 重构后使用统一RAG服务，实现检索增强生成(RAG)的聊天功能： 1. 向量检索 - 将用户问题转换为向量，在知识库中检索相关内容 2. 智能策略选择 -
 * 自动选择标准问答或向量检索策略 3. 降级处理 - 标准化数据优先，无匹配时自动降级到语义检索
 * </p>
 *
 * <p>
 * 使用场景： - 企业知识库问答 - 文档检索与总结 - FAQ自动回复 - 专业领域问答
 * </p>
 *
 * @author caoyuan
 * @date 2024/3/26
 */
@Slf4j
@Component("vectorChat")
public class VectorChatRule implements ChatRule {

	private final AiDatasetService aiDatasetService;

	private final ModelProvider modelProvider;

	/**
	 * 标准问答策略
	 */
	private final UnifiedRagStrategy standardQuestionAnswerStrategy;

	/**
	 * 向量检索增强生成策略
	 */
	private final UnifiedRagStrategy vectorRetrievalAugmentedGenerationStrategy;

	public VectorChatRule(AiDatasetService aiDatasetService, ModelProvider modelProvider,
			@Qualifier("standardQuestionAnswerStrategy") UnifiedRagStrategy standardQuestionAnswerStrategy,
			@Qualifier("vectorRetrievalAugmentedGenerationStrategy") UnifiedRagStrategy vectorRetrievalAugmentedGenerationStrategy) {
		this.aiDatasetService = aiDatasetService;
		this.modelProvider = modelProvider;
		this.standardQuestionAnswerStrategy = standardQuestionAnswerStrategy;
		this.vectorRetrievalAugmentedGenerationStrategy = vectorRetrievalAugmentedGenerationStrategy;
	}

	/**
	 * 处理基于知识库的聊天请求
	 * <p>
	 * 直接使用RAG策略处理，根据数据集配置选择合适的策略
	 * </p>
	 * @param chatMessageDTO 聊天消息DTO，必须包含datasetId
	 * @return 基于知识库检索的AI响应流
	 */
	@Override
	public Flux<AiMessageResultDTO> process(ChatMessageDTO chatMessageDTO) {
		// 如果用户没有传递，则根据用户语义查询一个 符合语义的知识库
		if (Objects.isNull(chatMessageDTO.getDatasetId())) {
			Flux<AiMessageResultDTO> just = autoChoice(chatMessageDTO);
			if (just != null)
				return just;
		}

		AiDatasetEntity dataset = aiDatasetService.getById(chatMessageDTO.getDatasetId());
		if (Objects.isNull(dataset)) {
			log.warn("知识库不存在或当前租户无权访问: {}", chatMessageDTO.getDatasetId());
			return Flux.just(new AiMessageResultDTO("知识库不存在或当前用户无权访问"));
		}
		DimensionAwareEmbeddingModel embeddingModel = modelProvider.getEmbeddingModel(dataset.getEmbeddingModel());
		Embedding queryEmbedding = embeddingModel.embed(chatMessageDTO.getContent()).content();

		// 如果启用了标准化数据，先尝试标准问答匹配
		if (YesNoEnum.YES.getCode().equals(dataset.getStandardFlag())) {
			Flux<AiMessageResultDTO> standardResult = standardQuestionAnswerStrategy
				.processChat(queryEmbedding, dataset, chatMessageDTO)
				.cache();

			// 如果标准问答没有结果，降级到向量检索
			return standardResult.switchIfEmpty(
					vectorRetrievalAugmentedGenerationStrategy.processChat(queryEmbedding, dataset, chatMessageDTO));
		}

		// 直接使用向量检索策略
		return vectorRetrievalAugmentedGenerationStrategy.processChat(queryEmbedding, dataset, chatMessageDTO);
	}

	@Nullable
	private static Flux<AiMessageResultDTO> autoChoice(ChatMessageDTO chatMessageDTO) {
		EmbeddingSearchResult<TextSegment> searchResult = EmbeddingProvider.search(chatMessageDTO.getContent(),
				metadataKey(EmbedBizTypeEnums.Fields.type).isEqualTo(EmbedBizTypeEnums.DATASET.getType()));

		if (Objects.isNull(searchResult) || searchResult.matches().isEmpty()) {
			return Flux.just(new AiMessageResultDTO("未找到相关知识库，请点击下方+按钮选择目标知识库"));
		}

		Long dataId = searchResult.matches().get(0).embedded().metadata().getLong(TEMP_ID);
		// 修改上下文中目标功能名称
		chatMessageDTO.setDatasetId(dataId);
		ChatMessageContextHolder.set(chatMessageDTO);
		return null;
	}

}
