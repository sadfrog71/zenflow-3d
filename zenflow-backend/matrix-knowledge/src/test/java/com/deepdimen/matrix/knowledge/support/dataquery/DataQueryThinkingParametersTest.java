package com.deepdimen.matrix.knowledge.support.dataquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import com.deepdimen.matrix.knowledge.entity.AiModelEntity;
import com.deepdimen.matrix.knowledge.support.handler.model.OpenAIModelHandler;
import com.deepdimen.matrix.knowledge.support.provider.ChatMemoryAdvisorProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.tool.ToolProvider;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DataQueryThinkingParametersTest {

	@ParameterizedTest
	@CsvSource({ "default,false", "enabled,false", "disabled,false", "default,true", "enabled,true", "disabled,true" })
	void existingProductionHandlerSendsPersistedThinkingBooleanOrOmitsDefault(String mode, boolean streaming)
			throws Exception {
		var json = new ObjectMapper();
		var captured = new CompletableFuture<JsonNode>();
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/chat/completions", exchange -> {
			captured.complete(json.readTree(exchange.getRequestBody()));
			String reply = streaming
					? "data: {\"id\":\"fixture\",\"model\":\"fixture\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"OK\"}}]}\n\n"
							+ "data: {\"id\":\"fixture\",\"model\":\"fixture\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n"
							+ "data: [DONE]\n\n"
					: "{\"id\":\"fixture\",\"model\":\"fixture\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}";
			byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", streaming ? "text/event-stream" : "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (var output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		});
		server.start();
		try {
			var properties = new AiKnowledgeProperties();
			properties.setConnectTimeout(5);
			properties.setMaxRetry(0);
			var handler = new OpenAIModelHandler(mock(ChatMemoryAdvisorProvider.class), properties, Optional.empty(),
					List.of(), mock(ToolProvider.class));
			var entity = new AiModelEntity();
			entity.setModelName("synthetic-future-model");
			entity.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
			entity.setApiKey("synthetic-only");
			entity.setResponseLimit(128);
			entity.setTemperature(0.4);
			entity.setTopP(0.7);
			var parameters = json.createObjectNode().put("custom_marker", "keep");
			if (!"default".equals(mode))
				parameters.put("enable_thinking", "enabled".equals(mode));
			entity.setExtData(parameters.toString());
			if (streaming) {
				var completed = new CompletableFuture<ChatResponse>();
				handler.buildAiStreamAssistant(entity)
					.getKey()
					.chat(List.of(UserMessage.from("fixture")), new StreamingChatResponseHandler() {
						@Override
						public void onPartialResponse(String text) {
						}

						@Override
						public void onCompleteResponse(ChatResponse response) {
							completed.complete(response);
						}

						@Override
						public void onError(Throwable error) {
							completed.completeExceptionally(error);
						}
					});
				assertThat(completed.get(8, TimeUnit.SECONDS).aiMessage().text()).isEqualTo("OK");
			}
			else {
				assertThat(handler.buildAiAssistant(entity).getKey().chat("fixture")).isEqualTo("OK");
			}
			var body = captured.get(5, TimeUnit.SECONDS);
			assertThat(body.path("custom_marker").asText()).isEqualTo("keep");
			assertThat(body.path("model").asText()).isEqualTo("synthetic-future-model");
			if ("default".equals(mode))
				assertThat(body.has("enable_thinking")).isFalse();
			else {
				assertThat(body.path("enable_thinking").isBoolean()).isTrue();
				assertThat(body.path("enable_thinking").asBoolean()).isEqualTo("enabled".equals(mode));
			}
		}
		finally {
			server.stop(0);
		}
	}

}
