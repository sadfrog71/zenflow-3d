package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.List;

/**
 * 向量存储服务接口：提供向量数据库的管理功能，支持文本向量的存储、检索和删除
 *
 * @author caoyuan
 * @date 2025/08/04
 */
public interface EmbeddingStoreService {

	/**
	 * 获取向量存储实例 根据集合名称获取对应的向量存储对象，用于后续的向量操作
	 * @param collectionName 向量集合名称，通常对应一个知识库或文档集
	 * @return 返回文本段向量存储对象
	 */
	EmbeddingStore<TextSegment> embeddingStore(String collectionName);

	/**
	 * 根据嵌入存储实体和模型实体获取对应的嵌入存储实例
	 * @param collectionName 集合名称
	 * @param embedStoreEntity 嵌入存储配置实体
	 * @param modelEntity AI模型配置实体
	 * @return 文本片段的嵌入存储实例
	 */
	EmbeddingStore<TextSegment> embeddingStore(String collectionName, AiEmbedStoreEntity embedStoreEntity,
			AiModelEntity modelEntity);

	/**
	 * Java-only entry for a verified immutable configuration; never uses default storage.
	 */
	EmbeddingStore<TextSegment> embeddingStoreFixed(String collectionName, AiEmbedStoreEntity embedStoreEntity,
			int dimensions, String configurationDigest);

	/**
	 * 批量删除向量数据 从指定集合中删除多个向量记录
	 * @param collectionName 向量集合名称
	 * @param idList 待删除的向量ID列表
	 */
	void delete(String collectionName, List<String> idList);

	/**
	 * 删除整个向量集合 删除指定名称的向量存储集合及其所有数据
	 * @param name 向量集合名称
	 */
	void deleteEmbeddingStore(String name);

}
