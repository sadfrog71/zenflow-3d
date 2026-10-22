package com.deepdimen.matrix.work.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.deepdimen.matrix.work.api.entity.InformationPolicy;
import com.deepdimen.matrix.work.service.InformationTavilyConfigService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TavilyDiscoveryProvider {

	private final InformationTavilyConfigService configService;
	private final RestClient.Builder restClientBuilder;
	private final boolean configureTimeouts;

	/**
	 * Keep the production constructor independent of an optional RestClient
	 * auto-configuration while allowing deterministic HTTP contract tests to
	 * bind a MockRestServiceServer to the builder.
	 */
	@Autowired
	public TavilyDiscoveryProvider(InformationTavilyConfigService configService) {
		this(configService, RestClient.builder(), true);
	}

	public TavilyDiscoveryProvider(InformationTavilyConfigService configService, RestClient.Builder restClientBuilder) {
		this(configService, restClientBuilder, true);
	}

	public TavilyDiscoveryProvider(InformationTavilyConfigService configService, RestClient.Builder restClientBuilder,
			boolean configureTimeouts) {
		this.configService = configService;
		this.restClientBuilder = restClientBuilder;
		this.configureTimeouts = configureTimeouts;
	}

	public List<DiscoveredDocument> discover(InformationPolicy policy) {
		InformationTavilyConfigService.RuntimeConfig config = configService.current();
		ensureEnabled(config);
		RestClient client = client(config);
		Map<String, Object> searchBody = new LinkedHashMap<>();
		searchBody.put("query", policy.getQueryText());
		searchBody.put("topic", topic(policy.getTopic()));
		String timeRange = timeRange(policy.getTimeRange());
		if (!timeRange.isBlank()) {
			searchBody.put("time_range", timeRange);
		}
		searchBody.put("search_depth", config.searchDepth());
		searchBody.put("max_results", config.maxResults());

		searchBody.put("include_answer", false);
		searchBody.put("include_raw_content", false);
		putDomains(searchBody, "include_domains", policy.getIncludeDomains());
		putDomains(searchBody, "exclude_domains", policy.getExcludeDomains());

		JsonNode searchResponse = post(client, "/search", config.apiKey(), searchBody);
		if (searchResponse == null || !searchResponse.path("results").isArray()) {
			return List.of();
		}
		List<DiscoveredDocument> discovered = new ArrayList<>();
		for (JsonNode result : searchResponse.path("results")) {
			String url = result.path("url").asText("");
			if (!isHttpUrl(url)) {
				continue;
			}
			discovered.add(new DiscoveredDocument(result.path("title").asText(""), url,
					result.path("content").asText(""), result.path("published_date").asText(null)));
		}
		if (discovered.isEmpty()) {
			return discovered;
		}

		List<String> urls = discovered.stream().map(DiscoveredDocument::url).distinct().limit(20).toList();
		Map<String, Object> extractBody = new LinkedHashMap<>();
		extractBody.put("urls", urls);
		extractBody.put("extract_depth", "basic");
		extractBody.put("format", "markdown");
		JsonNode extractResponse = post(client, "/extract", config.apiKey(), extractBody);
		Map<String, String> extracted = new LinkedHashMap<>();
		if (extractResponse != null && extractResponse.path("results").isArray()) {
			for (JsonNode result : extractResponse.path("results")) {
				extracted.put(result.path("url").asText(""), result.path("raw_content").asText(""));
			}
		}
		return discovered.stream()
				.map(item -> new DiscoveredDocument(item.title(), item.url(),
						extracted.getOrDefault(item.url(), item.preview()), item.publishedDate()))
				.filter(item -> item.content() != null && !item.content().isBlank())
				.toList();
	}

	private RestClient client(InformationTavilyConfigService.RuntimeConfig config) {
		RestClient.Builder builder = restClientBuilder.baseUrl(config.baseUrl());
		if (configureTimeouts) {
			SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
			Duration timeout = Duration.ofSeconds(config.timeoutSeconds());
			factory.setConnectTimeout(timeout);
			factory.setReadTimeout(timeout);
			builder.requestFactory(factory);
		}
		return builder.build();
	}

	private void ensureEnabled(InformationTavilyConfigService.RuntimeConfig config) {
		if (!config.enabled() || config.apiKey() == null || config.apiKey().isBlank()) {
			throw new IllegalStateException("Tavily采集未启用，请在AI资讯管理中配置Key或设置TAVILY_API_KEY");
		}
		if (!config.apiKey().startsWith("tvly-")) {
			throw new IllegalStateException("当前保存的 Tavily Key 格式无效，请在AI资讯管理中重新录入");
		}
	}

	private JsonNode post(RestClient client, String path, String apiKey, Map<String, Object> body) {
		try {
			return client.post().uri(path)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
				.body(body).retrieve().body(JsonNode.class);
		}
		catch (RestClientResponseException ex) {
			if (ex.getStatusCode().value() == 401) {
				throw new IllegalStateException("Tavily Key 无效或已失效，请在AI资讯管理中重新录入", ex);
			}
			throw new IllegalStateException("Tavily API 调用失败（HTTP " + ex.getStatusCode().value() + "）", ex);
		}
	}

	private void putDomains(Map<String, Object> body, String key, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		List<String> domains = Arrays.stream(value.split("[,\\n]"))
				.map(String::trim).filter(item -> !item.isBlank()).distinct().toList();
		if (!domains.isEmpty()) {
			body.put(key, domains);
		}
	}

	private String topic(String value) {
		return "general".equalsIgnoreCase(value) ? "general" : "news";
	}

	private String timeRange(String value) {
		if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
			return "";
		}
		return switch (value.toLowerCase()) {
			case "day", "week", "month", "year" -> value.toLowerCase();
			default -> "month";
		};
	}

	private boolean isHttpUrl(String value) {
		try {
			URI uri = URI.create(value);
			String scheme = uri.getScheme();
			return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
					&& uri.getHost() != null && !uri.getHost().isBlank();
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

	public record DiscoveredDocument(String title, String url, String content, String publishedDate) {
		public String preview() {
			return content;
		}
	}
}
