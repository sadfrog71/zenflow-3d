package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.assistant.LocalAssistantModelGateway;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;

class KnowledgeMarketSkillCapabilitiesTest {

	final KnowledgeSkillResources resources = mock(KnowledgeSkillResources.class);

	final ObjectProvider<LocalAssistantModelGateway> models = mock(ObjectProvider.class);

	final LocalAssistantModelGateway gateway = mock(LocalAssistantModelGateway.class);

	final ObjectProvider<AgentSkillRequestReader> requests = mock(ObjectProvider.class);

	final ObjectProvider<AgentSkillStreamPort> streams = mock(ObjectProvider.class);

	final ObjectProvider<AgentSkillResultContextReader> contexts = mock(ObjectProvider.class);

	final KnowledgeMarketSkillCapabilities capability = new KnowledgeMarketSkillCapabilities(resources, models,
			requests, streams, contexts);

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
	}

	void ready() {
		TenantContextHolder.setTenantId(7L);
		ReflectionTestUtils.setField(capability, "enabled", true);
		when(models.getIfAvailable()).thenReturn(gateway);
		when(requests.getIfAvailable()).thenReturn(mock(AgentSkillRequestReader.class));
		when(streams.getIfAvailable()).thenReturn(mock(AgentSkillStreamPort.class));
		when(contexts.getIfAvailable()).thenReturn(mock(AgentSkillResultContextReader.class));
		var identity = new IdentityEnvelope("7", "3", null, List.of(), List.of(), List.of(), "inspect", "MARKET_SKILL");
		when(resources.captureConfigured(any()))
			.thenReturn(new KnowledgeSkillResources.Capture(identity, null, "77", List.of(), "fixed"));
		when(gateway.resolve(any())).thenReturn(new ResolvedAssistantModel("assistant-model/v1", "77", "qwen", "local",
				new ModelCapabilities(true, 8192, 1024, true),
				new AssistantEgress(DeploymentDomain.INTERNAL, "local", true, DataClassification.SENSITIVE, "none")));
	}

	@Test
	void disabledOrMissingBridgeCannotAdvertiseSupport() {
		TenantContextHolder.setTenantId(7L);
		assertEquals(CompatibilityStatus.UNAVAILABLE, capability.inspect("7", "88").status());
		verifyZeroInteractions(resources, gateway);
		ready();
		when(streams.getIfAvailable()).thenReturn(null);
		assertEquals(CompatibilityStatus.UNAVAILABLE, capability.inspect("7", "88").status());
		verifyZeroInteractions(resources, gateway);
	}

	@Test
	void reportsOnlyImplementedPromptRetrievalAndStreamWithoutRunningModel() {
		ready();
		var result = capability.inspect("7", "88");
		assertEquals(CompatibilityStatus.SUPPORTED, result.status());
		assertEquals(List.of(RuntimeKind.PROMPT), result.runtimeKinds());
		assertTrue(result.instructions());
		assertTrue(result.retrievalContext());
		assertTrue(result.streaming());
		assertFalse(result.independentActions());
		assertFalse(result.cancellation());
		verify(gateway, never()).stream(any());
	}

	@Test
	void generationBeanCanStartWhenLocalGatewayIsNotConfigured() {
		try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
			context.registerBean(KnowledgeSkillResources.class, () -> resources);
			context.registerBean(KnowledgeSkillRetrieval.class, () -> mock(KnowledgeSkillRetrieval.class));
			context.registerBean(KnowledgeSkillInputPolicy.class, () -> mock(KnowledgeSkillInputPolicy.class));
			context.register(KnowledgeSkillGeneration.class);
			context.refresh();
			assertNotNull(context.getBean(KnowledgeSkillGeneration.class));
		}
	}

	@Test
	void rejectsWrongTenantFrozenAgentAndUnavailableModel() {
		ready();
		assertThrows(IllegalArgumentException.class, () -> capability.inspect("8", "88"));
		assertThrows(IllegalArgumentException.class, () -> capability.inspect("7", "2026"));
		when(gateway.resolve(any())).thenThrow(new IllegalArgumentException("MODEL_EGRESS_DENIED"));
		assertEquals(CompatibilityStatus.UNAVAILABLE, capability.inspect("7", "88").status());
	}

}
