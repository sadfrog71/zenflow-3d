package com.deepdimen.matrix.knowledge.support.handler.rag.strategy.impl;

import cn.hutool.core.collection.CollUtil;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.knowledge.support.constant.DocumentTypeEnums;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.RagHelper;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.UnifiedRagStrategy;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 标准问答策略实现
 * <p>
 * 实现基于标准问答对的精确匹配功能，适用于FAQ、客服等需要精确回答的场景 只有当相似度超过阈值时，才返回对应的标准答案
 * </p>
 *
 * @author matrix
 * @date 2024-03-14
 */
@Slf4j
@Component("standardQuestionAnswerStrategy")
@RequiredArgsConstructor
public class StandardQuestionAnswerStrategy implements UnifiedRagStrategy {

	private static final String HANDLER_TYPE = "Q2Q_STANDARD";

	private static final double HIGH_SIMILARITY_THRESHOLD = 0.9; // 高相似度阈值

	private final EmbeddingStoreService embeddingStoreService;

	@Override
	public boolean supports(String handlerType) {
		return HANDLER_TYPE.equals(handlerType);
	}

	@Override
	public Flux<AiMessageResultDTO> processChat(Embedding queryEmbedding, AiDatasetEntity dataset,
			ChatMessageDTO chatMessageDTO) {
		log.debug("使用标准问答策略处理查询: {}", chatMessageDTO.getContent());

		// 1. 构建高精度搜索请求
		EmbeddingSearchRequest searchRequest = RagHelper.buildSearchRequest(queryEmbedding, dataset,
				DocumentTypeEnums.QUESTION.getType(), HIGH_SIMILARITY_THRESHOLD);

		// 2. 执行向量搜索
		EmbeddingStore<TextSegment> embeddingStore = embeddingStoreService.embeddingStore(dataset.getCollectionName());
		EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
		List<EmbeddingMatch<TextSegment>> embeddingMatches = searchResult.matches();

		// 3. 检查是否找到高度相似的标准问题
		if (RagHelper.isEmpty(embeddingMatches)) {
			log.debug("未找到相似度 > {} 的标准问题", HIGH_SIMILARITY_THRESHOLD);
			return Flux.empty();
		}

		// 4. 提取标准答案
		List<String> standardAnswers = RagHelper.extractStandardAnswers(embeddingMatches);

		if (CollUtil.isEmpty(standardAnswers)) {
			log.warn("匹配到标准问题但未找到对应答案");
			return Flux.empty();
		}

		// 5. 返回第一个匹配的标准答案
		String answer = standardAnswers.get(0);
		log.debug("标准问答策略: 返回标准答案，相似度: {}", embeddingMatches.get(0).score());

		return Flux.just(new AiMessageResultDTO(answer));
	}

}