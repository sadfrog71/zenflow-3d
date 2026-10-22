package com.deepdimen.matrix.knowledge.support.handler.rag.strategy.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.common.neo4j.embedding.Neo4jEmbeddingStore;
import com.deepdimen.matrix.common.neo4j.graph.Neo4jGraphEmbeddingStoreIngestor;
import com.deepdimen.matrix.common.neo4j.graph.Neo4jSummaryGraphIngestor;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentMapper;
import com.deepdimen.matrix.knowledge.mapper.AiSliceMapper;
import com.deepdimen.matrix.knowledge.support.constant.DocumentTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.SliceStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.SourceTypeEnums;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.EmbeddingStrategy;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.deepdimen.matrix.knowledge.support.handler.rag.Neo4jEmbeddingStoreFactory.DRIVER_MAP;

/**
 * Neo4j 向量化策略实现
 * <p>
 * 专门处理 Neo4j 图数据库的向量化逻辑，支持文档摘要和层次化存储 使用 Neo4j 的图嵌入处理器来完成批量向量化操作
 * </p>
 *
 * @author matrix
 * @date 2024-03-14
 */
@Slf4j
@Component
public class Neo4jEmbeddingStrategy implements EmbeddingStrategy {

	private final ModelProvider modelProvider;

	private final AiSliceMapper sliceMapper;

	public Neo4jEmbeddingStrategy(ModelProvider modelProvider, AiSliceMapper sliceMapper) {
		this.modelProvider = modelProvider;
		this.sliceMapper = sliceMapper;
	}

	@Override
	public boolean supports(EmbeddingStore<TextSegment> embeddingStore) {
		return embeddingStore instanceof Neo4jEmbeddingStore;
	}

	@Override
	public void processEmbedding(AiDocumentEntity documentEntity, AiDatasetEntity aiDataset,
			List<AiSliceEntity> sliceEntityList, EmbeddingStore<TextSegment> embeddingStore) {

		log.debug("使用 Neo4j 策略处理文档向量化: {}, 切片数量: {}", documentEntity.getName(), sliceEntityList.size());

		long startTime = System.currentTimeMillis();

		// 组合所有切片内容
		String combinedContent = sliceEntityList.stream().map(AiSliceEntity::getContent).collect(Collectors.joining());

		// 检查内容是否为空
		if (StrUtil.isBlank(combinedContent)) {
			log.warn("Neo4j 策略: 文档内容为空，跳过处理: {}", documentEntity.getId());
			return;
		}

		// 构建文档内容，可能包含摘要
		String documentContent = buildDocumentContent(documentEntity, aiDataset, combinedContent);

		// 创建文档对象
		Document document = createDocument(documentEntity, documentContent);

		try {
			// 执行 Neo4j 图嵌入处理
			List<String> elementIdList = performGraphEmbedding(aiDataset, embeddingStore, document);

			// 更新切片状态和向量ID
			updateSliceStatus(sliceEntityList, elementIdList);

			// 标记文档图状态
			documentEntity.setGraphStatus(YesNoEnum.YES.getCode());
			SpringUtil.getBean(AiDocumentMapper.class).updateById(documentEntity);

			long duration = System.currentTimeMillis() - startTime;
			log.info("Neo4j 策略: 成功处理 {} 个切片，耗时 {} ms", sliceEntityList.size(), duration);
		}
		catch (Exception e) {
			log.error("Neo4j 策略: 处理文档向量化失败: {}", documentEntity.getName(), e);
			// 标记所有切片为失败状态
			sliceEntityList.forEach(slice -> slice.setSliceStatus(SliceStatusEnums.FAILED.getStatus()));
			throw e;
		}
	}

	/**
	 * 构建文档内容，根据配置决定是否包含摘要 YE
	 */
	private String buildDocumentContent(AiDocumentEntity documentEntity, AiDatasetEntity aiDataset,
			String combinedContent) {
		String documentContent = combinedContent;

		// 如果配置了预摘要且文档有摘要内容，则追加摘要信息
		if (shouldIncludeSummary(aiDataset, documentEntity)) {
			String summary = StrUtil.subSufByLength(documentEntity.getSummary(), 200);
			documentContent = summary + combinedContent;
		}

		return documentContent;
	}

	/**
	 * 判断是否应该包含摘要
	 */
	private boolean shouldIncludeSummary(AiDatasetEntity aiDataset, AiDocumentEntity documentEntity) {
		return YesNoEnum.YES.getCode().equals(aiDataset.getPreSummary())
				&& !SourceTypeEnums.QA.getType().equals(documentEntity.getSourceType())
				&& StrUtil.isNotBlank(documentEntity.getSummary());
	}

	/**
	 * 创建 Document 对象
	 */
	private Document createDocument(AiDocumentEntity documentEntity, String content) {
		Map<String, Object> metadata = Map.of("title", documentEntity.getName(), AiSliceEntity.Fields.documentId,
				documentEntity.getId().toString(), AiDocumentEntity.Fields.datasetId,
				documentEntity.getDatasetId().toString(), DocumentTypeEnums.Fields.type,
				DocumentTypeEnums.ANSWER.getType());

		return Document.from(content, Metadata.from(metadata));
	}

	/**
	 * 执行 Neo4j 图嵌入处理
	 */
	private List<String> performGraphEmbedding(AiDatasetEntity aiDataset, EmbeddingStore<TextSegment> embeddingStore,
			Document document) {
		// 创建文档分割器
		DocumentSplitter paragraphSplitter = new DocumentByParagraphSplitter(aiDataset.getFragmentSize(), 50);

		// 获取模型
		DimensionAwareEmbeddingModel embeddingModel = modelProvider.getEmbeddingModel(aiDataset.getEmbeddingModel());
		ChatModel chatModel = modelProvider.getAiAssistant(aiDataset.getSummaryModel()).getKey();

		// 创建 Neo4j 图嵌入处理器
		Neo4jGraphEmbeddingStoreIngestor ingestor = Neo4jSummaryGraphIngestor.builder()
			.driver(DRIVER_MAP.get(aiDataset.getCollectionName()))
			.embeddingStore(embeddingStore)
			.embeddingModel(embeddingModel)
			.questionModel(chatModel)
			.documentSplitter(paragraphSplitter)
			.build();

		// 执行向量化处理
		return ingestor.ingest(document);
	}

	/**
	 * 更新切片状态和向量ID
	 */
	private void updateSliceStatus(List<AiSliceEntity> sliceEntityList, List<String> elementIdList) {
		for (int i = 0; i < sliceEntityList.size() && i < elementIdList.size(); i++) {
			AiSliceEntity slice = sliceEntityList.get(i);
			slice.setSliceStatus(SliceStatusEnums.SLICED.getStatus());
			slice.setQdrantId(elementIdList.get(i));
			sliceMapper.updateById(slice);
		}
	}

}