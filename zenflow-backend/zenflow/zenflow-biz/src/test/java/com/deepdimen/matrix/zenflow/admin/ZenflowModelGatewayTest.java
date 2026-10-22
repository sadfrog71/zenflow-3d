package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ZenflowModelGatewayTest {

	private static Map<String, Object> row(boolean privateContext) {
		return Map.of("id", 42L, "model_name", "test-model", "provider", "OpenAI-compatible", "response_limit", 1024,
				"temperature", 0.6, "api_key", "credential-sentinel", "base_url", "https://8.8.8.8/v1", "ext_data",
				"{\"zenflowVersion\":1,\"zenflowTested\":true,\"zenflowPublished\":true,\"zenflowAllowPrivateContext\":"
						+ privateContext + "}");
	}

	private static IdentityEnvelope identity(String tenant) {
		return new IdentityEnvelope(tenant, "42", null, List.of(), List.of(), List.of(), "test", "TEXT_CONVERSATION");
	}

	private ZenflowModelGateway gateway(ZenflowModelService models, ZenflowModelGateway.Transport transport) {
		var p = new ZenflowProperties();
		p.setTenantId(1);
		return new ZenflowModelGateway(models, p, new ObjectMapper(), transport);
	}

	private AssistantModelRequest request(ZenflowModelGateway gateway, List<ModelContextFragment> fragments) {
		var model = gateway.resolve(new ModelResolutionRequest(AssistantModelContract.VERSION, "run", identity("1"),
				"42", true, DataClassification.PUBLIC, false));
		return new AssistantModelRequest(AssistantModelContract.VERSION, "run", "test", identity("1"), model,
				"Safe system", List.of(), fragments, "Hello", 0.6, null, 1024, true);
	}

	@Test
	void nativeChunksAreOrderedAndHiddenReasoningIsFiltered() {
		var models = mock(ZenflowModelService.class);
		when(models.require("42")).thenReturn(row(false));
		var gateway = gateway(models, (uri, key, body, sink, data) -> {
			assertEquals("https://8.8.8.8/v1/chat/completions", uri.toString());
			assertTrue(body.contains("\"stream\":true"));
			assertFalse(body.contains("credential-sentinel"));
			data.accept("{\"choices\":[{\"delta\":{\"content\":\"<thi\"}}]}");
			data.accept("{\"choices\":[{\"delta\":{\"content\":\"nk>hidden</think>Hello\"}}]}");
			data.accept("{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
			data.accept("[DONE]");
		});
		var chunks = gateway.stream(request(gateway, List.of())).collectList().block();
		assertEquals(2, chunks.size());
		assertEquals("Hello", chunks.get(0).delta());
		assertEquals(0, chunks.get(0).sequence());
		assertTrue(chunks.get(1).terminal());
		assertEquals(1, chunks.get(1).sequence());
	}

	@Test
	void incompleteOrMalformedStreamsFailWithoutLeakingProviderException() {
		var models = mock(ZenflowModelService.class);
		when(models.require("42")).thenReturn(row(false));
		var incomplete = gateway(models,
				(uri, key, body, sink, data) -> data.accept("{\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}"));
		var error = assertThrows(AssistantModelException.class,
				() -> incomplete.stream(request(incomplete, List.of())).collectList().block());
		assertEquals("MODEL_INCOMPLETE_STREAM", error.errorCode());
		var failed = gateway(models, (uri, key, body, sink, data) -> {
			throw new IllegalStateException("credential-sentinel provider response");
		});
		error = assertThrows(AssistantModelException.class,
				() -> failed.stream(request(failed, List.of())).collectList().block());
		assertEquals("MODEL_UPSTREAM_UNAVAILABLE", error.errorCode());
		assertFalse(error.getMessage().contains("credential-sentinel"));
	}

	@Test
	void privateContextIsDeniedBeforeTransportAndWrongTenantCannotResolve() {
		var models = mock(ZenflowModelService.class);
		when(models.require("42")).thenReturn(row(false));
		var gateway = gateway(models, (uri, key, body, sink, data) -> fail("Private content must not reach transport"));
		assertThrows(AssistantModelException.class,
				() -> gateway
					.stream(request(gateway,
							List.of(new ModelContextFragment("note", "Selected", "private",
									DataClassification.SENSITIVE, Map.of()))))
					.collectList()
					.block());
		assertThrows(AssistantModelException.class,
				() -> gateway.resolve(new ModelResolutionRequest(AssistantModelContract.VERSION, "run", identity("2"),
						"42", true, DataClassification.PUBLIC, false)));
	}

	@Test
	void approvedPublicReferenceIsAllowedWithoutPrivateEgressWhileSensitiveRemainsDenied() {
		var models = mock(ZenflowModelService.class);
		when(models.require("42")).thenReturn(row(false));
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var gateway = gateway(models, (uri, key, body, sink, data) -> {
			calls.incrementAndGet();
			assertTrue(body.contains("approved public reference"));
			data.accept("{\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}");
			data.accept("{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
			data.accept("[DONE]");
		});
		var publicData = new ModelContextFragment("REFERENCE_20", "Public", "approved public reference",
				DataClassification.PUBLIC, Map.of("sourceType", "REFERENCE"));
		assertEquals("OK", gateway.stream(request(gateway, List.of(publicData))).collectList().block().get(0).delta());
		for (var classification : List.of(DataClassification.INTERNAL, DataClassification.SENSITIVE)) {
			var restricted = new ModelContextFragment("private", "Private", "restricted body", classification,
					Map.of());
			assertThrows(AssistantModelException.class,
					() -> gateway.stream(request(gateway, List.of(publicData, restricted))).collectList().block());
		}
		assertEquals(1, calls.get());
	}

	@Test
	void probeOnlyMarksMatchingSuccessfulStreamingConfigurationTested() {
		var models = mock(ZenflowModelService.class);
		when(models.require("42")).thenReturn(row(false));
		var gateway = gateway(models, (uri, key, body, sink, data) -> {
			data.accept("{\"choices\":[{\"delta\":{\"content\":\"OK\"}}]}");
			data.accept("[DONE]");
		});
		assertThrows(ResponseStatusException.class, () -> gateway.probe("42", 2));
		verify(models, never()).recordTest(anyString(), anyInt());
		gateway.probe("42", 1);
		verify(models).recordTest("42", 1);
	}

	@Test
	void sseLineReaderHandlesCrLfAndRejectsUnboundedFrames() throws Exception {
		var reader = new java.io.BufferedReader(new java.io.StringReader("data: hello\r\n\r\ndata: [DONE]"));
		assertEquals("data: hello", ZenflowModelGateway.boundedLine(reader));
		assertEquals("", ZenflowModelGateway.boundedLine(reader));
		assertEquals("data: [DONE]", ZenflowModelGateway.boundedLine(reader));
		assertNull(ZenflowModelGateway.boundedLine(reader));
		var oversized = new java.io.BufferedReader(new java.io.StringReader("x".repeat(65537)));
		assertThrows(AssistantModelException.class, () -> ZenflowModelGateway.boundedLine(oversized));
	}

	@Test
	void configuredCapacityRejectsOversizedCombinedInputBeforeSupplierTransport() {
		var models = mock(ZenflowModelService.class);
		var configured = new java.util.HashMap<String, Object>(row(true));
		configured.put("ext_data",
				"{\"zenflowVersion\":1,\"zenflowTested\":true,\"zenflowPublished\":true,\"zenflowAllowPrivateContext\":true,\"zenflowContextWindow\":2048}");
		when(models.require("42")).thenReturn(configured);
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var gateway = gateway(models, (uri, key, body, sink, data) -> {
			calls.incrementAndGet();
			data.accept("[DONE]");
		});
		var request = request(gateway, List.of(new ModelContextFragment("private", "Selected", "中".repeat(400),
				DataClassification.SENSITIVE, Map.of())));
		assertEquals(2048, request.model().capabilities().contextWindow());
		var error = assertThrows(AssistantModelException.class, () -> gateway.stream(request).collectList().block());
		assertEquals("MODEL_CONTEXT_BUDGET_EXCEEDED", error.errorCode());
		assertEquals(0, calls.get());
	}

	static void anthropicText(java.util.function.Consumer<String> data, String stop, boolean complete) {
		data.accept("{\"type\":\"message_start\",\"message\":{\"role\":\"assistant\"}}");
		data.accept("{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"thinking\"}}");
		data.accept(
				"{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"never expose private reasoning\"}}");
		data.accept("{\"type\":\"content_block_stop\",\"index\":0}");
		data.accept(
				"{\"type\":\"content_block_start\",\"index\":1,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}");
		data.accept(
				"{\"type\":\"content_block_delta\",\"index\":1,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}");
		data.accept("{\"type\":\"content_block_stop\",\"index\":1}");
		data.accept("{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"" + stop + "\"}}");
		if (complete)
			data.accept("{\"type\":\"message_stop\"}");
	}

	Map<String, Object> anthropicRow() {
		var row = new java.util.HashMap<String, Object>(row(false));
		row.put("provider", "anthropic");
		row.put("base_url", "https://8.8.8.8/anthropic");
		return row;
	}

	@Test
	void anthropicUsesMessagesSystemHeadersAndTextOnlyOrderedStream() throws Exception {
		var models = mock(ZenflowModelService.class);
		when(models.require("42")).thenReturn(anthropicRow());
		var gateway = gateway(models, (uri, headers, body, sink, data) -> {
			assertEquals("https://8.8.8.8/anthropic/v1/messages", uri.toString());
			assertEquals("credential-sentinel", headers.get("x-api-key"));
			assertEquals("2023-06-01", headers.get("anthropic-version"));
			var parsed = new ObjectMapper().readTree(body);
			assertEquals("Safe system", parsed.path("system").asText());
			assertFalse(parsed.path("messages").get(0).path("role").asText().equals("system"));
			assertTrue(parsed.path("stream").asBoolean());
			anthropicText(data, "end_turn", true);
		});
		var chunks = gateway.stream(request(gateway, List.of())).collectList().block();
		assertEquals(2, chunks.size());
		assertEquals("Hello", chunks.get(0).delta());
		assertEquals(0, chunks.get(0).sequence());
		assertEquals("STOP", chunks.get(1).finishReason());
		assertEquals(1, chunks.get(1).sequence());
	}

	@Test
	void anthropicRequiresMessageStopAndDoesNotTreatMaxTokensAsSuccessfulStop() {
		var models = mock(ZenflowModelService.class);
		when(models.require("42")).thenReturn(anthropicRow());
		var incomplete = gateway(models, (uri, headers, body, sink, data) -> anthropicText(data, "end_turn", false));
		assertEquals("MODEL_INCOMPLETE_STREAM", assertThrows(AssistantModelException.class,
				() -> incomplete.stream(request(incomplete, List.of())).collectList().block())
			.errorCode());
		var length = gateway(models, (uri, headers, body, sink, data) -> anthropicText(data, "max_tokens", true));
		var chunks = length.stream(request(length, List.of())).collectList().block();
		assertEquals("LENGTH", chunks.get(chunks.size() - 1).finishReason());
	}

	@Test
	void anthropicErrorAndUnsolicitedToolBlocksFailSafely() {
		var models = mock(ZenflowModelService.class);
		when(models.require("42")).thenReturn(anthropicRow());
		var error = gateway(models, (uri, headers, body, sink, data) -> data
			.accept("{\"type\":\"error\",\"error\":{\"message\":\"credential-sentinel\"}}"));
		var failure = assertThrows(AssistantModelException.class,
				() -> error.stream(request(error, List.of())).collectList().block());
		assertFalse(failure.getMessage().contains("credential-sentinel"));
		var tools = gateway(models, (uri, headers, body, sink, data) -> {
			data.accept("{\"type\":\"message_start\",\"message\":{\"role\":\"assistant\"}}");
			data.accept("{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\"}}");
		});
		assertEquals("MODEL_UNSUPPORTED_RESPONSE", assertThrows(AssistantModelException.class,
				() -> tools.stream(request(tools, List.of())).collectList().block())
			.errorCode());
	}

	@Test
	void protocolEndpointsAndExplicitChoiceKeepSupplierLabelsSeparate() {
		var json = new ObjectMapper();
		assertEquals("https://api.anthropic.com/v1/messages",
				ZenflowModelProtocol.ANTHROPIC_MESSAGES.endpoint(java.net.URI.create("https://api.anthropic.com"))
					.toString());
		assertEquals("https://api.minimaxi.com/anthropic/v1/messages",
				ZenflowModelProtocol.ANTHROPIC_MESSAGES
					.endpoint(java.net.URI.create("https://api.minimaxi.com/anthropic"))
					.toString());
		assertEquals("https://example.com/v1/messages",
				ZenflowModelProtocol.ANTHROPIC_MESSAGES.endpoint(java.net.URI.create("https://example.com/v1"))
					.toString());
		var metadata = json.createObjectNode().put("zenflowApiProtocol", "OPENAI_CHAT_COMPLETIONS");
		assertEquals(ZenflowModelProtocol.OPENAI_CHAT_COMPLETIONS, ZenflowModelProtocol.select(metadata, "anthropic"));
		assertThrows(ResponseStatusException.class, () -> ZenflowModelProtocol.parse("arbitrary"));
	}

}
