package com.deepdimen.matrix.knowledge.support.handler.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * REST 客户端 HTTP 请求日志拦截器
 * <p>
 * 实现 Spring 的 ClientHttpRequestInterceptor 接口，用于记录 REST 客户端发起的 HTTP 请求和响应信息。 主要用于调试和监控
 * HTTP 调用，方便排查问题。
 * </p>
 * <p>
 * 功能特点： 1. 记录请求的 URI、方法、头部和请求体 2. 记录响应的状态码、状态文本、头部和响应体 3. 使用缓冲包装器确保响应体可以重复读取 4.
 * 自动过滤二进制内容和大型文件，避免日志污染
 * </p>
 *
 * @author caoyuan
 * @date 2025/05/08
 */
@Slf4j
public class RestClientLoggingInterceptor implements ClientHttpRequestInterceptor {

	/**
	 * 需要过滤的二进制媒体类型
	 */
	private static final List<String> BINARY_MEDIA_TYPES = Arrays.asList("multipart", "image", "video", "audio",
			"application/pdf", "application/zip", "application/x-zip-compressed", "application/octet-stream",
			"application/x-tar", "application/x-rar-compressed", "application/x-7z-compressed");

	/**
	 * 响应体最大长度限制（字节），超过此长度的响应体将被截断
	 */
	private static final int MAX_RESPONSE_BODY_LENGTH = 10000;

	/**
	 * 请求体最大长度限制（字节），超过此长度的请求体将被截断
	 */
	private static final int MAX_REQUEST_BODY_LENGTH = 1000;

	/**
	 * 拦截 HTTP 请求并记录日志
	 * <p>
	 * 该方法在 HTTP 请求执行前后记录相关信息： 1. 记录请求信息（可选） 2. 执行实际的 HTTP 请求 3. 包装响应对象以便重复读取响应体 4. 记录响应信息
	 * </p>
	 * @param request HTTP 请求对象
	 * @param body 请求体字节数组
	 * @param execution 请求执行器，用于执行实际的 HTTP 请求
	 * @return 包装后的 HTTP 响应对象
	 * @throws IOException 当 I/O 操作失败时抛出
	 */
	@Override
	public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
			throws IOException {
		// 记录请求信息
		logRequest(request, body);

		// 执行请求
		ClientHttpResponse response = execution.execute(request, body);

		// 记录响应信息
		// 为了能够重复读取响应体，我们需要包装响应对象
		BufferingClientHttpResponseWrapper bufferedResponse = new BufferingClientHttpResponseWrapper(response);
		logResponse(bufferedResponse);

		return bufferedResponse;
	}

	/**
	 * 记录 HTTP 请求信息
	 * <p>
	 * 当日志级别为 INFO 及以上时，记录请求的详细信息，包括： - URI：请求的目标地址 - Method：HTTP 方法（GET、POST 等） -
	 * Headers：请求头信息 - Request body：请求体内容（如果有且不是二进制内容）
	 * </p>
	 * @param request HTTP 请求对象
	 * @param body 请求体字节数组
	 */
	private void logRequest(HttpRequest request, byte[] body) {
		if (log.isInfoEnabled()) {
			log.info("===========================request begin================================================");
			log.info("URI         : {}", request.getURI());
			log.info("Method      : {}", request.getMethod());
			log.info("Headers     : {}", request.getHeaders());

			if (body.length > 0) {
				// 检查是否为二进制内容
				if (isBinaryContent(request.getHeaders().getContentType())) {
					log.info("Request body: [Binary content - {} bytes] (content filtered)", body.length);
				}
				else if (body.length > MAX_REQUEST_BODY_LENGTH) {
					// 截断过长的请求体
					String truncatedBody = new String(Arrays.copyOf(body, MAX_REQUEST_BODY_LENGTH),
							StandardCharsets.UTF_8);
					log.info("Request body: {} ... [truncated - total {} bytes]", truncatedBody, body.length);
				}
				else {
					// 检查是否包含base64图片数据
					String bodyStr = new String(body, StandardCharsets.UTF_8);
					if (containsBase64Image(bodyStr)) {
						log.info("Request body: [Contains base64 image data - {} bytes] (content filtered)",
								body.length);
					}
					else {
						log.info("Request body: {}", bodyStr);
					}
				}
			}
			log.info("==========================request end==================================================");
		}
	}

