package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.dify.DifyServiceClient;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DifyMarketSkillCapabilitiesTest {

	final AiAgentService agents = mock(AiAgentService.class);

	@SuppressWarnings("unchecked")
	final ObjectProvider<DifyServiceClient> clients = mock(ObjectProvider.class);

	final DifyServiceClient client = mock(DifyServiceClient.class);

	final DifyMarketConfigurationReader configs = mock(DifyMarketConfigurationReader.class);

	final ObjectProvider<com.deepdimen.matrix.discover.controller.DifyProxyController> transports = mock(
			ObjectProvider.class);

	final DifyMarketSkillCapabilities adapter = new DifyMarketSkillCapabilities(agents, clients, configs, transports);

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(7L);
		var a = new AiAgentEntity();
		a.setId(88L);
		a.setTenantId("7");
		a.setAgentType("dify");
		a.setDifyMode("chat");
		a.setStatus("published");
		a.setDifyApiKey("test-key");
		when(agents.getById(88L)).thenReturn(a);
		when(clients.getIfAvailable()).thenReturn(client);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
	}

	@Test void missingVariableIsUnsupported() throws Exception {when(client.getParameters("test-key")).thenReturn(Mono.just(new ObjectMapper().readTree("{\"user_input_form\":[]}")));assertEquals(CompatibilityStatus.UNSUPPORTED,adapter.inspect("7","88").status());}

	@Test void declarationIsNotProofOfRuntimeSupport() throws Exception {when(client.getParameters("test-key")).thenReturn(Mono.just(new ObjectMapper().readTree("{\"user_input_form\":[{\"text-input\":{\"variable\":\"extended_system_prompt\"}}]}")));var result=adapter.inspect("7","88");assertEquals(CompatibilityStatus.UNAVAILABLE,result.status());assertTrue(result.runtimeKinds().isEmpty());}

	@Test void missingClientIsUnavailable(){when(clients.getIfAvailable()).thenReturn(null);assertEquals(CompatibilityStatus.UNAVAILABLE,adapter.inspect("7","88").status());verifyZeroInteractions(client);}

	@Test
	void wrongTenantCannotUseApiKey() {
		assertThrows(IllegalArgumentException.class, () -> adapter.inspect("8", "88"));
		verifyZeroInteractions(client);
	}

	@Test void enabledClassicChatRequiresRealTransportAndVerifiedConfiguration() throws Exception {
        when(client.getParameters("test-key")).thenReturn(Mono.just(new ObjectMapper().readTree("{\"user_input_form\":[{\"text-input\":{\"variable\":\"extended_system_prompt\"}}]}")));
        org.springframework.test.util.ReflectionTestUtils.setField(adapter,"enabled",true);
        assertEquals(CompatibilityStatus.UNAVAILABLE,adapter.inspect("7","88").status());
        when(transports.getIfAvailable()).thenReturn(mock(com.deepdimen.matrix.discover.controller.DifyProxyController.class));
        assertEquals(CompatibilityStatus.UNKNOWN,adapter.inspect("7","88").status());
        when(configs.read(88L)).thenReturn(new DifyChatConfigurationSnapshot("app","config","a".repeat(64)));
        var result=adapter.inspect("7","88");assertEquals(CompatibilityStatus.SUPPORTED,result.status());
        assertEquals(java.util.List.of(RuntimeKind.PROMPT),result.runtimeKinds());assertTrue(result.instructions());assertFalse(result.independentActions());
        when(configs.read(88L)).thenThrow(new IllegalArgumentException("unverified"));
        assertThrows(IllegalArgumentException.class,()->adapter.inspect("7","88"));
    }

}
