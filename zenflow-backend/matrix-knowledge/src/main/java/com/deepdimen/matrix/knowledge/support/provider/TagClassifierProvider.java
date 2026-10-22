package com.deepdimen.matrix.knowledge.support.provider;

import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import dev.langchain4j.classification.EmbeddingModelTextClassifier;
import dev.langchain4j.classification.TextClassifier;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文档标签分类提供者
 *
 * @author caoyuan
 * @date 2025/11/16
 */
@Service
@RequiredArgsConstructor
public class TagClassifierProvider {

	private static final Map<String, TextClassifier<String>> CLASSIFIER_MAP = new HashMap<>();

	private final AiKnowledgeProperties knowledgeProperties;

	private final ModelProvider modelProvider;

	/**
	 * 根据业务标识获取文本分类器，如果不存在则创建新的分类器
	 * @param biz 业务标识
	 * @param allTags 所有标签的映射
	 * @return 文本分类器实例
	 */
	public TextClassifier<String> getClassify(String biz, List<String> allTags) {
		// 获取默认的向量模型
		DimensionAwareEmbeddingModel embeddingModel = modelProvider.getEmbeddingModel(null);
		TextClassifier<String> stringTextClassifier = CLASSIFIER_MAP.get(biz);
		if (Objects.nonNull(stringTextClassifier)) {
			return CLASSIFIER_MAP.get(biz);
		}

		// 构建分类器
		double minScore = knowledgeProperties.getInMemorySearch().getMinScore();
		int topK = knowledgeProperties.getInMemorySearch().getTopK();

		Map<String, List<String>> examplesByLabel = new HashMap<>();
		allTags.forEach(str -> examplesByLabel.put(str, List.of(str)));
		EmbeddingModelTextClassifier<String> classifier = new EmbeddingModelTextClassifier<>(embeddingModel,
				examplesByLabel, topK, minScore, 0.5);
		CLASSIFIER_MAP.put(biz, classifier);
		return classifier;
	}

	/**
	 * 根据业务标识移除对应的分类器
	 * @param biz 业务标识
	 */
	public void remove(String biz) {
		CLASSIFIER_MAP.remove(biz);
	}

}
