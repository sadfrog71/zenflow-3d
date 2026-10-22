package com.deepdimen.matrix.discover.controller;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/** Safe, client-visible classification for failures while proxying a Dify SSE stream. */
record DifyStreamFailure(String code, String message) {

	static DifyStreamFailure from(Throwable error, ObjectMapper objectMapper) {
		WebClientResponseException responseError = findCause(error, WebClientResponseException.class);
		if (responseError != null) {
			return new DifyStreamFailure("DIFY_UPSTREAM_HTTP_" + responseError.getStatusCode().value(),
					httpMessage(responseError, objectMapper));
		}
		if (hasCause(error, TimeoutException.class) || hasCause(error, HttpTimeoutException.class)) {
			return new DifyStreamFailure("DIFY_UPSTREAM_TIMEOUT", "Dify 服务连接或响应超时");
		}
		if (hasCause(error, WebClientRequestException.class) || hasCause(error, ConnectException.class)
				|| hasCause(error, UnknownHostException.class)) {
			return new DifyStreamFailure("DIFY_UPSTREAM_UNREACHABLE", "Dify 服务当前不可连接");
		}
		return new DifyStreamFailure("DIFY_STREAM_FAILED", "Dify 智能体事件流处理失败");
	}

	private static String httpMessage(WebClientResponseException error, ObjectMapper objectMapper) {
		String fallback = "Dify 服务返回 HTTP " + error.getStatusCode().value();
		try {
			JsonNode body = objectMapper.readTree(error.getResponseBodyAsString());
			String message = body.path("message").asText("").trim();
			if (!message.isEmpty()) {
				return message.length() <= 240 ? message : message.substring(0, 240);
			}
		}
		catch (Exception ignored) {
		}
		return fallback;
	}

	private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
		return findCause(error, type) != null;
	}

	private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
		Throwable current = error;
		for (int depth = 0; current != null && depth < 12; depth++) {
			if (type.isInstance(current)) {
				return type.cast(current);
			}
			if (current.getCause() == current) {
				break;
			}
			current = current.getCause();
		}
		return null;
	}

}
