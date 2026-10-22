package com.deepdimen.matrix.assistant.provider.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;

public class AssistantInternalRequestHasher {

	private final ObjectWriter canonicalWriter;

	public AssistantInternalRequestHasher(ObjectMapper objectMapper) {
		ObjectMapper canonicalMapper = objectMapper.copy();
		canonicalMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
		canonicalMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
		canonicalMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
		this.canonicalWriter = canonicalMapper.writer();
	}

	public String hash(Object body) {
		return hashSerializedBody(serialize(body));
	}

	public String serialize(Object body) {
		try {
			return body == null ? "" : canonicalWriter.writeValueAsString(body);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("ASSISTANT_INTERNAL_REQUEST_INVALID", ex);
		}
	}

	public String hashSerializedBody(String body) {
		try {
			String serialized = body == null ? "" : body;
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(serialized.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

}
