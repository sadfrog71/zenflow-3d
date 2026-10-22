package com.deepdimen.matrix.knowledge.support.handler.rag.strategy;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.milvus.MilvusEmbeddingStore;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.config.properties.FullTextSearchProperties;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallRequestDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.service.AiChatRecordService;
import com.deepdimen.matrix.knowledge.service.AiDocumentService;
import com.deepdimen.matrix.knowledge.service.AiStreamAssistantService;
import com.deepdimen.matrix.knowledge.support.constant.DocumentTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.LLMUseStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.SourceTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.SummaryStatusEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import com.deepdimen.matrix.knowledge.support.util.PromptBuilder;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.*;

import static com.deepdimen.matrix.knowledge.support.constant.AiPromptField.systemTime;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

/**
 * 统一RAG辅助工具类
 * <p>
 * 合并了原RAG处理和切片向量化的功能，提供完整的RAG处理辅助方法： 1. 向量搜索：构建搜索请求、执行检索 2. 全文检索：BM25搜索支持 3.
 * 重排序：基于重排序模型的结果优化 4. 答案生成：AI模型结果总结 5. 数据转换：DTO转换、链接构建 6. 工具方法：空值判断、ID提取等
 * </p>
 *
 * @author matrix
 * @date 2024-03-14
 */
@Slf4j
@UtilityClass
public class RagHelper {

	private static final int MIN_CANDIDATE_RESULTS = 20;

	private static final int MAX_CANDIDATE_RESULTS = 50;

	private static final int RRF_RANK_CONSTANT = 60;

	/**
	 * 构建向量搜索请求
	 * @param queryEmbedding 查询向量
	 * @param dataset 数据集配置
	 * @param documentType 文档类型
	 * @param minScore 最小分数（可选）
	 * @return 搜索请求对象
	 */
	public static EmbeddingSearchRequest buildSearchRequest(Embedding queryEmbedding, AiDatasetEntity dataset,
			String documentType, Double minScore) {
		double finalMinScore;
		if (Objects.nonNull(minScore)) {
			finalMinScore = minScore;
		}
		else {
			// 使用数据集配置的默认分数阈值
			finalMinScore = NumberUtil.div(Double.parseDouble(dataset.getScore().toString()), 100.0, 2);
		}

		int finalTopK = resolveFinalTopK(null, dataset.getTopK());
		return new EmbeddingSearchRequest(queryEmbedding, calculateCandidateLimit(finalTopK), finalMinScore,
				metadataKey(AiDocumentEntity.Fields.datasetId).isEqualTo(dataset.getId().toString())
					.and(metadataKey(DocumentTypeEnums.Fields.type).isEqualTo(documentType)));
	}

	/**
	 * 执行全文检索（仅支持 Milvus）
	 * @param embeddingStore 向量存储
	 * @param queryContent 查询内容
	 * @return 全文检索结果
	 */
	public static List<EmbeddingMatch<TextSegment>> performFullTextSearch(EmbeddingStore<TextSegment> embeddingStore,
			String queryContent, int maxResults) {
		if (!(embeddingStore instanceof MilvusEmbeddingStore milvusEmbeddingStore)) {
			return Collections.emptyList();
		}

		try {
			AiKnowledgeProperties knowledgeProperties = SpringUtil.getBean(AiKnowledgeProperties.class);
			FullTextSearchProperties fullTextSearch = knowledgeProperties.getFullTextSearch();

			if (!fullTextSearch.isEnabled()) {
				return Collections.emptyList();
			}

			Map<String, Object> searchParams = Map.of(fullTextSearch.getAlgorithm(), fullTextSearch.getDropRatio());
			EmbeddingSearchResult<TextSegment> searchResult = milvusEmbeddingStore.searchByBm25(queryContent,
					searchParams, maxResults);

			if (Objects.nonNull(searchResult) && !searchResult.matches().isEmpty()) {
				return searchResult.matches();
			}
		}
		catch (Exception e) {
			log.warn("全文检索失败: {}", e.getMessage(), e);
		}

		return Collections.emptyList();
	}

