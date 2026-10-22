package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.discover.controller.DifyProxyController;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.*;
import org.springframework.web.context.request.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DifyMarketSkillPromptPortTest {

	final ObjectMapper json = new ObjectMapper();

	final MarketSkillRequestStore requests = mock(MarketSkillRequestStore.class);

	final DifyMarketConfigurationReader configs = mock(DifyMarketConfigurationReader.class);

	final ObjectProvider<DifyProxyController> transports = mock(ObjectProvider.class);

	final DifyProxyController transport = mock(DifyProxyController.class);

	final DifyMarketSkillPromptPort port = new DifyMarketSkillPromptPort(requests, configs, transports, json);

	final Identity identity = new Identity("7", "3", "3", "market-skill:99");

	final Consumer consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "skill-1");

	final String digest = "a".repeat(64);

	final DifyChatConfigurationSnapshot config = new DifyChatConfigurationSnapshot("app-1", "config-1", digest);

	final ResolvedSkillSnapshot release = new ResolvedSkillSnapshot("1", "2", "release-digest", "9", RuntimeKind.PROMPT,
			new ExecutorRef("PROMPT", "DIFY", "app-1", digest), "{}", "{}", "reviewed",
			List.of(new ResourceRef("app-1", "config-1", digest)), "{}", List.of());

	final MockHttpServletResponse response = new MockHttpServletResponse();

	@BeforeEach
	void setup() throws Exception {
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(), response));
		when(requests.conversationObserver(identity, consumer, "99")).thenReturn(conversation -> {
		});
		when(transports.getIfAvailable()).thenReturn(transport);
		when(configs.read(88L)).thenReturn(config);
		when(requests.authorizeInput(eq(identity), eq(consumer), eq("99"), anyString()))
			.thenAnswer(call -> call.getArgument(3));
		doAnswer(call -> {
			var exchange = call.getArgument(4, DifyManagedChatExchange.class);
			exchange.start();
			exchange.blocking(json.readTree(
					"{\"event\":\"message\",\"answer\":\"answer\",\"message_id\":\"m\",\"conversation_id\":\"c\"}"));
			return null;
		}).when(transport).invokeManagedChat(anyLong(), anyString(), any(), any(), any());
	}

	@AfterEach
	void clear() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void authorizedInputUsesOriginalTransportAndReportsActualConfiguration() throws Exception {
		var invoked = mock(Runnable.class);
		var output = port.execute("10", identity, consumer, release, "{\"query\":\"question\"}", invoked);
		assertEquals(digest, output.executorVersion());
		assertNull(output.modelId());
		assertEquals("m", json.readTree(output.outputJson()).path("messageId").asText());
		assertEquals("10", response.getHeader("X-Skill-Run-Id"));
		assertEquals("2", response.getHeader("X-Skill-Release-Id"));
		verify(invoked).run();
		verify(requests, times(3)).requireConversation(identity, consumer, "99", release);
		verify(configs, times(3)).read(88L);
	}

	@Test void driftBeforeActualInvocationDoesNotCrossInvocationBoundary(){
        when(configs.read(88L)).thenReturn(config,new DifyChatConfigurationSnapshot("app-1","different",digest));
        var invoked=mock(Runnable.class);
        assertThrows(IllegalArgumentException.class,()->port.execute("10",identity,consumer,release,"{\"query\":\"q\"}",invoked));verifyZeroInteractions(invoked);
    }

	@Test void driftAfterInvocationCannotReturnSuccess(){
        when(configs.read(88L)).thenReturn(config,config,new DifyChatConfigurationSnapshot("app-1","different",digest));
        var invoked=mock(Runnable.class);
        assertThrows(IllegalArgumentException.class,()->port.execute("10",identity,consumer,release,"{\"query\":\"q\"}",invoked));verify(invoked).run();
    }

	@Test
	void unverifiedSessionsAndMissingHttpContextCannotInvoke() throws Exception {
		doThrow(new IllegalArgumentException("DIFY_CONVERSATION_CONFIGURATION_UNVERIFIED")).when(requests)
			.requireConversation(identity, consumer, "99", release);
		for (String body : List.of("{\"query\":\"q\",\"conversation_id\":\"existing\"}"))
			assertThrows(IllegalArgumentException.class,
					() -> port.execute("10", identity, consumer, release, body, () -> {
					}));
		RequestContextHolder.resetRequestAttributes();
		assertThrows(IllegalArgumentException.class,
				() -> port.execute("10", identity, consumer, release, "{\"query\":\"q\"}", () -> {
				}));
		verifyZeroInteractions(transport);
	}

	@Test
	void unsupportedConsumerOrConfigurationCannotAdvertiseAvailability() {
		assertTrue(port.available(consumer, release));
		assertFalse(port.available(new Consumer("MARKET_AGENT", "2026", "CHAT", "skill-1"), release));
		when(configs.read(88L)).thenReturn(new DifyChatConfigurationSnapshot("app-2", "config-1", digest));
		assertFalse(port.available(consumer, release));
	}

}
