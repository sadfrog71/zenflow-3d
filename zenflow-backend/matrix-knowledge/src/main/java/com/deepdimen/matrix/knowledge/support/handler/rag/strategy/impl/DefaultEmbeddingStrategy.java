package com.deepdimen.matrix.knowledge.support.handler.rag.strategy.impl;

import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.config.AsyncEmbeddingConfiguration;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.mapper.AiSliceMapper;
import com.deepdimen.matrix.knowledge.service.EmbeddingStoreService;
import com.deepdimen.matrix.knowledge.support.constant.DocumentTypeEnums;
import com.deepdimen.matrix.knowledge.support.constant.SliceStatusEnums;
import com.deepdimen.matrix.knowledge.support.constant.SourceTypeEnums;
import com.deepdimen.matrix.knowledge.support.handler.rag.strategy.EmbeddingStrategy;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 默认向量化策略实现
 * <p>
 * 适用于 Milvus、Qdrant、Chroma 等标准向量数据库 采用逐个切片向量化的方式，支持摘要增强和灵活的元数据配置
 * </p>
 *
 * @author matrix
 * @date 2024-03-14
 */
@Slf4j
@Component
public class DefaultEmbeddingStrategy implements EmbeddingStrategy {

	private final ModelProvider modelProvider;

	private final EmbeddingStoreService embeddingStoreService;

	private final AsyncEmbeddingConfiguration asyncConfig;

	private final Executor embeddingExecutor;

	private final AiSliceMapper sliceMapper;

	public DefaultEmbeddingStrategy(ModelProvider modelProvider, EmbeddingStoreService embeddingStoreService,
			AsyncEmbeddingConfiguration asyncConfig, @Qualifier("embeddingExecutor") Executor embeddingExecutor,
			AiSliceMapper sliceMapper) {
		this.modelProvider = modelProvider;
		this.embeddingStoreService = embeddingStoreService;
		this.asyncConfig = asyncConfig;
		this.embeddingExecutor = embeddingExecutor;
		this.sliceMapper = sliceMapper;
	}

	@Override
	public boolean supports(EmbeddingStore<TextSegment> embeddingStore) {
		// 默认策略支持所有非 Neo4j 的向量存储
		return !embeddingStore.getClass().getSimpleName().contains("Neo4j");
	}

	@Override
	public void processEmbedding(AiDocumentEntity documentEntity, AiDatasetEntity aiDataset,
			List<AiSliceEntity> sliceEntityList, EmbeddingStore<TextSegment> embeddingStore) {

		log.debug("使用默认策略处理文档向量化: {}, 切片数量: {}", documentEntity.getName(), sliceEntityList.size());

		// 获取向量化模型
		DimensionAwareEmbeddingModel embeddingModel = modelProvider.getEmbeddingModel(aiDataset.getEmbeddingModel());

		// 检查是否启用异步处理
		if (asyncConfig.isEnabled() && sliceEntityList.size() > 1) {
			processEmbeddingAsync(documentEntity, aiDataset, sliceEntityList, embeddingModel, embeddingStore);
		}
		else {
			processEmbeddingSync(documentEntity, aiDataset, sliceEntityList, embeddingModel, embeddingStore);
		}
	}

	/**
	 * 同步处理向量化(原有逻辑)
	 */
	private void processEmbeddingSync(AiDocumentEntity documentEntity, AiDatasetEntity aiDataset,
			List<AiSliceEntity> sliceEntityList, DimensionAwareEmbeddingModel embeddingModel,
			EmbeddingStore<TextSegment> embeddingStore) {

		log.debug("使用同步模式处理文档向量化: {}", documentEntity.getName());

		int processedCount = 0;
		int failedCount = 0;

		// 逐个处理每个切片
		for (AiSliceEntity slice : sliceEntityList) {
			try {
				if (processSlice(slice, documentEntity, aiDataset, embeddingModel, embeddingStore)) {
					processedCount++;
				}
				else {
					failedCount++;
				}
			}
			catch (Exception e) {
				log.warn("切片 {} 向量化失败: {}", slice.getName(), e.getMessage(), e);
				slice.setSliceStatus(SliceStatusEnums.FAILED.getStatus());
				failedCount++;
			}

			sliceMapper.updateById(slice);
		}

		log.debug("同步模式: 成功处理 {} 个切片，失败 {} 个", processedCount, failedCount);
	}

