package com.deepdimen.matrix.assistant.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ErrorCode;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.Citation;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.FactType;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.provider.AssistantProviderRegistry;

class AssistantContextOrchestratorTest {

	@Test
	void reportsAnUnknownProviderAsNotRegistered() {
		AssistantContextOrchestrator orchestrator = new AssistantContextOrchestrator(
				new AssistantProviderRegistry(List.of()));
		IdentityEnvelope identity = new IdentityEnvelope("tenant-1", "user-1", "org-1", List.of("org-1"), List.of(),
				List.of(), "correlation-1", "ASSISTANT_CONTEXT");

		var pack = orchestrator.buildContextPack(identity, "session-1", "message-1", "customer",
				List.of(ProviderId.of("CUSTOMER_SERVICE")), null);

		assertEquals(1, pack.providerResults().size());
		assertEquals(ProviderStatus.UNAVAILABLE, pack.providerResults().get(0).status());
		assertTrue(pack.providerResults()
			.get(0)
			.warnings()
			.stream()
			.anyMatch(warning -> warning.code() == ErrorCode.PROVIDER_NOT_REGISTERED));
	}

	@Test
	void providersRunConcurrentlyAndATimeoutDoesNotDiscardAnotherResult() {
		ProviderId slowId = ProviderId.of("SLOW_TEST");
		ProviderId fastId = ProviderId.of("FAST_TEST");
		AtomicBoolean fastCalled = new AtomicBoolean();
		AssistantContextProvider slow = provider(slowId, 50, () -> {
			try {
				Thread.sleep(500);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("interrupted", ex);
			}
		});
		AssistantContextProvider fast = provider(fastId, 50, () -> fastCalled.set(true));
		AssistantContextOrchestrator orchestrator = new AssistantContextOrchestrator(
				new AssistantProviderRegistry(List.of(slow, fast)));
		IdentityEnvelope identity = new IdentityEnvelope("tenant-1", "user-1", "org-1", List.of("org-1"), List.of(),
				List.of(), "correlation-1", "ASSISTANT_CONTEXT");

		long started = System.nanoTime();
		var pack = orchestrator.buildContextPack(identity, "session-1", "message-1", "query", List.of(slowId, fastId),
				null);
		long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

		assertEquals(List.of(slowId, fastId), pack.providerResults().stream().map(ProviderResult::provider).toList());
		assertEquals(ProviderStatus.UNAVAILABLE, pack.providerResults().get(0).status());
		assertEquals(ProviderStatus.NO_MATCH, pack.providerResults().get(1).status());
		assertTrue(fastCalled.get());
		assertTrue(elapsedMs < 400, "Provider timeout should bound the whole parallel stage, elapsed=" + elapsedMs);
	}

	@Test
	void selectedModulesSummaryBrowsesTheSelectedProviderWithoutAFalseKeyword() {
		ProviderId providerId = ProviderId.of("BROWSE_TEST");
		AtomicReference<String> receivedQuery = new AtomicReference<>();
		AssistantContextProvider browseProvider = new AssistantContextProvider() {
			@Override
			public ProviderDescriptor describe() {
				return new ProviderDescriptor(com.deepdimen.matrix.assistant.api.AssistantContextContract.VERSION,
						providerId, providerId.value(), "test", "generic",
						new ProviderCapabilities(true, true, true, true, true, false, false, List.of(), List.of()),
						new ProviderLimits(20, 12000, 3000));
			}

			@Override
			public ProviderResult query(ProviderQuery query) {
				receivedQuery.set(query.query());
				Citation citation = new Citation("citation-1", providerId, "TEST", "1", "v1", "route", "test",
						Map.of());
				ContextFragment fragment = new ContextFragment("fragment-1", providerId, "TEST", "1", "v1",
						FactType.SOURCE_EVIDENCE, "Selected source", "Available content", Instant.now(), Instant.now(),
						"hash", citation);
				return new ProviderResult(com.deepdimen.matrix.assistant.api.AssistantContextContract.VERSION,
						providerId, ProviderStatus.OK, Instant.now(), 1, 0, false, List.of(fragment), List.of());
			}
		};
		AssistantContextOrchestrator orchestrator = new AssistantContextOrchestrator(
				new AssistantProviderRegistry(List.of(browseProvider)));
		IdentityEnvelope identity = new IdentityEnvelope("tenant-1", "user-1", "org-1", List.of("org-1"), List.of(),
				List.of(), "correlation-1", "ASSISTANT_CONTEXT");

		var pack = orchestrator.buildContextPack(identity, "session-1", "message-1", "总结下这些模块的内容", List.of(providerId),
				null, List.of("请查看本地联调样例"));

		assertEquals("", receivedQuery.get());
		assertEquals(ProviderStatus.OK, pack.providerResults().get(0).status());
		assertEquals(1, pack.fragments().size());
	}

	private AssistantContextProvider provider(ProviderId providerId, long timeoutMs, Runnable queryAction) {
		return new AssistantContextProvider() {
			@Override
			public ProviderDescriptor describe() {
				return new ProviderDescriptor(com.deepdimen.matrix.assistant.api.AssistantContextContract.VERSION,
						providerId, providerId.value(), "test", "generic",
						new ProviderCapabilities(true, true, true, true, true, false, false, List.of(), List.of()),
						new ProviderLimits(20, 12000, timeoutMs));
			}

			@Override
			public ProviderResult query(ProviderQuery query) {
				queryAction.run();
				return new ProviderResult(com.deepdimen.matrix.assistant.api.AssistantContextContract.VERSION,
						providerId, ProviderStatus.NO_MATCH, Instant.now(), 0, 0, false, List.of(), List.of());
			}
		};
	}

}
