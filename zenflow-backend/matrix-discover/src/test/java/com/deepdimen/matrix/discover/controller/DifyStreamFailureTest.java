package com.deepdimen.matrix.discover.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class DifyStreamFailureTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void classifiesConnectTimeoutWithoutLeakingTransportDetails() {
		WebClientRequestException error = new WebClientRequestException(
				new HttpConnectTimeoutException("HTTP connect timed out"), org.springframework.http.HttpMethod.POST,
				URI.create("http://dify.example/v1/chat-messages"), HttpHeaders.EMPTY);

		DifyStreamFailure failure = DifyStreamFailure.from(error, objectMapper);

		assertEquals("DIFY_UPSTREAM_TIMEOUT", failure.code());
		assertEquals("Dify 服务连接或响应超时", failure.message());
	}

	@Test
	void classifiesConnectionRefusal() {
		DifyStreamFailure failure = DifyStreamFailure.from(new ConnectException("refused"), objectMapper);

		assertEquals("DIFY_UPSTREAM_UNREACHABLE", failure.code());
	}

	@Test
	void preservesSafeUpstreamHttpMessage() {
		WebClientResponseException error = WebClientResponseException.create(502, "Bad Gateway", HttpHeaders.EMPTY,
				"{\"message\":\"模型服务暂不可用\"}".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

		DifyStreamFailure failure = DifyStreamFailure.from(error, objectMapper);

		assertEquals("DIFY_UPSTREAM_HTTP_502", failure.code());
		assertEquals("模型服务暂不可用", failure.message());
	}

	@Test
	void classifiesUnknownStreamFailureWithStableFallback() {
		DifyStreamFailure failure = DifyStreamFailure.from(new IllegalStateException("decoder detail"), objectMapper);

		assertEquals("DIFY_STREAM_FAILED", failure.code());
		assertEquals("Dify 智能体事件流处理失败", failure.message());
	}

}
