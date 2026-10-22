package com.deepdimen.matrix.assistant.orchestrator;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.*;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;
import com.deepdimen.matrix.assistant.capability.AssistantAnalysisSkillCatalog.AnalysisSkillSnapshot;
import com.deepdimen.matrix.assistant.generation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class NativeAssistantClarificationGatewayTest {

	@Test
	void transmitsSeparateContextOnceAndRejectsUnauthorizedCandidateBeforeModel() throws Exception {
		var mapper = new ObjectMapper();
		var captured = new AtomicReference<AssistantModelRequest>();
		var count = new AtomicInteger();
		var model = new ResolvedAssistantModel("v1", "model", "Test", "local",
				new ModelCapabilities(false, 8192, 1024, true),
				new AssistantEgress(DeploymentDomain.INTERNAL, "local", true, DataClassification.SENSITIVE, "test"));
		AssistantModelGateway gateway = new AssistantModelGateway() {
			public ResolvedAssistantModel resolve(ModelResolutionRequest request) {
				return model;
			}

			public Flux<ModelChunk> stream(AssistantModelRequest request) {
				count.incrementAndGet();
				captured.set(request);
				return Flux.just(new ModelChunk(request.runId(), 0,
						"{\"disposition\":\"AMBIGUOUS\",\"providers\":[],\"workflows\":[],\"reasonCodes\":[]}", false,
						null), new ModelChunk(request.runId(), 1, "", true, "stop"));
			}
		};
		var nativeGateway = new NativeAssistantStructuredPlanningGateway((run, identity, stream, classification,
				contains) -> new AssistantNativeProfileResolver.ResolvedNativeProfile("profile", "Test", null, model),
				gateway, mapper, 1000);
		for (String name : List.of("semanticRoutingEnabled", "candidateRetrievalEnabled")) {
			var f = NativeAssistantStructuredPlanningGateway.class.getDeclaredField(name);
			f.setAccessible(true);
			f.setBoolean(nativeGateway, true);
		}
		var identity = new IdentityEnvelope("t", "u", "org", List.of(), List.of(), List.of(), "corr", "assistant");
		var catalog = new FrozenCapabilityCatalog(
				List.of(new ProviderCapabilitySnapshot("KNOWLEDGE", "资料", "资料查询", 1, "d")), List.of(), "catalog",
				Instant.EPOCH);
		var skill = new AnalysisSkillSnapshot("s", 1, "v1", "s", "instruction", "d", "d", Map.of(), "d", Map.of(),
				"schema", Set.of());
		var context = new AssistantStructuredPlanningGateway.ClarificationContext("origin", "查询昨天资料", "需要哪个时间范围？",
				List.of(new ClarificationCandidate(ExecutionStepType.MATRIX_PROVIDER, "KNOWLEDGE", null, "资料")));
		assertTrue(nativeGateway.suggest("run", identity, "改为今天", SourceMode.AUTO, List.of(), catalog, skill, context)
			.isPresent());
		assertEquals(1, count.get());
		var input = mapper.readTree(captured.get().userMessage());
		assertEquals("改为今天", input.path("question").asText());
		assertEquals("查询昨天资料", input.path("clarificationContext").path("originalQuestion").asText());
		assertEquals("origin", input.path("clarificationContext").path("originRunId").asText());
		var bad = new AssistantStructuredPlanningGateway.ClarificationContext("origin", "查询资料", "哪类？",
				List.of(new ClarificationCandidate(ExecutionStepType.MATRIX_PROVIDER, "FOREIGN", null, "秘密")));
		assertTrue(nativeGateway.suggest("other", identity, "继续", SourceMode.AUTO, List.of(), catalog, skill, bad)
			.isEmpty());
		assertEquals(1, count.get());
	}

}
