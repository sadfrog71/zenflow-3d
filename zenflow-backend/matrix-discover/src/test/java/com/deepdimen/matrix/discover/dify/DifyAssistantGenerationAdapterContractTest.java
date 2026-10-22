package com.deepdimen.matrix.discover.dify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantContextPack;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.ExecutionPlan;
import com.deepdimen.matrix.assistant.generation.AssistantGenerationAdapter.GenerationRequest;
import com.deepdimen.matrix.assistant.session.AssistantSessionRepository;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.service.AiAgentPermissionService;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.service.AiAgentSkillService;
import com.deepdimen.matrix.discover.service.TenantAgentRouteService;
import com.deepdimen.matrix.discover.vo.AiAgentSkillVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

class DifyAssistantGenerationAdapterContractTest {

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	private final AiAgentService agentService = mock(AiAgentService.class);

	private final AiAgentPermissionService permissionService = mock(AiAgentPermissionService.class);

	private final AiAgentSkillService agentSkillService = mock(AiAgentSkillService.class);

	private final AssistantSessionRepository sessionRepository = mock(AssistantSessionRepository.class);

	private final TenantAgentRouteService routeService = mock(TenantAgentRouteService.class);

	private final AtomicReference<String> requestPath = new AtomicReference<>();

	private final AtomicReference<String> requestBody = new AtomicReference<>();

	private final AtomicReference<String> responsePayload = new AtomicReference<>();

	private HttpServer server;

