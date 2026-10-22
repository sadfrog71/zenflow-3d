package com.deepdimen.matrix.discover.dify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Dify Service API 客户端
 * <p>
 * 基于 API Key 鉴权，支持普通请求和 SSE 流式响应透传。 与 DifyConsoleClient（Console JWT 鉴权）互补，用于外部应用集成场景。
 *
 * @author matrix
 * @date 2026/03/14
 */
public class DifyServiceClient {

	private final WebClient webClient;

	private final ObjectMapper objectMapper;

	private final String baseUrl;

	private final int streamReadTimeout;

	public DifyServiceClient(WebClient webClient, ObjectMapper objectMapper, String baseUrl, int streamReadTimeout) {
		if (baseUrl == null || baseUrl.isEmpty()) {
			throw new IllegalArgumentException("Dify Service API baseUrl 不能为空，请检查 matrix.dify.service-api-base-url 配置");
		}
		this.webClient = webClient;
		this.objectMapper = objectMapper;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.streamReadTimeout = streamReadTimeout;
	}

	/**
	 * 普通 POST 请求（非流式）
	 * @param path API 路径（如 /chat-messages）
	 * @param apiKey Dify 应用 API Key
	 * @param body 请求体
	 * @return 响应 JSON
	 */
	public Mono<JsonNode> post(String path, String apiKey, Object body) {
		return webClient.post()
			.uri(buildUri(path, null))
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(body)
			.retrieve()
			.bodyToMono(String.class)
			.map(this::parseJson);
	}

	/**
	 * SSE 流式 POST 请求，保留完整 SSE 事件结构（event type + data）
	 * @param path API 路径（如 /chat-messages）
	 * @param apiKey Dify 应用 API Key
	 * @param body 请求体
	 * @return SSE 事件流
	 */
	public Flux<ServerSentEvent<String>> postStream(String path, String apiKey, Object body) {
		return webClient.post()
			.uri(buildUri(path, null))
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(body)
			.retrieve()
			.bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {
			})
			.timeout(Duration.ofSeconds(streamReadTimeout));
	}

	/**
	 * 普通 GET 请求
	 * @param path API 路径
	 * @param apiKey Dify 应用 API Key
	 * @param queryParams 查询参数
	 * @return 响应 JSON
	 */
	public Mono<JsonNode> get(String path, String apiKey, Map<String, String> queryParams) {
		String uri = buildUri(path, queryParams);
		return webClient.get()
			.uri(uri)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
			.retrieve()
			.bodyToMono(String.class)
			.map(this::parseJson);
	}

	/**
	 * DELETE 请求（无 body）
	 * @param path API 路径
	 * @param apiKey Dify 应用 API Key
	 * @return 响应 JSON
	 */
	public Mono<JsonNode> delete(String path, String apiKey) {
		return webClient.delete()
			.uri(buildUri(path, null))
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
			.retrieve()
			.bodyToMono(String.class)
			.map(this::parseJson);
	}

	/**
	 * DELETE 请求（带 body，Dify 部分接口如删除会话需要 body 中包含 user 字段）
	 */
	public Mono<JsonNode> delete(String path, String apiKey, Object body) {
		return requestWithBody(HttpMethod.DELETE, path, apiKey, body);
	}

	/**
	 * PUT 请求（带 body，用于更新会话变量、更新分段等）
	 */
	public Mono<JsonNode> put(String path, String apiKey, Object body) {
		return requestWithBody(HttpMethod.PUT, path, apiKey, body);
	}

	/**
	 * PATCH 请求（带 body，用于变更文档状态等）
	 */
	public Mono<JsonNode> patch(String path, String apiKey, Object body) {
		return requestWithBody(HttpMethod.PATCH, path, apiKey, body);
	}

	/**
	 * 通用带 body 请求（DELETE/PUT/PATCH 共用）
	 * @param method HTTP 方法
	 * @param path API 路径
	 * @param apiKey Dify 应用 API Key
	 * @param body 请求体
	 * @return 响应 JSON
	 */
	private Mono<JsonNode> requestWithBody(HttpMethod method, String path, String apiKey, Object body) {
		return webClient.method(method)
			.uri(buildUri(path, null))
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(body)
			.retrieve()
			.bodyToMono(String.class)
			.map(this::parseJson);
	}

	/**
	 * 文件上传 POST 请求
	 * @param path API 路径
	 * @param apiKey Dify 应用 API Key
	 * @param body Multipart 请求体
	 * @return 响应 JSON
	 */
	public Mono<JsonNode> postMultipart(String path, String apiKey, BodyInserter<?, ? super ClientHttpRequest> body) {
		return webClient.post()
			.uri(buildUri(path, null))
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
			.body(body)
			.retrieve()
			.bodyToMono(String.class)
			.map(this::parseJson);
	}

	/**
	 * 获取 Dify 应用参数配置（user_input_form）
	 * @param apiKey Dify 应用 API Key
	 * @return 响应 JSON（包含 user_input_form 等字段）
	 */
	public Mono<JsonNode> getParameters(String apiKey) {
		return get("/parameters", apiKey, null);
	}

	/**
	 * GET 请求返回原始二进制响应（用于文件预览/下载）
	 * @param path API 路径
	 * @param apiKey Dify 应用 API Key
	 * @param queryParams 查询参数
	 * @return 包含响应体和 Content-Type 的 ResponseEntity
	 */
	public Mono<ResponseEntity<byte[]>> getRaw(String path, String apiKey, Map<String, String> queryParams) {
		String uri = buildUri(path, queryParams);
		return webClient.get()
			.uri(uri)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
			.retrieve()
			.toEntity(byte[].class);
	}

	/**
	 * GET 请求返回原始二进制响应（不带 /v1 API 前缀，用于 Dify 文件预览等非 API 路径）
	 * @param path 完整路径（如 /files/{id}/file-preview）
	 * @param apiKey Dify 应用 API Key
	 * @param queryParams 查询参数
	 * @return 包含响应体和 Content-Type 的 ResponseEntity
	 */
	public Mono<ResponseEntity<byte[]>> getRawDirect(String path, String apiKey, Map<String, String> queryParams) {
		String hostUrl = baseUrl.endsWith("/v1") ? baseUrl.substring(0, baseUrl.length() - 3) : baseUrl;
		String uri = DifyUrlUtils.buildUri(hostUrl, path, queryParams);
		return webClient.get()
			.uri(uri)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
			.retrieve()
			.toEntity(byte[].class);
	}

	/**
	 * POST 请求返回原始二进制响应（用于语音播报 text-to-audio 等返回音频流的接口）
	 * @param path API 路径
	 * @param apiKey Dify 应用 API Key
	 * @param body 请求体（JSON）
	 * @return 包含响应体和 Content-Type 的 ResponseEntity
	 */
	public Mono<ResponseEntity<byte[]>> postRaw(String path, String apiKey, Object body) {
		return webClient.post()
			.uri(buildUri(path, null))
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(body)
			.retrieve()
			.toEntity(byte[].class);
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
			throw new DifyApiException("解析 Dify Service API 响应 JSON 失败: " + e.getMessage(), e);
		}
	}

}
