package com.deepdimen.matrix.zenflow.admin;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Wire format is separate from a human-readable supplier label. */
enum ZenflowModelProtocol {

	OPENAI_CHAT_COMPLETIONS, ANTHROPIC_MESSAGES;

	static ZenflowModelProtocol select(JsonNode metadata, Object supplier) {
		if (metadata.hasNonNull("zenflowApiProtocol"))
			return parse(metadata.path("zenflowApiProtocol").asText());
		// Compatibility with configurations saved before the protocol selector existed.
		return "anthropic".equalsIgnoreCase(String.valueOf(supplier).trim()) ? ANTHROPIC_MESSAGES
				: OPENAI_CHAT_COMPLETIONS;
	}

	static ZenflowModelProtocol parse(Object value) {
		try {
			return valueOf(String.valueOf(value));
		}
		catch (IllegalArgumentException failure) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_MODEL_PROTOCOL");
		}
	}

	URI endpoint(URI base) {
		String url = base.toString().replaceAll("/+$", "");
		if (this == OPENAI_CHAT_COMPLETIONS)
			return URI.create(url + "/chat/completions");
		return URI.create(url + (url.endsWith("/v1") ? "/messages" : "/v1/messages"));
	}

	Map<String, String> headers(String key) {
		return this == OPENAI_CHAT_COMPLETIONS ? Map.of("Authorization", "Bearer " + key)
				: Map.of("x-api-key", key, "Authorization", "Bearer " + key, "anthropic-version", "2023-06-01");
	}

}
