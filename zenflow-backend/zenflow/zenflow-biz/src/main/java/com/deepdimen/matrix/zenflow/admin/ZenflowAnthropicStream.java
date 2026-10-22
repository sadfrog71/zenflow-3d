package com.deepdimen.matrix.zenflow.admin;

import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelChunk;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import com.deepdimen.matrix.assistant.generation.AssistantReasoningFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.FluxSink;

/** Text-only Messages SSE adapter. Thinking/signatures are never exposed or persisted. */
final class ZenflowAnthropicStream {

	private final ObjectMapper json;

	private final FluxSink<ModelChunk> sink;

	private final String runId;

	private final AssistantReasoningFilter reasoning = new AssistantReasoningFilter();

	private long sequence;

	private boolean started, terminal;

	private int nextBlock, activeIndex = -1, characters;

	private String blockType = "", finish;

	ZenflowAnthropicStream(ObjectMapper json, FluxSink<ModelChunk> sink, String runId) {
		this.json = json;
		this.sink = sink;
		this.runId = runId;
	}

	boolean terminal() {
		return terminal;
	}

	void accept(String data) {
		if (sink.isCancelled() || terminal)
			return;
		try {
			var node = json.readTree(data);
			String type = node.path("type").asText();
			switch (type) {
				case "ping" -> {
				}
				case "error" -> throw fail("MODEL_UPSTREAM_UNAVAILABLE");
				case "message_start" -> {
					if (started || !"assistant".equals(node.path("message").path("role").asText()))
						throw fail("MODEL_INVALID_STREAM");
					started = true;
				}
				case "content_block_start" -> {
					requireStarted();
					if (activeIndex != -1 || finish != null || index(node) != nextBlock || nextBlock >= 1024)
						throw fail("MODEL_INVALID_STREAM");
					activeIndex = nextBlock++;
					blockType = node.path("content_block").path("type").asText();
					if (!java.util.Set.of("text", "thinking", "redacted_thinking").contains(blockType))
						throw fail("MODEL_UNSUPPORTED_RESPONSE");
					if ("text".equals(blockType))
						emit(node.path("content_block").path("text").asText(""));
				}
				case "content_block_delta" -> {
					requireBlock(index(node));
					String delta = node.path("delta").path("type").asText();
					if ("text_delta".equals(delta) && "text".equals(blockType))
						emit(node.path("delta").path("text").asText(""));
					else if (!("thinking".equals(blockType)
							&& java.util.Set.of("thinking_delta", "signature_delta").contains(delta)))
						throw fail("MODEL_INVALID_STREAM");
				}
				case "content_block_stop" -> {
					requireBlock(index(node));
					activeIndex = -1;
				}
				case "message_delta" -> {
					requireStarted();
					if (activeIndex != -1)
						throw fail("MODEL_INVALID_STREAM");
					if (node.path("delta").hasNonNull("stop_reason"))
						finish = switch (node.path("delta").path("stop_reason").asText()) {
							case "end_turn", "stop_sequence" -> "STOP";
							case "max_tokens" -> "LENGTH";
							case "refusal" -> "CONTENT_FILTER";
							default -> "UNSUPPORTED";
						};
				}
				case "message_stop" -> {
					requireStarted();
					if (activeIndex != -1 || finish == null)
						throw fail("MODEL_INCOMPLETE_STREAM");
					String tail = reasoning.finish();
					if (!tail.isEmpty())
						sink.next(new ModelChunk(runId, sequence++, tail, false, ""));
					terminal = true;
					sink.next(new ModelChunk(runId, sequence++, "", true, finish));
				}
				default -> {
					if (type.isEmpty())
						throw fail("MODEL_INVALID_STREAM");
				} // Future non-content events.
			}
		}
		catch (AssistantModelException failure) {
			throw failure;
		}
		catch (Exception failure) {
			throw fail("MODEL_INVALID_STREAM");
		}
	}

	private void emit(String text) {
		characters += text.length();
		if (characters > 262144)
			throw fail("MODEL_OUTPUT_LIMIT_EXCEEDED");
		String visible = reasoning.accept(text);
		if (!visible.isEmpty())
			sink.next(new ModelChunk(runId, sequence++, visible, false, ""));
	}

	private static int index(com.fasterxml.jackson.databind.JsonNode node) {
		var index = node.path("index");
		if (!index.isIntegralNumber() || !index.canConvertToInt() || index.asInt() < 0)
			throw fail("MODEL_INVALID_STREAM");
		return index.asInt();
	}

	private void requireStarted() {
		if (!started)
			throw fail("MODEL_INVALID_STREAM");
	}

	private void requireBlock(int index) {
		requireStarted();
		if (activeIndex < 0 || index != activeIndex)
			throw fail("MODEL_INVALID_STREAM");
	}

	private static AssistantModelException fail(String code) {
		return new AssistantModelException(code, "模型调用未完成，请检查配置或稍后重试");
	}

}
