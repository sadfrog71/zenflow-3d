package com.deepdimen.matrix.work.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.work.api.entity.*;
import com.deepdimen.matrix.work.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;

class GovernedInformationReviewModelTest {

	final InformationPolicyMapper policies = mock(InformationPolicyMapper.class);

	final InformationRunMapper runs = mock(InformationRunMapper.class);

	final InformationSkillCollectionScope scope = new InformationSkillCollectionScope(policies, runs,
			mock(TenantUserDirectory.class), new ObjectMapper());

	final ObjectProvider<AssistantModelGateway> gateways = mock(ObjectProvider.class);

	final ObjectProvider<AssistantIdentityResolver> identities = mock(ObjectProvider.class);

	final AssistantModelGateway gateway = mock(AssistantModelGateway.class);

	final GovernedInformationReviewModel adapter = new GovernedInformationReviewModel(gateways, identities, scope);

	InformationSkillCollectionScope.Lease lease;

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(42L);
		SecurityContextHolder.clearContext();
		var policy = new InformationPolicy();
		policy.setId(7L);
		policy.setEnabled(1);
		policy.setModelName("77");
		var run = new InformationRun();
		run.setId(8L);
		run.setPolicyId(7L);
		run.setStatus("RUNNING");
		when(policies.selectOne(any())).thenReturn(policy);
		when(runs.selectOne(any())).thenReturn(run);
		when(gateways.getIfAvailable()).thenReturn(gateway);
		when(gateway.resolve(any())).thenReturn(new ResolvedAssistantModel("assistant-model/v1", "77", "local", "local",
				new ModelCapabilities(true, 8192, 4096, true),
				new AssistantEgress(DeploymentDomain.INTERNAL, "local", true, DataClassification.INTERNAL, "none")));
		lease = scope.open(7L, 8L);
	}

	@AfterEach
	void cleanup() {
		lease.close();
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test void fixedModelUsesDataContextAndRequiresSuccessfulTerminal() {
  when(gateway.stream(any())).thenReturn(Flux.just(new ModelChunk("run",0,"{}",false,""), new ModelChunk("run",1,"",true,"STOP")));
  assertTrue(adapter.supportsManagedModel("77"));
  var result = adapter.chatManaged("run", "77", "fixed instructions", "untrusted article");
  assertEquals("{}", result.outputJson()); assertEquals("77", result.actualModelId());
  verify(gateway).stream(argThat(r -> r.systemPrompt().equals("fixed instructions") && r.contextFragments().get(0).content().equals("untrusted article")));
 }

	@Test
	void rejectsIncompleteOutOfOrderAndFailedStreams() {
		for (var chunks : List.of(List.of(new ModelChunk("run", 0, "{}", false, "")),
				List.of(new ModelChunk("run", 1, "{}", true, "STOP")),
				List.of(new ModelChunk("run", 0, "{}", true, "LENGTH")))) {
			when(gateway.stream(any())).thenReturn(Flux.fromIterable(chunks));
			assertThrows(IllegalStateException.class, () -> adapter.chatManaged("run", "77", "fixed", "article"));
		}
	}

	@Test
	void neverUsesDefaultModelOrExecutesOutsideCollectionScope() {
		assertFalse(adapter.supportsManagedModel("default"));
		assertThrows(IllegalStateException.class, () -> adapter.chat("run", "system", "input"));
		lease.close();
		assertThrows(IllegalArgumentException.class, () -> adapter.chatManaged("run", "77", "system", "input"));
		verify(gateway, never()).stream(any());
	}

	@Test
	void managedPortsUseGovernedModelForBothFixedStages() throws Exception {
		ObjectProvider<InformationReviewModel> modelProvider = mock(ObjectProvider.class);
		when(modelProvider.getIfAvailable()).thenReturn(adapter);
		var store = mock(InformationSkillResultStore.class);
		when(store.authorizeInput(any(), any(), any(), any())).thenAnswer(call -> call.getArgument(3));
		var json = new ObjectMapper();
		var port = new InformationSkillPromptPort(modelProvider, scope, store, json);
		var input = new InformationSkillInput("title", "url", "evidence", java.time.LocalDate.of(2026, 9, 26));
		String analysis = "{\"eligible\":true,\"contentType\":\"CASE\",\"title\":\"t\",\"summary\":\"s\",\"tags\":\"AI\",\"confidence\":0.9,\"reason\":\"ok\"}";
		String review = "{\"approved\":true,\"riskLevel\":\"LOW\",\"reason\":\"ok\"}";
		when(gateway.stream(any())).thenAnswer(call -> {
			var request = call.getArgument(0, AssistantModelRequest.class);
			String text = request.runId().contains("analyze") ? analysis : review;
			return Flux.just(new ModelChunk(request.runId(), 0, text, false, ""),
					new ModelChunk(request.runId(), 1, "", true, "STOP"));
		});
		var identity = new com.deepdimen.matrix.skill.api.SkillContract.Identity("42", "information-collector",
				"information-collector", "work-information:9");
		for (String slot : List.of("ANALYZE", "REVIEW")) {
			var consumer = new com.deepdimen.matrix.skill.api.SkillContract.Consumer("INFORMATION", "7", slot,
					"default");
			var snapshot = new com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot("1",
					slot.equals("ANALYZE") ? "2" : "3", "digest", "9",
					com.deepdimen.matrix.skill.api.SkillContract.RuntimeKind.PROMPT,
					new com.deepdimen.matrix.skill.api.SkillContract.ExecutorRef("PROMPT", null, "77", null), "{}",
					"{}", InformationSkillInstructions.source(slot).instruction(), List.of(), "{}", List.of());
			var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(input.snapshot(json));
			if (slot.equals("REVIEW"))
				body.set("analysis", json.readTree(analysis));
			var output = port.execute("10", identity, consumer, snapshot, body.toString(), () -> {
			});
			assertEquals("77", output.modelId());
			assertEquals(json.readTree(slot.equals("ANALYZE") ? analysis : review), json.readTree(output.outputJson()));
		}
		verify(gateway, times(2)).stream(any());
	}

	@Test
	void unsupportedSystemInstructionsOrEgressCannotAdvertiseOrInvoke() {
		for (var model : List.of(
				new ResolvedAssistantModel("assistant-model/v1", "77", "local", "local",
						new ModelCapabilities(true, 8192, 4096, false),
						new AssistantEgress(DeploymentDomain.INTERNAL, "local", true, DataClassification.INTERNAL,
								"none")),
				new ResolvedAssistantModel("assistant-model/v1", "77", "local", "local",
						new ModelCapabilities(true, 8192, 4096, true), new AssistantEgress(DeploymentDomain.INTERNAL,
								"local", false, DataClassification.PUBLIC, "none")))) {
			when(gateway.resolve(any())).thenReturn(model);
			assertFalse(adapter.supportsManagedModel("77"));
			assertThrows(IllegalStateException.class, () -> adapter.chatManaged("run", "77", "system", "input"));
		}
		verify(gateway, never()).stream(any());
	}

	@Test
	void springRegistrationIsOptInAndMissingGatewayRemainsUnavailable() {
		for (boolean enabled : List.of(false, true)) {
			try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
				if (enabled)
					org.springframework.test.context.support.TestPropertySourceUtils
						.addInlinedPropertiesToEnvironment(context, "matrix.skill.information.model-enabled=true");
				context.registerBean(InformationSkillCollectionScope.class, () -> scope);
				context.register(GovernedInformationReviewModel.class);
				context.refresh();
				var adapters = context.getBeansOfType(InformationReviewModel.class);
				assertEquals(enabled ? 1 : 0, adapters.size());
				if (enabled)
					assertFalse(adapters.values().iterator().next().supportsManagedModel("77"));
			}
		}
	}

}