	private DifyAssistantGenerationAdapter adapter;

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			requestPath.set(exchange.getRequestURI().getPath());
			requestBody
				.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
			String response = responsePayload.get();
			if (response == null) {
				response = exchange.getRequestURI().getPath().endsWith("/workflows/run")
						? "{\"task_id\":\"task-workflow\",\"workflow_run_id\":\"run-workflow\",\"data\":{\"outputs\":{\"result\":\"workflow answer\"}}}"
						: "{\"task_id\":\"task-chat\",\"conversation_id\":\"conversation-chat\",\"answer\":\"chat answer\"}";
			}
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		});
		server.start();
		String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
		WebClient webClient = WebClient.builder()
			.clientConnector(new JdkClientHttpConnector(HttpClient.newBuilder().build()))
			.build();
		DifyServiceClient difyClient = new DifyServiceClient(webClient, objectMapper, baseUrl, 5);
		adapter = new DifyAssistantGenerationAdapter(difyClient, agentService, permissionService, agentSkillService,
				sessionRepository, objectMapper, routeService);
		AiAgentEntity primary = agent(2026L, "primary-key", "primary-app", "advanced-chat");
		when(routeService.resolve(any(), eq(TenantAgentRouteService.UNIFIED_ASSISTANT)))
			.thenReturn(new TenantAgentRouteService.ResolvedTenantAgentRoute(1L, 1L, 0L,
					TenantAgentRouteService.UNIFIED_ASSISTANT, primary, "v1"));
	}

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void invokesRealChatMessageContractAndPersistsConversation() throws Exception {
		AiAgentEntity primary = agent(2026L, "primary-key", "primary-app", "advanced-chat");
		when(agentService.getById(2026L)).thenReturn(primary);
		when(permissionService.getPermission(2026L)).thenReturn(null);
		when(sessionRepository.findExternalConversation("session-1", "agent:2026:primary-app"))
			.thenReturn(Optional.of("conversation-old"));
		when(agentSkillService.getPublishedByAgentId(2026L)).thenReturn(List.of());

		var result = adapter.generate(request("session-1", "message-1", "请汇总", List.of()));

		assertTrue(result.available());
		assertEquals("chat answer", result.content());
		assertEquals(ExecutionPlan.PRIMARY_ONLY, result.trace().executionPlan());
		assertEquals("conversation-chat", result.trace().conversationId());
		assertEquals("/v1/chat-messages", requestPath.get());
		JsonNode body = objectMapper.readTree(requestBody.get());
		assertEquals("conversation-old", body.path("conversation_id").asText());
		assertTrue(body.path("query").asText().contains("请汇总"));
		verify(sessionRepository).saveExternalConversation("session-1", "agent:2026:primary-app", "conversation-chat");
	}

	@Test
	void rejectsDedicatedWorkflowSkillInPromptOnlyMvp() {
		AiAgentEntity primary = agent(2026L, "primary-key", "primary-app", "advanced-chat");
		when(agentService.getById(2026L)).thenReturn(primary);
		when(permissionService.getPermission(2026L)).thenReturn(null);
		when(agentSkillService.getPublishedByAgentId(2026L)).thenReturn(List.of(workflowSkill()));

		var result = adapter.generate(request("session-2", "message-2", "分析这条", List.of("workflow")));

		assertFalse(result.available());
		assertEquals("SKILL_NOT_ALLOWED", result.trace().errorCode());
		assertTrue(result.content().contains("刷新 Skill 列表"));
		assertEquals(null, requestPath.get());
	}

	@Test
	void rejectsMoreThanOneExplicitSkillWithStableErrorCode() {
		AiAgentEntity primary = agent(2026L, "primary-key", "primary-app", "advanced-chat");
		when(agentService.getById(2026L)).thenReturn(primary);
		when(permissionService.getPermission(2026L)).thenReturn(null);
		when(agentSkillService.getPublishedByAgentId(2026L))
			.thenReturn(List.of(promptSkill(1L, "skill-a"), promptSkill(2L, "skill-b")));

		var result = adapter.generate(request("session-3", "message-3", "hello", List.of("skill-a", "skill-b")));

		assertFalse(result.available());
		assertEquals("SKILL_SELECTION_LIMIT_EXCEEDED", result.trace().errorCode());
		assertTrue(result.content().contains("一次只能选择一个 Skill"));
	}

	@Test
	void injectsOnePublishedPromptSkillIntoPrimaryAgentRequest() throws Exception {
		AiAgentEntity primary = agent(2026L, "primary-key", "primary-app", "advanced-chat");
		when(agentService.getById(2026L)).thenReturn(primary);
		when(permissionService.getPermission(2026L)).thenReturn(null);
		AiAgentSkillVO published = promptSkill(1L, "skill-a");
		published.setRuntimeKind("PROMPT_INJECTION");
		published.setPromptSnapshot("Only use the frozen release instruction.");
		published.setSkillReleaseId(901L);
		published.setReleaseVersion("v3");
		published.setPackageSha256("package-sha");
		published.setInstructionDigest("instruction-sha");
		when(agentSkillService.getPublishedByAgentId(2026L)).thenReturn(List.of(published));

		var result = adapter.generate(request("session-4", "message-4", "summarize", List.of("skill-a")));

		assertTrue(result.available());
		assertEquals(List.of("skill-a"), result.trace().activatedSkillCodes());
		assertEquals(List.of("skill-a"), result.trace().executedSkillCodes());
		assertEquals(901L, result.trace().skillReleaseId());
		assertEquals("v3", result.trace().skillReleaseVersion());
		assertEquals("package-sha", result.trace().skillPackageSha256());
		assertEquals("instruction-sha", result.trace().skillInstructionDigest());
		assertEquals("/v1/chat-messages", requestPath.get());
		JsonNode body = objectMapper.readTree(requestBody.get());
		assertTrue(body.path("query").asText().contains("Only use the frozen release instruction."));
	}

	@Test
	void blocksDifyWhenARequiredProviderIsNotAvailableButRetainsSelectionProvenance() {
		when(permissionService.getPermission(2026L)).thenReturn(null);
		AiAgentSkillVO published = frozenPromptSkill("skill-a");
		published.setRequiredProviders(List.of("KNOWLEDGE"));
		when(agentSkillService.getPublishedByAgentId(2026L)).thenReturn(List.of(published));

		var result = adapter.generate(request("session-provider", "message-provider", "summarize", List.of("skill-a")));

		assertFalse(result.available());
		assertEquals("SKILL_REQUIRED_PROVIDER_UNAVAILABLE", result.trace().errorCode());
		assertTrue(result.content().contains("所需数据源当前不可用"));
		assertEquals(List.of("skill-a"), result.trace().activatedSkillCodes());
		assertEquals(List.of(), result.trace().executedSkillCodes());
		assertEquals(901L, result.trace().skillReleaseId());
		assertEquals(null, requestPath.get());
	}

	@Test
	void invalidRequiredProviderNameIsReportedAsInvalidSkillPackageWithoutCallingDify() {
		when(permissionService.getPermission(2026L)).thenReturn(null);
		AiAgentSkillVO published = frozenPromptSkill("skill-a");
		published.setRequiredProviders(List.of("not-a-provider"));
		when(agentSkillService.getPublishedByAgentId(2026L)).thenReturn(List.of(published));

		var result = adapter.generate(request("session-invalid-provider", "message-invalid-provider", "summarize",
				List.of("skill-a")));

		assertFalse(result.available());
		assertEquals("SKILL_PACKAGE_INVALID", result.trace().errorCode());
		assertEquals(List.of("skill-a"), result.trace().activatedSkillCodes());
		assertEquals(List.of(), result.trace().executedSkillCodes());
		assertEquals(null, requestPath.get());
	}

	@Test
	void successfulDifyTransportWithoutAnAnswerStillRecordsTheSkillAsExecuted() {
		when(permissionService.getPermission(2026L)).thenReturn(null);
		AiAgentSkillVO published = frozenPromptSkill("skill-a");
		when(agentSkillService.getPublishedByAgentId(2026L)).thenReturn(List.of(published));
		responsePayload.set("{\"task_id\":\"task-chat\",\"conversation_id\":\"conversation-chat\"}");

		var result = adapter.generate(request("session-no-answer", "message-no-answer", "summarize", List.of("skill-a")));

		assertFalse(result.available());
		assertEquals("DIFY_OUTPUT_MAPPING_FAILED", result.trace().errorCode());
		assertEquals(List.of("skill-a"), result.trace().activatedSkillCodes());
		assertEquals(List.of("skill-a"), result.trace().executedSkillCodes());
		assertEquals(901L, result.trace().skillReleaseId());
		assertEquals("/v1/chat-messages", requestPath.get());
	}

	@Test
	void failedRuntimeKeepsReleaseProvenanceWithoutClaimingSkillExecution() {
		AiAgentEntity invalidRuntime = agent(2026L, "primary-key", "primary-app", "unsupported-mode");
		when(routeService.resolve(any(), eq(TenantAgentRouteService.UNIFIED_ASSISTANT)))
			.thenReturn(new TenantAgentRouteService.ResolvedTenantAgentRoute(1L, 1L, 0L,
					TenantAgentRouteService.UNIFIED_ASSISTANT, invalidRuntime, "v1"));
		when(permissionService.getPermission(2026L)).thenReturn(null);
		AiAgentSkillVO published = promptSkill(1L, "skill-a");
		published.setRuntimeKind("PROMPT_INJECTION");
		published.setPromptSnapshot("Frozen instruction.");
		published.setSkillReleaseId(901L);
		published.setReleaseVersion("v3");
		published.setPackageSha256("package-sha");
		published.setInstructionDigest("instruction-sha");
		when(agentSkillService.getPublishedByAgentId(2026L)).thenReturn(List.of(published));

		var result = adapter.generate(request("session-5", "message-5", "summarize", List.of("skill-a")));

		assertFalse(result.available());
		assertEquals(List.of("skill-a"), result.trace().activatedSkillCodes());
		assertEquals(List.of(), result.trace().executedSkillCodes());
		assertEquals(901L, result.trace().skillReleaseId());
		assertEquals("package-sha", result.trace().skillPackageSha256());
	}

	@Test
	void contextFreePromptSkillWithoutRequiredProvidersInvokesDify() throws Exception {
		when(permissionService.getPermission(2026L)).thenReturn(null);
		when(agentSkillService.getPublishedByAgentId(2026L)).thenReturn(List.of(frozenPromptSkill("skill-a")));

		var result = adapter.generate(emptyContextRequest("session-empty", "message-empty", "summarize",
				List.of("skill-a")));

		assertTrue(result.available());
		assertEquals("chat answer", result.content());
		assertEquals(List.of("skill-a"), result.trace().activatedSkillCodes());
		assertEquals(List.of("skill-a"), result.trace().executedSkillCodes());
		assertEquals(901L, result.trace().skillReleaseId());
		assertEquals("/v1/chat-messages", requestPath.get());
		JsonNode body = objectMapper.readTree(requestBody.get());
		assertTrue(body.path("query").asText().contains("Frozen instruction."));
		assertTrue(body.path("query").asText().contains("CONTEXT 为空时"));
	}

	@Test
	void contextUnavailableStillBlocksPrimaryAgentWithoutAnExplicitSkill() {
		when(permissionService.getPermission(2026L)).thenReturn(null);
		when(agentSkillService.getPublishedByAgentId(2026L)).thenReturn(List.of());

		var result = adapter.generate(emptyContextRequest("session-primary-empty", "message-primary-empty", "summarize",
				List.of()));

		assertFalse(result.available());
		assertEquals("CONTEXT_UNAVAILABLE", result.trace().errorCode());
		assertEquals(List.of(), result.trace().activatedSkillCodes());
		assertEquals(List.of(), result.trace().executedSkillCodes());
		assertEquals(null, requestPath.get());
	}

	private GenerationRequest request(String sessionId, String messageId, String query, List<String> skillCodes) {
		Instant now = Instant.now();
		ContextFragment fragment = new ContextFragment("fragment-1", ProviderId.WORK, "work", "work-1", "v1",
				AssistantContextContract.FactType.DOMAIN_FACT, "任务", "任务内容", now, now, "hash",
				new AssistantContextContract.Citation("citation-1", ProviderId.WORK, "work", "work-1", "v1", "work",
						"待办", Map.of("id", "work-1")));
		ProviderResult provider = new ProviderResult(AssistantContextContract.VERSION, ProviderId.WORK,
				ProviderStatus.OK, now, 1, 0, false, List.of(fragment), List.of());
		AssistantContextPack pack = new AssistantContextPack("pack-1", sessionId, messageId, now, now.plusSeconds(60),
				"fingerprint", List.of(provider), List.of(fragment), List.of());
		return new GenerationRequest("run-1", sessionId, messageId, new IdentityEnvelope("tenant-1", "7", "org-1",
				List.of("org-1"), List.of(), List.of(), "corr-1", "ASSISTANT_CONTEXT"), query, pack, skillCodes);
	}

	private GenerationRequest emptyContextRequest(String sessionId, String messageId, String query,
			List<String> skillCodes) {
		Instant now = Instant.now();
		AssistantContextPack pack = new AssistantContextPack("pack-empty", sessionId, messageId, now,
				now.plusSeconds(60), "fingerprint-empty", List.of(), List.of(), List.of());
		return new GenerationRequest("run-empty", sessionId, messageId, new IdentityEnvelope("tenant-1", "7", "org-1",
				List.of("org-1"), List.of(), List.of(), "corr-1", "ASSISTANT_CONTEXT"), query, pack, skillCodes);
	}

	private AiAgentEntity agent(long id, String apiKey, String appId, String mode) {
		AiAgentEntity agent = new AiAgentEntity();
		agent.setId(id);
		agent.setTenantId("tenant-1");
		agent.setAgentType("dify");
		agent.setStatus("published");
		agent.setDifyApiKey(apiKey);
		agent.setDifyAppId(appId);
		agent.setDifyMode(mode);
		return agent;
	}

	private AiAgentSkillVO workflowSkill() {
		AiAgentSkillVO skill = new AiAgentSkillVO();
		skill.setSkillCode("workflow");
		skill.setSkillName("Workflow");
		skill.setImplType("dify_plugin");
		skill.setExecutionStrategy("DEDICATED_DIFY_WORKFLOW");
		skill.setWorkflowAgentId(3030L);
		skill.setInputMapping(Map.of("matrix_query", "query_var", "matrix_context_json", "context_var",
				"matrix_request_id", "request_var"));
		skill.setOutputMapping(Map.of("answer", "outputs.result"));
		skill.setResultMode("FINAL_RESPONSE");
		skill.setSideEffectClass("READ_ONLY");
		skill.setTimeoutMs(3000);
		skill.setRetryPolicy("NONE");
		return skill;
	}

	private AiAgentSkillVO promptSkill(long id, String code) {
		AiAgentSkillVO skill = new AiAgentSkillVO();
		skill.setSkillId(id);
		skill.setSkillCode(code);
		skill.setSkillName(code);
		skill.setImplType("prompt_injection");
		skill.setExecutionStrategy("PRIMARY_APP_INSTRUCTION");
		return skill;
	}

	private AiAgentSkillVO frozenPromptSkill(String code) {
		AiAgentSkillVO skill = promptSkill(1L, code);
		skill.setRuntimeKind("PROMPT_INJECTION");
		skill.setPromptSnapshot("Frozen instruction.");
		skill.setSkillReleaseId(901L);
		skill.setReleaseVersion("v3");
		skill.setPackageSha256("package-sha");
		skill.setInstructionDigest("instruction-sha");
		return skill;
	}

}
