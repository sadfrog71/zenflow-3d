package com.deepdimen.matrix.knowledge.support.handler.websearch;

import dev.langchain4j.web.search.*;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * BoCha网络搜索引擎实现
 * <p>
 * 实现LangChain4j的WebSearchEngine接口，提供网络搜索功能 通过BoCha搜索API进行网页搜索，返回结构化的搜索结果
 *
 * @author caoyuan
 * @date 2025/06/20
 */
public class BoChaWebSearchEngine implements WebSearchEngine {

	/**
	 * BoCha客户端实例
	 */
	private final BoChaClient boChaClient;

	private BoChaWebSearchEngine(BoChaWebSearchEngine.Builder builder) {
		ensureNotNull(builder.baseUrl, "baseUrl");
		this.boChaClient = new BoChaClient(builder.baseUrl, builder.apiKey,
				getOrDefault(builder.duration, Duration.ofSeconds(10L)));
	}

	public static BoChaWebSearchEngine.Builder builder() {
		return new BoChaWebSearchEngine.Builder();
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
	 * 调用BoCha API进行搜索，并将结果转换为LangChain4j格式
	 * @param webSearchRequest 搜索请求参数（包含关键词、结果数量等）
	 * @return 网络搜索结果，请求失败时返回null
	 */
	@Override
	public WebSearchResults search(WebSearchRequest webSearchRequest) {
		final SearchResponse results = boChaClient.search(webSearchRequest);

		// 检查响应状态码
		if (HTTP_OK != results.getCode()) {
			return null;
		}

		// 转换搜索结果格式
		SearchResponse.WebPages webPages = results.getData().getWebPages();
		List<WebSearchOrganicResult> webSearchOrganicResults = Arrays.stream(webPages.getValue())
			.map(value -> WebSearchOrganicResult.from(value.getName(), URI.create(value.getUrl()), value.getSnippet(),
					value.getSummary()))
			.toList();

		// 构建并返回搜索结果
		return WebSearchResults.from(
				WebSearchInformationResult
					.from(Objects.requireNonNullElse(webPages.getTotalEstimatedMatches(), 0L).longValue()),
				webSearchOrganicResults);
	}

	/**
	 * 构建器类
	 * <p>
	 * 用于创建BoChaWebSearchEngine实例
	 */
	public static class Builder {

		/**
		 * API基础URL
		 */
		private String baseUrl;

		/**
		 * API密钥
		 */
		private String apiKey;

		/**
		 * 请求超时时间
		 */
		private Duration duration;

		public BoChaWebSearchEngine.Builder baseUrl(String baseUrl) {
			this.baseUrl = baseUrl;
			return this;
		}

		public BoChaWebSearchEngine.Builder apiKey(String apiKey) {
			this.apiKey = apiKey;
			return this;
		}

		public BoChaWebSearchEngine.Builder duration(Duration duration) {
			this.duration = duration;
			return this;
		}

		public BoChaWebSearchEngine build() {
			return new BoChaWebSearchEngine(this);
		}

	}

}
