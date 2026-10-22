package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskParseSkillContractValidatorTest {

	static final String INPUT = """
			{"type":"object","properties":{"text":{"type":"string","minLength":1,"maxLength":30000},
			"currentTime":{"type":"string"},"parseMode":{"type":"string","enum":["ROOT","DIRECT_CHILDREN"]},
			"maxTasks":{"type":"integer","enum":[1,30]}},"required":["text","currentTime","parseMode","maxTasks"],"additionalProperties":false}
			""";
	static final String OUTPUT = """
			{"type":"object","properties":{"tasks":{"type":"array","minItems":1,"maxItems":30,"items":{
			"type":"object","properties":{"title":{"type":"string"},"content":{"type":"string"},
			"priority":{"type":"string"},"dueAt":{"type":["string","null"]}},"required":["title","content"],"additionalProperties":false}},
			"warnings":{"type":"array","items":{"type":"string"}}},"required":["tasks"],"additionalProperties":false}
			""";

	private final ObjectMapper json = new ObjectMapper();

	private ResolvedSkillSnapshot release(String input, String output) {
		return new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, "77", null), input, output, "original", List.of(), "{}", List.of());
	}

	@Test
	void acceptsBothServerModesAndNullableDateWithoutChangingInstructions() {
		assertDoesNotThrow(() -> TaskParseSkillContractValidator.validate(json, release(INPUT, OUTPUT)));
		assertDoesNotThrow(() -> TaskParseSkillContractValidator.validate(json,
				release(INPUT, OUTPUT.replace("[\"string\",\"null\"]", "[\"null\",\"string\"]"))));
	}

	@Test
	void rejectsInputThatCannotRepresentTheOriginalTextOrBothModes() throws Exception {
		for (String property : List.of("text", "currentTime", "parseMode", "maxTasks")) {
			ObjectNode input = (ObjectNode) json.readTree(INPUT);
			((ObjectNode) input.get("properties")).remove(property);
			reject(input.toString(), OUTPUT);
		}
		reject(INPUT.replace("30000", "200"), OUTPUT);
		reject(INPUT.replace("[1,30]", "[1]"), OUTPUT);
		reject(INPUT.replace("[\"ROOT\",\"DIRECT_CHILDREN\"]", "[\"ROOT\"]"), OUTPUT);
		reject(INPUT.replace("\"type\":\"string\",\"minLength\"",
				"\"type\":\"string\",\"const\":\"fixed\",\"minLength\""), OUTPUT);
		reject(INPUT.replace("\"required\":[\"text\",\"currentTime\",\"parseMode\",\"maxTasks\"]", "\"required\":[]"),
				OUTPUT);
	}

	@Test
	void rejectsOutputWithAuthorityFieldsMissingDraftFieldsOrNonNullableDate() throws Exception {
		reject(INPUT, OUTPUT.replace("[\"string\",\"null\"]", "\"string\""));
		reject(INPUT, OUTPUT.replace("[\"string\",\"null\"]", "\"null\""));
		reject(INPUT, OUTPUT.replace("\"additionalProperties\":false", "\"additionalProperties\":true"));
		reject(INPUT, OUTPUT.replace("\"maxItems\":30", "\"maxItems\":1"));
		for (String field : List.of("title", "content", "priority", "dueAt")) {
			ObjectNode output = (ObjectNode) json.readTree(OUTPUT);
			((ObjectNode) output.path("properties").path("tasks").path("items").path("properties")).remove(field);
			reject(INPUT, output.toString());
		}
		ObjectNode output = (ObjectNode) json.readTree(OUTPUT);
		((ObjectNode) output.path("properties").path("tasks").path("items").path("properties")).putObject("assigneeId")
			.put("type", "integer");
		reject(INPUT, output.toString());
	}

	@Test
	void malformedOrDuplicateSchemaIsRejected() {
		reject("{}", OUTPUT);
		reject(INPUT, "{}");
		reject(INPUT + " {}", OUTPUT);
		reject(INPUT, OUTPUT.replace("\"maxItems\":30", "\"maxItems\":30,\"maxItems\":1"));
	}

	private void reject(String input, String output) {
		var error = assertThrows(IllegalArgumentException.class,
				() -> TaskParseSkillContractValidator.validate(json, release(input, output)));
		assertEquals("TASK_PARSE_CONTRACT_INCOMPATIBLE", error.getMessage());
	}

}
