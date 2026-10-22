package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks task-specific structure; generic schema admission and runtime validation remain
 * required.
 */
final class TaskParseSkillContractValidator {

	private TaskParseSkillContractValidator() {
	}

	static void validate(ObjectMapper json, ResolvedSkillSnapshot release) {
		try {
			var reader = json.reader()
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
			JsonNode input = reader.readTree(release.inputSchemaJson());
			object(input, Set.of("text", "currentTime", "parseMode", "maxTasks"),
					Set.of("text", "currentTime", "parseMode", "maxTasks"));
			var in = input.path("properties");
			type(in.path("text"), "string");
			type(in.path("currentTime"), "string");
			type(in.path("parseMode"), "string");
			type(in.path("maxTasks"), "integer");
			for (String name : Set.of("text", "currentTime")) {
				JsonNode field = in.path(name);
				if (field.has("enum") || field.has("const"))
					throw invalid();
			}
			bounds(in.path("text"), "minLength", "maxLength", 1, 30000);
			bounds(in.path("currentTime"), "minLength", "maxLength", 1, 40);
			containsValues(in.path("parseMode"), json.readTree("[\"ROOT\",\"DIRECT_CHILDREN\"]"));
			containsValues(in.path("maxTasks"), json.readTree("[1,30]"));
			bounds(in.path("maxTasks"), "minimum", "maximum", 1, 30);
			JsonNode output = reader.readTree(release.outputSchemaJson());
			object(output, Set.of("tasks", "warnings"), Set.of("tasks"));
			JsonNode tasks = output.path("properties").path("tasks");
			type(tasks, "array");
			bounds(tasks, "minItems", "maxItems", 1, 30);
			JsonNode task = tasks.path("items");
			object(task, Set.of("title", "content", "priority", "dueAt"), Set.of("title", "content"));
			var fields = task.path("properties");
			type(fields.path("title"), "string");
			type(fields.path("content"), "string");
			type(fields.path("priority"), "string");
			nullableString(fields.path("dueAt"));
			if (fields.path("dueAt").has("const") || fields.path("dueAt").has("enum"))
				throw invalid();
			JsonNode warnings = output.path("properties").path("warnings");
			type(warnings, "array");
			type(warnings.path("items"), "string");
			if (!release.allowedParameters().isEmpty() || !release.resourceRefs().isEmpty())
				throw invalid();
		}
		catch (java.io.IOException | NullPointerException ex) {
			throw invalid();
		}
	}

	private static void object(JsonNode schema, Set<String> allowed, Set<String> required) {
		type(schema, "object");
		if (!schema.path("properties").isObject() || !schema.path("additionalProperties").isBoolean()
				|| schema.path("additionalProperties").booleanValue() || !schema.path("required").isArray())
			throw invalid();
		Set<String> fields = new HashSet<>();
		schema.path("properties").fieldNames().forEachRemaining(fields::add);
		if (!fields.equals(allowed))
			throw invalid();
		Set<String> names = new HashSet<>();
		for (JsonNode entry : schema.path("required")) {
			if (!entry.isTextual() || !fields.contains(entry.textValue()) || !names.add(entry.textValue()))
				throw invalid();
		}
		if (!names.containsAll(required))
			throw invalid();
		if (schema.has("const") || schema.has("enum"))
			throw invalid();
	}

	private static void type(JsonNode schema, String type) {
		if (!schema.isObject() || !schema.path("type").isTextual() || !type.equals(schema.path("type").textValue()))
			throw invalid();
	}

	private static void nullableString(JsonNode schema) {
		JsonNode types = schema.path("type");
		if (!schema.isObject() || !types.isArray() || types.size() != 2)
			throw invalid();
		Set<String> names = new HashSet<>();
		for (JsonNode item : types) {
			if (!item.isTextual())
				throw invalid();
			names.add(item.textValue());
		}
		if (!names.equals(Set.of("string", "null")))
			throw invalid();
	}

	private static void bounds(JsonNode schema, String min, String max, int lowest, int highest) {
		if (schema.has(min) && (!schema.get(min).isNumber()
				|| schema.get(min).decimalValue().compareTo(java.math.BigDecimal.valueOf(lowest)) > 0))
			throw invalid();
		if (schema.has(max) && (!schema.get(max).isNumber()
				|| schema.get(max).decimalValue().compareTo(java.math.BigDecimal.valueOf(highest)) < 0))
			throw invalid();
	}

	private static void containsValues(JsonNode schema, JsonNode expected) {
		if (schema.has("const"))
			throw invalid();
		if (!schema.has("enum"))
			return;
		if (!schema.get("enum").isArray())
			throw invalid();
		Set<JsonNode> values = new HashSet<>();
		schema.get("enum").forEach(values::add);
		for (JsonNode value : expected)
			if (!values.contains(value))
				throw invalid();
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("TASK_PARSE_CONTRACT_INCOMPATIBLE");
	}

}
