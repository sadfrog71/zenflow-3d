package com.deepdimen.matrix.discover.dify;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

final class DifyAssistantRuntimeProtocol {

	private static final String WORKFLOW_MODE = "workflow";

	private DifyAssistantRuntimeProtocol() {
	}

	static RuntimeRequest build(String mode, Map<String, Object> inputs, String query, String user,
			String conversationId) {
		if (WORKFLOW_MODE.equals(mode)) {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("inputs", inputs);
			body.put("response_mode", "blocking");
			body.put("user", user);
			return new RuntimeRequest("/workflows/run", body, false);
		}
		if (isChatMode(mode)) {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("inputs", inputs);
			body.put("query", query);
			body.put("response_mode", "blocking");
			body.put("user", user);
			if (conversationId != null && !conversationId.isBlank()) {
				body.put("conversation_id", conversationId);
			}
			return new RuntimeRequest("/chat-messages", body, true);
		}
		throw new IllegalArgumentException("DIFY_RUNTIME_MODE_MISMATCH: " + mode);
	}

	static RuntimeResponse parse(String mode, JsonNode response) {
		return parse(mode, response, Map.of());
	}

	static RuntimeResponse parse(String mode, JsonNode response, Map<String, String> outputMapping) {
		if (response == null || response.isNull()) {
			return new RuntimeResponse(null, null, null, null, RuntimeUsage.empty());
		}
		RuntimeUsage usage = usage(response);
		if (WORKFLOW_MODE.equals(mode)) {
			JsonNode outputs = response.path("data").path("outputs");
			String answer = mappedText(response, outputMapping.get("answer"));
			if (answer == null) {
				answer = firstText(outputs, "answer", "text", "result", "output");
			}
			return new RuntimeResponse(answer, response.path("task_id").asText(null),
					response.path("workflow_run_id").asText(response.path("data").path("id").asText(null)), null,
					usage);
		}
		if (isChatMode(mode)) {
			return new RuntimeResponse(response.path("answer").asText(null), response.path("task_id").asText(null),
					null, response.path("conversation_id").asText(null), usage);
		}
		throw new IllegalArgumentException("DIFY_RUNTIME_MODE_MISMATCH: " + mode);
	}

	private static RuntimeUsage usage(JsonNode response) {
		JsonNode usage = response.path("metadata").path("usage");
		if (!usage.isObject()) {
			usage = response.path("data").path("usage");
		}
		if (!usage.isObject()) {
			usage = response.path("data");
		}
		Long promptTokens = longValue(usage, "prompt_tokens", "input_tokens");
		Long completionTokens = longValue(usage, "completion_tokens", "output_tokens");
		Long totalTokens = longValue(usage, "total_tokens");
		if (totalTokens == null && (promptTokens != null || completionTokens != null)) {
			totalTokens = valueOrZero(promptTokens) + valueOrZero(completionTokens);
		}
		String model = firstText(usage, "model", "model_name");
		if (model == null) {
			model = firstText(response.path("metadata"), "model", "model_name");
		}
		String requestId = firstText(response, "id", "task_id", "workflow_run_id");
		if (requestId == null) {
			requestId = firstText(response.path("data"), "id", "workflow_run_id");
		}
		return new RuntimeUsage(model, promptTokens, completionTokens, totalTokens, requestId);
	}

	private static Long longValue(JsonNode object, String... fields) {
		if (object == null || !object.isObject()) {
			return null;
		}
		for (String field : fields) {
			JsonNode value = object.get(field);
			if (value != null && value.isNumber()) {
				return value.asLong();
			}
		}
		return null;
	}

	private static long valueOrZero(Long value) {
		return value == null ? 0L : value;
	}

	private static String mappedText(JsonNode response, String configuredPath) {
		if (configuredPath == null || configuredPath.isBlank()) {
			return null;
		}
		String path = configuredPath.startsWith("outputs.") ? "data." + configuredPath : configuredPath;
		JsonNode current = response;
		for (String part : path.split("\\.")) {
			current = current.path(part);
		}
		return current.isValueNode() && !current.asText().isBlank() ? current.asText() : null;
	}

	static boolean isChatMode(String mode) {
		return "chat".equals(mode) || "agent-chat".equals(mode) || "advanced-chat".equals(mode);
	}

	private static String firstText(JsonNode object, String... fields) {
		if (object == null || !object.isObject()) {
			return null;
		}
		for (String field : fields) {
			JsonNode value = object.get(field);
			if (value != null && value.isValueNode() && !value.asText().isBlank()) {
				return value.asText();
			}
		}
		if (object.size() == 1) {
			JsonNode value = object.elements().next();
			if (value.isValueNode() && !value.asText().isBlank()) {
				return value.asText();
			}
		}
		return null;
	}

	record RuntimeRequest(String path, Map<String, Object> body, boolean conversational) {
	}

	record RuntimeResponse(String answer, String taskId, String workflowRunId, String conversationId,
			RuntimeUsage usage) {
	}

	record RuntimeUsage(String model, Long promptTokens, Long completionTokens, Long totalTokens,
			String requestId) {

		private static RuntimeUsage empty() {
			return new RuntimeUsage(null, null, null, null, null);
		}
	}

}
