package com.deepdimen.matrix.knowledge.support.handler.rag.strategy;

import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.List;

/**
 * 向量化策略接口
 * <p>
 * 定义不同向量存储的文档向量化处理策略，支持 Neo4j、Milvus 等多种向量数据库 通过策略模式将不同存储类型的处理逻辑分离，提高代码的可维护性和扩展性
 * </p>
 *
 * @author matrix
 * @date 2024-03-14
 */
public interface EmbeddingStrategy {

	/**
	 * 判断策略是否支持指定的向量存储类型
	 * @param embeddingStore 向量存储实例
	 * @return 支持返回true，不支持返回false
	 */
	boolean supports(EmbeddingStore<TextSegment> embeddingStore);

	/**
	 * 执行文档向量化处理
	 * <p>
	 * 根据不同的向量存储类型，采用相应的向量化策略： - Neo4j：使用图嵌入处理器，支持摘要和层次化存储 - 其他存储：逐个切片进行向量化处理
	 * </p>
	 * @param documentEntity 文档实体对象
	 * @param aiDataset 知识库配置信息
	 * @param sliceEntityList 待处理的切片列表
	 * @param embeddingStore 向量存储实例
	 */
	void processEmbedding(AiDocumentEntity documentEntity, AiDatasetEntity aiDataset,
			List<AiSliceEntity> sliceEntityList, EmbeddingStore<TextSegment> embeddingStore);

}