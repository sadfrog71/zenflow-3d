package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.*;
import com.deepdimen.matrix.skill.api.AgentSkillRequestReader;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;

class KnowledgeMarketSkillPromptAdapterTest {

	final KnowledgeSkillResources resources = mock(KnowledgeSkillResources.class);

	final KnowledgeSkillGeneration generation = mock(KnowledgeSkillGeneration.class);

	final ObjectProvider<AgentSkillRequestReader> readers = mock(ObjectProvider.class);

	final AgentSkillRequestReader reader = mock(AgentSkillRequestReader.class);

	final ObjectProvider<com.deepdimen.matrix.skill.api.AgentSkillStreamPort> streams = mock(ObjectProvider.class);

	final com.deepdimen.matrix.skill.api.AgentSkillStreamPort stream = mock(
			com.deepdimen.matrix.skill.api.AgentSkillStreamPort.class);

	final ObjectProvider<com.deepdimen.matrix.skill.api.AgentSkillResultContextReader> contexts = mock(
			ObjectProvider.class);

	final Runnable guard = mock(Runnable.class);

	final KnowledgeMarketSkillPromptAdapter adapter = new KnowledgeMarketSkillPromptAdapter(resources, generation,
			readers, new ObjectMapper(), streams, contexts);

	final Identity identity = new Identity("7", "3", "3", "market-skill:99");

	final Consumer consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "key");

	final ResolvedSkillSnapshot release = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
			new ExecutorRef("PROMPT", "KNOWLEDGE", "77", "fixed"), "{}", "{}", "rules", List.of(), "{}", List.of());

	final String input = "{\"query\":\"question\",\"response_mode\":\"streaming\"}";

	@BeforeEach
	void setup() {
		when(readers.getIfAvailable()).thenReturn(reader);
		when(streams.getIfAvailable()).thenReturn(stream);
		when(stream.open(any(), any(), any(), any(), any(), any())).thenReturn(delta -> { });
		when(reader.authorizeInput(identity, consumer, "99", input)).thenReturn(input);
		when(generation.generate(eq("9"), eq(identity), eq(consumer), eq(release), eq("question"), any(), any(), any()))
			.thenAnswer(call -> {
				((Runnable)call.getArgument(5)).run();
				return new KnowledgeSkillGeneration.Answer("answer", "77", "fixed",
						new ProviderResult(null, ProviderId.KNOWLEDGE, ProviderStatus.OK, Instant.now(), 0, 0, false, List.of(), List.of()));
			});
	}

	@Test
	void usesAuthorizedRequestAndReturnsActualModelAndExecutorForExistingResultStore() throws Exception {
		var result = adapter.execute("9", identity, consumer, release, input, guard);
		assertEquals("77", result.modelId());
		assertEquals("fixed", result.executorVersion());
		assertEquals("answer", new ObjectMapper().readTree(result.outputJson()).path("answer").asText());
		verify(reader, times(3)).authorizeInput(identity, consumer, "99", input);
		verify(guard).run();
		verify(resources, times(2)).verify(identity, consumer, release);
	}

	@Test
	void policyReplyRetainsNoModelMarkerThroughMarketOutput() throws Exception {
		when(generation.generate(eq("9"), eq(identity), eq(consumer), eq(release), eq("question"), any(), any(), any()))
			.thenReturn(new KnowledgeSkillGeneration.Answer("请调整问题后再试", null, "fixed",
					new ProviderResult(null, ProviderId.KNOWLEDGE, ProviderStatus.FORBIDDEN_SCOPE, Instant.now(), 0, 0, false, List.of(), List.of())));
		var result = adapter.execute("9", identity, consumer, release, input, guard);
		assertNull(result.modelId());
		assertEquals(com.deepdimen.matrix.skill.api.SkillPromptInvocationPort.InvocationMode.FIXED_REPLY, result.invocationMode());
		var output = new ObjectMapper().readTree(result.outputJson());
		assertEquals("请调整问题后再试", output.path("answer").asText());
		assertEquals("INPUT_POLICY", output.path("response_reason").asText());
	}

	@Test
	void explicitReferenceIsReadAndRecheckedInsteadOfAcceptingClientHistory() {
		var contextReader = mock(com.deepdimen.matrix.skill.api.AgentSkillResultContextReader.class);
		when(contexts.getIfAvailable()).thenReturn(contextReader);
		var reference = new com.deepdimen.matrix.skill.api.AgentSkillResultContextReader.Context("old question",
				"old answer", "7", release.releaseId());
		when(contextReader.read(identity, consumer, release, "previous")).thenReturn(reference);
		String body = "{\"query\":\"question\",\"result_context_request_id\":\"previous\"}";
		when(reader.authorizeInput(identity, consumer, "99", body)).thenReturn(body);
		adapter.execute("9", identity, consumer, release, body, guard);
		verify(contextReader, times(3)).read(identity, consumer, release, "previous");
		verify(generation).generate(eq("9"), eq(identity), eq(consumer), eq(release), eq("question"), any(), any(),
				eq(reference));
	}

	@Test
	void missingBridgeOrUnauthorizedInputNeverGenerates() {
		when(readers.getIfAvailable()).thenReturn(null);
		assertThrows(IllegalArgumentException.class, () -> adapter.execute("9", identity, consumer, release, input, guard));
		when(readers.getIfAvailable()).thenReturn(reader);
		when(reader.authorizeInput(identity, consumer, "99", input)).thenThrow(new IllegalArgumentException("denied"));
		assertThrows(IllegalArgumentException.class, () -> adapter.execute("9", identity, consumer, release, input, guard));
		verifyZeroInteractions(generation);
	}

	@Test
	void unsupportedInputIsRejectedRatherThanSilentlyDropped() {
		for (String extra : List.of("\"conversation_id\":\"old\"", "\"files\":[{}]",
				"\"inputs\":{\"rule\":\"override\"}", "\"system_prompt\":\"override\"")) {
			String body = "{\"query\":\"question\"," + extra + "}";
			when(reader.authorizeInput(identity, consumer, "99", body)).thenReturn(body);
			assertThrows(IllegalArgumentException.class,
					() -> adapter.execute("9", identity, consumer, release, body, guard));
		}
		verifyZeroInteractions(generation);
	}

	@Test
	void lostAuthorizationAfterGenerationDoesNotReturnOutput() {
		when(reader.authorizeInput(identity, consumer, "99", input)).thenReturn(input, input)
			.thenThrow(new IllegalArgumentException("revoked"));
		assertThrows(IllegalArgumentException.class, () -> adapter.execute("9", identity, consumer, release, input, guard));
	}

}
