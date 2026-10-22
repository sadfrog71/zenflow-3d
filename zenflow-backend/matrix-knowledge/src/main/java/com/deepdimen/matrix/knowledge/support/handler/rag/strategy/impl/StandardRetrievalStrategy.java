package com.deepdimen.matrix.knowledge.support.handler.rag.strategy.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.NumberUtil;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallDTO;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallRequestDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.RagHelper;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.UnifiedRagStrategy;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiFunction;
import dev.langchain4j.model.scoring.ScoringModel;

/**
 * 标准检索策略实现
 * <p>
 * 适用于大多数向量数据库的标准检索逻辑 支持向量检索、全文检索和重排序功能
 * </p>
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StandardRetrievalStrategy implements UnifiedRagStrategy {

	private final ModelProvider modelProvider;

	@Override
	public boolean supports(String handlerType) {
		// 标准检索策略支持所有处理类型
		return true;
	}

	/**
	 * 执行知识检索操作
	 * @param requestDTO 检索请求参数
	 * @param dataset 数据集实体
	 * @param embeddingStore 向量存储
	 * @return 检索结果列表
	 */
	@Override
	public List<AiSliceReCallDTO> performRetrieval(AiSliceReCallRequestDTO requestDTO, AiDatasetEntity dataset,
			EmbeddingStore<TextSegment> embeddingStore) {

		return retrieve(requestDTO, dataset, embeddingStore,
				modelProvider.getEmbeddingModel(dataset.getEmbeddingModel()),
				(matches, topK) -> RagHelper.rankSearchResults(dataset, requestDTO.getContent(), matches,
						BooleanUtil.toBoolean(requestDTO.getReRanking()), topK));
	}

	/** Uses already resolved models, sharing the existing retrieval and DTO pipeline. */
	public List<AiSliceReCallDTO> performFixedRetrieval(AiSliceReCallRequestDTO requestDTO, AiDatasetEntity dataset,
			EmbeddingStore<TextSegment> embeddingStore, DimensionAwareEmbeddingModel embeddingModel,
			ScoringModel reranker) {
		if (embeddingModel == null || (BooleanUtil.toBoolean(requestDTO.getReRanking())
				&& dataset.getRerankerModel() != null && !dataset.getRerankerModel().isBlank() && reranker == null))
			throw new IllegalArgumentException("MODEL_BINDING_CHANGED");
		return retrieve(requestDTO, dataset, embeddingStore, embeddingModel,
				(matches, topK) -> RagHelper.rerankWithFixedModel(matches, requestDTO.getContent(),
						BooleanUtil.toBoolean(requestDTO.getReRanking()) ? reranker : null, topK));
	}

	private List<AiSliceReCallDTO> retrieve(AiSliceReCallRequestDTO requestDTO, AiDatasetEntity dataset,
			EmbeddingStore<TextSegment> embeddingStore, DimensionAwareEmbeddingModel embeddingModel,
			BiFunction<List<EmbeddingMatch<TextSegment>>, Integer, List<EmbeddingMatch<TextSegment>>> ranker) {

		Embedding queryEmbedding = embeddingModel.embed(requestDTO.getContent()).content();

		// 2. 执行向量相似度搜索
		List<EmbeddingMatch<TextSegment>> vectorMatches = performVectorSearch(queryEmbedding, requestDTO, dataset,
				embeddingStore);

		// 3. 执行全文检索（如果启用）
		List<EmbeddingMatch<TextSegment>> fullTextMatches = RagHelper.performFullTextSearch(embeddingStore, requestDTO,
				dataset);

		// 4. 合并检索结果
		List<EmbeddingMatch<TextSegment>> mergedMatches = RagHelper.mergeSearchResults(vectorMatches, fullTextMatches);

		// 5. 执行重排序（如果启用）
		int finalTopK = RagHelper.resolveFinalTopK(requestDTO.getTopK(), dataset.getTopK());
		List<EmbeddingMatch<TextSegment>> finalMatches = ranker.apply(mergedMatches, finalTopK);

		// 6. 转换为DTO格式返回
		List<AiSliceReCallDTO> results = finalMatches.stream().map(embeddingMatch -> {
			AiSliceReCallDTO dto = new AiSliceReCallDTO();
			Metadata metadata = embeddingMatch.embedded().metadata();

			dto.setSliceId(metadata.getLong(AiSliceEntity.Fields.id));
			dto.setContent(embeddingMatch.embedded().text());
			dto.setScore(NumberUtil.formatPercent(embeddingMatch.score(), 4));
			dto.setMetadata(metadata.toString());

			return dto;
		}).toList();

		log.debug("标准检索策略: 返回 {} 个结果", results.size());
		return results;
	}

	/**
	 * 执行向量相似度搜索
	 */
	private List<EmbeddingMatch<TextSegment>> performVectorSearch(Embedding queryEmbedding,
			AiSliceReCallRequestDTO requestDTO, AiDatasetEntity dataset, EmbeddingStore<TextSegment> embeddingStore) {
		// 构建搜索请求
		EmbeddingSearchRequest searchRequest = RagHelper.buildSearchRequest(queryEmbedding, requestDTO, dataset);

		// 执行搜索
		EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

		return searchResult.matches();
	}

}