	/**
	 * 异步并发处理向量化
	 */
	private void processEmbeddingAsync(AiDocumentEntity documentEntity, AiDatasetEntity aiDataset,
			List<AiSliceEntity> sliceEntityList, DimensionAwareEmbeddingModel embeddingModel,
			EmbeddingStore<TextSegment> embeddingStore) {

		log.debug("使用异步模式处理文档向量化: {}, 批次大小: {}", documentEntity.getName(), asyncConfig.getBatchSize());

		long startTime = System.currentTimeMillis();

		// 使用原子计数器保证线程安全
		AtomicInteger processedCount = new AtomicInteger(0);
		AtomicInteger failedCount = new AtomicInteger(0);

		// 将切片列表分批处理
		List<List<AiSliceEntity>> batches = partitionList(sliceEntityList, asyncConfig.getBatchSize());
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		// 为每批切片创建异步任务
		int batchIndex = 0;
		for (List<AiSliceEntity> batch : batches) {
			batchIndex++;
			// 当前批次的 future 与 slice 一一对应，便于超时诊断
			List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
			List<AiSliceEntity> batchSlices = new ArrayList<>();
			for (AiSliceEntity slice : batch) {
				CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
					try {
						if (processSlice(slice, documentEntity, aiDataset, embeddingModel, embeddingStore)) {
							processedCount.incrementAndGet();
						}
						else {
							failedCount.incrementAndGet();
						}
					}
					catch (Exception e) {
						log.warn("切片向量化失败: sliceId={}, sliceName={}, errorType={}, errorMsg={}", slice.getId(),
								slice.getName(), e.getClass().getName(), e.getMessage(), e);
						slice.setSliceStatus(SliceStatusEnums.FAILED.getStatus());
						failedCount.incrementAndGet();
					}
					sliceMapper.updateById(slice);
				}, embeddingExecutor);

				batchFutures.add(future);
				batchSlices.add(slice);
				futures.add(future);
			}

