package com.deepdimen.matrix.discover.dify;

import cn.hutool.cache.impl.TimedCache;
import com.deepdimen.matrix.discover.dify.config.DifyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Dify Console API 认证服务
 * <p>
 * 封装登录获取 token pair、内存级缓存、基于 refresh_token 的自动续期逻辑。 线程安全，支持并发调用。
 * <p>
 * Dify 1.11+ 登录/刷新接口通过 Set-Cookie 返回 token，不再在 JSON body 中返回。 密码字段需要 Base64 编码后传输。
 *
 * @author matrix
 * @date 2026/03/14
 */
@Slf4j
public class DifyAuthService {

	private static final String LOGIN_PATH = "/console/api/login";

	private static final String REFRESH_PATH = "/console/api/refresh-token";

	private static final String CACHE_KEY = "dify_token";

	/**
	 * token 缓存，默认有效期 50 分钟（Dify 默认 token 有效期 1 小时，提前 10 分钟刷新）
	 */
	private static final long TOKEN_TTL_MS = 50 * 60 * 1000L;

	private final DifyProperties properties;

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	private final TimedCache<String, TokenPair> tokenCache;

	/**
	 * 独立存储 refresh_token，不受 access_token TTL 影响
	 */
	private volatile String lastRefreshToken;

	/**
	 * Dify 是否使用 __Host- 前缀的 Cookie（HTTPS 模式），从登录响应动态检测
	 */
	@Getter
	private volatile boolean hostPrefixMode = false;

	private final ReentrantLock refreshLock = new ReentrantLock();

	public DifyAuthService(DifyProperties properties, RestClient restClient, ObjectMapper objectMapper) {
		this.properties = properties;
		this.restClient = restClient;
		this.objectMapper = objectMapper;
		this.tokenCache = new TimedCache<>(TOKEN_TTL_MS);
		this.tokenCache.schedulePrune(60 * 1000L);
	}

	/**
	 * 获取有效的 access_token，自动处理缓存和刷新
	 * @return 有效的 access_token
	 */
	public String getAccessToken() {
		TokenPair cached = tokenCache.get(CACHE_KEY, false);
		if (cached != null) {
			return cached.getAccessToken();
		}
		return refreshOrLogin();
	}

	/**
	 * 获取当前缓存的 csrf_token
	 * @return csrf_token，如果未登录则先触发登录
	 */
	public String getCsrfToken() {
		TokenPair cached = tokenCache.get(CACHE_KEY, false);
		if (cached == null) {
			refreshOrLogin();
			cached = tokenCache.get(CACHE_KEY, false);
		}
		return cached != null ? cached.getCsrfToken() : null;
	}

	/**
	 * 刷新或重新登录获取 token
	 */
	private String refreshOrLogin() {
		refreshLock.lock();
		try {
			// 双重检查，避免并发重复刷新
			TokenPair cached = tokenCache.get(CACHE_KEY, false);
			if (cached != null) {
				return cached.getAccessToken();
			}

			// 尝试用独立存储的 refresh_token 续期
			if (lastRefreshToken != null) {
				try {
					TokenPair refreshed = doRefresh(lastRefreshToken);
					cacheTokenPair(refreshed);
					log.info("Dify token 刷新成功");
					return refreshed.getAccessToken();
				}
				catch (Exception e) {
					log.warn("Dify token 刷新失败，尝试重新登录: {}", e.getMessage());
				}
			}

			// 刷新失败或无 refresh_token，重新登录
			TokenPair newToken = doLogin();
			cacheTokenPair(newToken);
			log.info("Dify 登录成功");
			return newToken.getAccessToken();
		}
		finally {
			refreshLock.unlock();
		}
	}

	/**
	 * 缓存 token pair，同时独立保存 refresh_token
	 */
	private void cacheTokenPair(TokenPair pair) {
		tokenCache.put(CACHE_KEY, pair);
		this.lastRefreshToken = pair.getRefreshToken();
	}

