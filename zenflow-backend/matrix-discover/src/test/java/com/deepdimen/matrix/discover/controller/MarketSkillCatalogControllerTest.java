package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.mapper.MarketSkillCatalogMapper;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.vo.AiAgentVO;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketSkillCatalogControllerTest {

	final AiAgentService agents = mock(AiAgentService.class);

	final TenantUserDirectory users = mock(TenantUserDirectory.class);

	final MarketSkillCatalogMapper catalog = mock(MarketSkillCatalogMapper.class);

	final MarketSkillCatalogController controller = new MarketSkillCatalogController(agents, users, catalog);

	AiAgentEntity agent;

	AiAgentVO detail;

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(7L);
		var user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(3L);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "unused", List.of()));
		when(users.isAssignableUser(7L, 3L)).thenReturn(true);
		agent = new AiAgentEntity();
		agent.setId(88L);
		agent.setTenantId("7");
		agent.setStatus("published");
		when(agents.getById(88L)).thenReturn(agent);
		detail = new AiAgentVO();
		detail.setId(88L);
		detail.setAccessible(true);
		when(agents.getAgentDetail(88L)).thenReturn(detail);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test void absentAuthenticationAndRevokedMembershipCannotRead(){
        when(users.isAssignableUser(7L,3L)).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->controller.list(88L,null));
        SecurityContextHolder.clearContext();assertThrows(AccessDeniedException.class,()->controller.list(88L,null));verifyZeroInteractions(catalog);
    }

	@Test
	void crossTenantUnpublishedAndDeniedAgentCannotRead() {
		agent.setTenantId("8");
		assertThrows(AccessDeniedException.class, () -> controller.list(88L, null));
		agent.setTenantId("7");
		agent.setStatus("draft");
		assertThrows(AccessDeniedException.class, () -> controller.list(88L, null));
		agent.setStatus("published");
		detail.setAccessible(false);
		assertThrows(AccessDeniedException.class, () -> controller.list(88L, null));
		verifyZeroInteractions(catalog);
	}

	@Test
	void defaultAssistantAndInvalidCursorCannotRead() {
		assertThrows(AccessDeniedException.class, () -> controller.list(AiAgentService.DEFAULT_AGENT_ID, null));
		assertThrows(IllegalArgumentException.class, () -> controller.list(88L, -1L));
		verifyZeroInteractions(catalog);
	}

	@Test
	void passesAuthenticatedScopeAndUsesUnreturnedLookahead() {
		var rows = new ArrayList<MarketSkillCatalogMapper.BoundSkill>();
		for (int i = 100; i >= 50; i--) {
			var row = new MarketSkillCatalogMapper.BoundSkill();
			row.setBindingId("" + i);
			rows.add(row);
		}
		when(catalog.list("7", "88", 101L)).thenReturn(rows);
		var page = controller.list(88L, 101L).getData();
		assertEquals(50, page.items().size());
		assertEquals("51", page.nextCursor());
		when(catalog.list("7", "88", 51L)).thenReturn(List.of(rows.get(50)));
		var last = controller.list(88L, 51L).getData();
		assertEquals("50", last.items().get(0).getBindingId());
		assertNull(last.nextCursor());
	}

}