	/**
	 * 使用知识库配置的模型重排融合候选，并截取最终结果数。
	 */
	public static List<EmbeddingMatch<TextSegment>> rankSearchResults(AiDatasetEntity dataset, String queryContent,
			List<EmbeddingMatch<TextSegment>> embeddingMatches, boolean rerankingEnabled, int finalTopK) {
		if (!rerankingEnabled || StrUtil.isBlank(dataset.getRerankerModel())) {
			return limitResults(embeddingMatches, finalTopK);
		}

		try {
			ModelProvider modelProvider = SpringUtil.getBean(ModelProvider.class);
			ScoringModel rerankerModel = modelProvider.getRerankerModel(dataset.getRerankerModel());
			return rerankWithModel(embeddingMatches, queryContent, rerankerModel, finalTopK);
		}
		catch (Exception e) {
			log.warn("重排序模型加载失败，使用融合排序结果: {}", e.getMessage(), e);
			return limitResults(embeddingMatches, finalTopK);
		}
	}

	static List<EmbeddingMatch<TextSegment>> rerankWithModel(List<EmbeddingMatch<TextSegment>> embeddingMatches,
			String queryContent, ScoringModel rerankerModel, int finalTopK) {
		if (CollUtil.isEmpty(embeddingMatches) || Objects.isNull(rerankerModel)) {
			return limitResults(embeddingMatches, finalTopK);
		}

		try {
			return rerank(embeddingMatches, queryContent, rerankerModel, finalTopK, false);
		}
		catch (Exception e) {
			log.warn("重排序失败，使用融合排序结果: {}", e.getMessage(), e);
			return limitResults(embeddingMatches, finalTopK);
		}
	}

	/**
	 * Explicit model entry for fixed releases; never silently skips a failed reranker.
	 */
	public static List<EmbeddingMatch<TextSegment>> rerankWithFixedModel(List<EmbeddingMatch<TextSegment>> matches,
			String query, ScoringModel model, int finalTopK) {
		return rerank(matches, query, model, finalTopK, true);
	}

	private static List<EmbeddingMatch<TextSegment>> rerank(List<EmbeddingMatch<TextSegment>> matches, String query,
			ScoringModel model, int finalTopK, boolean strict) {
		if (CollUtil.isEmpty(matches) || model == null)
			return limitResults(matches, finalTopK);
		List<TextSegment> segments = matches.stream().map(EmbeddingMatch::embedded).toList();
		List<Double> scores = model.scoreAll(segments, query).content();
		if (scores == null || scores.size() != matches.size()
				|| (strict && scores.stream().anyMatch(score -> score == null || !Double.isFinite(score))))
			throw new IllegalStateException("RERANKER_OUTPUT_INVALID");
		List<Integer> indexes = new ArrayList<>();
		for (int index = 0; index < scores.size(); index++)
			indexes.add(index);
		indexes.sort(Comparator.comparingDouble((Integer index) -> scores.get(index)).reversed());
		return indexes.stream().limit(finalTopK).map(matches::get).toList();
	}