	/**
	 * 记录 HTTP 响应信息
	 * <p>
	 * 当日志级别为 INFO 及以上时，记录响应的详细信息，包括： - Status code：HTTP 状态码 - Status text：状态文本描述 -
	 * Headers：响应头信息 - Response body：响应体内容（如果有且不是二进制内容）
	 * </p>
	 * @param response HTTP 响应对象
	 * @throws IOException 当读取响应体失败时抛出
	 */
	private void logResponse(ClientHttpResponse response) throws IOException {
		if (log.isInfoEnabled()) {
			log.info("============================response begin==========================================");
			log.info("Status code  : {}", response.getStatusCode());
			log.info("Status text  : {}", response.getStatusText());
			log.info("Headers      : {}", response.getHeaders());

			// 检查响应的媒体类型
			MediaType contentType = response.getHeaders().getContentType();

			if (isBinaryContent(contentType)) {
				log.info("Response body: [Binary content] (content filtered)");
			}
			else {
				// 从包装器中获取响应体
				String responseBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
				if (!responseBody.isEmpty()) {
					if (responseBody.length() > MAX_RESPONSE_BODY_LENGTH) {
						// 截断过长的响应体
						String truncatedBody = responseBody.substring(0, MAX_RESPONSE_BODY_LENGTH);
						log.info("Response body: {} ... [truncated - total {} characters]", truncatedBody,
								responseBody.length());
					}
					else if (containsBase64Image(responseBody)) {
						log.info("Response body: [Contains base64 image data - {} characters] (content filtered)",
								responseBody.length());
					}
					else {
						log.info("Response body: {}", responseBody);
					}
				}
			}
			log.info("=======================response end=================================================");
		}
	}

	/**
	 * 判断是否为二进制内容类型
	 * @param contentType 媒体类型
	 * @return 如果是二进制内容类型返回true，否则返回false
	 */
	private boolean isBinaryContent(MediaType contentType) {
		if (contentType == null) {
			return false;
		}

		String mediaType = contentType.getType();
		return BINARY_MEDIA_TYPES.stream().anyMatch(mediaType::startsWith);
	}

	/**
	 * 检查字符串是否包含base64编码的图片数据
	 * @param content 要检查的内容
	 * @return 如果包含base64图片数据返回true，否则返回false
	 */
	private boolean containsBase64Image(String content) {
		if (content == null || content.length() < 100) {
			return false;
		}

		// 检查是否包含常见的base64图片数据格式
		return content.contains("data:image/") && content.contains("base64,")
				|| content.matches(".*['\"]data:image/[^'\"]*base64,[^'\"]*['\"].*") ||
				// 检查是否包含大量的base64字符（可能是纯base64图片数据）
				(content.length() > 1000 && content.matches(".*[A-Za-z0-9+/]{500,}.*"));
	}

	/**
	 * 缓冲客户端 HTTP 响应包装类
	 * <p>
	 * 用于包装 ClientHttpResponse 对象，实现响应体的缓冲处理。 该包装器在构造时会读取并缓存整个响应体，以便后续可以多次读取。
	 * </p>
	 * <p>
	 * 注意：如果响应体非常大，这可能会消耗大量内存。 在生产环境中应谨慎使用，或者考虑增加大小限制。
	 * </p>
	 *
	 * @author caoyuan
	 * @date 2025/05/08
	 */
	static class BufferingClientHttpResponseWrapper implements ClientHttpResponse {

		/**
		 * 原始的 HTTP 响应对象
		 */
		private final ClientHttpResponse response;

		/**
		 * 缓存的响应体字节数组
		 */
		private byte[] body;

		/**
		 * 构造函数
		 * <p>
		 * 创建一个新的缓冲响应包装器，并立即读取和缓存响应体。
		 * </p>
		 * @param response 原始的 HTTP 响应对象
		 * @throws IOException 当读取响应体失败时抛出
		 */
		public BufferingClientHttpResponseWrapper(ClientHttpResponse response) throws IOException {
			this.response = response;
			// 在构造时读取并缓冲响应体
			// 注意：这可能会消耗大量内存，如果响应体非常大
			this.body = StreamUtils.copyToByteArray(response.getBody());
		}

		/**
		 * 获取 HTTP 状态码
		 */
		@Override
		public org.springframework.http.HttpStatusCode getStatusCode() throws IOException {
			return this.response.getStatusCode();
		}

		/**
		 * 获取 HTTP 状态文本
		 */
		@Override
		public String getStatusText() throws IOException {
			return this.response.getStatusText();
		}

		/**
		 * 关闭响应
		 */
		@Override
		public void close() {
			this.response.close();
		}

		/**
		 * 获取响应体
		 * <p>
		 * 每次调用 getBody() 都返回一个新的基于缓冲字节数组的 InputStream， 这样可以实现响应体的多次读取。
		 * </p>
		 * @return 包含响应体内容的输入流
		 */
		@Override
		public java.io.InputStream getBody() throws IOException {
			// 每次调用 getBody() 都返回一个新的基于缓冲字节数组的 InputStream
			return new java.io.ByteArrayInputStream(this.body);
		}

		/**
		 * 获取 HTTP 响应头
		 */
		@Override
		public HttpHeaders getHeaders() {
			return this.response.getHeaders();
		}

	}

}