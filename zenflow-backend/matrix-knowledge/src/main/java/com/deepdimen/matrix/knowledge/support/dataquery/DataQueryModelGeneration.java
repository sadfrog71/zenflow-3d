package com.deepdimen.matrix.knowledge.support.dataquery;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** One complete regeneration, never concatenation or execution of a truncated answer. */
public final class DataQueryModelGeneration {

	private DataQueryModelGeneration() {
	}

	public static ChatResponse generate(ChatModel model, ChatRequest request,
			BiFunction<String, Supplier<ChatResponse>, ChatResponse> invocation, Runnable beforeRetry) {
		var configured = model.defaultRequestParameters();
		var initial = request;
		if (configured != null && configured.modelName() != null
				&& configured.modelName().toLowerCase(java.util.Locale.ROOT).startsWith("qwen")) {
			// Structured candidates still pass the same SQL and permission validators.
			// Avoid spending the shared JSON output budget entirely on hidden reasoning.
			var parameters = configured.overrideWith(request.parameters()).overrideWith(
					OpenAiChatRequestParameters.builder().customParameters(java.util.Map.of("enable_thinking", false)).build());
			initial = ChatRequest.builder().messages(request.messages()).parameters(parameters).build();
		}
		final var initialRequest = initial;
		var response = invocation.apply("MODEL_GENERATE", () -> model.chat(initialRequest));
		if (response == null || response.finishReason() != FinishReason.LENGTH)
			return response;
		var defaults = model.defaultRequestParameters();
		var effective = defaults == null ? request.parameters() : defaults.overrideWith(initialRequest.parameters());
		Integer outputLimit = effective.maxOutputTokens();
		Integer completionLimit = effective instanceof OpenAiChatRequestParameters openAi ? openAi.maxCompletionTokens()
				: null;
		// The production OpenAI-compatible handler uses max_completion_tokens, which
		// is independent of max_tokens in the SDK. Never guess conflicting limits.
		if (outputLimit != null && completionLimit != null && !outputLimit.equals(completionLimit))
			return response;
		Integer limit = completionLimit != null ? completionLimit : outputLimit;
		// Do not guess a provider's unknown limit or retry indefinitely at its ceiling.
		if (limit == null || limit < 1 || limit >= 8192)
			return response;
		int retryLimit = (int) Math.min(8192L, Math.max(4096L, limit.longValue() * 2));
		ChatRequestParameters budget = completionLimit == null
				? ChatRequestParameters.builder().maxOutputTokens(retryLimit).build()
				: OpenAiChatRequestParameters.builder()
					.maxCompletionTokens(retryLimit)
					.maxOutputTokens(outputLimit == null ? null : retryLimit)
					.build();
		var parameters = effective.overrideWith(budget);
		var messages = new ArrayList<ChatMessage>(request.messages());
		messages.add(UserMessage.from("上一次模型响应达到输出额度，未解析或执行。请重新生成一份完整、简洁的JSON，不续写、不引用被截断内容。"
				+ "保持原问题的筛选、排序、统计粒度与权限，不擅自缩小统计范围。介绍或推荐直接给简洁回答；" + "推荐未指定数量时给5个问题。需要取数时给完整候选SQL，不附长篇解释，后端仍须校验。"));
		var retry = ChatRequest.builder().messages(messages).parameters(parameters).build();
		beforeRetry.run();
		return invocation.apply("MODEL_REGENERATE", () -> model.chat(retry));
	}

}