	/**
	 * 处理空搜索结果
	 * @param dataset 数据集配置
	 * @param chatMessageDTO 聊天消息
	 * @param recordId 聊天记录ID（可选）
	 * @return 空结果响应
	 */
	public static Flux<AiMessageResultDTO> handleEmptyResult(AiDatasetEntity dataset, ChatMessageDTO chatMessageDTO,
			Long recordId) {
		// 更新聊天记录状态为"没有查询到向量"
		if (Objects.nonNull(recordId)) {
			AiChatRecordService chatRecordService = SpringUtil.getBean(AiChatRecordService.class);
			AiChatRecordEntity record = new AiChatRecordEntity();
			record.setRecordId(recordId);
			record.setLlmFlag(LLMUseStatusEnums.NO_VECTOR.getStatus());
			chatRecordService.updateById(record);
		}

		// 根据配置决定返回固定回复或使用大模型生成回答
		if (YesNoEnum.NO.getCode().equals(dataset.getEmptyLlmFlag())) {
			// 直接返回预设的空结果描述
			return Flux.just(new AiMessageResultDTO(dataset.getEmptyDesc()));
		}
		else {
			// 使用大模型生成回答
			try {
				ModelProvider modelProvider = SpringUtil.getBean(ModelProvider.class);
				AiStreamAssistantService streamAssistantService = modelProvider
					.getAiStreamAssistant(chatMessageDTO.getModelName())
					.getValue();

				return streamAssistantService.chat(chatMessageDTO.getConversationId(), chatMessageDTO.getContent())
					.map(text -> {
						AiMessageResultDTO dto = new AiMessageResultDTO(text);
						dto.setExtLinks(List.of(new AiMessageResultDTO.ExtLink("未匹配知识库内容，由大模型直接推理返回", null, null)));
						return dto;
					});
			}
			catch (Exception e) {
				log.error("大模型回答生成失败: {}", e.getMessage(), e);
				return Flux.just(new AiMessageResultDTO(dataset.getEmptyDesc()));
			}
		}
	}

	/**
	 * 构建参考资料链接
	 * @param embeddingMatches 匹配结果
	 * @param documentService 文档服务
	 * @return 参考链接列表
	 */
	@SneakyThrows
	public static List<AiMessageResultDTO.ExtLink> buildReferenceLinks(
			List<EmbeddingMatch<TextSegment>> embeddingMatches, AiDocumentService documentService) {
		List<AiMessageResultDTO.ExtLink> extLinks = new ArrayList<>();

		for (EmbeddingMatch<TextSegment> match : embeddingMatches) {
			String documentId = match.embedded().metadata().getString(AiSliceEntity.Fields.documentId);
			if (Objects.isNull(documentId)) {
				continue;
			}

			Float distance = match.score().floatValue();
			AiDocumentEntity document = documentService.getById(Long.parseLong(documentId));
			if (Objects.nonNull(document)) {
				extLinks.add(new AiMessageResultDTO.ExtLink(document.getName(),
						String.format("/admin/sys-file/oss/file?fileName=%s", document.getFileUrl()), distance));
			}
		}

		return extLinks.stream().distinct().toList();
	}

	/**
	 * 提取标准问答的答案
	 * @param embeddingMatches 匹配结果
	 * @return 答案列表
	 */
	public static List<String> extractStandardAnswers(List<EmbeddingMatch<TextSegment>> embeddingMatches) {
		return embeddingMatches.stream()
			.map(match -> match.embedded().metadata().getString(AiChatRecordEntity.Fields.answerText))
			.filter(Objects::nonNull)
			.toList();
	}

	/**
	 * 合并多个搜索结果（支持可变参数）
	 * @param searchResults 多个搜索结果列表
	 * @return 合并后的结果
	 */
	@SafeVarargs
	public static List<EmbeddingMatch<TextSegment>> mergeSearchResults(
			List<EmbeddingMatch<TextSegment>>... searchResults) {
		Map<String, EmbeddingMatch<TextSegment>> matchesByKey = new LinkedHashMap<>();
		Map<String, Double> rrfScores = new HashMap<>();

		for (List<EmbeddingMatch<TextSegment>> results : searchResults) {
			if (CollUtil.isEmpty(results)) {
				continue;
			}

			for (int index = 0; index < results.size(); index++) {
				EmbeddingMatch<TextSegment> match = results.get(index);
				if (Objects.isNull(match) || Objects.isNull(match.embedded())) {
					continue;
				}

				String key = buildMatchKey(match);
				matchesByKey.putIfAbsent(key, match);
				rrfScores.merge(key, 1.0 / (RRF_RANK_CONSTANT + index + 1), Double::sum);
			}
		}

		List<Map.Entry<String, EmbeddingMatch<TextSegment>>> rankedMatches = new ArrayList<>(matchesByKey.entrySet());
		rankedMatches.sort(Comparator
			.comparingDouble((Map.Entry<String, EmbeddingMatch<TextSegment>> entry) -> rrfScores.get(entry.getKey()))
			.reversed());
		return rankedMatches.stream().map(Map.Entry::getValue).toList();
	}

