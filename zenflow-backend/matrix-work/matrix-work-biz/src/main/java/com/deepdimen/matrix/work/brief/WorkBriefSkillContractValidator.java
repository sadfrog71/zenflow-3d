package com.deepdimen.matrix.work.brief;

import com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;

/** Checks the published output against the existing brief DTO before enabling it. */
final class WorkBriefSkillContractValidator {

	private static final Set<String> SECTIONS = Set.of("focusItems", "scheduleItems", "informationItems",
			"messageItems", "emailItems", "riskItems", "citations");

	private static final Set<String> TEXT = Set.of("title", "executiveSummary", "continuityNote");

	private static final Set<String> ITEM_TEXT = Set.of("refType", "title", "detail", "priority", "occurredAt");

	private static final Set<String> PROTECTED_INPUT = Set.of("tenantId", "ownerUserId", "generatedAt", "tasks",
			"schedules", "information", "messages", "emails", "previousSummary");

	private WorkBriefSkillContractValidator() {
	}

	static void validate(ObjectMapper json, ResolvedSkillSnapshot release) {
		try {
			JsonNode schema = json.reader()
				.with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readTree(release.outputSchemaJson());
			object(schema);
			required(schema, "title");
			required(schema, "executiveSummary");
			var fields = schema.path("properties").fields();
			while (fields.hasNext()) {
				var field = fields.next();
				String name = field.getKey();
				JsonNode value = field.getValue();
				if (TEXT.contains(name)) {
					type(value, "string");
				}
				else if ("metrics".equals(name)) {
					type(value, "object");
					// Counts are replaced with the authoritative business snapshot, but
					// must first be deserializable as Map<String, Integer>.
					if (!value.path("additionalProperties").isBoolean()
							|| value.path("additionalProperties").asBoolean()) {
						type(value.path("additionalProperties"), "integer");
					}
					var counts = value.path("properties").elements();
					while (counts.hasNext())
						type(counts.next(), "integer");
				}
				else if (SECTIONS.contains(name)) {
					type(value, "array");
					JsonNode item = value.path("items");
					object(item);
					required(item, "refType");
					required(item, "refId");
					var parts = item.path("properties").fields();
					while (parts.hasNext()) {
						var part = parts.next();
						if ("refId".equals(part.getKey()))
							type(part.getValue(), "integer");
						else if (ITEM_TEXT.contains(part.getKey()))
							type(part.getValue(), "string");
						else
							throw invalid();
					}
				}
				else
					throw invalid();
			}
			if (release.allowedParameters() != null
					&& release.allowedParameters().stream().anyMatch(PROTECTED_INPUT::contains))
				throw invalid();
		}
		catch (java.io.IOException | NullPointerException ex) {
			throw invalid();
		}
	}

	private static void object(JsonNode schema) {
		type(schema, "object");
		if (!schema.path("properties").isObject() || !schema.path("additionalProperties").isBoolean()
				|| schema.path("additionalProperties").asBoolean())
			throw invalid();
	}

	private static void required(JsonNode schema, String name) {
		boolean found = false;
		for (JsonNode entry : schema.path("required"))
			if (name.equals(entry.asText()))
				found = true;
		if (!found || !schema.path("properties").has(name))
			throw invalid();
	}

	private static void type(JsonNode schema, String expected) {
		if (schema == null || !schema.isObject() || !expected.equals(schema.path("type").asText()))
			throw invalid();
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("WORK_BRIEF_OUTPUT_CONTRACT_INCOMPATIBLE");
	}

}
