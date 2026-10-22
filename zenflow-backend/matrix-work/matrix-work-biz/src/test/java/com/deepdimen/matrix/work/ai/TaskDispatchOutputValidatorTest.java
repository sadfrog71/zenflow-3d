package com.deepdimen.matrix.work.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskDispatchOutputValidatorTest {

	private final ObjectMapper json = new ObjectMapper();

	@Test
	void acceptsOnlyOneAuthorizedCanonicalIdOrAnExplicitUnknownSuggestion() {
		for (String input : new String[] { "{\"candidates\":[{\"userId\":4}]}",
				"{\"candidates\":[{\"userId\":\"4\"}]}" }) {
			assertEquals("4",
					TaskDispatchOutputValidator.validate(json, "{\"assigneeId\":\"4\",\"reason\":\"匹配\"}", input)
						.path("assigneeId")
						.textValue());
			assertTrue(TaskDispatchOutputValidator.validate(json, "{\"assigneeId\":null,\"reason\":\"无法判断\"}", input)
				.path("assigneeId")
				.isNull());
		}
	}

	@Test
	void rejectsUnknownCandidateCoercionExtraActionsAndMalformedSuggestions() {
		for (String value : new String[] { "{}", "[]", "null", "{\"assigneeId\":\"5\",\"reason\":\"x\"}",
				"{\"assigneeId\":4,\"reason\":\"x\"}", "{\"assigneeId\":\"04\",\"reason\":\"x\"}",
				"{\"assigneeId\":\"4\",\"reason\":\" \"}", "{\"assigneeId\":null,\"reason\":42}",
				"{\"assigneeId\":\"4\",\"reason\":\"x\",\"status\":\"TODO\"}",
				"{\"assigneeId\":null,\"assigneeId\":\"4\",\"reason\":\"x\"}",
				"{\"assigneeId\":null,\"reason\":\"x\"} {}" })
			assertTrue(assertThrows(IllegalArgumentException.class,
					() -> TaskDispatchOutputValidator.validate(json, value, "{\"candidates\":[{\"userId\":4}]}"))
				.getMessage()
				.startsWith("OUTPUT_INVALID"));
		assertThrows(IllegalArgumentException.class, () -> TaskDispatchOutputValidator.validate(json,
				"{\"assigneeId\":null,\"reason\":\"" + "x".repeat(2001) + "\"}", "{}"));
	}

}
