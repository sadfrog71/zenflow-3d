package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskDispatchSkillContractValidatorTest {

	static final String INPUT = """
			{"type":"object","properties":{"title":{"type":"string"},"content":{"type":"string"},"candidates":{"type":"array","items":{
			"type":"object","properties":{"userId":{"type":"string"},"displayName":{"type":["string","null"]},"username":{"type":["string","null"]},
			"recommendedApprover":{"type":"boolean"}},"required":["userId"],"additionalProperties":false}}},"required":["title","content","candidates"],"additionalProperties":false}
			""";
	static final String OUTPUT = """
			{"type":"object","properties":{"assigneeId":{"type":["string","null"]},"reason":{"type":"string"}},"required":["assigneeId","reason"],"additionalProperties":false}
			""";

	private final ObjectMapper json = new ObjectMapper();

	static ResolvedSkillSnapshot release(String input, String output) {
		return new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, "8", null), input, output, "dispatch instructions", List.of(), "{}",
				List.of());
	}

	@Test
	void supportsFrozenCandidateInputAndExplicitUnknownSuggestion() {
		assertDoesNotThrow(() -> TaskDispatchSkillContractValidator.validate(json, release(INPUT, OUTPUT)));
	}

	@Test
	void rejectsMissingCandidatesFixedIdsAndExtraBusinessActions() {
		for (String input : List.of("{}", INPUT.replace("\"candidates\"", "\"people\""),
				INPUT.replace("\"userId\":{\"type\":\"string\"}", "\"userId\":{\"type\":\"string\",\"const\":\"4\"}")))
			assertThrows(IllegalArgumentException.class,
					() -> TaskDispatchSkillContractValidator.validate(json, release(input, OUTPUT)));
		for (String output : List.of("{}", OUTPUT.replace("[\"string\",\"null\"]", "\"string\""),
				OUTPUT.replace("\"reason\"", "\"status\""),
				OUTPUT.replace("\"additionalProperties\":false", "\"additionalProperties\":true"), OUTPUT + " {}"))
			assertThrows(IllegalArgumentException.class,
					() -> TaskDispatchSkillContractValidator.validate(json, release(INPUT, output)));
	}

}
