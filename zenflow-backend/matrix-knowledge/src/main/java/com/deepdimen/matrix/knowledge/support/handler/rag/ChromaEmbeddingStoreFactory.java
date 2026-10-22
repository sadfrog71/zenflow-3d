package com.deepdimen.matrix.knowledge.support.handler.rag;

import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiEmbedStoreEntity;
import com.deepdimen.matrix.knowledge.support.constant.EmbedStoreSupportEnums;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Chroma向量数据库工厂类 负责创建和配置Chroma向量存储实例，Chroma是一个开源的向量数据库
 *
 * @author caoyuan
 * @date 2025/2/11
 */
@Service
@RequiredArgsConstructor
public class ChromaEmbeddingStoreFactory implements EmbeddingStoreFactory {

	/**
	 * 判断是否支持指定的向量存储类型
	 * @param storeType 向量存储类型
	 * @return 如果是CHROMA类型返回true，否则返回false
	 */
	@Override
	public boolean support(String storeType) {
		return EmbedStoreSupportEnums.CHROMA.getType().equals(storeType);
	}

	/**
	 * 创建Chroma向量存储实例 根据提供的配置信息创建连接到Chroma服务器的向量存储
	 * @param embedStoreEntity 向量存储配置实体，包含连接URL等信息
	 * @param aiDatasetEntity AI数据集实体，包含集合名称等信息
	 * @return 配置好的Chroma向量存储实例
	 * @throws Exception 创建过程中可能出现的异常
	 */
	@Override
	public EmbeddingStore<TextSegment> createEmbeddingStore(AiEmbedStoreEntity embedStoreEntity,
			AiDatasetEntity aiDatasetEntity) throws Exception {
		// 会自动创建集合
		return ChromaEmbeddingStore.builder()
			.baseUrl(embedStoreEntity.getUri())
			.collectionName(aiDatasetEntity.getCollectionName())
			.build();
	}

	/**
	 * 创建Chroma向量存储实例 使用指定的集合名称和维度创建向量存储
	 * @param embedStoreEntity 向量存储配置实体，包含连接URL等信息
	 * @param collectionName 集合名称
	 * @param dimension 向量维度（Chroma会自动处理维度，此参数可不使用）
	 * @return 配置好的Chroma向量存储实例
	 * @throws Exception 创建过程中可能出现的异常
	 */
	@Override
	public EmbeddingStore<TextSegment> createEmbeddingStore(AiEmbedStoreEntity embedStoreEntity, String collectionName,
			Integer dimension) throws Exception {
		// Chroma会自动创建集合，不需要显式指定维度
		return ChromaEmbeddingStore.builder().baseUrl(embedStoreEntity.getUri()).collectionName(collectionName).build();
	}

}
