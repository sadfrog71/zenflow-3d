package com.deepdimen.matrix.discover.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

/**
 * Snapshot of a console app's current classic-chat configuration, not a workflow export.
 */
public record DifyChatConfigurationSnapshot(String appId, String modelConfigId, String digest) {
	public static DifyChatConfigurationSnapshot capture(String expectedApp, JsonNode app, ObjectMapper json) {
		if (expectedApp == null || expectedApp.isBlank() || app == null || !expectedApp.equals(app.path("id").asText())
				|| !"chat".equals(app.path("mode").asText()) || !app.path("model_config").isObject())
			throw invalid();
		JsonNode config = app.path("model_config");
		String id = config.path("id").asText("");
		if (id.isBlank() || !config.path("pre_prompt").isTextual()
				|| !config.path("pre_prompt").textValue().contains("{{extended_system_prompt}}"))
			throw invalid();
		if (app.hasNonNull("app_model_config_id") && !id.equals(app.path("app_model_config_id").asText()))
			throw invalid();
		var material = json.createObjectNode();
		material.put("appId", expectedApp);
		material.put("mode", "chat");
		material.set("modelConfig", config);
		try {
			String canonical = json.writeValueAsString(sort(material, json));
			return new DifyChatConfigurationSnapshot(expectedApp, id, HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))));
		}
		catch (Exception failure) {
			throw new IllegalArgumentException("DIFY_CHAT_CONFIGURATION_UNVERIFIED", failure);
		}
	}

	public void requireSame(DifyChatConfigurationSnapshot current) {
		if (!equals(current))
			throw new IllegalArgumentException("EXECUTOR_VERSION_DRIFT");
	}

	private static JsonNode sort(JsonNode value, ObjectMapper json) {
		if (value.isObject()) {
			var fields = new TreeMap<String, JsonNode>();
			value.fields().forEachRemaining(e -> fields.put(e.getKey(), sort(e.getValue(), json)));
			var result = json.createObjectNode();
			fields.forEach(result::set);
			return result;
		}
		if (value.isArray()) {
			var result = json.createArrayNode();
			value.forEach(v -> result.add(sort(v, json)));
			return result;
		}
		return value;
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("DIFY_CHAT_CONFIGURATION_UNVERIFIED");
	}
}
