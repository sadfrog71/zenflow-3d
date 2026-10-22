package com.deepdimen.matrix.discover.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisArtifact;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ArtifactKind;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStep;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType;
import com.deepdimen.matrix.assistant.workflow.AssistantWorkflowExecutor.WorkflowExecutionControl;
import com.deepdimen.matrix.assistant.run.AssistantJsonSchemaValidator;
import com.deepdimen.matrix.discover.dify.DifyServiceClient;
import com.deepdimen.matrix.discover.dify.api.DifyAppApi;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityReleaseEntity;
import com.deepdimen.matrix.discover.mapper.AiAssistantWorkflowCapabilityReleaseMapper;
import com.deepdimen.matrix.discover.service.AiAgentPermissionService;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DifyAssistantWorkflowExecutorTest {

	private static final IdentityEnvelope IDENTITY = new IdentityEnvelope("11", "7", "3", List.of("3"), List.of(),
			List.of(), "corr", "assistant");

	private final AiAssistantWorkflowCapabilityReleaseMapper releaseMapper = mock(
			AiAssistantWorkflowCapabilityReleaseMapper.class);

	private final AiAgentService agentService = mock(AiAgentService.class);

	private final AiAgentPermissionService permissionService = mock(AiAgentPermissionService.class);

	private final DifyAppApi difyAppApi = mock(DifyAppApi.class);

	private final DifyServiceClient difyServiceClient = mock(DifyServiceClient.class);

	private final ObjectMapper objectMapper = new ObjectMapper();

	private DifyAssistantWorkflowExecutor executor;

	@BeforeEach
	void setUp() {
		AiAssistantWorkflowCapabilityReleaseEntity release = release();
		AiAgentEntity agent = new AiAgentEntity();
		agent.setId(88L);
		agent.setTenantId("11");
		agent.setStatus("published");
		agent.setDelFlag("0");
		agent.setAgentType("dify");
		agent.setDifyMode("workflow");
		agent.setDifyAppId("app-1");
		agent.setDifyApiKey("server-secret");
		when(releaseMapper.selectById(9L)).thenReturn(release);
		when(agentService.getById(88L)).thenReturn(agent);
		when(permissionService.isAccessible(88L, IDENTITY)).thenReturn(true);
		when(difyAppApi.exportApp("app-1")).thenReturn(TextNode.valueOf("definition"));
		executor = new DifyAssistantWorkflowExecutor(releaseMapper, agentService, permissionService, difyAppApi,
				difyServiceClient, objectMapper, new AssistantJsonSchemaValidator());
	}

	@Test
	void streamingWorkflowPersistsEarlyIdentifiersAndValidatesCitations() throws Exception {
		when(difyServiceClient.postStream(eq("/workflows/run"), eq("server-secret"), any()))
			.thenReturn(Flux.just(event("workflow_started", started()), event("workflow_finished", finished())));
		AtomicReference<String> taskId = new AtomicReference<>();
		AtomicReference<String> workflowRunId = new AtomicReference<>();

		var result = executor.execute(IDENTITY, "run-1", step(), List.of(artifact()), new WorkflowExecutionControl() {
			@Override
			public void externalIdentifiersAvailable(String externalTaskId, String externalWorkflowRunId) {
				taskId.set(externalTaskId);
				workflowRunId.set(externalWorkflowRunId);
			}

			@Override
			public boolean cancellationRequested() {
				return false;
			}
		});

		assertEquals("task-1", taskId.get());
		assertEquals("workflow-1", workflowRunId.get());
		assertEquals("ok", result.payload().get("summary"));
		assertEquals(List.of("citation-1"), result.citationKeys());
	}

	@Test
	@SuppressWarnings("unchecked")
	void existingDifyQueryInputReceivesSemanticQuestionAlias() throws Exception {
		AiAssistantWorkflowCapabilityReleaseEntity release = release();
		release.setInputSchemaJson(
				"{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"],\"additionalProperties\":false}");
		when(releaseMapper.selectById(9L)).thenReturn(release);
		when(difyServiceClient.postStream(eq("/workflows/run"), eq("server-secret"), any()))
			.thenReturn(Flux.just(event("workflow_started", started()), event("workflow_finished", finished())));

		executor.execute(IDENTITY, "run-1", step(), List.of(artifact()));

		ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
		verify(difyServiceClient).postStream(eq("/workflows/run"), eq("server-secret"), body.capture());
		Map<String, Object> inputs = (Map<String, Object>) body.getValue().get("inputs");
		assertEquals("\u603b\u7ed3\u6c34\u8d28", inputs.get("query"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void accessibleMarketplaceWorkflowUsesPublishedAgentAndDetectedQueryInput() throws Exception {
		when(difyServiceClient.getParameters("server-secret"))
			.thenReturn(Mono.just(objectMapper.readTree("{\"user_input_form\":[{\"variable\":\"query\"}]}")));
		when(difyServiceClient.postStream(eq("/workflows/run"), eq("server-secret"), any()))
			.thenReturn(Flux.just(event("workflow_started", started()), event("workflow_finished", finished())));

		var result = executor.execute(IDENTITY, "run-1", marketplaceStep(), List.of(artifact()));

		ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
		verify(difyServiceClient).postStream(eq("/workflows/run"), eq("server-secret"), body.capture());
		Map<String, Object> inputs = (Map<String, Object>) body.getValue().get("inputs");
		assertEquals(Map.of("query", "今天的水源水质如何"), inputs);
		assertEquals("ok", result.payload().get("summary"));
		assertEquals(88L, result.payload().get("_matrixMarketplaceAgentId"));
	}

	@Test
	void inaccessibleMarketplaceWorkflowIsRejectedBeforeDifyCall() {
		when(permissionService.isAccessible(88L, IDENTITY)).thenReturn(false);

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> executor.execute(IDENTITY, "run-1", marketplaceStep(), List.of()));

		assertEquals("MARKET_WORKFLOW_NOT_ALLOWED", failure.getMessage());
		verifyZeroInteractions(difyServiceClient);
	}

	@Test
	@SuppressWarnings("unchecked")
	void accessibleMarketplaceAdvancedChatUsesChatMessagesAndReturnsAnswer() throws Exception {
		AiAgentEntity chatAgent = new AiAgentEntity();
		chatAgent.setId(88L);
		chatAgent.setTenantId("11");
		chatAgent.setStatus("published");
		chatAgent.setDelFlag("0");
		chatAgent.setAgentType("dify");
		chatAgent.setDifyMode("advanced-chat");
		chatAgent.setDifyAppId("app-1");
		chatAgent.setDifyApiKey("server-secret");
		when(agentService.getById(88L)).thenReturn(chatAgent);
		when(difyServiceClient.getParameters("server-secret"))
			.thenReturn(Mono.just(objectMapper.readTree("{\"user_input_form\":[]}")));
		when(difyServiceClient.postStream(eq("/chat-messages"), eq("server-secret"), any()))
			.thenReturn(Flux.just(event("message", chatMessage()), event("message_end", chatMessageEnd())));

		var result = executor.execute(IDENTITY, "run-1", marketplaceStep(), List.of());

		ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
		verify(difyServiceClient).postStream(eq("/chat-messages"), eq("server-secret"), body.capture());
		assertEquals("今天的水源水质如何", body.getValue().get("query"));
		assertEquals(Map.of(), body.getValue().get("inputs"));
		assertEquals("今日客服工单共 12 条。", result.payload().get("text"));
		assertEquals("advanced-chat", result.payload().get("_matrixDifyMode"));
	}

	@Test
	void cancellationStopsKnownDifyTaskAndDoesNotReturnPartialOutput() throws Exception {
		when(difyServiceClient.postStream(eq("/workflows/run"), eq("server-secret"), any()))
			.thenReturn(Flux.just(event("workflow_started", started())).concatWith(Flux.never()));
		when(difyServiceClient.post(eq("/workflows/tasks/task-1/stop"), eq("server-secret"), any()))
			.thenReturn(Mono.just(objectMapper.readTree("{\"result\":\"success\"}")));
		AtomicBoolean cancelled = new AtomicBoolean();

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> executor.execute(IDENTITY, "run-1", step(), List.of(artifact()), new WorkflowExecutionControl() {
					@Override
					public void externalIdentifiersAvailable(String externalTaskId, String workflowRunId) {
						cancelled.set(true);
					}

					@Override
					public boolean cancellationRequested() {
						return cancelled.get();
					}
				}));

		assertEquals("WORKFLOW_CANCELLED", failure.getMessage());
		verify(difyServiceClient).post(eq("/workflows/tasks/task-1/stop"), eq("server-secret"), any());
	}

	@Test
	void sensitiveArtifactIsRejectedBeforeAnyDifyRequest() {
		AnalysisArtifact sensitive = new AnalysisArtifact("assistant-analysis-artifact/v1", "artifact-1", "run-1",
				"provider-knowledge", ArtifactKind.PROVIDER_CONTEXT,
				Map.of("dataClassification", "SENSITIVE", "summary", "source"), List.of("citation-1"), "digest",
				Instant.EPOCH);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> executor.execute(IDENTITY, "run-1", step(), List.of(sensitive)));

		assertEquals("DIFY_EGRESS_POLICY_DENIED", failure.getMessage());
		verifyZeroInteractions(difyServiceClient);
	}

	private ServerSentEvent<String> event(String type, String data) {
		return ServerSentEvent.builder(data).event(type).build();
	}

	private String started() {
		return "{\"event\":\"workflow_started\",\"task_id\":\"task-1\",\"workflow_run_id\":\"workflow-1\",\"data\":{\"id\":\"workflow-1\"}}";
	}

	private String finished() {
		return "{\"event\":\"workflow_finished\",\"task_id\":\"task-1\",\"workflow_run_id\":\"workflow-1\",\"data\":{\"status\":\"succeeded\",\"outputs\":{\"summary\":\"ok\",\"citationKeys\":[\"citation-1\"]}}}";
	}

	private String chatMessage() {
		return "{\"event\":\"message\",\"task_id\":\"task-chat\",\"conversation_id\":\"conversation-1\",\"answer\":\"今日客服工单共 12 条。\"}";
	}

	private String chatMessageEnd() {
		return "{\"event\":\"message_end\",\"task_id\":\"task-chat\",\"conversation_id\":\"conversation-1\",\"id\":\"message-1\"}";
	}

	private ExecutionStep step() {
		return new ExecutionStep("workflow-9", ExecutionStepType.DIFY_WORKFLOW, "water-quality", 9L, true,
				List.of("provider-knowledge"), null, Map.of("query", "总结水质"), "sha256:output");
	}

	private ExecutionStep marketplaceStep() {
		return new ExecutionStep("workflow-88", ExecutionStepType.DIFY_WORKFLOW, "market-agent-88", 88L, false,
				List.of("provider-knowledge"), null,
				Map.of("query", "今天的水源水质如何", "displayName", "水源水质监测智能体"),
				"market-workflow-output/v1");
	}

	private AnalysisArtifact artifact() {
		return new AnalysisArtifact("assistant-analysis-artifact/v1", "artifact-1", "run-1", "provider-knowledge",
				ArtifactKind.PROVIDER_CONTEXT, Map.of("dataClassification", "INTERNAL", "summary", "source"),
				List.of("citation-1"), "digest", Instant.EPOCH);
	}

	private AiAssistantWorkflowCapabilityReleaseEntity release() {
		AiAssistantWorkflowCapabilityReleaseEntity release = new AiAssistantWorkflowCapabilityReleaseEntity();
		release.setId(9L);
		release.setTenantId("11");
		release.setAgentId(88L);
		release.setCapabilityCode("water-quality");
		release.setReleaseStatus("ACTIVE");
		release.setPolicyAuditPassed(1);
		release.setSideEffectClass("READ_ONLY");
		release.setDataClassification("INTERNAL");
		release.setDifyAppId("app-1");
		release.setWorkflowDefinitionDigest(sha256("definition"));
		release.setInputSchemaJson(
				"{\"type\":\"object\",\"properties\":{\"matrix_query\":{\"type\":\"string\"},\"matrix_artifacts_json\":{\"type\":\"string\"},\"matrix_citation_keys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"matrix_run_id\":{\"type\":\"string\"}},\"required\":[\"matrix_query\"],\"additionalProperties\":false}");
		release.setOutputSchemaId("sha256:output");
		release.setOutputSchemaJson(
				"{\"type\":\"object\",\"properties\":{\"summary\":{\"type\":\"string\"},\"citationKeys\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}},\"required\":[\"summary\"],\"additionalProperties\":true}");
		release.setTimeoutMs(5000);
		return release;
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
