package com.deepdimen.matrix.knowledge.support.handler.rag;

import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.support.constant.EmbedStoreSupportEnums;
import com.deepdimen.matrix.knowledge.support.provider.ModelProvider;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import com.deepdimen.matrix.common.milvus.MilvusEmbeddingStore;
import io.milvus.param.IndexType;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Milvus向量数据库工厂类 负责创建和配置Milvus向量存储实例 Milvus是一个开源的、高性能的向量数据库，专为AI应用设计
 *
 * @author caoyuan
 * @date 2025/2/11
 */
@Service
@RequiredArgsConstructor
public class MilvusEmbeddingStoreFactory implements EmbeddingStoreFactory {

	private final ModelProvider modelProvider;

	/**
	 * 判断是否支持Milvus类型的向量存储
	 * @param storeType 向量存储类型
	 * @return 如果是MILVUS类型返回true，否则返回false
	 */
	@Override
	public boolean support(String storeType) {
		return EmbedStoreSupportEnums.MILVUS.getType().equals(storeType);
	}

	/**
	 * 创建Milvus向量存储实例 配置向量维度、索引类型、相似度计算方式等参数
	 * @param embedStoreEntity 向量存储配置实体，包含连接URI、API密钥等信息
	 * @param aiDatasetEntity AI数据集实体，包含集合名称、嵌入模型等信息
	 * @return 配置好的Milvus向量存储实例
	 * @throws Exception 创建过程中可能出现的异常
	 */
	@Override
	public EmbeddingStore<TextSegment> createEmbeddingStore(AiEmbedStoreEntity embedStoreEntity,
			AiDatasetEntity aiDatasetEntity) throws Exception {

		DimensionAwareEmbeddingModel embeddingModel = modelProvider
			.getEmbeddingModel(aiDatasetEntity.getEmbeddingModel());
		return new MilvusEmbeddingStore(aiDatasetEntity.getCollectionName(), // 集合名称
				embeddingModel.dimension(), // 维度
				IndexType.FLAT, // 索引类型
				IndexParam.MetricType.COSINE, // 距离计算方式
				embedStoreEntity.getUri(), // URI
				embedStoreEntity.getApiKey(), // token
				ConsistencyLevel.EVENTUALLY, // 一致性级别
				false, // retrieveEmbeddingsOnSearch
				false, // 是否autoFlushOnInsert
				embedStoreEntity.getExtData(), // database
				AiSliceEntity.Fields.id, // id 字段
				"doc_content", // 文本字段
				"metadata", // 文本字段
				"vector");// 文本字段
	}

	/**
	 * 创建Milvus向量存储实例 使用指定的集合名称和维度创建向量存储
	 * @param embedStoreEntity 向量存储配置实体，包含连接URI、API密钥等信息
	 * @param collectionName 集合名称
	 * @param dimension 向量维度
	 * @return 配置好的Milvus向量存储实例
	 * @throws Exception 创建过程中可能出现的异常
	 */
	@Override
	public EmbeddingStore<TextSegment> createEmbeddingStore(AiEmbedStoreEntity embedStoreEntity, String collectionName,
			Integer dimension) throws Exception {
		return new MilvusEmbeddingStore(collectionName, // 集合名称
				dimension, // 维度
				IndexType.FLAT, // 索引类型
				IndexParam.MetricType.COSINE, // 距离计算方式
				embedStoreEntity.getUri(), // URI
				embedStoreEntity.getApiKey(), // token
				ConsistencyLevel.EVENTUALLY, // 一致性级别
				false, // retrieveEmbeddingsOnSearch
				false, // 是否autoFlushOnInsert
				embedStoreEntity.getExtData(), // database
				AiSliceEntity.Fields.id, // id 字段
				"doc_content", // 文本字段
				"metadata", // 文本字段
				"vector");// 文本字段
	}

}
