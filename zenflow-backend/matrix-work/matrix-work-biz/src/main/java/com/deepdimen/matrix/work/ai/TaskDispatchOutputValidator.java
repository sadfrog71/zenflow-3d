package com.deepdimen.matrix.work.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;

/** Validates a suggestion against authorized candidates, never applies task commands. */
final class TaskDispatchOutputValidator {

	private TaskDispatchOutputValidator() {
	}

	static JsonNode validate(ObjectMapper json, String output, String input) {
		if (output == null || output.length() > 32000)
			throw invalid();
		try {
			var reader = json.reader()
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
			JsonNode value = reader.readTree(output);
			if (value == null || !value.isObject() || value.size() != 2 || !value.has("assigneeId")
					|| !value.has("reason") || !value.path("reason").isTextual()
					|| value.path("reason").textValue().isBlank() || value.path("reason").textValue().length() > 2000)
				throw invalid();
			JsonNode selected = value.get("assigneeId");
			if (selected.isNull())
				return value;
			if (!selected.isTextual())
				throw invalid();
			long id = Long.parseLong(selected.textValue());
			if (id <= 0 || !Long.toString(id).equals(selected.textValue()))
				throw invalid();
			JsonNode saved = reader.readTree(input);
			if (saved == null || !saved.path("candidates").isArray())
				throw invalid();
			for (JsonNode candidate : saved.path("candidates")) {
				JsonNode candidateId = candidate.path("userId");
				if (candidateId.isIntegralNumber() && candidateId.canConvertToLong() && candidateId.longValue() == id)
					return value;
				if (candidateId.isTextual() && selected.textValue().equals(candidateId.textValue()))
					return value;
			}
			throw invalid();
		}
		catch (java.io.IOException | NumberFormatException ex) {
			throw invalid();
		}
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("OUTPUT_INVALID: TASK_DISPATCH suggestion violates the task contract");
	}

}
