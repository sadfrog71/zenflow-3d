package com.deepdimen.matrix.discover.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantEgress;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DeploymentDomain;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.entity.AiTenantAgentRouteSwitchAuditEntity;
import com.deepdimen.matrix.discover.mapper.AiTenantAgentRouteSwitchAuditMapper;
import com.deepdimen.matrix.discover.mapper.AiUserSkillPrefMapper;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.service.AiAgentSkillService;
import com.deepdimen.matrix.discover.service.TenantAgentRouteService;
import com.deepdimen.matrix.discover.service.TenantAgentRouteService.RouteTransition;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class NativeAssistantRoutingIntegrationTest {

	private final AiAgentService agentService = mock(AiAgentService.class);

	private final AiAgentSkillService skillService = mock(AiAgentSkillService.class);

	private final AiUserSkillPrefMapper prefMapper = mock(AiUserSkillPrefMapper.class);

	private final TenantAgentRouteService routeService = mock(TenantAgentRouteService.class);

	private final AiTenantAgentRouteSwitchAuditMapper auditMapper = mock(AiTenantAgentRouteSwitchAuditMapper.class);

	private final AssistantModelGateway gateway = mock(AssistantModelGateway.class);

	private final NativeAssistantCutoverGate cutoverGate = mock(NativeAssistantCutoverGate.class);

	private final NativeAssistantProvisioningService service = new NativeAssistantProvisioningService(agentService,
			skillService, prefMapper, routeService, auditMapper, gateway, cutoverGate, new ObjectMapper());

	@Test
	void switchesOnlyTheRequestedTenantAfterNativeModelDiagnostic() {
		when(agentService.getById(2200L)).thenReturn(nativeProfile());
		when(gateway.resolve(any())).thenReturn(model());
		when(routeService.switchTenantDefaultNative(any(), any())).thenReturn(new RouteTransition(1L, 1100L, 2200L));
		when(auditMapper.insert(any(AiTenantAgentRouteSwitchAuditEntity.class))).thenReturn(1);

		AiTenantAgentRouteSwitchAuditEntity audit = service.switchRoute(identity(), 2200L, "tenant gray");

		assertEquals(1100L, audit.getPreviousAgentId());
		assertEquals(2200L, audit.getNewAgentId());
		assertEquals(11L, audit.getTenantId());
		verify(routeService).switchTenantDefaultNative(identity(), 2200L);
		verify(cutoverGate).assertReady(identity(), 2200L);
	}

	@Test
	void modelDiagnosticFailureLeavesRouteUnchanged() {
		when(agentService.getById(2200L)).thenReturn(nativeProfile());
		when(gateway.resolve(any())).thenThrow(new AssistantModelException("MODEL_NOT_CONFIGURED", "missing"));

		assertThrows(AssistantModelException.class, () -> service.switchRoute(identity(), 2200L, "tenant gray"));
		verify(routeService, never()).switchTenantDefaultNative(any(), any());
		verify(cutoverGate, never()).assertReady(any(), any());
		verify(auditMapper, never()).insert(any(AiTenantAgentRouteSwitchAuditEntity.class));
	}

	@Test
	void rollbackRestoresTheExactPreviousAgentFromAudit() {
		AiTenantAgentRouteSwitchAuditEntity audit = new AiTenantAgentRouteSwitchAuditEntity();
		audit.setId(9L);
		audit.setTenantId(11L);
		audit.setRouteType(TenantAgentRouteService.UNIFIED_ASSISTANT);
		audit.setPreviousAgentId(1100L);
		audit.setNewAgentId(2200L);
		when(auditMapper.selectById(9L)).thenReturn(audit);
		when(auditMapper.updateById(any(AiTenantAgentRouteSwitchAuditEntity.class))).thenReturn(1);

		service.rollbackRoute(identity(), 9L);

		verify(routeService).restoreTenantDefault(identity(), 2200L, 1100L);
	}

	private AiAgentEntity nativeProfile() {
		AiAgentEntity agent = new AiAgentEntity();
		agent.setId(2200L);
		agent.setTenantId("11");
		agent.setAgentType("native");
		agent.setStatus("published");
		agent.setConfig("{\"modelId\":\"99\"}");
		return agent;
	}

	private ResolvedAssistantModel model() {
		return new ResolvedAssistantModel(AssistantModelContract.VERSION, "99", "chat", "openai",
				new ModelCapabilities(true, 8192, 1024, true),
				new AssistantEgress(DeploymentDomain.INTERNAL, "cn", true, DataClassification.SENSITIVE, "p1"));
	}

	private IdentityEnvelope identity() {
		return new IdentityEnvelope("11", "7", "3", List.of("3"), List.of(), List.of(), "routing-test",
				"ASSISTANT_MODEL");
	}

}
