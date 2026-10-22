package com.deepdimen.matrix.assistant.run;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.stereotype.Component;

/**
 * Deterministic JSON Schema subset shared by Skill analysis and Dify Workflow
 * contracts. Unsupported annotation keywords are inert; executable references are
 * intentionally not resolved.
 */
@Component
public class AssistantJsonSchemaValidator {

	public void validate(JsonNode schema, JsonNode value) {
		validate(schema, value, "$");
	}

	private void validate(JsonNode schema, JsonNode value, String path) {
		if (schema == null || !schema.isObject()) {
			throw violation("SCHEMA_NOT_OBJECT", path);
		}
		if (!matchesType(schema.path("type"), value)) {
			throw violation("TYPE", path);
		}
		JsonNode constant = schema.get("const");
		if (constant != null && !constant.equals(value)) {
			throw violation("CONST", path);
		}
		JsonNode enums = schema.path("enum");
		if (enums.isArray() && !contains(enums, value)) {
			throw violation("ENUM", path);
		}
		if (value.isTextual()) {
			validateString(schema, value.asText(), path);
		}
		else if (value.isArray()) {
			validateArray(schema, value, path);
		}
		else if (value.isObject()) {
			validateObject(schema, value, path);
		}
		else if (value.isNumber()) {
			validateNumber(schema, value.decimalValue(), path);
		}
	}

	private boolean matchesType(JsonNode declared, JsonNode value) {
		if (declared.isMissingNode() || declared.isNull()) {
			return true;
		}
		if (declared.isTextual()) {
			return matchesType(declared.asText(), value);
		}
		if (!declared.isArray()) {
			return false;
		}
		for (JsonNode item : declared) {
			if (item.isTextual() && matchesType(item.asText(), value)) {
				return true;
			}
		}
		return false;
	}

	private boolean matchesType(String declared, JsonNode value) {
		return switch (declared) {
			case "object" -> value.isObject();
			case "array" -> value.isArray();
			case "string" -> value.isTextual();
			case "number" -> value.isNumber();
			case "integer" -> value.isIntegralNumber();
			case "boolean" -> value.isBoolean();
			case "null" -> value.isNull();
			default -> false;
		};
	}

	private void validateString(JsonNode schema, String value, String path) {
		int length = value.codePointCount(0, value.length());
		if (schema.has("minLength") && length < nonNegativeInt(schema.get("minLength"), path)) {
			throw violation("MIN_LENGTH", path);
		}
		if (schema.has("maxLength") && length > nonNegativeInt(schema.get("maxLength"), path)) {
			throw violation("MAX_LENGTH", path);
		}
		if (schema.path("pattern").isTextual()) {
			String expression = schema.path("pattern").asText();
			if (expression.length() > 256) {
				throw violation("PATTERN_TOO_LONG", path);
			}
			try {
				if (!Pattern.compile(expression).matcher(value).find()) {
					throw violation("PATTERN", path);
				}
			}
			catch (PatternSyntaxException ex) {
				throw violation("PATTERN_INVALID", path);
			}
		}
	}

	private void validateArray(JsonNode schema, JsonNode value, String path) {
		if (schema.has("minItems") && value.size() < nonNegativeInt(schema.get("minItems"), path)) {
			throw violation("MIN_ITEMS", path);
		}
		if (schema.has("maxItems") && value.size() > nonNegativeInt(schema.get("maxItems"), path)) {
			throw violation("MAX_ITEMS", path);
		}
		JsonNode items = schema.path("items");
		if (items.isObject()) {
			for (int index = 0; index < value.size(); index++) {
				validate(items, value.get(index), path + "[" + index + "]");
			}
		}
	}

	private void validateObject(JsonNode schema, JsonNode value, String path) {
		JsonNode required = schema.path("required");
		if (required.isArray()) {
			for (JsonNode name : required) {
				if (!name.isTextual() || !value.has(name.asText())) {
					throw violation("REQUIRED", path);
				}
			}
		}
		JsonNode properties = schema.path("properties");
		JsonNode additional = schema.get("additionalProperties");
		value.fields().forEachRemaining(field -> {
			JsonNode propertySchema = properties.isObject() ? properties.get(field.getKey()) : null;
			if (propertySchema != null) {
				validate(propertySchema, field.getValue(), child(path, field.getKey()));
			}
			else if (additional != null && additional.isBoolean() && !additional.asBoolean()) {
				throw violation("ADDITIONAL_PROPERTY", child(path, field.getKey()));
			}
			else if (additional != null && additional.isObject()) {
				validate(additional, field.getValue(), child(path, field.getKey()));
			}
		});
	}

	private void validateNumber(JsonNode schema, BigDecimal value, String path) {
		if (schema.path("minimum").isNumber() && value.compareTo(schema.path("minimum").decimalValue()) < 0) {
			throw violation("MINIMUM", path);
		}
		if (schema.path("maximum").isNumber() && value.compareTo(schema.path("maximum").decimalValue()) > 0) {
			throw violation("MAXIMUM", path);
		}
	}

	private int nonNegativeInt(JsonNode node, String path) {
		if (node == null || !node.canConvertToInt() || node.asInt() < 0) {
			throw violation("SCHEMA_LIMIT_INVALID", path);
		}
		return node.asInt();
	}

	private boolean contains(JsonNode values, JsonNode expected) {
		List<JsonNode> candidates = new ArrayList<>();
		values.forEach(candidates::add);
		return candidates.contains(expected);
	}

	private String child(String parent, String name) {
		return parent + "." + name.replaceAll("[^A-Za-z0-9_-]", "_");
	}

	private SchemaViolationException violation(String reason, String path) {
		return new SchemaViolationException(reason + ":" + path);
	}

	public static final class SchemaViolationException extends IllegalArgumentException {

		private static final long serialVersionUID = 1L;

		private SchemaViolationException(String message) {
			super(message);
		}

	}

}
