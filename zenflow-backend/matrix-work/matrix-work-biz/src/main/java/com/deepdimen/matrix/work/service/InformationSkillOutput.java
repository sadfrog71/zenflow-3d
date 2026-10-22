package com.deepdimen.matrix.work.service;

import com.fasterxml.jackson.databind.*;
import java.util.Set;

/** Strict managed-output contract; never coerces approval strings into booleans. */
final class InformationSkillOutput {

	private InformationSkillOutput() {
	}

	static JsonNode validate(ObjectMapper json, String slot, String raw) {
		try {
			if (raw == null || raw.length() > 32768)
				throw invalid();
			JsonNode value = json.reader()
				.with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readTree(raw);
			if (value == null || !value.isObject())
				throw invalid();
			Set<String> fields;
			if ("ANALYZE".equals(slot)) {
				fields = Set.of("eligible", "contentType", "title", "summary", "tags", "confidence", "reason");
				if (!value.path("eligible").isBoolean())
					throw invalid();
				text(value, "reason", 2000);
				// Rejected analyses can stop with only eligible=false and an explanation.
				if (value.path("eligible").booleanValue()) {
					if (!value.path("contentType").isTextual()
							|| !Set.of("CASE", "PROJECT").contains(value.path("contentType").textValue()))
						throw invalid();
					text(value, "title", 1000);
					text(value, "summary", 1000);
					text(value, "tags", 500);
					if (!value.path("confidence").isNumber() || value.path("confidence").doubleValue() < 0
							|| value.path("confidence").doubleValue() > 1)
						throw invalid();
				}
			}
			else if ("REVIEW".equals(slot)) {
				fields = Set.of("approved", "riskLevel", "reason");
				if (!value.path("approved").isBoolean() || !value.path("riskLevel").isTextual()
						|| !Set.of("LOW", "MEDIUM", "HIGH").contains(value.path("riskLevel").textValue()))
					throw invalid();
				text(value, "reason", 2000);
			}
			else
				throw invalid();
			value.fieldNames().forEachRemaining(name -> {
				if (!fields.contains(name))
					throw invalid();
			});
			return value;
		}
		catch (Exception failure) {
			throw new IllegalArgumentException("OUTPUT_INFORMATION_INVALID", failure);
		}
	}

	private static void text(JsonNode value, String key, int max) {
		if (!value.path(key).isTextual() || value.path(key).textValue().length() > max)
			throw invalid();
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("OUTPUT_INFORMATION_INVALID");
	}

}
