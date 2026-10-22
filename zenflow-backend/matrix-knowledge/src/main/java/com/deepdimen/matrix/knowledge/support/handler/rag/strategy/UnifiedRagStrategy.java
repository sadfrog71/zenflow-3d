package com.deepdimen.matrix.knowledge.support.handler.rag.strategy;

import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallDTO;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallRequestDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 统一RAG策略接口
 * <p>
 * 合并了RAG处理策略和检索策略的功能，提供统一的接口规范 支持两种操作模式： 1. 端到端RAG处理：从查询到最终回答生成 2. 底层向量检索：从查询内容到切片召回
 * </p>
 *
 */
public interface UnifiedRagStrategy {

	/**
	 * 判断策略是否支持指定的处理类型
	 * @param handlerType RAG处理器类型标识
	 * @return 支持返回true，不支持返回false
	 */
	boolean supports(String handlerType);

	/**
	 * 判断策略是否支持指定的向量存储类型
	 * @param embeddingStore 向量存储实例
	 * @return 支持返回true，不支持返回false
	 */
	default boolean supports(EmbeddingStore<TextSegment> embeddingStore) {
		// 默认支持所有向量存储类型，子类可重写
		return true;
	}

	/**
	 * 执行端到端RAG处理
	 * <p>
	 * 根据用户查询和数据集配置，执行完整的RAG处理流程： 检索 -> 重排序 -> 生成答案
	 * </p>
	 * @param queryEmbedding 用户查询的向量表示
	 * @param dataset 数据集配置信息
	 * @param chatMessageDTO 聊天消息DTO，包含用户问题和上下文
	 * @return 处理结果流，包含生成的回答和相关信息
	 */
	default Flux<AiMessageResultDTO> processChat(Embedding queryEmbedding, AiDatasetEntity dataset,
			ChatMessageDTO chatMessageDTO) {
		throw new UnsupportedOperationException("该策略不支持端到端RAG处理");
	}

	/**
	 * 执行知识召回检索
	 * <p>
	 * 根据用户查询内容从知识库中检索相关切片 支持向量检索、全文检索和重排序等多种检索方式
	 * </p>
	 * @param requestDTO 召回请求参数，包含查询内容、TopK等配置
	 * @param dataset 知识库配置信息
	 * @param embeddingStore 向量存储实例
	 * @return 召回的切片列表，包含内容、分数和元数据
	 */
	default List<AiSliceReCallDTO> performRetrieval(AiSliceReCallRequestDTO requestDTO, AiDatasetEntity dataset,
			EmbeddingStore<TextSegment> embeddingStore) {
		throw new UnsupportedOperationException("该策略不支持知识召回检索");
	}

}