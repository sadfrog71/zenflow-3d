package com.deepdimen.matrix.zenflow.admin;

import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantModelRequest;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import java.nio.charset.StandardCharsets;

/**
 * Conservative UTF-8 byte budget for the supported text-only compatible gateway. This is
 * deliberately an upper estimate, not a provider-specific tokenizer.
 */
public final class ZenflowModelBudget {

	private ZenflowModelBudget() {
	}

	public static void validate(AssistantModelRequest request) {
		if (input(request) + request.maxOutputTokens() > window(request))
			throw new AssistantModelException("MODEL_CONTEXT_BUDGET_EXCEEDED", "本轮上下文超过模型容量，请减少选入内容或缩短问题后重试");
	}

	static long window(AssistantModelRequest request) {
		return Math.min(200000, request.model().capabilities().contextWindow());
	}

	static long messageCost(com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelMessage message) {
		return 64L + bytes(message.content());
	}

	static long input(AssistantModelRequest request) {
		long input = 256L + bytes(request.systemPrompt()) + bytes(request.userMessage());
		for (var message : request.history())
			input += messageCost(message);
		for (var fragment : request.contextFragments())
			input += 128L + bytes(fragment.citationKey()) + bytes(fragment.title()) + bytes(fragment.content());
		return input;
	}

	static int bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8).length;
	}

}
