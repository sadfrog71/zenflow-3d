package com.deepdimen.matrix.knowledge.support.handler.rag.strategy.impl;

import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.service.AiDocumentService;
import com.deepdimen.matrix.knowledge.service.AiSliceService;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.knowledge.support.constant.DocumentTypeEnums;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.RagHelper;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.UnifiedRagStrategy;
import com.deepdimen.matrix.knowledge.support.rule.ChatRule;
import com.deepdimen.matrix.knowledge.support.util.ChatMessageContextHolder;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量检索增强生成策略实现
 * <p>
 * 实现基于向量检索的RAG功能，通过向量相似度搜索找到最相关的文档片段， 然后使用大模型生成答案，支持全文检索和重排序
 * </p>
 */
@Slf4j
@Component("vectorRetrievalAugmentedGenerationStrategy")
public class VectorRetrievalAugmentedGenerationStrategy implements UnifiedRagStrategy, ChatRule {

	private static final String HANDLER_TYPE = "Q2A_VECTOR";

	private final EmbeddingStoreService embeddingStoreService;

	private final AiDocumentService aiDocumentService;

	private final AiSliceService aiSliceService;

	public VectorRetrievalAugmentedGenerationStrategy(EmbeddingStoreService embeddingStoreService,
			AiDocumentService aiDocumentService, AiSliceService aiSliceService) {
		this.embeddingStoreService = embeddingStoreService;
		this.aiDocumentService = aiDocumentService;
		this.aiSliceService = aiSliceService;
	}

	@Override
	public boolean supports(String handlerType) {
		return HANDLER_TYPE.equals(handlerType);
	}

	@Override
	public Flux<AiMessageResultDTO> processChat(Embedding queryEmbedding, AiDatasetEntity dataset,
			ChatMessageDTO chatMessageDTO) {
		log.debug("使用向量检索增强生成策略处理查询: {}", chatMessageDTO.getContent());

		// 1. 执行向量检索
		List<EmbeddingMatch<TextSegment>> vectorMatches = performVectorSearch(queryEmbedding, dataset);

		// 2. 执行全文检索（如果支持）
		List<EmbeddingMatch<TextSegment>> fullTextMatches = performFullTextSearch(dataset, chatMessageDTO);

		// 3. 合并检索结果
		List<EmbeddingMatch<TextSegment>> allMatches = RagHelper.mergeSearchResults(vectorMatches, fullTextMatches);

		// 4. 检查是否有匹配结果
		if (RagHelper.isEmpty(allMatches)) {
			log.debug("未找到匹配的文档片段");
			// 获取聊天记录ID用于更新状态
			Long recordId = null;
			if (ChatMessageContextHolder.get() != null) {
				recordId = ChatMessageContextHolder.get().getMessageKey();
			}
			return RagHelper.handleEmptyResult(dataset, chatMessageDTO, recordId);
		}

		// 5. 重排序搜索结果，并只保留知识库配置的最终条数
		int finalTopK = RagHelper.resolveFinalTopK(null, dataset.getTopK());
		List<EmbeddingMatch<TextSegment>> finalMatches = RagHelper.rankSearchResults(dataset,
				chatMessageDTO.getContent(), allMatches, true, finalTopK);

		// 6. 更新命中计数
		updateHitCount(finalMatches);

		// 7. 生成答案并附加参考资料
		return generateAnswerWithReferences(dataset, chatMessageDTO, finalMatches);
	}

	/**
	 * 执行向量相似度搜索
	 */
	private List<EmbeddingMatch<TextSegment>> performVectorSearch(Embedding queryEmbedding, AiDatasetEntity dataset) {
		EmbeddingSearchRequest searchRequest = RagHelper.buildSearchRequest(queryEmbedding, dataset,
				DocumentTypeEnums.ANSWER.getType(), null // 使用数据集默认分数阈值
		);

		EmbeddingStore<TextSegment> embeddingStore = embeddingStoreService.embeddingStore(dataset.getCollectionName());
		EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

		return new ArrayList<>(searchResult.matches());
	}

	/**
	 * 执行全文检索
	 */
	private List<EmbeddingMatch<TextSegment>> performFullTextSearch(AiDatasetEntity dataset,
			ChatMessageDTO chatMessageDTO) {
		EmbeddingStore<TextSegment> embeddingStore = embeddingStoreService.embeddingStore(dataset.getCollectionName());
		int finalTopK = RagHelper.resolveFinalTopK(null, dataset.getTopK());
		return RagHelper.performFullTextSearch(embeddingStore, chatMessageDTO.getContent(),
				RagHelper.calculateCandidateLimit(finalTopK));
	}

	/**
	 * 更新命中计数
	 */
	private void updateHitCount(List<EmbeddingMatch<TextSegment>> embeddingMatches) {
		List<String> embeddingIds = RagHelper.extractEmbeddingIds(embeddingMatches);
		if (!embeddingIds.isEmpty()) {
			aiSliceService.updateHitCount(embeddingIds);
		}
	}

	/**
	 * 生成答案并附加参考资料
	 */
	private Flux<AiMessageResultDTO> generateAnswerWithReferences(AiDatasetEntity dataset,
			ChatMessageDTO chatMessageDTO, List<EmbeddingMatch<TextSegment>> embeddingMatches) {
		// 使用模型根据检索内容生成回答
		List<String> contentTexts = embeddingMatches.stream()
			.map(EmbeddingMatch::embedded)
			.map(TextSegment::text)
			.toList();

		Flux<AiMessageResultDTO> answerFlux = RagHelper.summaryResult(dataset, chatMessageDTO, contentTexts).cache();

		// 创建参考资料链接
		AiMessageResultDTO referenceLinks = new AiMessageResultDTO();
		referenceLinks.setMessage("");
		List<AiMessageResultDTO.ExtLink> extLinks = RagHelper.buildReferenceLinks(embeddingMatches, aiDocumentService);
		referenceLinks.setExtLinks(extLinks);

		// 在回答后添加参考资料
		return answerFlux.concatWithValues(referenceLinks);
	}

}
