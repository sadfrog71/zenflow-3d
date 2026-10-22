package com.deepdimen.matrix.assistant.generation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.FactType;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantContextPack;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.ExecutionPlan;
import com.deepdimen.matrix.assistant.generation.AssistantGenerationAdapter.GenerationRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StubAssistantGenerationAdapterTest {

	@Test
	void returnsTraceWithoutInventingUncitedContent() {
		Instant now = Instant.parse("2026-08-03T00:00:00Z");
		ContextFragment fragment = new ContextFragment("information-summary:1", ProviderId.INFORMATION,
				"INFORMATION_ITEM", "1", "v1", FactType.AI_SUMMARY, "资讯", "摘要", now, now, "sha256:1",
				new AssistantContextContract.Citation("information:1", ProviderId.INFORMATION, "INFORMATION_ITEM", "1",
						"v1", "INTERNAL", "InformationDetail", Map.of("id", "1")));
		ProviderResult providerResult = new ProviderResult(AssistantContextContract.VERSION, ProviderId.INFORMATION,
				ProviderStatus.OK, now, 1, 0, false, List.of(fragment), List.of());
		AssistantContextPack pack = new AssistantContextPack("pack-1", "session-1", "message-1", now,
				now.plusSeconds(1800), "fingerprint", List.of(providerResult), List.of(fragment), List.of());

		AssistantGenerationAdapter.GenerationResult result = new StubAssistantGenerationAdapter()
			.generate(new GenerationRequest("run-1", "session-1", "message-1", null, "查资讯", pack, List.of("artifact")));

		assertTrue(result.available());
		assertTrue(result.content().contains("[information:1]"));
		assertEquals(ExecutionPlan.PRIMARY_ONLY, result.trace().executionPlan());
		assertEquals(List.of("artifact"), result.trace().activatedSkillCodes());
	}

	@Test
	void refusesToAnswerWithoutAuthorizedCitation() {
		Instant now = Instant.parse("2026-08-03T00:00:00Z");
		AssistantContextPack pack = new AssistantContextPack("pack-2", "session-2", "message-2", now,
				now.plusSeconds(1800), "fingerprint", List.of(ProviderResult.unavailable(ProviderId.KNOWLEDGE, "stub")),
				List.of(), List.of());

		AssistantGenerationAdapter.GenerationResult result = new StubAssistantGenerationAdapter()
			.generate(new GenerationRequest("run-2", "session-2", "message-2", null, "查知识库", pack, List.of()));

		assertFalse(result.available());
		assertEquals(ExecutionPlan.UNAVAILABLE, result.trace().executionPlan());
		assertEquals("STUB_CONTEXT_UNAVAILABLE", result.trace().errorCode());
	}

}
