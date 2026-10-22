package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.skill.MarketSkillCapabilities;
import com.deepdimen.matrix.discover.vo.AiAgentVO;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketSkillCapabilitiesControllerTest {

	final AiAgentService agents = mock(AiAgentService.class);

	final MarketSkillCapabilities capabilities = mock(MarketSkillCapabilities.class);

	final MarketSkillCapabilitiesController controller = new MarketSkillCapabilitiesController(agents, capabilities);

	@BeforeEach
	void tenant() {
		TenantContextHolder.setTenantId(7L);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
	}

	@Test
	void unifiedDefaultCannotEnterMarketAdapter() {
		assertThrows(AccessDeniedException.class, () -> controller.get(AiAgentService.DEFAULT_AGENT_ID));
		verifyZeroInteractions(agents, capabilities);
	}

	@Test
	void deniedAgentCannotInspectAdapter() {
		var a = new AiAgentVO();
		a.setId(88L);
		a.setAccessible(false);
		when(agents.getAgentDetail(88L)).thenReturn(a);
		assertThrows(AccessDeniedException.class, () -> controller.get(88L));
		verifyZeroInteractions(capabilities);
	}

	@Test
	void grantedAgentPassesCurrentTenantAndActualType() {
		var a = new AiAgentVO();
		a.setId(88L);
		a.setAccessible(true);
		a.setAgentType("dify");
		when(agents.getAgentDetail(88L)).thenReturn(a);
		controller.get(88L);
		verify(capabilities).inspect("7", "88", "dify");
	}

	@Test
	void absentTenantCannotQueryAgent() {
		TenantContextHolder.clear();
		assertThrows(AccessDeniedException.class, () -> controller.get(88L));
		verifyZeroInteractions(agents, capabilities);
	}

}
