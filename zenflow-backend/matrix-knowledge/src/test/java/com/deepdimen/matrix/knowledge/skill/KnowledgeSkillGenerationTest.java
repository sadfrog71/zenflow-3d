package com.deepdimen.matrix.knowledge.skill;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.*;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;
import com.deepdimen.matrix.knowledge.assistant.LocalAssistantModelGateway;
import com.deepdimen.matrix.skill.api.SkillContract.*;

class KnowledgeSkillGenerationTest {

	final KnowledgeSkillResources resources = mock(KnowledgeSkillResources.class);

	final KnowledgeSkillRetrieval retrieval = mock(KnowledgeSkillRetrieval.class);

	final LocalAssistantModelGateway models = mock(LocalAssistantModelGateway.class);

	final Runnable guard = mock(Runnable.class);

	final KnowledgeSkillInputPolicy inputPolicy = mock(KnowledgeSkillInputPolicy.class);

	final KnowledgeSkillGeneration service = new KnowledgeSkillGeneration(resources, retrieval, models, inputPolicy);

	final Identity identity = new Identity("7", "3", "3", "market-skill:99");

	final Consumer consumer = new Consumer("MARKET_AGENT", "88", "CHAT", "key");

	final ResolvedSkillSnapshot release = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
			new ExecutorRef("PROMPT", "KNOWLEDGE", "77", "fixed"), "{}", "{}", "fixed rules", List.of(), "{}",
			List.of());

	@BeforeEach
	void setup() {
		var actor = new IdentityEnvelope("7", "3", null, List.of(), List.of(), List.of(), "market-skill:99",
				"MARKET_SKILL");
		when(resources.verify(identity, consumer, release)).thenReturn(new KnowledgeSkillResources.Capture(actor,
				new KnowledgeMarketConfigurationSnapshot("88", List.of("31"), "local", "qwen", "fixed", "{}"), "77",
				List.of(), "fixed"));
		var citation = new Citation("citation-1", ProviderId.KNOWLEDGE, "document", "31", "v1", "document", "doc",
				Map.of());
		var fragment = new ContextFragment("f1", ProviderId.KNOWLEDGE, "document", "31", "v1", FactType.SOURCE_EVIDENCE,
				"document", "source text", Instant.now(), Instant.now(), "hash", citation);
		when(retrieval.query(identity, consumer, release, "question")).thenReturn(new ProviderResult(null,
				ProviderId.KNOWLEDGE, ProviderStatus.OK, Instant.now(), 1, 0, false, List.of(fragment), List.of()));
		when(models.resolve(any())).thenReturn(new ResolvedAssistantModel("assistant-model/v1", "77", "qwen", "local",
				new ModelCapabilities(true, 8192, 1024, true),
				new AssistantEgress(DeploymentDomain.INTERNAL, "local", true, DataClassification.SENSITIVE, "none")));
		when(models.stream(any())).thenReturn(
				Flux.just(new ModelChunk("9", 0, "answer", false, ""), new ModelChunk("9", 1, "", true, "STOP")));
	}

	@Test
	void reusesGatewayWithFixedInstructionsEvidenceAndModelAndChecksAfterCompletion() {
		var answer = service.generate("9", identity, consumer, release, "question", guard);
		assertEquals("answer", answer.answer());
		assertEquals("77", answer.modelId());
		var request = ArgumentCaptor.forClass(AssistantModelRequest.class);
		verify(models).stream(request.capture());
		assertEquals("fixed rules", request.getValue().systemPrompt());
		assertEquals("source text", request.getValue().contextFragments().get(0).content());
		assertEquals(DataClassification.SENSITIVE, request.getValue().contextFragments().get(0).dataClassification());
		verify(resources, times(3)).verify(identity, consumer, release);
		var order = inOrder(inputPolicy, guard, retrieval, models);
		order.verify(inputPolicy).fixedReply(identity, consumer, release, "question");
		order.verify(guard).run();
		order.verify(retrieval).query(identity, consumer, release, "question");
		order.verify(models).resolve(any());
		order.verify(models).stream(any());
	}

	@Test
	void incompleteOutOfOrderAndPostTerminalStreamsCannotSucceed() {
		for (var stream : List.of(Flux.just(new ModelChunk("9", 0, "partial", false, "")),
				Flux.just(new ModelChunk("9", 1, "wrong sequence", false, "")),
				Flux.just(new ModelChunk("9", 0, "answer", true, "STOP"), new ModelChunk("9", 1, "late", false, "")),
				Flux.just(new ModelChunk("other", 0, "wrong run", true, "STOP")),
				Flux.just(new ModelChunk("9", 0, "truncated", true, "LENGTH")))) {
			when(models.stream(any())).thenReturn(stream);
			assertThrows(IllegalStateException.class,
					() -> service.generate("9", identity, consumer, release, "question", guard));
		}
	}

	@Test
	void unavailableRetrievalDoesNotCallModel() {
		when(retrieval.query(identity, consumer, release, "question"))
			.thenReturn(ProviderResult.unavailable(ProviderId.KNOWLEDGE, "offline"));
		assertThrows(IllegalStateException.class, () -> service.generate("9", identity, consumer, release, "question", guard));
		verify(models, never()).resolve(any());
		verify(models, never()).stream(any());
	}

	@Test
	void configurationDriftAfterModelCompletionRejectsAnswer() {
		when(models.stream(any())).thenAnswer(call -> {
			when(resources.verify(identity, consumer, release)).thenThrow(new IllegalArgumentException("EXECUTOR_VERSION_DRIFT"));
			return Flux.just(new ModelChunk("9", 0, "answer", true, "STOP"));
		});
		assertThrows(IllegalArgumentException.class, () -> service.generate("9", identity, consumer, release, "question", guard));
	}

	@Test
	void blockedInputCannotReachAnyExternalInvocation() {
		doThrow(new IllegalArgumentException("KNOWLEDGE_SENSITIVE_INPUT")).when(inputPolicy)
			.fixedReply(identity, consumer, release, "question");
		assertThrows(IllegalArgumentException.class,
				() -> service.generate("9", identity, consumer, release, "question", guard));
		verifyZeroInteractions(guard);
		verify(retrieval, never()).query(any(), any(), any(), any());
		verify(models, never()).stream(any());
	}

	@Test
	void noMatchUsesConfiguredReplyWithoutClaimingOrCallingModel() {
		var dataset = new com.deepdimen.matrix.knowledge.entity.AiDatasetEntity();
		dataset.setEmptyLlmFlag(com.deepdimen.matrix.common.core.constant.enums.YesNoEnum.NO.getCode());
		dataset.setEmptyDesc("没有匹配的资料");
		when(resources.resolveRetrieval(identity, consumer, release, 31L))
			.thenReturn(new KnowledgeSkillResources.Retrieval(dataset, null, null, null, "fixed"));
		when(retrieval.query(identity, consumer, release, "question")).thenReturn(new ProviderResult(null,
				ProviderId.KNOWLEDGE, ProviderStatus.NO_MATCH, Instant.now(), 0, 0, false, List.of(), List.of()));
		var answer = service.generate("9", identity, consumer, release, "question", guard);
		assertEquals("没有匹配的资料", answer.answer());
		assertNull(answer.modelId());
		verify(models, never()).resolve(any());
		verify(models, never()).stream(any());
		dataset.setEmptyDesc(" ");
		assertThrows(IllegalArgumentException.class,
				() -> service.generate("9", identity, consumer, release, "question", guard));
	}

	@Test
	void noMatchAllowedFallbackStillUsesPinnedGovernedModel() {
		var dataset = new com.deepdimen.matrix.knowledge.entity.AiDatasetEntity();
		dataset.setEmptyLlmFlag(com.deepdimen.matrix.common.core.constant.enums.YesNoEnum.YES.getCode());
		when(resources.resolveRetrieval(identity, consumer, release, 31L))
			.thenReturn(new KnowledgeSkillResources.Retrieval(dataset, null, null, null, "fixed"));
		when(retrieval.query(identity, consumer, release, "question")).thenReturn(new ProviderResult(null,
				ProviderId.KNOWLEDGE, ProviderStatus.NO_MATCH, Instant.now(), 0, 0, false, List.of(), List.of()));
		assertEquals("77", service.generate("9", identity, consumer, release, "question", guard).modelId());
		var request = ArgumentCaptor.forClass(AssistantModelRequest.class);
		verify(models).stream(request.capture());
		assertTrue(request.getValue().contextFragments().isEmpty());
		assertEquals("fixed rules", request.getValue().systemPrompt());
	}

	@Test
	void deliversPartialTextBeforeModelCompletesAndPropagatesDisconnectedSink() {
		var deltas = new java.util.ArrayList<String>();
		when(models.stream(any()))
			.thenReturn(Flux.just(new ModelChunk("9", 0, "partial", false, "")).concatWith(Flux.defer(() -> {
				assertEquals(List.of("partial"), deltas);
				return Flux.just(new ModelChunk("9", 1, "", true, "STOP"));
			})));
		assertEquals("partial",
				service.generate("9", identity, consumer, release, "question", guard, deltas::add).answer());
		assertThrows(IllegalStateException.class,
				() -> service.generate("9", identity, consumer, release, "question", guard, delta -> {
					throw new IllegalStateException("disconnected");
				}));
	}

	@Test
	void sensitiveConfiguredReplySkipsRetrievalAndModelAndReportsNoInvocation() {
		when(inputPolicy.fixedReply(identity, consumer, release, "question")).thenReturn("请调整问题后再试");
		var observer = mock(com.deepdimen.matrix.skill.api.AgentSkillStreamPort.Observer.class);
		var answer = service.generate("9", identity, consumer, release, "question", guard, observer);
		assertEquals("请调整问题后再试", answer.answer());
		assertNull(answer.modelId());
		assertEquals(ProviderStatus.FORBIDDEN_SCOPE, answer.evidence().status());
		verify(guard).run();
		verifyZeroInteractions(observer);
		verify(retrieval, never()).query(any(), any(), any(), any());
		verify(models, never()).stream(any());
	}

	@Test
	void referencedResultIsSensitiveBusinessEvidenceNotSystemInstructions() {
		var reference = new com.deepdimen.matrix.skill.api.AgentSkillResultContextReader.Context("old question",
				"untrusted old answer", "7", release.releaseId());
		service.generate("9", identity, consumer, release, "question", guard, delta -> {
		}, reference);
		var request = ArgumentCaptor.forClass(AssistantModelRequest.class);
		verify(models).stream(request.capture());
		assertEquals("fixed rules", request.getValue().systemPrompt());
		assertTrue(request.getValue().history().isEmpty());
		var adopted = request.getValue()
			.contextFragments()
			.stream()
			.filter(value -> value.citationKey().equals("R1"))
			.findFirst()
			.orElseThrow();
		assertEquals(DataClassification.SENSITIVE, adopted.dataClassification());
		assertEquals("7", adopted.resourceRef().get("skillRunId"));
		assertTrue(adopted.content().contains("untrusted old answer"));
	}

	@Test
	void guardFailureStopsRetrievalAndGeneration() {
		doThrow(new IllegalArgumentException("revoked")).when(guard).run();
		assertThrows(IllegalArgumentException.class,
				() -> service.generate("9", identity, consumer, release, "question", guard));
		verify(retrieval, never()).query(any(), any(), any(), any());
		verify(models, never()).stream(any());
	}

}
