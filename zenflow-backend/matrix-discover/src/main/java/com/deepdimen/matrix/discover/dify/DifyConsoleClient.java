package com.deepdimen.matrix.discover.dify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Dify Console API 统一 HTTP 客户端
 * <p>
 * 自动注入 Bearer token，token 过期时自动重试一次。 后续新增 Dify 业务接口只需调用 get/post/put/delete 方法。
 *
 * @author matrix
 * @date 2026/03/14
 */
@Slf4j
public class DifyConsoleClient {

	private final DifyAuthService authService;

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	private final String baseUrl;

	private static final String HEADER_CSRF_TOKEN = "X-CSRF-Token";

	public DifyConsoleClient(DifyAuthService authService, RestClient restClient, ObjectMapper objectMapper,
			String baseUrl) {
		this.authService = authService;
		this.restClient = restClient;
		this.objectMapper = objectMapper;
		this.baseUrl = baseUrl;
	}

	/**
	 * 构建 Cookie 头（兼容 Dify HTTPS 模式的 __Host- 前缀）
	 * <p>
	 * Dify 1.13+ CSRF 验证会比较 X-CSRF-Token header 和 Cookie 中的值， HTTPS 模式下 Cookie name 为
	 * __Host-csrf_token。 是否使用 __Host- 前缀从 Dify
	 * 登录响应动态检测（DifyAuthService.isHostPrefixMode）。
	 */
	private String buildCookieHeader(String accessToken, String csrfToken) {
		boolean hostPrefix = authService.isHostPrefixMode();
		String csrfCookieName = hostPrefix ? "__Host-csrf_token" : "csrf_token";
		String accessCookieName = hostPrefix ? "__Host-access_token" : "access_token";
		return csrfCookieName + "=" + csrfToken + "; " + accessCookieName + "=" + accessToken;
	}

	/**
	 * GET 请求
	 * @param path API 路径（如 /console/api/apps）
	 * @return 响应 JSON
	 */
	public JsonNode get(String path) {
		return get(path, null);
	}

	/**
	 * GET 请求（带查询参数）
	 * @param path API 路径
	 * @param queryParams 查询参数
	 * @return 响应 JSON
	 */
	public JsonNode get(String path, Map<String, String> queryParams) {
		return executeWithRetry((token, csrfToken) -> {
			RestClient.RequestHeadersSpec<?> spec = restClient.get()
				.uri(buildUri(path, queryParams))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.header(HEADER_CSRF_TOKEN, csrfToken)
				.header(HttpHeaders.COOKIE, buildCookieHeader(token, csrfToken));
			String body = spec.retrieve().body(String.class);
			return parseJson(body);
		});
	}

	/**
	 * POST 请求
	 * @param path API 路径
	 * @param requestBody 请求体
	 * @return 响应 JSON
	 */
	public JsonNode post(String path, Object requestBody) {
		return executeWithRetry((token, csrfToken) -> {
			String body = restClient.post()
				.uri(buildUri(path, null))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.header(HEADER_CSRF_TOKEN, csrfToken)
				.header(HttpHeaders.COOKIE, buildCookieHeader(token, csrfToken))
				.contentType(MediaType.APPLICATION_JSON)
				.body(requestBody)
				.retrieve()
				.body(String.class);
			return parseJson(body);
		});
	}

	/**
	 * PUT 请求
	 * @param path API 路径
	 * @param requestBody 请求体
	 * @return 响应 JSON
	 */
	public JsonNode put(String path, Object requestBody) {
		return executeWithRetry((token, csrfToken) -> {
			String body = restClient.put()
				.uri(buildUri(path, null))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.header(HEADER_CSRF_TOKEN, csrfToken)
				.header(HttpHeaders.COOKIE, buildCookieHeader(token, csrfToken))
				.contentType(MediaType.APPLICATION_JSON)
				.body(requestBody)
				.retrieve()
				.body(String.class);
			return parseJson(body);
		});
	}

	/**
	 * DELETE 请求
	 * @param path API 路径
	 * @return 响应 JSON
	 */
	public JsonNode delete(String path) {
		return executeWithRetry((token, csrfToken) -> {
			String body = restClient.delete()
				.uri(buildUri(path, null))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.header(HEADER_CSRF_TOKEN, csrfToken)
				.header(HttpHeaders.COOKIE, buildCookieHeader(token, csrfToken))
				.retrieve()
				.body(String.class);
			return parseJson(body);
		});
	}

	/**
	 * PATCH 请求
	 * @param path API 路径
	 * @param requestBody 请求体
	 * @return 响应 JSON
	 */
	public JsonNode patch(String path, Object requestBody) {
		return executeWithRetry((token, csrfToken) -> {
			String body = restClient.patch()
				.uri(buildUri(path, null))
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.header(HEADER_CSRF_TOKEN, csrfToken)
				.header(HttpHeaders.COOKIE, buildCookieHeader(token, csrfToken))
				.contentType(MediaType.APPLICATION_JSON)
				.body(requestBody)
				.retrieve()
				.body(String.class);
			return parseJson(body);
		});
	}

	/**
	 * 带 token 过期自动重试的请求执行器
	 * <p>
	 * 首次请求使用缓存 token，若返回 401 则清除缓存重新获取 token 后重试一次
	 */
	private JsonNode executeWithRetry(BiFunction<String, String, JsonNode> request) {
		String token = authService.getAccessToken();
		String csrfToken = authService.getCsrfToken();
		try {
			return request.apply(token, csrfToken);
		}
		catch (HttpClientErrorException.Unauthorized e) {
			log.warn("Dify token 已过期，尝试重新获取");
			authService.clearAccessToken();
			String newToken = authService.getAccessToken();
			String newCsrfToken = authService.getCsrfToken();
			return request.apply(newToken, newCsrfToken);
		}
	}

	private String buildUri(String path, Map<String, String> queryParams) {
		return DifyUrlUtils.buildUri(baseUrl, path, queryParams);
	}

	private JsonNode parseJson(String body) {
		try {
			if (body == null || body.isEmpty()) {
				return objectMapper.createObjectNode();
			}
			return objectMapper.readTree(body);
		}
		catch (Exception e) {
			throw new DifyApiException("解析 Dify 响应 JSON 失败: " + e.getMessage(), e);
		}
	}

}
