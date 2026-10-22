package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ManagedPromptSkillExecutorTest {

	private final AssistantModelGateway gateway = mock(AssistantModelGateway.class);

	private final TenantUserDirectory users = mock(TenantUserDirectory.class);

	private ManagedPromptSkillExecutor executor;

	private final Identity identity = new Identity("42", "3", "3", "correlation");

	private final Consumer consumer = new Consumer("WORK_BRIEF", "7", "WORK_BRIEF", "default");

	private final ResolvedSkillSnapshot snapshot = new ResolvedSkillSnapshot("1", "2", "digest", "9",
			RuntimeKind.PROMPT, new ExecutorRef("PROMPT", null, null, null), "{\"type\":\"object\",\"properties\":{}}",
			"{\"type\":\"object\",\"properties\":{\"summary\":{\"type\":\"string\"}},\"required\":[\"summary\"]}",
			"Reviewed frozen instructions", List.of(), "{}", List.of());

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setup() {
		ObjectProvider<AssistantModelGateway> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(gateway);
		when(provider.getObject()).thenReturn(gateway);
		when(users.isAssignableUser(42L, 3L)).thenReturn(true);
		when(users.listOrganizationIds(42L, 3L)).thenReturn(List.of(5L));
		when(users.findDefaultOrganizationId(42L, 3L)).thenReturn(5L);
		when(gateway.resolve(any())).thenReturn(new ResolvedAssistantModel(AssistantModelContract.VERSION, "77",
				"Model", "internal", new ModelCapabilities(true, 8192, 4096, true),
				new AssistantEgress(DeploymentDomain.INTERNAL, "cn", true, DataClassification.INTERNAL, "policy")));
		executor = new ManagedPromptSkillExecutor(provider, users, new ObjectMapper());
	}

	@Test
    void usesFrozenInstructionsAndGovernedModelWithoutOldSkillLookup() {
        when(gateway.stream(any())).thenReturn(Flux.just(new ModelChunk("run",0,"{\"summary\":\"ok\"}",true,"stop")));
        var result = executor.execute("run",identity,consumer,snapshot,"{}");
        assertEquals("77",result.modelId());
        assertEquals("{\"summary\":\"ok\"}",result.outputJson());
        var capture=org.mockito.ArgumentCaptor.forClass(AssistantModelRequest.class);
        verify(gateway).stream(capture.capture());
        assertTrue(capture.getValue().systemPrompt().startsWith("Reviewed frozen instructions"));
        assertEquals("WORK_BRIEF",capture.getValue().identity().purpose());
    }

	@Test
	void incompleteOrInvalidOutputCannotSucceed() {
		for (var chunk : List.of(new ModelChunk("run", 0, "{}", false, ""),
				new ModelChunk("run", 0, "{}", true, "stop"),
				new ModelChunk("run", 0, "{\"summary\":\"ok\"}{}", true, "stop"),
				new ModelChunk("run", 0, "{\"summary\":\"ok\"}", true, "length"))) {
			when(gateway.stream(any())).thenReturn(Flux.just(chunk));
			assertThrows(IllegalArgumentException.class,
					() -> executor.execute("run", identity, consumer, snapshot, "{}"));
		}
	}

	@Test
    void inactiveMemberNeverCallsModel() {
        when(users.isAssignableUser(42L,3L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> executor.execute("run",identity,consumer,snapshot,"{}"));
        verify(gateway,never()).resolve(any());
        verify(gateway,never()).stream(any());
    }

	@Test
	void taskConsumerUsesItsExplicitModelAndPurposeWhileKeepingTheFrozenInstructions() {
		var policy = mock(com.deepdimen.matrix.skill.api.SkillPromptModelPolicy.class);
		when(policy.consumerType()).thenReturn("TASK");
		var task = new Consumer("TASK", "7", "TASK_PARSE", "default");
		when(policy.resolve(identity, task, snapshot)).thenReturn(
				new com.deepdimen.matrix.skill.api.SkillPromptModelPolicy.Selection("77", "TASK_PARSE", "只生成任务建议"));
		var target = withPolicies(List.of(policy));
		when(gateway.stream(any()))
			.thenReturn(Flux.just(new ModelChunk("run", 0, "{\"summary\":\"ok\"}", true, "stop")));
		target.execute("run", identity, task, snapshot, "{}");
		var resolution = org.mockito.ArgumentCaptor.forClass(ModelResolutionRequest.class);
		verify(gateway).resolve(resolution.capture());
		assertEquals("77", resolution.getValue().requestedModelId());
		assertEquals("TASK_PARSE", resolution.getValue().identity().purpose());
		var request = org.mockito.ArgumentCaptor.forClass(AssistantModelRequest.class);
		verify(gateway).stream(request.capture());
		assertTrue(request.getValue().systemPrompt().startsWith(snapshot.instructionSnapshot()));
		assertEquals("TASK_PARSE", request.getValue().identity().purpose());
	}

	@Test
	void aDifferentResolvedModelOrAmbiguousAdapterNeverInvokesTheModel() {
		var policy = mock(com.deepdimen.matrix.skill.api.SkillPromptModelPolicy.class);
		when(policy.consumerType()).thenReturn("TASK");
		var task = new Consumer("TASK", "7", "TASK_DISPATCH", "default");
		when(policy.resolve(identity, task, snapshot)).thenReturn(
				new com.deepdimen.matrix.skill.api.SkillPromptModelPolicy.Selection("88", "TASK_DISPATCH", "只生成派发建议"));
		var target = withPolicies(List.of(policy));
		assertEquals("MODEL_BINDING_CHANGED", assertThrows(IllegalArgumentException.class,
				() -> target.execute("run", identity, task, snapshot, "{}"))
			.getMessage());
		verify(gateway, never()).stream(any());
		assertFalse(withPolicies(List.of(policy, policy)).available(task, snapshot));
		assertFalse(withPolicies(List.of()).available(task, snapshot));
	}

	private ResolvedSkillSnapshot fixed(String model) {
		return new ResolvedSkillSnapshot(snapshot.skillId(), snapshot.releaseId(), snapshot.releaseDigest(),
				snapshot.bindingRevisionId(), snapshot.runtimeKind(), new ExecutorRef("PROMPT", null, model, null),
				snapshot.inputSchemaJson(), snapshot.outputSchemaJson(), snapshot.instructionSnapshot(),
				snapshot.resourceRefs(), snapshot.limitsJson(), snapshot.allowedParameters());
	}

	@Test
    void briefHonorsReviewedFixedModelReference() {
        when(gateway.stream(any())).thenReturn(Flux.just(new ModelChunk("run",0,"{\"summary\":\"ok\"}",true,"stop")));
        executor.execute("run",identity,consumer,fixed("77"),"{}");
        var resolution=org.mockito.ArgumentCaptor.forClass(ModelResolutionRequest.class);
        verify(gateway).resolve(resolution.capture());
        assertEquals("77",resolution.getValue().requestedModelId());
    }

	@Test
	void briefCannotSilentlyUseDifferentModelThanRelease() {
		assertEquals("MODEL_BINDING_CHANGED", assertThrows(IllegalArgumentException.class,
				() -> executor.execute("run", identity, consumer, fixed("88"), "{}"))
			.getMessage());
		verify(gateway, never()).stream(any());
	}

	@SuppressWarnings("unchecked")
	private ManagedPromptSkillExecutor withPolicies(
			List<com.deepdimen.matrix.skill.api.SkillPromptModelPolicy> policies) {
		ObjectProvider<AssistantModelGateway> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(gateway);
		when(provider.getObject()).thenReturn(gateway);
		return new ManagedPromptSkillExecutor(provider, users, new ObjectMapper(), policies);
	}

	@Test
	void invocationBoundaryIsOnlyReportedAfterPreparationAndBeforeOpeningTheStream() {
		var started = new java.util.concurrent.atomic.AtomicInteger();
		when(users.isAssignableUser(42L, 3L)).thenReturn(false);
		assertThrows(IllegalArgumentException.class,
				() -> executor.execute("run", identity, consumer, snapshot, "{}", started::incrementAndGet));
		assertEquals(0, started.get());
		verify(gateway, never()).stream(any());
		when(users.isAssignableUser(42L, 3L)).thenReturn(true);
		when(gateway.stream(any())).thenAnswer(call -> {
			assertEquals(1, started.get());
			return Flux.error(new IllegalStateException("connection lost"));
		});
		assertThrows(IllegalStateException.class,
				() -> executor.execute("run", identity, consumer, snapshot, "{}", started::incrementAndGet));
		assertEquals(1, started.get());
		verify(gateway, times(1)).stream(any());
	}

	@Test
	void externalAppCannotFallThroughToPlatformModelGateway() {
		var external = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", "DIFY", "app-1", "a".repeat(64)), "{}", "{}", "frozen",
				List.of(new ResourceRef("app-1", "config-1", "a".repeat(64))), "{}", List.of());
		assertFalse(executor.available(consumer, external));
		assertThrows(IllegalArgumentException.class, () -> executor.execute("run", identity, consumer, external, "{}"));
		verify(gateway, never()).resolve(any());
		verify(gateway, never()).stream(any());
	}

	@Test
	void knowledgeOutputMustReportThePinnedModelAndWholeConfigurationDigest() {
		var port = mock(com.deepdimen.matrix.skill.api.SkillPromptInvocationPort.class);
		when(port.consumerType()).thenReturn("MARKET_AGENT");
		when(port.available(any(), any())).thenReturn(true);
		ObjectProvider<AssistantModelGateway> provider = mock(ObjectProvider.class);
		var target = new ManagedPromptSkillExecutor(provider, users, new ObjectMapper(), List.of(), List.of(port));
		var refs = List.of(new ResourceRef("agent:88", "a".repeat(64), "a".repeat(64)),
				new ResourceRef("model:77", "b".repeat(64), "b".repeat(64)),
				new ResourceRef("dataset:31", "c".repeat(64), "c".repeat(64)));
		var digest = com.deepdimen.matrix.skill.api.KnowledgeSkillRelease.configurationDigest(refs);
		var fixed = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", "KNOWLEDGE", "77", digest), "{\"type\":\"object\",\"properties\":{}}",
				"{\"type\":\"object\",\"properties\":{}}", "reviewed", refs, "{}", List.of());
		var market = new Consumer("MARKET_AGENT", "88", "CHAT", "skill-1");
		when(port.execute(any(), any(), any(), any(), any(), any()))
			.thenReturn(new com.deepdimen.matrix.skill.api.SkillPromptInvocationPort.Output("{}", "77", digest));
		assertEquals(digest, target.execute("10", identity, market, fixed, "{}").executorVersion());
		when(port.execute(any(), any(), any(), any(), any(), any()))
			.thenReturn(new com.deepdimen.matrix.skill.api.SkillPromptInvocationPort.Output("{}", "78", digest));
		assertThrows(IllegalArgumentException.class, () -> target.execute("10", identity, market, fixed, "{}"));
		when(port.execute(any(), any(), any(), any(), any(), any())).thenReturn(
				new com.deepdimen.matrix.skill.api.SkillPromptInvocationPort.Output("{}", "77", "d".repeat(64)));
		assertThrows(IllegalArgumentException.class, () -> target.execute("10", identity, market, fixed, "{}"));
		verifyZeroInteractions(provider);
	}

}
