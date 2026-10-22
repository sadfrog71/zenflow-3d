package com.deepdimen.matrix.assistant.session;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.SourceMode;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.CreateSessionRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SendMessageRequest;
import com.deepdimen.matrix.assistant.run.AssistantSourceSelectionNormalizer;

class AssistantSessionSourceSelectionTest {

	private final IdentityEnvelope identity = new IdentityEnvelope("tenant-1", "user-1", "org-1", List.of("org-1"),
			List.of(), List.of(), "correlation-1", "assistant");

	private final AssistantSourceSelectionNormalizer normalizer = new AssistantSourceSelectionNormalizer();

	@Test
	void creatingSessionWithEmptyScopesKeepsModelOnlySelection() {
		AssistantSessionService service = new AssistantSessionService(new InMemoryAssistantSessionRepository(), null,
				null);

		var session = assertDoesNotThrow(
				() -> service.create(identity, new CreateSessionRequest("新对话", List.of(), "/todo")));

		assertTrue(session.activeScopes().isEmpty());
	}

	@Test
	void blockingRequestPreservesNullAndEmptyScopeSemantics() {
		SendMessageRequest inherited = new SendMessageRequest("message-1", "hello", null, null, List.of());
		SendMessageRequest modelOnly = new SendMessageRequest("message-2", "hello", List.of(), null, List.of());

		assertNull(inherited.enabledScopes());
		assertTrue(modelOnly.enabledScopes().isEmpty());
	}

	@Test
	void legacyModePreservesNullEmptyAndExplicitProviderSemantics() {
		List<ProviderId> active = List.of(ProviderId.KNOWLEDGE, ProviderId.WORK);

		var inherited = normalizer.normalize(null, null, active);
		var modelOnly = normalizer.normalize(null, List.of(), active);
		var explicit = normalizer.normalize(null, List.of(ProviderId.WORK), active);

		assertEquals(SourceMode.LEGACY, inherited.sourceMode());
		assertEquals(active, inherited.providerIds());
		assertTrue(!inherited.providerSelectionExplicit());
		assertEquals(List.of(), modelOnly.providerIds());
		assertTrue(modelOnly.providerSelectionExplicit());
		assertEquals(List.of(ProviderId.WORK), explicit.providerIds());
		assertTrue(explicit.providerSelectionExplicit());
	}

	@Test
	void explicitModesRejectConflictingProviderCaps() {
		List<ProviderId> active = List.of(ProviderId.KNOWLEDGE);

		assertEquals("SOURCE_MODE_CONFLICT",
				assertThrows(IllegalArgumentException.class,
						() -> normalizer.normalize(SourceMode.AUTO, List.of(ProviderId.WORK), active))
					.getMessage());
		assertEquals("SOURCE_MODE_CONFLICT",
				assertThrows(IllegalArgumentException.class,
						() -> normalizer.normalize(SourceMode.CAP, List.of(), active))
					.getMessage());
		assertEquals("SOURCE_MODE_CONFLICT",
				assertThrows(IllegalArgumentException.class,
						() -> normalizer.normalize(SourceMode.NONE, List.of(ProviderId.WORK), active))
					.getMessage());
	}

	@Test
	void explicitModesProduceOnlyTheirAllowedSelectionShape() {
		List<ProviderId> active = List.of(ProviderId.KNOWLEDGE, ProviderId.WORK);

		var automatic = normalizer.normalize(SourceMode.AUTO, null, active);
		var capped = normalizer.normalize(SourceMode.CAP,
				List.of(ProviderId.WORK, ProviderId.WORK, ProviderId.KNOWLEDGE), active);
		var none = normalizer.normalize(SourceMode.NONE, List.of(), active);

		assertEquals(active, automatic.providerIds());
		assertTrue(!automatic.providerSelectionExplicit());
		assertEquals(List.of(ProviderId.WORK, ProviderId.KNOWLEDGE), capped.providerIds());
		assertTrue(capped.providerSelectionExplicit());
		assertEquals(List.of(), none.providerIds());
		assertTrue(none.providerSelectionExplicit());
	}

}
