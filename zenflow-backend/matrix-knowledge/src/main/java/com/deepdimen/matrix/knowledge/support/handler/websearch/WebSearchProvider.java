package com.deepdimen.matrix.knowledge.support.handler.websearch;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.core.constant.enums.YesNoEnum;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.mapper.AiModelMapper;
import com.deepdimen.matrix.knowledge.support.constant.WebSearchEnums;
import dev.langchain4j.community.web.search.searxng.SearXNGWebSearchEngine;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 网络搜索提供者
 * <p>
 * 提供统一的网络搜索服务，支持多种搜索引擎 目前支持SearXNG和BoCha两种搜索引擎 通过数据库配置动态选择搜索引擎
 *
 * @author caoyuan
 * @date 2025/2/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchProvider {

	/**
	 * AI模型数据访问层
	 */
	private final AiModelMapper aiModelMapper;

	/**
	 * AI知识库配置属性
	 */
	private final AiKnowledgeProperties aiKnowledgeProperties;

	/**
	 * 网络搜索引擎缓存 key: 搜索引擎名称, value: 搜索引擎实例 避免重复创建搜索引擎实例
	 */
	public static Map<String, WebSearchEngine> webSearchEngineMap = new HashMap<>();

	/**
	 * 执行网络搜索
	 * <p>
	 * 根据配置的默认搜索引擎执行搜索 支持SearXNG（开源搜索引擎）和BoCha搜索引擎
	 * @param query 搜索关键词
	 * @return 搜索结果文本，多个结果拼接成字符串
	 */
	public String search(String query) {

		// 查询默认的搜索引擎配置
		AiModelEntity searchModel = aiModelMapper.selectOne(Wrappers.<AiModelEntity>lambdaQuery()
			.eq(AiModelEntity::getModelType, WebSearchEnums.BOCHA.getType())
			.eq(AiModelEntity::getDefaultModel, YesNoEnum.YES.getCode()), false);

		if (Objects.isNull(searchModel)) {
			log.warn("未找到默认搜索引擎，请配置");
			return null;
		}

		// 处理SearXNG搜索引擎
		if (searchModel.getModelName().equals(WebSearchEnums.SEARXNG.getName())) {
			WebSearchEngine webSearchEngine = webSearchEngineMap.getOrDefault(WebSearchEnums.SEARXNG.getName(),
					SearXNGWebSearchEngine.builder()
						.baseUrl(searchModel.getBaseUrl())
						.duration(Duration.ofSeconds(aiKnowledgeProperties.getWebSearch().getDuration()))
						.build());

			// 缓存搜索引擎实例
			webSearchEngineMap.put(WebSearchEnums.SEARXNG.getName(), webSearchEngine);

			// 执行搜索
			WebSearchResults searchResults = webSearchEngine.search(WebSearchRequest.builder()
				.searchTerms(query)
				.maxResults(aiKnowledgeProperties.getWebSearch().getMaxResults())
				.build());

			// 将搜索结果转换为文本并拼接
			return searchResults.toDocuments().stream().map(Document::text).collect(Collectors.joining());
		}

		// 处理BoCha搜索引擎
		if (searchModel.getModelName().equals(WebSearchEnums.BOCHA.getName())) {
			WebSearchEngine webSearchEngine = webSearchEngineMap.getOrDefault(WebSearchEnums.BOCHA.getName(),
					BoChaWebSearchEngine.builder()
						.baseUrl(searchModel.getBaseUrl())
						.apiKey(searchModel.getApiKey())
						.duration(Duration.ofSeconds(aiKnowledgeProperties.getWebSearch().getDuration()))
						.build());

			// 缓存搜索引擎实例
			webSearchEngineMap.put(WebSearchEnums.BOCHA.getName(), webSearchEngine);

			// 执行搜索
			WebSearchResults searchResults = webSearchEngine.search(WebSearchRequest.builder()
				.searchTerms(query)
				.maxResults(aiKnowledgeProperties.getWebSearch().getMaxResults())
				.build());
			// 将搜索结果转换为文本并拼接
			return searchResults.toDocuments().stream().map(Document::text).collect(Collectors.joining());
		}

		// 处理Ollama搜索引擎
		if (searchModel.getModelName().equals(WebSearchEnums.OLLAMA.getName())) {
			WebSearchEngine webSearchEngine = webSearchEngineMap.getOrDefault(WebSearchEnums.OLLAMA.getName(),
					OllamaWebSearchEngine.builder()
						.apiKey(searchModel.getApiKey())
						.duration(Duration.ofSeconds(aiKnowledgeProperties.getWebSearch().getDuration()))
						.build());

			// 缓存搜索引擎实例
			webSearchEngineMap.put(WebSearchEnums.OLLAMA.getName(), webSearchEngine);

			// 执行搜索
			WebSearchResults searchResults = webSearchEngine.search(WebSearchRequest.builder()
				.searchTerms(query)
				.maxResults(aiKnowledgeProperties.getWebSearch().getMaxResults())
				.build());

			// 将搜索结果转换为文本并拼接
			return searchResults.toDocuments().stream().map(Document::text).collect(Collectors.joining());
		}

		return null;
	}

}
