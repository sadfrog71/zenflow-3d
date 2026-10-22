package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelMessage;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.MessageRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Only the administrator's explicit test materials; never a consumer-history loader. */
public record ZenflowPreviewInput(String question, String locale, List<ModelMessage> history) {
	public ZenflowPreviewInput {
		if (question == null || question.isBlank() || question.length() > 8000 || locale == null
				|| !Set.of("zh-CN", "en-US").contains(locale) || history == null || history.size() > 8)
			throw invalid();
		history = List.copyOf(history);
		int budget = 0;
		for (var message : history) {
			if (message == null || !Set.of(MessageRole.USER, MessageRole.ASSISTANT).contains(message.role())
					|| message.content() == null || message.content().isBlank() || message.content().length() > 8000)
				throw invalid();
			budget += message.content().length();
		}
		if (budget > 16000)
			throw invalid();
	}

	public static ZenflowPreviewInput parse(Object question, Object locale, Object history) {
		if (!(question instanceof String text) || !(locale instanceof String language)
				|| !(history instanceof List<?> rows))
			throw invalid();
		var messages = new ArrayList<ModelMessage>();
		if (rows.size() > 8)
			throw invalid();
		for (var raw : rows) {
			if (!(raw instanceof Map<?, ?> row) || !row.keySet().equals(Set.of("role", "content"))
					|| !(row.get("role") instanceof String role) || !Set.of("USER", "ASSISTANT").contains(role)
					|| !(row.get("content") instanceof String content))
				throw invalid();
			messages.add(new ModelMessage(MessageRole.valueOf(role), content));
		}
		return new ZenflowPreviewInput(text, language, messages);
	}

	public String digest(ObjectMapper json) {
		try {
			return ZenflowRunAuthorization.hash(json.writer()
				.with(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
				.writeValueAsString(Map.of("question", question, "locale", locale, "history", history)));
		}
		catch (java.io.IOException failure) {
			throw invalid();
		}
	}

	public List<Map<String, String>> historyPayload() {
		return history.stream()
			.map(message -> Map.of("role", message.role().name(), "content", message.content()))
			.toList();
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("PREVIEW_INPUT_INVALID");
	}
}
