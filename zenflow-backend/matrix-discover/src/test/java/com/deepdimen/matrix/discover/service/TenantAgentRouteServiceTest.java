package com.deepdimen.matrix.discover.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.entity.AiTenantAgentRouteEntity;
import com.deepdimen.matrix.discover.mapper.AiAgentMapper;
import com.deepdimen.matrix.discover.mapper.AiTenantAgentRouteMapper;
import com.deepdimen.matrix.discover.service.TenantAgentRouteService.ResolvedTenantAgentRoute;

class TenantAgentRouteServiceTest {

	private final AiTenantAgentRouteMapper routeMapper = mock(AiTenantAgentRouteMapper.class);

	private final AiAgentMapper agentMapper = mock(AiAgentMapper.class);

	private final AiAgentSkillService agentSkillService = mock(AiAgentSkillService.class);

	private final TenantAgentRouteService service = new TenantAgentRouteService(routeMapper, agentMapper,
			agentSkillService);

	@AfterEach
	void clearTenant() {
		TenantContextHolder.clear();
	}

	@Test
	void resolvesDifferentPublishedAgentsForTwoTenants() {
		when(routeMapper.selectList(any())).thenAnswer(invocation -> {
			Long tenantId = TenantContextHolder.getTenantId();
			return List.of(route(tenantId, tenantId * 100));
		});
		when(agentMapper.selectById(anyLong())).thenAnswer(invocation -> {
			Long agentId = invocation.getArgument(0);
			return agent(agentId, String.valueOf(agentId / 100));
		});

		TenantContextHolder.setTenantId(11L);
		assertEquals(1100L, service.resolve(identity(11L), TenantAgentRouteService.UNIFIED_ASSISTANT).agent().getId());
		TenantContextHolder.setTenantId(22L);
		assertEquals(2200L, service.resolve(identity(22L), TenantAgentRouteService.UNIFIED_ASSISTANT).agent().getId());
	}

	@Test
	void rejectsHeaderOrIdentityTenantMismatchBeforeRouteLookup() {
		TenantContextHolder.setTenantId(22L);

		assertThrows(IllegalStateException.class,
				() -> service.resolve(identity(11L), TenantAgentRouteService.UNIFIED_ASSISTANT));
	}

	@Test
	void rejectsAgentOwnedByAnotherTenant() {
		TenantContextHolder.setTenantId(11L);
		when(routeMapper.selectList(any())).thenReturn(List.of(route(11L, 1100L)));
		when(agentMapper.selectById(1100L)).thenReturn(agent(1100L, "22"));

		assertThrows(IllegalStateException.class,
				() -> service.resolve(identity(11L), TenantAgentRouteService.UNIFIED_ASSISTANT));
	}

	@Test
	void rejectsGlobalSystemAgentAsTenantDefaultRoute() {
		TenantContextHolder.setTenantId(11L);
		when(routeMapper.selectList(any())).thenReturn(List.of(route(11L, 2026L)));
		when(agentMapper.selectById(2026L)).thenReturn(agent(2026L, "0"));

		assertThrows(IllegalStateException.class,
				() -> service.resolve(identity(11L), TenantAgentRouteService.UNIFIED_ASSISTANT));
	}

	@Test
	void configuresTenantDefaultAndSynchronizesEnabledSkills() {
		TenantContextHolder.setTenantId(11L);
		AiAgentEntity agent = agent(1100L, "11");
		agent.setAgentType("dify");
		agent.setDifyMode("chat");
		agent.setDifyApiKey("app-key");
		when(agentMapper.selectById(1100L)).thenReturn(agent);
		when(routeMapper.selectList(any())).thenReturn(List.of());

		ResolvedTenantAgentRoute configured = service.configureTenantDefault(identity(11L),
				TenantAgentRouteService.UNIFIED_ASSISTANT, 1100L);

		assertEquals(1100L, configured.agent().getId());
		verify(routeMapper).insert(any(AiTenantAgentRouteEntity.class));
		verify(agentSkillService).syncEnabledSkillsToAgent(1100L);
	}

	@Test
	void switchesOnlyToPublishedNativeProfileAndReturnsExactPreviousAgent() {
		TenantContextHolder.setTenantId(11L);
		AiAgentEntity nativeProfile = agent(2200L, "11");
		nativeProfile.setAgentType("native");
		when(agentMapper.selectById(2200L)).thenReturn(nativeProfile);
		when(routeMapper.selectList(any())).thenReturn(List.of(route(11L, 1100L)));
		when(routeMapper.update(any(), any())).thenReturn(1);

		var transition = service.switchTenantDefaultNative(identity(11L), 2200L);

		assertEquals(1100L, transition.previousAgentId());
		assertEquals(2200L, transition.newAgentId());
		verify(routeMapper).update(any(), any());
	}

	@Test
	void nativeSwitchDoesNotBroadenLegacyDifyConfigurationMethod() {
		TenantContextHolder.setTenantId(11L);
		AiAgentEntity dify = agent(2200L, "11");
		dify.setAgentType("dify");
		when(agentMapper.selectById(2200L)).thenReturn(dify);

		assertThrows(IllegalArgumentException.class, () -> service.switchTenantDefaultNative(identity(11L), 2200L));
	}

	private IdentityEnvelope identity(Long tenantId) {
		return new IdentityEnvelope(String.valueOf(tenantId), "7", "0", List.of("0"), List.of(), List.of(),
				"route-test", "ASSISTANT_CONTEXT");
	}

	private AiTenantAgentRouteEntity route(Long tenantId, Long agentId) {
		AiTenantAgentRouteEntity route = new AiTenantAgentRouteEntity();
		route.setId(tenantId * 10);
		route.setTenantId(tenantId);
		route.setOrgId(0L);
		route.setChannelCode(TenantAgentRouteService.UNIFIED_ASSISTANT);
		route.setAgentId(agentId);
		route.setEnabled(1);
		route.setDelFlag("0");
		return route;
	}

	private AiAgentEntity agent(Long agentId, String tenantId) {
		AiAgentEntity agent = new AiAgentEntity();
		agent.setId(agentId);
		agent.setTenantId(tenantId);
		agent.setStatus("published");
		agent.setDelFlag("0");
		return agent;
	}

}
