package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.dify.api.DifyAppApi;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.vo.AiAgentVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DifyMarketConfigurationReaderTest {

	final AiAgentService agents = mock(AiAgentService.class);

	@SuppressWarnings("unchecked")
	final ObjectProvider<DifyAppApi> apis = mock(ObjectProvider.class);

	final DifyAppApi api = mock(DifyAppApi.class);

	final ObjectMapper json = new ObjectMapper();

	final DifyMarketConfigurationReader reader = new DifyMarketConfigurationReader(agents, apis, json);

	AiAgentEntity agent;

	AiAgentVO detail;

	@BeforeEach
	void setup() throws Exception {
		TenantContextHolder.setTenantId(7L);
		agent = new AiAgentEntity();
		agent.setId(88L);
		agent.setTenantId("7");
		agent.setAgentType("dify");
		agent.setDifyMode("chat");
		agent.setStatus("published");
		agent.setDifyAppId("app");
		detail = new AiAgentVO();
		detail.setId(88L);
		detail.setAccessible(true);
		when(agents.getById(88L)).thenReturn(agent);
		when(agents.getAgentDetail(88L)).thenReturn(detail);
		when(apis.getIfAvailable()).thenReturn(api);
		when(api.getApp("app")).thenReturn(json.readTree(
				"{\"id\":\"app\",\"mode\":\"chat\",\"model_config\":{\"id\":\"config\",\"pre_prompt\":\"{{extended_system_prompt}}\"}}"));
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
	}

	@Test
	void authorizedReaderReturnsOnlySnapshotMetadata() {
		var result = reader.read(88L);
		assertEquals("app", result.appId());
		assertEquals("config", result.modelConfigId());
		assertEquals(64, result.digest().length());
		verify(api).getApp("app");
		verifyNoMoreInteractions(api);
	}

	@Test
	void forbiddenUserDoesNotReadRemote() {
		detail.setAccessible(false);
		assertThrows(AccessDeniedException.class, () -> reader.read(88L));
		verifyZeroInteractions(api);
	}

	@Test
	void wrongTenantDoesNotReadRemote() {
		agent.setTenantId("8");
		assertThrows(AccessDeniedException.class, () -> reader.read(88L));
		verifyZeroInteractions(api);
	}

	@Test void repointedAppDuringReadCannotReturnOldProof(){when(api.getApp("app")).thenAnswer(call->{agent.setDifyAppId("changed");return json.readTree("{\"id\":\"app\",\"mode\":\"chat\",\"model_config\":{\"id\":\"config\",\"pre_prompt\":\"{{extended_system_prompt}}\"}}");});assertThrows(AccessDeniedException.class,()->reader.read(88L));}

}
