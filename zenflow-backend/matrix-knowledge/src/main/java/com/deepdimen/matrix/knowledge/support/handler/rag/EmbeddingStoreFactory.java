package com.deepdimen.matrix.knowledge.support.handler.rag;

import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * 向量存储工厂接口 定义了创建不同类型向量数据库的标准接口，支持多种向量存储后端 实现类负责创建具体的向量存储实例（如Milvus、Chroma、Qdrant等）
 *
 * @author caoyuan
 * @date 2025/2/11
 */
public interface EmbeddingStoreFactory {

	/**
	 * 判断工厂是否支持指定的向量存储类型
	 * @param storeType 向量存储类型（如：milvus、chroma、qdrant等）
	 * @return 支持返回true，不支持返回false
	 */
	boolean support(String storeType);

	/**
	 * 创建向量存储实例 根据配置信息创建特定类型的向量存储，用于存储和检索文本向量
	 * @param embedStoreEntity 向量存储配置实体，包含连接信息、认证信息等
	 * @param aiDatasetEntity AI数据集实体，包含集合名称、向量维度等信息
	 * @return 创建好的向量存储实例，可用于存储和检索文本片段
	 * @throws Exception 创建过程中可能出现的异常，如连接失败、配置错误等
	 */
	EmbeddingStore<TextSegment> createEmbeddingStore(AiEmbedStoreEntity embedStoreEntity,
			AiDatasetEntity aiDatasetEntity) throws Exception;

	/**
	 * 创建嵌入存储 提供更简化的创建方式，直接传入集合名称和维度
	 * @param embedStoreEntity 嵌入存储实体，包含连接信息、认证信息等
	 * @param collectionName 集合名称
	 * @param dimension 向量维度
	 * @return 嵌入存储实例
	 * @throws Exception 创建过程中发生异常时抛出
	 */
	EmbeddingStore<TextSegment> createEmbeddingStore(AiEmbedStoreEntity embedStoreEntity, String collectionName,
			Integer dimension) throws Exception;

}
