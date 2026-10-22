package com.deepdimen.matrix.knowledge.support.handler.websearch;

import dev.langchain4j.web.search.*;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;

/**
 * Ollama网络搜索引擎实现
 * <p>
 * 实现LangChain4j的WebSearchEngine接口，提供网络搜索功能 通过Ollama Web Search API进行网页搜索，返回结构化的搜索结果
 *
 * @author caoyuan
 * @date 2025/09/26
 */
public class OllamaWebSearchEngine implements WebSearchEngine {

	/**
	 * Ollama客户端实例
	 */
	private final OllamaClient ollamaClient;

	private OllamaWebSearchEngine(OllamaWebSearchEngine.Builder builder) {
		this.ollamaClient = new OllamaClient(builder.baseUrl, builder.apiKey,
				getOrDefault(builder.duration, Duration.ofSeconds(10L)));
	}

	public static OllamaWebSearchEngine.Builder builder() {
		return new OllamaWebSearchEngine.Builder();
	}

	/**
	 * 执行网络搜索
	 * <p>
	 * 使用默认参数执行搜索查询
	 * @param query 搜索关键词
	 * @return 搜索结果
	 */
	@Override
	public WebSearchResults search(String query) {
		return WebSearchEngine.super.search(query);
	}

	/**
	 * 执行网络搜索请求
	 * <p>
	 * 调用Ollama API进行搜索，并将结果转换为LangChain4j格式
	 * @param webSearchRequest 搜索请求参数（包含关键词、结果数量等）
	 * @return 网络搜索结果，请求失败时返回null
	 */
	@Override
	public WebSearchResults search(WebSearchRequest webSearchRequest) {
		final OllamaSearchResponse results = ollamaClient.search(webSearchRequest);

		// 检查响应是否为空
		if (Objects.isNull(results) || Objects.isNull(results.getResults())) {
			return null;
		}

		// 转换搜索结果格式
		List<WebSearchOrganicResult> webSearchOrganicResults = Arrays.stream(results.getResults())
			.map(result -> WebSearchOrganicResult.from(result.getTitle(), URI.create(result.getUrl()),
					result.getContent(), null // Ollama响应中没有summary字段，设为null
			))
			.toList();

		// 构建并返回搜索结果
		return WebSearchResults.from(WebSearchInformationResult.from((long) results.getResults().length),
				webSearchOrganicResults);
	}

	/**
	 * 构建器类
	 * <p>
	 * 用于创建OllamaWebSearchEngine实例
	 */
	public static class Builder {

		/**
		 * API基础URL
		 */
		private String baseUrl = "https://ollama.com";

		/**
		 * API密钥
		 */
		private String apiKey;

		/**
		 * 请求超时时间
		 */
		private Duration duration;

		public OllamaWebSearchEngine.Builder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		public OllamaWebSearchEngine.Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public OllamaWebSearchEngine.Builder duration(Duration duration) {
			this.duration = duration;
			return this;
		}

		public OllamaWebSearchEngine build() {
			return new OllamaWebSearchEngine(this);
		}

	}

}
