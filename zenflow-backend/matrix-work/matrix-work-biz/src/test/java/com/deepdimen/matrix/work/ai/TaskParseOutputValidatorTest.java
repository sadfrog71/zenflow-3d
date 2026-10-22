package com.deepdimen.matrix.work.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaskParseOutputValidatorTest {

	private final ObjectMapper json = new ObjectMapper();

	private static final String TASK = "{\"title\":\"巡检\",\"content\":\"核查设备\"}";

	@Test
	void preservesSuggestionsAndDoesNotInventMissingDatesOrPriorities() throws Exception {
		String value = "{\"tasks\":[" + TASK + "],\"warnings\":[\"请确认截止时间\"]}";
		assertEquals(json.readTree(value), TaskParseOutputValidator.validate(json, value, null));
		var task = json.readTree(TASK);
		((com.fasterxml.jackson.databind.node.ObjectNode) task).put("priority", "URGENT")
			.put("dueAt", "2026-09-27T12:30:00+08:00");
		String explicit = "{\"tasks\":[" + task + "]}";
		assertEquals(json.readTree(explicit), TaskParseOutputValidator.validate(json, explicit, null));
	}

	@Test
	void rootHasExactlyOneTaskAndDirectChildrenHaveAtMostThirty() {
		for (int count : new int[] { 0, 1, 2, 30, 31 }) {
			var output = json.createObjectNode();
			var tasks = output.putArray("tasks");
			for (int i = 0; i < count; i++)
				tasks.addObject().put("title", "T").put("content", "C");
			String value = output.toString();
			if (count == 1)
				assertDoesNotThrow(() -> TaskParseOutputValidator.validate(json, value, null));
			else
				rejects(value, null);
			if (count > 0 && count <= 30)
				assertDoesNotThrow(() -> TaskParseOutputValidator.validate(json, value, 99L));
			else
				rejects(value, 99L);
		}
	}

	@Test
	void rejectsCoercionsAuthorityFieldsNestedTasksAndInvalidDates() throws Exception {
		for (String replacement : new String[] { "\"title\":42", "\"title\":\" \"", "\"content\":false",
				"\"priority\":\"CRITICAL\"", "\"priority\":5", "\"dueAt\":\"2026-09-27T12:00:00\"",
				"\"dueAt\":\"2026-09-27T12:00:00Z\"", "\"dueAt\":\"2026-02-30T12:00:00+08:00\"", "\"dueAt\":123",
				"\"assigneeId\":3", "\"tenantId\":42", "\"children\":[]", "\"id\":1" }) {
			var task = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(TASK);
			task.setAll((com.fasterxml.jackson.databind.node.ObjectNode) json.readTree("{" + replacement + "}"));
			rejects("{\"tasks\":[" + task + "]}", null);
		}
	}

	@Test
	void rejectsMalformedDuplicateTrailingOrUnexpectedOutput() {
		for (String value : new String[] { "", "null", "[]", "{}", "{\"tasks\":[]}",
				"{\"tasks\":[" + TASK + "],\"warnings\":[{}]}", "{\"tasks\":[" + TASK + "],\"warnings\":null}",
				"{\"tasks\":[" + TASK + "],\"confirmationRequired\":false}", "{\"tasks\":[],\"tasks\":[" + TASK + "]}",
				"{\"tasks\":[" + TASK + "]} {}" })
			rejects(value, null);
	}

	@Test
	void enforcesTitleContentAndTotalOutputLimits() {
		for (String field : new String[] { "title", "content" }) {
			var task = json.createObjectNode().put("title", "T").put("content", "C");
			task.put(field, "x".repeat(field.equals("title") ? 201 : 30001));
			rejects("{\"tasks\":[" + task + "]}", null);
		}
		rejects(" ".repeat(32001), null);
	}

	private void rejects(String value, Long parentId) {
		var error = assertThrows(IllegalArgumentException.class,
				() -> TaskParseOutputValidator.validate(json, value, parentId));
		assertTrue(error.getMessage().startsWith("OUTPUT_INVALID"));
	}

}
