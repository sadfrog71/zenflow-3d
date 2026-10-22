package com.deepdimen.matrix.discover.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.vo.AiAgentVO;
import com.deepdimen.matrix.skill.api.AgentSkillPromptAdapter;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.skill.api.SkillPromptInvocationPort.Output;

class MarketSkillPromptPortTest {

	final AiAgentService agents = mock(AiAgentService.class);

	final AgentSkillPromptAdapter dify = mock(AgentSkillPromptAdapter.class);

	final AgentSkillPromptAdapter knowledge = mock(AgentSkillPromptAdapter.class);

	final MarketSkillPromptPort port = new MarketSkillPromptPort(agents, List.of(dify, knowledge));

	final Consumer consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "skill-1");

	final Identity identity = new Identity("7", "3", "3", "market-skill:99");

	final ResolvedSkillSnapshot release = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
			new ExecutorRef("PROMPT", null, "model-1", null), "{}", "{}", "reviewed", List.of(), "{}", List.of());

	final AiAgentEntity agent = new AiAgentEntity();

	final AiAgentVO detail = new AiAgentVO();

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(7L);
		agent.setId(88L);
		agent.setTenantId("7");
		agent.setStatus("published");
		agent.setAgentType("knowledge");
		detail.setId(88L);
		detail.setAccessible(true);
		when(agents.getById(88L)).thenReturn(agent);
		when(agents.getAgentDetail(88L)).thenReturn(detail);
		when(dify.agentType()).thenReturn("dify");
		when(knowledge.agentType()).thenReturn("knowledge");
		when(knowledge.available(consumer, release)).thenReturn(true);
		when(dify.available(consumer, release)).thenReturn(true);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
	}

	@Test
	void nonDifyTypeUsesItsOwnAdapterWithoutDifyConfiguration() {
		Output result = new Output("{\"answer\":\"evidence\"}", "model-1");
		when(knowledge.execute(eq("10"), eq(identity), eq(consumer), eq(release), eq("{}"), any())).thenAnswer(call -> {
			call.getArgument(5, Runnable.class).run();
			return result;
		});
		var boundary = mock(Runnable.class);
		assertTrue(port.available(consumer, release));
		assertSame(result, port.execute("10", identity, consumer, release, "{}", boundary));
		verify(boundary).run();
		verify(dify, never()).available(any(), any());
		verify(dify, never()).execute(any(), any(), any(), any(), any(), any());
	}

	@Test
	void unknownOrAmbiguousTypesCannotBorrowAnotherAdapter() {
		agent.setAgentType("unknown");
		assertFalse(port.available(consumer, release));
		agent.setAgentType("knowledge");
		var duplicate = new MarketSkillPromptPort(agents, List.of(knowledge, knowledge));
		assertFalse(duplicate.available(consumer, release));
		assertEquals("ADAPTER_AMBIGUOUS", assertThrows(IllegalArgumentException.class,
				() -> duplicate.execute("10", identity, consumer, release, "{}", () -> {
				}))
			.getMessage());
	}

	@Test
	void tenantAclAndDefaultAssistantCannotReachAnyExecution() {
		agent.setTenantId("8");
		assertFalse(port.available(consumer, release));
		agent.setTenantId("7");
		detail.setAccessible(false);
		assertFalse(port.available(consumer, release));
		assertFalse(port.available(new Consumer("MARKET_AGENT", "2026", "CHAT", "skill-1"), release));
		assertThrows(IllegalArgumentException.class, () -> port.execute("10",
				new Identity("8", "3", "3", "market-skill:99"), consumer, release, "{}", () -> {
				}));
		verify(knowledge, never()).available(any(), any());
	}

	@Test
	void typeChangeAtInvocationBoundaryDoesNotInvokeNewAdapterOrCrossBoundary() {
		when(knowledge.execute(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
			agent.setAgentType("dify"); call.getArgument(5, Runnable.class).run(); return null;
		});
		var boundary = mock(Runnable.class);
		assertThrows(IllegalArgumentException.class, () -> port.execute("10", identity, consumer, release, "{}", boundary));
		verifyZeroInteractions(boundary);
		verify(dify, never()).execute(any(), any(), any(), any(), any(), any());
	}

	@Test
	void typeChangeAfterExecutionCannotReturnOldResultAsCurrentSuccess() {
		when(knowledge.execute(any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
			call.getArgument(5, Runnable.class).run(); agent.setAgentType("dify"); return new Output("{}", "model-1");
		});
		assertThrows(IllegalStateException.class, () -> port.execute("10", identity, consumer, release, "{}", () -> {}));
	}

	@Test
	void springRegistersOneMarketBusinessPortAndKeepsDifyBehindIt() {
		try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
			context.registerBean(AiAgentService.class, () -> agents);
			context.registerBean(MarketSkillRequestStore.class, () -> mock(MarketSkillRequestStore.class));
			context.registerBean(DifyMarketConfigurationReader.class, () -> mock(DifyMarketConfigurationReader.class));
			context.registerBean(com.fasterxml.jackson.databind.ObjectMapper.class,
					() -> new com.fasterxml.jackson.databind.ObjectMapper());
			context.register(DifyMarketSkillPromptPort.class, MarketSkillTargets.class, MarketSkillPromptPort.class);
			context.refresh();
			var businessPorts = context.getBeansOfType(com.deepdimen.matrix.skill.api.SkillPromptInvocationPort.class);
			assertEquals(1, businessPorts.size());
			assertEquals("MARKET_AGENT", businessPorts.values().iterator().next().consumerType());
			assertEquals("dify", context.getBean(AgentSkillPromptAdapter.class).agentType());
		}
	}

}