	private static String buildMatchKey(EmbeddingMatch<TextSegment> match) {
		if (StrUtil.isNotBlank(match.embeddingId())) {
			return "embedding:" + match.embeddingId();
		}

		Object sliceId = match.embedded().metadata().toMap().get(AiSliceEntity.Fields.id);
		if (Objects.nonNull(sliceId)) {
			return "slice:" + sliceId;
		}

		return "text:" + match.embedded().text();
	}

	public static int resolveFinalTopK(Integer requestedTopK, Integer datasetTopK) {
		Integer configuredTopK = Objects.nonNull(requestedTopK) && requestedTopK > 0 ? requestedTopK : datasetTopK;
		return Objects.nonNull(configuredTopK) && configuredTopK > 0 ? Math.min(configuredTopK, MAX_CANDIDATE_RESULTS)
				: 5;
	}

	public static int calculateCandidateLimit(int finalTopK) {
		return Math.min(Math.max(finalTopK * 5, MIN_CANDIDATE_RESULTS), MAX_CANDIDATE_RESULTS);
	}

	private static List<EmbeddingMatch<TextSegment>> limitResults(List<EmbeddingMatch<TextSegment>> embeddingMatches,
			int finalTopK) {
		if (CollUtil.isEmpty(embeddingMatches)) {
			return Collections.emptyList();
		}
		return embeddingMatches.stream().limit(finalTopK).toList();
	}

	/**
	 * 验证搜索结果是否为空
	 * @param embeddingMatches 搜索结果
	 * @return true表示为空，false表示有结果
	 */
	public static boolean isEmpty(List<EmbeddingMatch<TextSegment>> embeddingMatches) {
		return CollUtil.isEmpty(embeddingMatches);
	}

	/**
	 * 提取嵌入ID列表（用于统计命中次数）
	 * @param embeddingMatches 匹配结果
	 * @return 嵌入ID列表
	 */
	public static List<String> extractEmbeddingIds(List<EmbeddingMatch<TextSegment>> embeddingMatches) {
		return embeddingMatches.stream().map(EmbeddingMatch::embeddingId).filter(Objects::nonNull).toList();
	}

	/**
	 * 结果总结输出 使用AI模型对检索到的多个结果进行总结，生成最终回答
	 * @param dataset 数据集配置信息
	 * @param chatMessageDTO 聊天消息DTO，包含用户问题和对话上下文
	 * @param resultList 从向量库检索到的相关文本片段列表
	 * @return 返回AI生成的总结回答流
	 */
	public static Flux<AiMessageResultDTO> summaryResult(AiDatasetEntity dataset, ChatMessageDTO chatMessageDTO,
			List<String> resultList) {
		// 对结果进行总结
		ModelProvider modelProvider = SpringUtil.getBean(ModelProvider.class);
		AiStreamAssistantService streamAssistantService = modelProvider
			.getAiStreamAssistant(chatMessageDTO.getModelName())
			.getValue();
		Flux<String> summaryResult = streamAssistantService.chat(chatMessageDTO.getConversationId(),
				PromptBuilder.render("knowledge-system.st"),
				PromptBuilder.render("knowledge-user.st",
						Map.of("contents", CollUtil.join(resultList, StrUtil.CRLF), "userMessage",
								chatMessageDTO.getContent(), "emptyDesc", dataset.getEmptyDesc(), systemTime,
								DateUtil.now())));
		return summaryResult.map(AiMessageResultDTO::new);
	}

