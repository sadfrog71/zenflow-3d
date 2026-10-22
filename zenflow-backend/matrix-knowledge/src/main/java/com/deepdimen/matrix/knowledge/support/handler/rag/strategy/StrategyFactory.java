package com.deepdimen.matrix.knowledge.support.handler.rag.strategy;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 策略工厂服务
 * <p>
 * 管理和选择合适的向量化和检索策略 根据向量存储类型自动选择最佳的处理策略
 * </p>
 *
 */
@Service
@RequiredArgsConstructor
public class StrategyFactory {

	private final List<EmbeddingStrategy> embeddingStrategies;

	private final List<UnifiedRagStrategy> unifiedRagStrategies;

	/**
	 * 获取适合的向量化策略
	 * @param embeddingStore 向量存储实例
	 * @return 匹配的向量化策略
	 * @throws IllegalArgumentException 如果没有找到合适的策略
	 */
	public EmbeddingStrategy getEmbeddingStrategy(EmbeddingStore<TextSegment> embeddingStore) {
		return embeddingStrategies.stream()
			.filter(strategy -> strategy.supports(embeddingStore))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(
					"未找到支持该向量存储类型的向量化策略: " + embeddingStore.getClass().getSimpleName()));
	}

	/**
	 * 获取适合的检索策略
	 * @param embeddingStore 向量存储实例
	 * @return 匹配的检索策略
	 * @throws IllegalArgumentException 如果没有找到合适的策略
	 */
	public UnifiedRagStrategy getRetrievalStrategy(EmbeddingStore<TextSegment> embeddingStore) {
		return unifiedRagStrategies.stream()
			.filter(strategy -> strategy.supports(embeddingStore))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(
					"未找到支持该向量存储类型的检索策略: " + embeddingStore.getClass().getSimpleName()));
	}

}