package com.deepdimen.matrix.knowledge.skill;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

import com.deepdimen.matrix.skill.api.AgentSkillTargetReader.Target;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Read-only fingerprint of the existing agent configuration, not a model or corpus
 * version.
 */
public record KnowledgeMarketConfigurationSnapshot(String agentId, List<String> datasetIds, String modelProvider,
		String modelName, String digest, String configurationJson) {

	public KnowledgeMarketConfigurationSnapshot {
		datasetIds = List.copyOf(datasetIds);
	}

	public static KnowledgeMarketConfigurationSnapshot capture(Target target, ObjectMapper json) {
		if (target == null || !"knowledge".equals(target.agentType()) || !positiveId(target.agentId())
				|| "2026".equals(target.agentId()) || target.configurationJson() == null
				|| target.configurationJson().length() > 262144) {
			throw invalid();
		}
		try {
			JsonNode config = json.reader()
				.with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readTree(target.configurationJson());
			if (config == null || !config.isObject() || !config.path("datasetIds").isArray()
					|| config.path("datasetIds").isEmpty() || config.path("datasetIds").size() > 20)
				throw invalid();
			List<String> datasets = new ArrayList<>();
			for (JsonNode id : config.path("datasetIds")) {
				if ((!id.isTextual() && !id.isIntegralNumber()) || !positiveId(id.asText())
						|| datasets.contains(id.asText()))
					throw invalid();
				datasets.add(id.asText());
			}
			JsonNode model = config.path("modelConfig");
			if (!model.isObject() || !model.path("provider").isTextual() || !model.path("modelName").isTextual()
					|| model.path("provider").asText().isBlank() || model.path("modelName").asText().isBlank())
				throw invalid();
			JsonNode canonical = sort(config, json);
			var material = json.createObjectNode().put("agentId", target.agentId()).put("agentType", "knowledge");
			material.set("configuration", canonical);
			String digest = HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(sort(material, json))));
			return new KnowledgeMarketConfigurationSnapshot(target.agentId(), datasets, model.path("provider").asText(),
					model.path("modelName").asText(), digest, json.writeValueAsString(canonical));
		}
		catch (IllegalArgumentException failure) {
			throw failure;
		}
		catch (Exception failure) {
			throw new IllegalArgumentException("KNOWLEDGE_CONFIGURATION_UNVERIFIED", failure);
		}
	}

	public void requireSame(KnowledgeMarketConfigurationSnapshot current) {
		if (current == null || !agentId.equals(current.agentId()) || !digest.equals(current.digest()))
			throw new IllegalArgumentException("EXECUTOR_VERSION_DRIFT");
	}

	private static boolean positiveId(String value) {
		if (value == null || !value.matches("[1-9][0-9]{0,18}"))
			return false;
		try {
			return Long.parseLong(value) > 0;
		}
		catch (NumberFormatException failure) {
			return false;
		}
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
		return new IllegalArgumentException("KNOWLEDGE_CONFIGURATION_UNVERIFIED");
	}
}
