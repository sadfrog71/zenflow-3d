package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.vo.AiAgentVO;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketAgentSkillConsumerPolicyTest {

	final AiAgentService agents = mock(AiAgentService.class);

	final TenantUserDirectory users = mock(TenantUserDirectory.class);

	final MarketSkillCapabilities capabilities = mock(MarketSkillCapabilities.class);

	@SuppressWarnings("unchecked")
	final ObjectProvider<SkillExecutionService> executors = mock(ObjectProvider.class);

	final MarketSkillRequestStore requests = mock(MarketSkillRequestStore.class);

	final MarketAgentSkillConsumerPolicy policy = new MarketAgentSkillConsumerPolicy(agents, users, capabilities,
			executors, new ObjectMapper(), requests);

	final Consumer consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "selected");

	final SkillConsumerPolicy.BindingConfiguration binding = new SkillConsumerPolicy.BindingConfiguration("9", "2",
			true, "{\"scope\":\"AGENT\"}", "EXPLICIT", "{}", "{\"mode\":\"NONE\"}");

	final ResolvedSkillSnapshot release = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
			new ExecutorRef("PROMPT", null, "app", "hash"), "{}", "{}", "instruction", List.of(), "{}", List.of());

	void auth(String... permissions) {
		var u = mock(MatrixUser.class);
		when(u.getId()).thenReturn(3L);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(u, "n/a",
					Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
	}

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(7L);
		auth("knowledge_skill_enable", "knowledge_agent_edit");
		when(users.isAssignableUser(7L, 3L)).thenReturn(true);
		var a = new AiAgentEntity();
		a.setId(88L);
		a.setTenantId("7");
		a.setAgentType("dify");
		a.setStatus("published");
		when(agents.getById(88L)).thenReturn(a);
		var d = new AiAgentVO();
		d.setId(88L);
		d.setAccessible(true);
		when(agents.getAgentDetail(88L)).thenReturn(d);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void bothManagementPermissionsRequired() {
		auth("knowledge_skill_enable");
		assertThrows(IllegalArgumentException.class, () -> policy.authorizeBinding("7", "3", consumer, binding));
		auth("knowledge_agent_edit");
		assertThrows(IllegalArgumentException.class, () -> policy.authorizeBinding("7", "3", consumer, binding));
	}

	@Test
	void authorizedDraftDoesNotGrantExecution() {
		policy.authorizeBinding("7", "3", consumer, binding);
		assertThrows(IllegalArgumentException.class,
				() -> policy.authorizeExecution(new Identity("7", "3", "3", "request"), consumer, binding, release));
		verifyZeroInteractions(capabilities, executors);
	}

	@Test void unknownCapabilityCannotEnable(){when(capabilities.inspect("7","88","dify")).thenReturn(new AgentCapabilities(CompatibilityStatus.UNKNOWN,List.of(),false,false,false,false,false,null,null,ErrorCode.AGENT_ADAPTER_UNAVAILABLE));assertThrows(IllegalArgumentException.class,()->policy.validateActivation("7","3",consumer,binding,release));verifyZeroInteractions(executors);}

	@Test void claimedSupportStillRequiresExecutor(){when(capabilities.inspect("7","88","dify")).thenReturn(new AgentCapabilities(CompatibilityStatus.SUPPORTED,List.of(RuntimeKind.PROMPT),true,false,false,true,true,"{}","{}",null));assertThrows(IllegalArgumentException.class,()->policy.validateActivation("7","3",consumer,binding,release));}

	@Test
	void otherTenantCannotSaveDraft() {
		assertThrows(IllegalArgumentException.class, () -> policy.authorizeBinding("8", "3", consumer, binding));
	}

	@Test
	void executionUsesDurableBusinessAuthorizationWithoutManagementGrant() {
		auth();
		var identity = new Identity("7", "3", "3", "market-skill:10");
		when(requests.load(identity, consumer, "10"))
			.thenReturn(new com.deepdimen.matrix.discover.entity.MarketSkillRequestEntity());
		when(capabilities.inspect("7", "88", "dify")).thenReturn(new AgentCapabilities(CompatibilityStatus.SUPPORTED,
				List.of(RuntimeKind.PROMPT), true, false, false, true, true, "{}", "{}", null));
		var executor = mock(SkillExecutionService.class);
		when(executors.getIfAvailable()).thenReturn(executor);
		when(executor.available(consumer, release)).thenReturn(true);
		policy.authorizeExecution(identity, consumer, binding, release);
		verify(requests).load(identity, consumer, "10");
		verify(executor).available(consumer, release);
		verifyNoMoreInteractions(executor);
	}

	@Test
	void savedInputDenialStopsBeforeCapabilityOrExecutor() {
		var identity = new Identity("7", "3", "3", "market-skill:10");
		when(requests.load(identity, consumer, "10"))
			.thenThrow(new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED"));
		assertThrows(IllegalArgumentException.class,
				() -> policy.authorizeExecution(identity, consumer, binding, release));
		verifyZeroInteractions(capabilities, executors);
	}

	@Test
	void changedBindingRevisionRejectedBeforeInputRead() {
		var stale = new SkillConsumerPolicy.BindingConfiguration("old", "2", true, binding.audienceJson(), "EXPLICIT",
				"{}", binding.fallbackPolicyJson());
		assertThrows(IllegalArgumentException.class, () -> policy
			.authorizeExecution(new Identity("7", "3", "3", "market-skill:10"), consumer, stale, release));
		verifyZeroInteractions(requests, capabilities, executors);
	}

}
