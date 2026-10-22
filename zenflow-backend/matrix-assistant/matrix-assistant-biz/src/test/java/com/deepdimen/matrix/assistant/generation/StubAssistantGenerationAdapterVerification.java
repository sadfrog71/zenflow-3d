package com.deepdimen.matrix.assistant.generation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.FactType;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantContextPack;
import com.deepdimen.matrix.assistant.generation.AssistantGenerationAdapter.GenerationRequest;

/** Offline verification for the opt-in deterministic local model seam. */
public final class StubAssistantGenerationAdapterVerification {

	private StubAssistantGenerationAdapterVerification() {
	}

	public static void main(String[] args) {
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
			.generate(new GenerationRequest(
					"run-1", "session-1", "message-1", new IdentityEnvelope("tenant", "user", "org", List.of("org"),
							List.of(), List.of(), "corr", "ASSISTANT_CONTEXT"),
					"查资讯", pack, List.of("information-summary")));
		require(result.available(), "stub should answer when cited context is available");
		require(result.content().contains("[information:1]"), "stub answer must preserve citation");
		require(result.trace().executionPlan().name().equals("PRIMARY_ONLY"), "stub trace should be primary-only");

		ProviderResult unavailable = ProviderResult.unavailable(ProviderId.KNOWLEDGE, "stub");
		AssistantContextPack emptyPack = new AssistantContextPack("pack-2", "session-2", "message-2", now,
				now.plusSeconds(1800), "fingerprint", List.of(unavailable), List.of(), List.of());
		AssistantGenerationAdapter.GenerationResult emptyResult = new StubAssistantGenerationAdapter()
			.generate(new GenerationRequest("run-2", "session-2", "message-2", null, "查知识库", emptyPack, List.of()));
		require(!emptyResult.available(), "stub must refuse to answer without cited context");
		System.out.println("stub-assistant-generation: PASS");
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

}
