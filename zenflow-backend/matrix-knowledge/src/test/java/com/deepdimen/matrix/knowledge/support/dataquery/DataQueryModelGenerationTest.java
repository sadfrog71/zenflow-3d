package com.deepdimen.matrix.knowledge.support.dataquery;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.output.FinishReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataQueryModelGenerationTest {

	@Test
	void qwenStructuredRequestPreservesFormatAndBoundsWithoutThinking() {
		var model = mock(ChatModel.class);
		when(model.defaultRequestParameters()).thenReturn(OpenAiChatRequestParameters.builder()
			.modelName("qwen3.8-max").maxCompletionTokens(2048).build());
		when(model.chat(any(ChatRequest.class))).thenReturn(response("{}", FinishReason.STOP));
		var format = dev.langchain4j.model.chat.request.ResponseFormat.builder()
			.type(dev.langchain4j.model.chat.request.ResponseFormatType.JSON).build();
		var request = ChatRequest.builder().messages(UserMessage.from("GIS占比")).responseFormat(format).build();
		DataQueryModelGeneration.generate(model, request, (phase, call) -> call.get(), () -> { });
		var captured = ArgumentCaptor.forClass(ChatRequest.class);
		verify(model).chat(captured.capture());
		var parameters = (OpenAiChatRequestParameters) captured.getValue().parameters();
		assertThat(parameters.customParameters()).containsEntry("enable_thinking", false);
		assertThat(parameters.responseFormat()).isEqualTo(format);
		assertThat(parameters.maxCompletionTokens()).isEqualTo(2048);
	}


	private ChatResponse response(String text, FinishReason reason) {
		return ChatResponse.builder().aiMessage(AiMessage.from(text)).finishReason(reason).build();
	}

	@Test
	void retriesOnceWithBoundedRequestBudgetPreservingThinkingAndOtherSettings() {
		var model = mock(ChatModel.class);
		var defaults = OpenAiChatRequestParameters.builder()
			.modelName("synthetic-model")
			.maxOutputTokens(2048)
			.customParameters(Map.of("enable_thinking", true, "custom_marker", "preserve"))
			.build();
		when(model.defaultRequestParameters()).thenReturn(defaults);
		when(model.chat(any(ChatRequest.class))).thenReturn(response("TRUNCATED_PRIVATE_CONTENT", FinishReason.LENGTH))
			.thenReturn(response("{\"responseType\":\"QUERY\",\"sql\":\"SELECT 1\"}", FinishReason.STOP));
		var request = ChatRequest.builder().messages(UserMessage.from("本轮原问题，保留公司条件")).build();
		var phases = new ArrayList<String>();
		var rebound = new AtomicInteger();
		var result = DataQueryModelGeneration.generate(model, request, (phase, call) -> {
			phases.add(phase);
			return call.get();
		}, rebound::incrementAndGet);
		assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
		var requests = ArgumentCaptor.forClass(ChatRequest.class);
		verify(model, times(2)).chat(requests.capture());
		var retry = requests.getAllValues().get(1);
		assertThat(retry.parameters().maxOutputTokens()).isEqualTo(4096);
		assertThat(((OpenAiChatRequestParameters) retry.parameters()).customParameters())
			.containsEntry("enable_thinking", true)
			.containsEntry("custom_marker", "preserve");
		assertThat(retry.messages().toString()).contains("保留公司条件").doesNotContain("TRUNCATED_PRIVATE_CONTENT");
		assertThat(request.messages()).hasSize(1);
		assertThat(defaults.maxOutputTokens()).isEqualTo(2048);
		assertThat(phases).containsExactly("MODEL_GENERATE", "MODEL_REGENERATE");
		assertThat(rebound.get()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void sdkActuallySendsRequestLocalBudgetAndPreservesProviderParameters(boolean completionBudget) throws Exception {
		var json = new com.fasterxml.jackson.databind.ObjectMapper();
		var bodies = new java.util.concurrent.CopyOnWriteArrayList<com.fasterxml.jackson.databind.JsonNode>();
		var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/chat/completions", exchange -> {
			bodies.add(json.readTree(exchange.getRequestBody()));
			String reason = bodies.size() == 1 ? "length" : "stop";
			var reply = ("{\"id\":\"synthetic\",\"model\":\"synthetic-model\",\"choices\":[{\"index\":0,"
					+ "\"message\":{\"role\":\"assistant\",\"content\":\"{}\"},\"finish_reason\":\"" + reason
					+ "\"}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":20,\"total_tokens\":30}}")
				.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, reply.length);
			try (var out = exchange.getResponseBody()) {
				out.write(reply);
			}
		});
		server.start();
		try {
			var model = dev.langchain4j.model.openai.OpenAiChatModel.builder()
				.baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/")
				.apiKey("synthetic-only")
				.modelName("synthetic-model")
				.maxTokens(completionBudget ? null : 2048)
				.maxCompletionTokens(completionBudget ? 2048 : null)
				.maxRetries(0)
				.defaultRequestParameters(
						OpenAiChatRequestParameters.builder().customParameters(Map.of("enable_thinking", true)).build())
				.build();
			var response = DataQueryModelGeneration.generate(model,
					ChatRequest.builder().messages(UserMessage.from("synthetic-question")).build(),
					(phase, call) -> call.get(), () -> {
					});
			assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
			assertThat(bodies).hasSize(2);
			String budgetField = completionBudget ? "max_completion_tokens" : "max_tokens";
			String otherField = completionBudget ? "max_tokens" : "max_completion_tokens";
			assertThat(bodies.get(0).path(budgetField).asInt()).isEqualTo(2048);
			assertThat(bodies.get(1).path(budgetField).asInt()).isEqualTo(4096);
			assertThat(bodies.get(1).has(otherField)).isFalse();
			assertThat(bodies.get(1).path("enable_thinking").asBoolean()).isTrue();
		}
		finally {
			server.stop(0);
		}
	}

	@Test
	void aSecondTruncationRemainsAnErrorEvenWhenItContainsCompleteLookingSql() {
		var model = mock(ChatModel.class);
		when(model.defaultRequestParameters())
			.thenReturn(OpenAiChatRequestParameters.builder().maxOutputTokens(4096).build());
		when(model.chat(any(ChatRequest.class))).thenReturn(response("{\"sql\":\"SELECT 1\"}", FinishReason.LENGTH));
		var request = ChatRequest.builder().messages(UserMessage.from("统计记录")).build();
		var result = DataQueryModelGeneration.generate(model, request, (phase, call) -> call.get(), () -> {
		});
		assertThatThrownBy(() -> DataQueryAiChatService.parseModelResponse(result))
			.isInstanceOf(DataQueryException.class)
			.hasMessage("AI_RESPONSE_TRUNCATED");
		var requests = ArgumentCaptor.forClass(ChatRequest.class);
		verify(model, times(2)).chat(requests.capture());
		assertThat(requests.getAllValues().get(1).parameters().maxOutputTokens()).isEqualTo(8192);
	}

	@Test
	void completeResponsesAndUnknownOrAlreadyLargeBudgetsDoNotCauseExtraCalls() {
		for (Integer limit : Arrays.asList(null, 8192, 32768)) {
			var model = mock(ChatModel.class);
			when(model.defaultRequestParameters())
				.thenReturn(OpenAiChatRequestParameters.builder().maxOutputTokens(limit).build());
			var truncated = response("{}", FinishReason.LENGTH);
			when(model.chat(any(ChatRequest.class))).thenReturn(truncated);
			assertThat(DataQueryModelGeneration.generate(model,
					ChatRequest.builder().messages(UserMessage.from("问题")).build(), (phase, call) -> call.get(), () -> {
						throw new AssertionError();
					}))
				.isSameAs(truncated);
			verify(model).chat(any(ChatRequest.class));
		}
	}

	@Test
	void completionBudgetHonorsPerRequestOverrideAndLeavesDefaultsUnchanged() {
		var model = mock(ChatModel.class);
		var defaults = OpenAiChatRequestParameters.builder()
			.maxCompletionTokens(2048)
			.customParameters(Map.of("enable_thinking", false))
			.build();
		when(model.defaultRequestParameters()).thenReturn(defaults);
		when(model.chat(any(ChatRequest.class))).thenReturn(response("partial", FinishReason.LENGTH))
			.thenReturn(response("{}", FinishReason.STOP));
		var request = ChatRequest.builder()
			.messages(UserMessage.from("本轮问题"))
			.parameters(OpenAiChatRequestParameters.builder().maxCompletionTokens(4096).build())
			.build();
		DataQueryModelGeneration.generate(model, request, (phase, call) -> call.get(), () -> {
		});
		var requests = ArgumentCaptor.forClass(ChatRequest.class);
		verify(model, times(2)).chat(requests.capture());
		var retry = (OpenAiChatRequestParameters) requests.getAllValues().get(1).parameters();
		assertThat(retry.maxCompletionTokens()).isEqualTo(8192);
		assertThat(retry.maxOutputTokens()).isNull();
		assertThat(retry.customParameters()).containsEntry("enable_thinking", false);
		assertThat(defaults.maxCompletionTokens()).isEqualTo(2048);
	}

	@Test
	void completionCeilingAndConflictingBudgetChannelsDoNotCauseUnboundedRetry() {
		for (var parameters : List.of(OpenAiChatRequestParameters.builder().maxCompletionTokens(8192).build(),
				OpenAiChatRequestParameters.builder().maxCompletionTokens(32768).build(),
				OpenAiChatRequestParameters.builder().maxCompletionTokens(2048).maxOutputTokens(4096).build())) {
			var model = mock(ChatModel.class);
			when(model.defaultRequestParameters()).thenReturn(parameters);
			var truncated = response("{}", FinishReason.LENGTH);
			when(model.chat(any(ChatRequest.class))).thenReturn(truncated);
			assertThat(DataQueryModelGeneration.generate(model,
					ChatRequest.builder().messages(UserMessage.from("问题")).build(), (phase, call) -> call.get(), () -> {
						throw new AssertionError();
					}))
				.isSameAs(truncated);
			verify(model).chat(any(ChatRequest.class));
		}
	}

	@Test
	void metadataPromptReadsCurrentFieldsAndNotesAndNeverAcceptsSqlOrPartialResponses() {
		var inventory = new DataQueryMetadataRepository.DatasetInventory(Set.of("new_table"),
				"[{\"table_name\":\"new_table\",\"fields\":[{\"field_name\":\"created_at\",\"virtual_comment\":\"业务日期，不是审核日期\"}]}]",
				"digest");
		var prompt = DataQueryMetadataAnalysis.request("能不能生成一些问题我看看", inventory, "词条说明", "关系仍需核验");
		assertThat(prompt.messages().toString()).contains("new_table", "created_at", "业务日期", "词条说明", "默认", "5个");
		assertThat(prompt.toolSpecifications()).isEmpty();
		assertThat(DataQueryMetadataAnalysis
			.answer(response("{\"responseType\":\"DATASET_GUIDANCE\",\"answer\":\"按业务日期检索\"}", FinishReason.STOP)))
			.isEqualTo("按业务日期检索");
		for (String body : List.of("{\"responseType\":\"QUERY\",\"sql\":\"SELECT 1\"}",
				"{\"responseType\":\"DATASET_GUIDANCE\",\"answer\":\"test\",\"sql\":\"SELECT 1\"}"))
			assertThatThrownBy(() -> DataQueryMetadataAnalysis.answer(response(body, FinishReason.STOP)))
				.isInstanceOf(DataQueryException.class)
				.hasMessage("AI_RESPONSE_INVALID");
		assertThatThrownBy(() -> DataQueryMetadataAnalysis.answer(response("{}", FinishReason.LENGTH)))
			.isInstanceOf(DataQueryException.class)
			.hasMessage("AI_RESPONSE_TRUNCATED");
	}

}
