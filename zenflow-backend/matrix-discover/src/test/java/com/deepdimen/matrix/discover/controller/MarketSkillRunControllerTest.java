package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.discover.entity.MarketSkillRequestEntity;
import com.deepdimen.matrix.discover.skill.MarketSkillRequestStore;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MarketSkillRunControllerTest {

	final ObjectMapper json = new ObjectMapper();

	final MarketSkillRequestStore store = mock(MarketSkillRequestStore.class);

	final SkillBindingResolver resolver = mock(SkillBindingResolver.class);

	final SkillExecutionService executions = mock(SkillExecutionService.class);

	final MarketSkillRunController controller = new MarketSkillRunController(store, resolver, executions, json);

	final ResolvedSkillSnapshot snapshot = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
			new ExecutorRef("PROMPT", null, "model", null), "{}", "{}", "private instruction", List.of(), "{}",
			List.of());

	@BeforeEach
	void setup() {
		var saved = new MarketSkillRequestEntity();
		saved.setId(99L);
		saved.setTenantId(7L);
		saved.setActorId(3L);
		saved.setAgentId(88L);
		saved.setBindingKey("skill-1");
		saved.setInputSnapshot("{\"query\":\"authorized\"}");
		when(store.prepare(eq(88L), eq("skill-1"), eq("request"), anyString())).thenReturn(saved);
		when(resolver.resolve(any()))
			.thenReturn(new Resolution(snapshot, "server-only-token", Instant.now().plusSeconds(60)));
		when(executions.execute(any())).thenReturn(result(RunStatus.SUCCEEDED));
	}

	ExecuteResult result(RunStatus status) {
		return new ExecuteResult("10", status, status == RunStatus.SUCCEEDED ? "{\"answer\":\"ok\"}" : null, null,
				List.of(), "2", null, null, status == RunStatus.UNKNOWN ? ErrorCode.RESULT_UNKNOWN : null);
	}

	MarketSkillRunController.RunRequest request(String mode) throws Exception {
		return new MarketSkillRunController.RunRequest("skill-1", "request", "2", "9",
				json.readTree("{\"query\":\"client\",\"response_mode\":\"" + mode + "\"}"));
	}

	@Test
	void serverResolvedIdentityInputAndTokenAreUsedWithoutLeakingCredentials() throws Exception {
		var response = new MockHttpServletResponse();
		controller.run(88L, request("blocking"), response);
		var request = org.mockito.ArgumentCaptor.forClass(ExecuteRequest.class);
		verify(executions).execute(request.capture());
		assertEquals("7", request.getValue().identity().tenantId());
		assertEquals("3", request.getValue().identity().actorId());
		assertEquals("market-skill:99", request.getValue().identity().correlationId());
		assertEquals("{\"query\":\"authorized\"}", request.getValue().inputJson());
		assertEquals("server-only-token", request.getValue().resolvedSnapshotToken());
		assertFalse(response.getContentAsString().contains("server-only-token"));
		assertFalse(response.getContentAsString().contains("private instruction"));
		assertEquals("SUCCEEDED", json.readTree(response.getContentAsString()).path("data").path("status").asText());
	}

	@Test
	void staleSelectionNeverStartsExecution() throws Exception {
		var request = new MarketSkillRunController.RunRequest("skill-1", "request", "2", "8",
				json.readTree("{\"query\":\"q\"}"));
		assertThrows(IllegalArgumentException.class, () -> controller.run(88L, request, new MockHttpServletResponse()));
		verifyZeroInteractions(executions);
	}

	@Test void durableUnknownIsAVisibleTerminalOutcomeNotAnAutomaticRetry() throws Exception {
        when(executions.execute(any())).thenThrow(new SkillExecutionFailure(result(RunStatus.UNKNOWN),new IllegalStateException("connection lost")));
        var response=new MockHttpServletResponse();controller.run(88L,request("streaming"),response);
        assertTrue(response.getContentAsString().startsWith("event: skill_result\n"));assertTrue(response.getContentAsString().contains("UNKNOWN"));verify(executions,times(1)).execute(any());
    }

	@Test
	void replayCanReturnOutcomeWithoutANewTransportStream() throws Exception {
		var response = new MockHttpServletResponse();
		controller.run(88L, request("streaming"), response);
		assertTrue(response.getContentAsString().contains("event: skill_result"));
		assertTrue(response.getContentAsString().contains("SUCCEEDED"));
	}

}