			// 等待当前批次完成后再处理下一批次,避免过多并发
			try {
				CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
					.get(asyncConfig.getTaskTimeout(), TimeUnit.SECONDS);
				futures.clear();
			}
			catch (Exception e) {
				// 输出未完成切片清单，避免 TimeoutException: null 这类无信息日志
				List<String> pending = new ArrayList<>();
				for (int i = 0; i < batchFutures.size(); i++) {
					CompletableFuture<Void> f = batchFutures.get(i);
					AiSliceEntity slice = batchSlices.get(i);
					if (!f.isDone()) {
						pending.add("sliceId=" + slice.getId() + ",name=" + slice.getName());
					}
					else if (f.isCompletedExceptionally()) {
						String detail;
						try {
							f.join();
							detail = "completedExceptionally";
						}
						catch (Exception ex) {
							detail = ex.getClass().getName() + ":" + ex.getMessage();
						}
						pending.add("sliceId=" + slice.getId() + ",name=" + slice.getName() + ",error=" + detail);
					}
				}
				log.error(
						"批次处理超时或失败: documentId={}, documentName={}, batchIndex={}/{}, batchSize={}, "
								+ "taskTimeoutSec={}, errorType={}, errorMsg={}, pendingOrFailed={}",
						documentEntity.getId(), documentEntity.getName(), batchIndex, batches.size(),
						batchFutures.size(), asyncConfig.getTaskTimeout(), e.getClass().getName(), e.getMessage(),
						pending, e);
			}
		}

		long duration = System.currentTimeMillis() - startTime;
		log.info("异步模式: 成功处理 {} 个切片，失败 {} 个，耗时 {} ms", processedCount.get(), failedCount.get(), duration);
	}

	/**
	 * 将列表分批
	 */
	private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
		List<List<T>> batches = new ArrayList<>();
		int size = list.size();
		for (int i = 0; i < size; i += batchSize) {
			batches.add(list.subList(i, Math.min(i + batchSize, size)));
		}
		return batches;
	}

	/**
	 * 处理单个切片的向量化
	 * @param slice 切片实体
	 * @param documentEntity 文档实体
	 * @param aiDataset 数据集配置
	 * @param embeddingModel 向量化模型
	 * @param embeddingStore 向量存储
	 * @return 处理是否成功
	 */
	private boolean processSlice(AiSliceEntity slice, AiDocumentEntity documentEntity, AiDatasetEntity aiDataset,
			DimensionAwareEmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {

		// 检查切片内容是否为空
		if (StrUtil.isBlank(slice.getContent())) {
			log.warn("切片内容为空，跳过向量化: {}", slice.getId());
			slice.setSliceStatus(SliceStatusEnums.FAILED.getStatus());
			return false;
		}

		// 如果切片已经向量化，先删除旧的向量数据
		if (StrUtil.isNotBlank(slice.getQdrantId())) {
			removeExistingEmbedding(aiDataset, slice);
		}

		// 构建向量化内容
		String embeddingContent = buildEmbeddingContent(slice, documentEntity, aiDataset);

		// 执行向量化
		String vectorId = performEmbedding(embeddingContent, slice, documentEntity, embeddingModel, embeddingStore);

		if (StrUtil.isNotBlank(vectorId)) {
			slice.setSliceStatus(SliceStatusEnums.SLICED.getStatus());
			slice.setQdrantId(vectorId);
			return true;
		}
		else {
			slice.setSliceStatus(SliceStatusEnums.FAILED.getStatus());
			return false;
		}
	}

	/**
	 * 构建向量化内容 组合文档名称、切片内容和可选的摘要信息
	 */
	private String buildEmbeddingContent(AiSliceEntity slice, AiDocumentEntity documentEntity,
			AiDatasetEntity aiDataset) {
		// 基础内容：文档名称 + 切片内容
		String content = String.format("%s\n%s", documentEntity.getName(), slice.getContent());

		// 如果配置了预摘要且条件满足，追加摘要信息
		if (shouldIncludeSummary(aiDataset, documentEntity)) {
			String summary = StrUtil.subSufByLength(documentEntity.getSummary(), 200);
			content = content + summary;
		}

		return content;
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
	 * 执行向量化并存储
	 */
	private String performEmbedding(String content, AiSliceEntity slice, AiDocumentEntity documentEntity,
			DimensionAwareEmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {

		// 向量化文本内容
		Response<Embedding> embeddingResponse = embeddingModel.embed(content);

		// 构建元数据
		Map<String, Object> metadataMap = Map.of(DocumentTypeEnums.Fields.type, DocumentTypeEnums.ANSWER.getType(),
				AiSliceEntity.Fields.id, slice.getId().toString(), AiDocumentEntity.Fields.datasetId,
				documentEntity.getDatasetId().toString(), AiSliceEntity.Fields.documentId,
				documentEntity.getId().toString(), AiDocumentEntity.Fields.sourceType, documentEntity.getSourceType());

		// 创建文本段落
		TextSegment textSegment = TextSegment.textSegment(content, new Metadata(metadataMap));

		// 存储向量和文本段落
		return embeddingStore.add(embeddingResponse.content(), textSegment);
	}

	/**
	 * 删除已存在的向量数据
	 */
	private void removeExistingEmbedding(AiDatasetEntity aiDataset, AiSliceEntity slice) {
		try {
			log.debug("删除已存在的向量数据: {}", slice.getId());
			embeddingStoreService.delete(aiDataset.getCollectionName(), List.of(slice.getQdrantId()));
		}
		catch (Exception e) {
			log.warn("删除旧向量数据失败: {}", e.getMessage(), e);
		}
	}

}