package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.discover.dify.*;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.service.*;
import com.deepdimen.matrix.discover.skill.DifyManagedChatExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DifyManagedChatTransportTest {

	final ObjectMapper json = new ObjectMapper();

	final DifyServiceClient client = mock(DifyServiceClient.class);

	final AiAgentService agents = mock(AiAgentService.class);

	final DifyChatRecordService records = mock(DifyChatRecordService.class);

	final DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);

	final DifyProxyController controller = new DifyProxyController(client, agents, mock(AiAgentPermissionService.class),
			records, extractor, json, new SkillProcessor(), mock(TenantAgentRouteService.class));

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(7L);
		var user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(3L);
		when(user.getUsername()).thenReturn("user");
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "unused", List.of()));
		var agent = new AiAgentEntity();
		agent.setId(88L);
		agent.setTenantId("7");
		agent.setAgentType("dify");
		agent.setDifyMode("chat");
		agent.setStatus("published");
		agent.setDifyApiKey("test-key");
		when(agents.getById(88L)).thenReturn(agent);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test void managedInjectionFollowsOriginalInputSnapshotAndNormalPathStillStripsBypass() throws Exception {
        when(client.post(eq("/chat-messages"),eq("test-key"),anyString())).thenReturn(Mono.just(json.readTree("{\"event\":\"message\",\"answer\":\"answer\",\"conversation_id\":\"c\",\"message_id\":\"m\"}")));
        var exchange=new DifyManagedChatExchange(json,"reviewed instruction",()->{});
        String body="{\"query\":\"question\",\"inputs\":{\"topic\":\"water\",\"extended_system_prompt\":\"untrusted\"}}";
        controller.invokeManagedChat(88L,body,new MockHttpServletRequest(),new MockHttpServletResponse(),exchange);
        var sent=org.mockito.ArgumentCaptor.forClass(String.class);verify(client).post(eq("/chat-messages"),eq("test-key"),sent.capture());
        assertEquals("reviewed instruction",json.readTree(sent.getValue()).path("inputs").path("extended_system_prompt").asText());
        var context=org.mockito.ArgumentCaptor.forClass(DifyChatRecordService.DifyChatContext.class);verify(records).asyncSaveChatRecord(context.capture(),any());
        assertFalse(context.getValue().sessionInputs().contains("extended_system_prompt"));
        var history=org.mockito.ArgumentCaptor.forClass(DifyChatRecordService.DifyChatResult.class);
        verify(records).asyncSaveChatRecord(any(),history.capture());assertEquals("m",history.getValue().messageId());
        controller.post("/chat-messages",88L,body,new MockHttpServletRequest(),new MockHttpServletResponse());
        verify(client,times(2)).post(eq("/chat-messages"),eq("test-key"),sent.capture());
        assertFalse(json.readTree(sent.getValue()).path("inputs").has("extended_system_prompt"));
    }

	@Test void swallowedProxyStreamFailureIsStillUnknownToManagedCaller() {
        when(client.postStream(anyString(),anyString(),anyString())).thenReturn(Flux.error(new IllegalStateException("connection lost")));
        var exchange=new DifyManagedChatExchange(json,"reviewed",()->{});
        assertThrows(IllegalStateException.class,()->controller.invokeManagedChat(88L,"{\"query\":\"q\",\"response_mode\":\"streaming\"}",new MockHttpServletRequest(),new MockHttpServletResponse(),exchange));
        verify(records,never()).asyncSaveChatRecord(any(),any());
    }

	@Test
	void defaultAssistantCannotUseManagedTransport() {
		assertThrows(IllegalArgumentException.class,
				() -> controller.invokeManagedChat(AiAgentService.DEFAULT_AGENT_ID, "{}", new MockHttpServletRequest(),
						new MockHttpServletResponse(), new DifyManagedChatExchange(json, "reviewed", () -> {
						})));
		verifyZeroInteractions(client);
	}

	@Test
	void completeStreamPreservesEventsButMissingTerminalCannotReturnSuccess() throws Exception {
		var delta = ServerSentEvent.<String>builder()
			.data("{\"event\":\"message\",\"answer\":\"answer\",\"conversation_id\":\"c\",\"message_id\":\"m\"}")
			.build();
		var end = ServerSentEvent.<String>builder()
			.data("{\"event\":\"message_end\",\"conversation_id\":\"c\",\"message_id\":\"m\"}")
			.build();
		when(client.postStream(anyString(), anyString(), anyString())).thenReturn(Flux.just(delta, end));
		var count = new java.util.concurrent.atomic.AtomicInteger();
		var complete = new DifyManagedChatExchange(json, "reviewed", count::incrementAndGet);
		var response = new MockHttpServletResponse();
		String input = "{\"query\":\"q\",\"response_mode\":\"streaming\"}";
		controller.invokeManagedChat(88L, input, new MockHttpServletRequest(), response, complete);
		assertEquals(1, count.get());
		assertEquals("answer", json.readTree(complete.result()).path("answer").asText());
		assertTrue(response.getContentAsString().contains("message_end"));
		when(client.postStream(anyString(), anyString(), anyString())).thenReturn(Flux.just(delta));
		var incomplete = new DifyManagedChatExchange(json, "reviewed", () -> {
		});
		assertThrows(IllegalStateException.class, () -> controller.invokeManagedChat(88L, input,
				new MockHttpServletRequest(), new MockHttpServletResponse(), incomplete));
	}

	@Test void managedFilesUseNativeReferencesAndTenantUserWithoutReadingLocalTextCache() throws Exception {
        when(client.post(anyString(),anyString(),anyString())).thenReturn(Mono.just(json.readTree("{\"event\":\"message\",\"answer\":\"answer\",\"conversation_id\":\"c\",\"message_id\":\"m\"}")));
        String body="{\"query\":\"question\",\"files\":[{\"type\":\"document\",\"transfer_method\":\"local_file\",\"upload_file_id\":\"12345678-1234-1234-1234-123456789abc\"}]}";
        controller.invokeManagedChat(88L,body,new MockHttpServletRequest(),new MockHttpServletResponse(),new DifyManagedChatExchange(json,"reviewed",()->{}));
        var sent=org.mockito.ArgumentCaptor.forClass(String.class);verify(client).post(eq("/chat-messages"),eq("test-key"),sent.capture());
        var forwarded=json.readTree(sent.getValue());
        assertEquals("7_3",forwarded.path("user").asText());assertEquals("question",forwarded.path("query").asText());
        assertEquals(json.readTree(body).path("files"),forwarded.path("files"));verifyZeroInteractions(extractor);
    }

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(booleans = { true, false })
	void ordinaryPathRejectsKnownManagedConversationBeforeCallingDify(boolean enabled) {
		var store = mock(com.deepdimen.matrix.discover.skill.MarketSkillRequestStore.class);
		org.springframework.beans.factory.ObjectProvider provider = mock(
				org.springframework.beans.factory.ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(store);
		org.springframework.test.util.ReflectionTestUtils.setField(controller, "marketSkillEnabled", enabled);
		org.springframework.test.util.ReflectionTestUtils.setField(controller, "marketSkillRequests", provider);
		when(store.isManagedConversation(eq(88L), eq("c"), anyBoolean())).thenReturn(true);
		var failure = assertThrows(RuntimeException.class,
				() -> controller.post("/chat-messages", 88L, "{\"query\":\"q\",\"conversation_id\":\"c\"}",
						new MockHttpServletRequest(), new MockHttpServletResponse()));
		assertTrue(failure.getMessage().contains("该会话已使用 Skill"));
		verifyZeroInteractions(client, records);
	}

	@Test
	void disabledFeatureStillInspectsExistingConversationRecords() throws Exception {
		var store = mock(com.deepdimen.matrix.discover.skill.MarketSkillRequestStore.class);
		org.springframework.beans.factory.ObjectProvider provider = mock(
				org.springframework.beans.factory.ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(store);
		org.springframework.test.util.ReflectionTestUtils.setField(controller, "marketSkillRequests", provider);
		when(client.post(anyString(), anyString(), anyString())).thenReturn(
				Mono.just(json.readTree("{\"answer\":\"answer\",\"conversation_id\":\"c\",\"message_id\":\"m\"}")));
		controller.post("/chat-messages", 88L, "{\"query\":\"q\",\"conversation_id\":\"c\"}",
				new MockHttpServletRequest(), new MockHttpServletResponse());
		verify(client).post(eq("/chat-messages"), eq("test-key"), anyString());
		verify(store).isManagedConversation(88L, "c", true);
	}

}