	// ========== 以下为切片向量化相关方法 ==========

	/**
	 * 判断是否应跳过摘要处理
	 * @param aiDataset 知识库配置
	 * @param documentEntity 文档实体
	 * @return true表示应跳过，false表示继续处理
	 */
	public static boolean shouldSkipSummary(AiDatasetEntity aiDataset, AiDocumentEntity documentEntity) {
		return YesNoEnum.YES.getCode().equals(aiDataset.getPreSummary())
				&& !SourceTypeEnums.QA.getType().equals(documentEntity.getSourceType())
				&& !SummaryStatusEnums.SUMMARYED.getStatus().equals(documentEntity.getSummaryStatus());
	}

	/**
	 * 构建用于切片召回的向量搜索请求（重载方法，支持不同参数）
	 * @param queryEmbedding 查询向量
	 * @param requestDTO 搜索请求参数
	 * @param dataset 数据集配置
	 * @return 向量搜索请求对象
	 */
	public static EmbeddingSearchRequest buildSearchRequest(Embedding queryEmbedding,
			AiSliceReCallRequestDTO requestDTO, AiDatasetEntity dataset) {
		double minScore = NumberUtil.div(Double.parseDouble(dataset.getScore().toString()), 100.0, 2);

		int finalTopK = resolveFinalTopK(requestDTO.getTopK(), dataset.getTopK());
		int maxResults = calculateCandidateLimit(finalTopK);

		return EmbeddingSearchRequest.builder()
			.queryEmbedding(queryEmbedding)
			.maxResults(maxResults)
			// 只在一个知识库目标知识库里面匹配是 isEqualTo () ，需要在多个知识库同时进行匹配 isIn（1,2,3）
			.filter(metadataKey(AiDocumentEntity.Fields.datasetId).isEqualTo(dataset.getId().toString())
				.and(metadataKey(DocumentTypeEnums.Fields.type).isEqualTo(DocumentTypeEnums.ANSWER.getType())))
			.minScore(minScore)
			.build();
	}

	/**
	 * 执行全文检索（支持切片召回请求）
	 * @param embeddingStore 向量存储
	 * @param requestDTO 请求参数
	 * @return 全文检索结果，如果不支持则返回空列表
	 */
	public static List<EmbeddingMatch<TextSegment>> performFullTextSearch(EmbeddingStore<TextSegment> embeddingStore,
			AiSliceReCallRequestDTO requestDTO, AiDatasetEntity dataset) {
		if (!(embeddingStore instanceof MilvusEmbeddingStore milvusEmbeddingStore)
				|| !BooleanUtil.toBoolean(requestDTO.getFullTextSearch())) {
			return Collections.emptyList();
		}

		try {
			AiKnowledgeProperties knowledgeProperties = SpringUtil.getBean(AiKnowledgeProperties.class);
			FullTextSearchProperties fullTextSearch = knowledgeProperties.getFullTextSearch();
			if (!fullTextSearch.isEnabled()) {
				return Collections.emptyList();
			}

			// 配置BM25算法参数
			Map<String, Object> searchParams = Map.of(fullTextSearch.getAlgorithm(), fullTextSearch.getDropRatio());

			// 执行BM25全文检索
			int finalTopK = resolveFinalTopK(requestDTO.getTopK(), dataset.getTopK());
			EmbeddingSearchResult<TextSegment> searchResult = milvusEmbeddingStore.searchByBm25(requestDTO.getContent(),
					searchParams, calculateCandidateLimit(finalTopK));

			if (Objects.nonNull(searchResult) && !searchResult.matches().isEmpty()) {
				return searchResult.matches();
			}
		}
		catch (Exception e) {
			log.warn("全文检索失败: {}", e.getMessage(), e);
		}

		return Collections.emptyList();
	}

}
