package com.deepdimen.matrix.discover.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManagedSkillSchemaValidatorTest {

	private final ObjectMapper json = new ObjectMapper();

	private final ManagedSkillSchemaValidator validator = new ManagedSkillSchemaValidator();

	@Test
	void rejectsRemoteReferencesAndUnsupportedConstraintsInsteadOfIgnoringThem() throws Exception {
		for (String source : new String[] { "{\"type\":\"object\",\"$ref\":\"https://example.org/schema\"}",
				"{\"type\":\"string\",\"format\":\"email\"}", "{\"type\":\"array\",\"uniqueItems\":true}",
				"{\"type\":\"string\",\"pattern\":\"(a+)+\"}" }) {
			var schema = json.readTree(source);
			assertThrows(IllegalArgumentException.class, () -> validator.validateSchema(schema));
		}
	}

	@Test
	void rejectsMalformedNestedSchemaAndContradictoryConstraints() throws Exception {
		for (String source : new String[] { "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"strng\"}}}",
				"{\"type\":\"object\",\"required\":[\"missing\"],\"properties\":{}}",
				"{\"type\":\"string\",\"minLength\":3,\"maxLength\":2}", "{\"type\":\"array\",\"minItems\":1.5}",
				"{\"type\":\"number\",\"minimum\":10,\"maximum\":2}", "{\"type\":\"integer\",\"enum\":[\"bad\"]}" }) {
			var schema = json.readTree(source);
			assertThrows(IllegalArgumentException.class, () -> validator.validateSchema(schema));
		}
	}

	@Test
	void reusesRuntimeForNestedRequiredTypesBoundsAndAdditionalProperties() throws Exception {
		var schema = json.readTree("""
				{"type":"object","properties":{"scores":{"type":"array","minItems":1,
				    "items":{"type":"integer","minimum":0,"maximum":100}}},
				 "required":["scores"],"additionalProperties":false}
				""");
		validator.validateValue(schema, json.readTree("{\"scores\":[0,100]}"));
		for (String input : new String[] { "{}", "{\"scores\":[]}", "{\"scores\":[101]}",
				"{\"scores\":[1],\"extra\":true}", "{\"scores\":[\"1\"]}" }) {
			var value = json.readTree(input);
			assertThrows(IllegalArgumentException.class, () -> validator.validateValue(schema, value));
		}
	}

	@Test
	void nullableDateAcceptsUnknownWithoutWeakeningStringConstraints() throws Exception {
		var schema = json.readTree("""
				{"type":"object","properties":{"dueAt":{"type":["string","null"],"minLength":1,"maxLength":40}},
				 "required":["dueAt"],"additionalProperties":false}
				""");
		validator.validateValue(schema, json.readTree("{\"dueAt\":null}"));
		validator.validateValue(schema, json.readTree("{\"dueAt\":\"2026-09-27T12:00:00+08:00\"}"));
		for (String value : new String[] { "{}", "{\"dueAt\":3}", "{\"dueAt\":\"\"}", "{\"dueAt\":{},\"extra\":1}" }) {
			var parsed = json.readTree(value);
			assertThrows(IllegalArgumentException.class, () -> validator.validateValue(schema, parsed));
		}
	}

	@Test
	void nullableNestedTypesRetainTheirNonNullValidation() throws Exception {
		var schema = json.readTree(
				"""
						{"type":["null","object"],"properties":{"scores":{"type":["array","null"],"minItems":1,
						 "items":{"type":["integer","null"],"minimum":0,"maximum":2}}},"required":["scores"],"additionalProperties":false}
						""");
		for (String value : new String[] { "null", "{\"scores\":null}", "{\"scores\":[null,0,2]}" })
			validator.validateValue(schema, json.readTree(value));
		for (String value : new String[] { "{}", "{\"scores\":[]}", "{\"scores\":[3]}", "{\"scores\":[\"1\"]}" }) {
			var parsed = json.readTree(value);
			assertThrows(IllegalArgumentException.class, () -> validator.validateValue(schema, parsed));
		}
	}

	@Test
	void malformedOrAmbiguousTypeUnionsRemainRejected() throws Exception {
		for (String types : new String[] { "[]", "[\"null\"]", "[\"string\",\"integer\"]", "[\"null\",\"null\"]",
				"[\"string\",\"string\"]", "[\"string\",\"null\",\"integer\"]", "[\"strng\",\"null\"]",
				"[null,\"string\"]" }) {
			var schema = json.readTree("{\"type\":" + types + "}");
			assertThrows(IllegalArgumentException.class, () -> validator.validateSchema(schema));
		}
		var wrongKeyword = json.readTree("{\"type\":[\"string\",\"null\"],\"items\":{\"type\":\"integer\"}}");
		assertThrows(IllegalArgumentException.class, () -> validator.validateSchema(wrongKeyword));
	}

	@Test
	void nullableEnumStillRestrictsNullAndNonNullValues() throws Exception {
		var schema = json.readTree("{\"type\":[\"string\",\"null\"],\"enum\":[\"NORMAL\",null]}");
		validator.validateValue(schema, json.readTree("null"));
		validator.validateValue(schema, json.readTree("\"NORMAL\""));
		assertThrows(IllegalArgumentException.class, () -> validator.validateValue(schema, json.readTree("\"HIGH\"")));
		var onlyNormal = json.readTree("{\"type\":[\"null\",\"string\"],\"const\":\"NORMAL\"}");
		assertThrows(IllegalArgumentException.class, () -> validator.validateValue(onlyNormal, json.readTree("null")));
	}

}
