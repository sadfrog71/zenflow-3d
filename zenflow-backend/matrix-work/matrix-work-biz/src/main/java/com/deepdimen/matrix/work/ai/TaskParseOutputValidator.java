package com.deepdimen.matrix.work.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * Validates suggestions only; task creation still requires the existing confirmation
 * flow.
 */
final class TaskParseOutputValidator {

	private static final Set<String> ROOT_FIELDS = Set.of("tasks", "warnings");

	private static final Set<String> TASK_FIELDS = Set.of("title", "content", "priority", "dueAt");

	private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");

	private TaskParseOutputValidator() {
	}

	static JsonNode validate(ObjectMapper json, String value, Long parentId) {
		if (value == null || value.length() > 32000)
			throw invalid();
		final JsonNode output;
		try {
			output = json.reader()
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.readTree(value);
		}
		catch (java.io.IOException ex) {
			throw invalid();
		}
		fields(output, ROOT_FIELDS);
		JsonNode tasks = output.path("tasks");
		if (!tasks.isArray() || tasks.isEmpty() || tasks.size() > TaskParseContract.maxTasks(parentId))
			throw invalid();
		for (JsonNode task : tasks) {
			fields(task, TASK_FIELDS);
			text(task.path("title"), 200);
			text(task.path("content"), 30000);
			JsonNode priority = task.get("priority");
			if (priority != null && !priority.isNull()
					&& (!priority.isTextual() || !PRIORITIES.contains(priority.textValue())))
				throw invalid();
			JsonNode due = task.get("dueAt");
			if (due != null && !due.isNull()) {
				if (!due.isTextual())
					throw invalid();
				try {
					if (!OffsetDateTime.parse(due.textValue()).getOffset().equals(ZoneOffset.ofHours(8)))
						throw invalid();
				}
				catch (java.time.format.DateTimeParseException ex) {
					throw invalid();
				}
			}
		}
		JsonNode warnings = output.get("warnings");
		if (warnings != null) {
			if (!warnings.isArray())
				throw invalid();
			for (JsonNode warning : warnings) {
				if (!warning.isTextual())
					throw invalid();
			}
		}
		return output;
	}

	private static void fields(JsonNode node, Set<String> allowed) {
		if (node == null || !node.isObject())
			throw invalid();
		var names = node.fieldNames();
		while (names.hasNext())
			if (!allowed.contains(names.next()))
				throw invalid();
	}

	private static void text(JsonNode node, int limit) {
		if (!node.isTextual() || node.textValue().isBlank() || node.textValue().length() > limit)
			throw invalid();
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("OUTPUT_INVALID: TASK_PARSE suggestions violate the task contract");
	}

}
