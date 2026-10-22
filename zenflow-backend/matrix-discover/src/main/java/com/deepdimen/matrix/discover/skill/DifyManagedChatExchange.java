package com.deepdimen.matrix.discover.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;

/**
 * Server-only invocation state. A completed HTTP stream is not a completed model result.
 */
public final class DifyManagedChatExchange {

	private final ObjectMapper json;

	private final String instruction;

	private final Runnable beforeInvocation;

	private final java.util.function.Consumer<String> conversationObserver;

	private final StringBuilder answer = new StringBuilder();

	private String conversationId, messageId, taskId;

	private boolean started, terminal;

	private RuntimeException failure;

	public DifyManagedChatExchange(ObjectMapper json, String instruction, Runnable beforeInvocation) {
		this(json, instruction, beforeInvocation, conversation -> {
		});
	}

	public DifyManagedChatExchange(ObjectMapper json, String instruction, Runnable beforeInvocation,
			java.util.function.Consumer<String> conversationObserver) {
		this.conversationObserver = Objects.requireNonNull(conversationObserver);
		this.json = Objects.requireNonNull(json);
		if (instruction == null || instruction.isBlank() || instruction.length() > 65536)
			throw new IllegalArgumentException("INPUT_INVALID");
		this.instruction = instruction;
		this.beforeInvocation = Objects.requireNonNull(beforeInvocation);
	}

	public String instruction() {
		return instruction;
	}

	public void start() {
		if (started)
			throw new IllegalStateException("DIFY_INVOCATION_ALREADY_STARTED");
		beforeInvocation.run();
		started = true;
	}

	public void fail(Throwable cause) {
		failure = new IllegalStateException("DIFY_RESULT_UNKNOWN", cause);
	}

	public void event(String event, String data) {
		if (!started || failure != null)
			throw uncertain();
		if ("ping".equals(event))
			return;
		if (data == null || data.isBlank()) {
			if (event == null || event.isBlank() || "ping".equals(event))
				return;
			fail(uncertain());
			throw failure;
		}
		try {
			JsonNode value = json.reader()
				.with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readTree(data);
			String kind = event == null || event.isBlank() ? value.path("event").asText() : event;
			if ("ping".equals(kind))
				return;
			if ("error".equals(kind))
				throw uncertain();
			if (!java.util.Set.of("message", "message_replace", "message_end").contains(kind))
				throw uncertain();
			if (terminal)
				throw uncertain();
			identify(value);
			if ("message_end".equals(kind)) {
				if (conversationId == null || messageId == null)
					throw uncertain();
				terminal = true;
			}
			else {
				if (!value.path("answer").isTextual())
					throw uncertain();
				if ("message_replace".equals(kind))
					answer.setLength(0);
				answer.append(value.path("answer").textValue());
				if (answer.length() > 32768)
					throw uncertain();
			}
		}
		catch (RuntimeException e) {
			fail(e);
			throw failure;
		}
		catch (java.io.IOException e) {
			fail(e);
			throw failure;
		}
	}

	public void blocking(JsonNode value) {
		if (!started || terminal || failure != null || value == null || !value.isObject())
			throw uncertain();
		if (!"message".equals(value.path("event").asText()) || !value.path("answer").isTextual())
			throw uncertain();
		identify(value);
		if (conversationId == null || messageId == null || value.path("answer").textValue().length() > 32768)
			throw uncertain();
		answer.append(value.path("answer").textValue());
		terminal = true;
	}

	public String result() {
		if (failure != null)
			throw failure;
		if (!started || !terminal)
			throw new IllegalArgumentException("OUTPUT_INCOMPLETE");
		var result = json.createObjectNode()
			.put("answer", answer.toString())
			.put("conversationId", conversationId)
			.put("messageId", messageId);
		if (taskId != null)
			result.put("taskId", taskId);
		return result.toString();
	}

	private void identify(JsonNode value) {
		String observed = identifier(conversationId, value.get("conversation_id"));
		if (conversationId == null && observed != null)
			conversationObserver.accept(observed);
		conversationId = observed;
		messageId = identifier(messageId, value.hasNonNull("message_id") ? value.get("message_id") : value.get("id"));
		taskId = identifier(taskId, value.get("task_id"));
	}

	private String identifier(String prior, JsonNode value) {
		if (value == null || value.isNull())
			return prior;
		if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 128)
			throw uncertain();
		if (prior != null && !prior.equals(value.textValue()))
			throw uncertain();
		return value.textValue();
	}

	private static IllegalStateException uncertain() {
		return new IllegalStateException("DIFY_RESULT_UNKNOWN");
	}

}