	/**
	 * 调用 Dify 登录接口（Dify 1.11+ 通过 Set-Cookie 返回 token）
	 */
	private TokenPair doLogin() {
		String encodedPassword = Base64.getEncoder().encodeToString(properties.getPassword().getBytes());
		Map<String, Object> body = Map.of("email", properties.getEmail(), "password", encodedPassword, "remember_me",
				true, "language", "zh-Hans");

		return restClient.post()
			.uri(properties.getBaseUrl() + LOGIN_PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.exchange((request, response) -> {
				if (response.getStatusCode().isError()) {
					String responseBody = new String(response.getBody().readAllBytes());
					throw new DifyAuthException("Dify 登录失败: " + response.getStatusCode() + " " + responseBody);
				}
				// 验证 body 中的 result
				String responseBody = new String(response.getBody().readAllBytes());
				JsonNode root = objectMapper.readTree(responseBody);
				String result = root.path("result").asText();
				if (!"success".equals(result)) {
					throw new DifyAuthException("Dify 登录失败: " + responseBody);
				}
				return extractTokensFromCookies(response.getHeaders());
			});
	}

	/**
	 * 调用 Dify refresh-token 接口（Dify 1.11+ 从 Cookie 读取 refresh_token）
	 */
	private TokenPair doRefresh(String refreshToken) {
		String cookieName = hostPrefixMode ? "__Host-refresh_token" : "refresh_token";
		return restClient.post()
			.uri(properties.getBaseUrl() + REFRESH_PATH)
			.header(HttpHeaders.COOKIE, cookieName + "=" + refreshToken)
			.exchange((request, response) -> {
				if (response.getStatusCode().isError()) {
					String responseBody = new String(response.getBody().readAllBytes());
					throw new DifyAuthException("Dify token 刷新失败: " + response.getStatusCode() + " " + responseBody);
				}
				return extractTokensFromCookies(response.getHeaders());
			});
	}

	/**
	 * 从 Set-Cookie 响应头中提取 access_token 和 refresh_token
	 */
	private TokenPair extractTokensFromCookies(HttpHeaders headers) {
		TokenPair pair = new TokenPair();
		List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
		if (cookies != null) {
			for (String cookie : cookies) {
				String name = cookie.split("=")[0].trim();
				String value = extractCookieValue(cookie);
				// Dify HTTPS 模式下 cookie 名带 __Host- 前缀，动态检测
				if ("access_token".equals(name) || "__Host-access_token".equals(name)) {
					pair.setAccessToken(value);
					if (name.startsWith("__Host-")) {
						this.hostPrefixMode = true;
					}
				}
				else if ("refresh_token".equals(name) || "__Host-refresh_token".equals(name)) {
					pair.setRefreshToken(value);
				}
				else if ("csrf_token".equals(name) || "__Host-csrf_token".equals(name)) {
					pair.setCsrfToken(value);
				}
			}
		}
		Assert.hasText(pair.getAccessToken(), "Dify 响应缺少 access_token cookie");
		Assert.hasText(pair.getRefreshToken(), "Dify 响应缺少 refresh_token cookie");
		return pair;
	}

	/**
	 * 从 Set-Cookie 头中提取 cookie 值（取第一个分号前的 name=value 部分）
	 */
	private String extractCookieValue(String setCookieHeader) {
		String nameValue = setCookieHeader.split(";")[0];
		int eq = nameValue.indexOf('=');
		return eq >= 0 ? nameValue.substring(eq + 1) : "";
	}

	/**
	 * 清除缓存的 access_token（保留 refresh_token 用于续期）
	 */
	public void clearAccessToken() {
		tokenCache.remove(CACHE_KEY);
	}

	/**
	 * 清除所有缓存的 token（用于外部强制重新登录）
	 */
	public void clearAllTokens() {
		tokenCache.remove(CACHE_KEY);
		this.lastRefreshToken = null;
	}

	@Getter
	@Setter
	public static class TokenPair {

		private String accessToken;

		private String refreshToken;

		private String csrfToken;

		@Override
		public String toString() {
			return "TokenPair(accessToken=***, refreshToken=***, csrfToken=***)";
		}

	}

}
