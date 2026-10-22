package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.assistant.run.AssistantJsonSchemaValidator;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;

/** Admission checks for the existing runtime's supported schema vocabulary. */
public final class ManagedSkillSchemaValidator {

	private static final Set<String> TYPES = Set.of("object", "array", "string", "number", "integer", "boolean",
			"null");

	private static final Set<String> KEYWORDS = Set.of("type", "title", "description", "properties", "required",
			"additionalProperties", "items", "minItems", "maxItems", "minLength", "maxLength", "minimum", "maximum",
			"enum", "const");

	public void validateSchema(JsonNode schema) {
		inspect(schema, 0, new int[] { 0 });
	}

	public void validateValue(JsonNode schema, JsonNode value) {
		validateSchema(schema);
		if (value == null) {
			throw invalid("JSON input is missing");
		}
		new AssistantJsonSchemaValidator().validate(schema, value);
	}

	private void inspect(JsonNode schema, int depth, int[] nodes) {
		if (depth > 24 || ++nodes[0] > 2048 || schema == null || !schema.isObject()) {
			throw invalid("schema structure or size");
		}
		schema.fieldNames().forEachRemaining(key -> {
			if (!KEYWORDS.contains(key)) {
				throw invalid("unsupported keyword: " + key);
			}
		});
		String type = primaryType(schema.path("type"));
		for (String annotation : Set.of("title", "description")) {
			if (schema.has(annotation) && !schema.get(annotation).isTextual()) {
				throw invalid(annotation);
			}
		}
		if (schema.has("properties")) {
			JsonNode props = schema.get("properties");
			if (!props.isObject() || !"object".equals(type)) {
				throw invalid("properties");
			}
			props.forEach(child -> inspect(child, depth + 1, nodes));
		}
		if (schema.has("required")) {
			JsonNode required = schema.get("required");
			if (!required.isArray() || !"object".equals(type)) {
				throw invalid("required");
			}
			Set<String> names = new HashSet<>();
			for (JsonNode name : required) {
				if (!name.isTextual() || !names.add(name.asText()) || !schema.path("properties").has(name.asText())) {
					throw invalid("required property is absent or duplicated");
				}
			}
		}
		if (schema.has("additionalProperties")) {
			JsonNode extra = schema.get("additionalProperties");
			if (!"object".equals(type)) {
				throw invalid("additionalProperties");
			}
			if (!extra.isBoolean()) {
				inspect(extra, depth + 1, nodes);
			}
		}
		if (schema.has("items")) {
			if (!"array".equals(type)) {
				throw invalid("items");
			}
			inspect(schema.get("items"), depth + 1, nodes);
		}
		for (String suffix : Set.of("Items", "Length")) {
			String expected = "Items".equals(suffix) ? "array" : "string";
			for (String prefix : Set.of("min", "max")) {
				String key = prefix + suffix;
				if (schema.has(key) && (!expected.equals(type) || !schema.get(key).isIntegralNumber()
						|| !schema.get(key).canConvertToInt() || schema.get(key).asInt() < 0)) {
					throw invalid(key);
				}
			}
			if (schema.has("min" + suffix) && schema.has("max" + suffix)
					&& schema.get("min" + suffix).asInt() > schema.get("max" + suffix).asInt()) {
				throw invalid("contradictory " + suffix + " bounds");
			}
		}
		for (String key : Set.of("minimum", "maximum")) {
			if (schema.has(key) && (!Set.of("number", "integer").contains(type) || !schema.get(key).isNumber())) {
				throw invalid(key);
			}
		}
		if (schema.has("minimum") && schema.has("maximum")
				&& schema.get("minimum").decimalValue().compareTo(schema.get("maximum").decimalValue()) > 0) {
			throw invalid("contradictory numeric bounds");
		}
		if (schema.has("enum")) {
			JsonNode values = schema.get("enum");
			if (!values.isArray() || values.isEmpty()) {
				throw invalid("enum");
			}
			Set<JsonNode> unique = new HashSet<>();
			for (JsonNode value : values) {
				if (!unique.add(value)) {
					throw invalid("duplicate enum");
				}
				new AssistantJsonSchemaValidator().validate(schema, value);
			}
		}
		if (schema.has("const")) {
			new AssistantJsonSchemaValidator().validate(schema, schema.get("const"));
		}
	}

	private String primaryType(JsonNode declared) {
		if (declared.isTextual() && TYPES.contains(declared.textValue()))
			return declared.textValue();
		// The runtime already supports unions. Admission deliberately permits only
		// one business type plus null, retaining unambiguous keyword validation.
		if (declared.isArray() && declared.size() == 2) {
			Set<String> types = new HashSet<>();
			for (JsonNode item : declared) {
				if (!item.isTextual() || !TYPES.contains(item.textValue()) || !types.add(item.textValue()))
					throw invalid("invalid nullable type");
			}
			if (types.remove("null") && types.size() == 1)
				return types.iterator().next();
		}
		throw invalid("one explicit type or one business type plus null is required");
	}

	private IllegalArgumentException invalid(String reason) {
		return new IllegalArgumentException("SKILL_SCHEMA_INVALID: " + reason);
	}

}
