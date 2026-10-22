package com.deepdimen.matrix.assistant.provider.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.InternalAuthenticationException;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.Purpose;
import com.fasterxml.jackson.databind.ObjectMapper;

class HmacAssistantInternalCredentialServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

	private static final String HASH = "a".repeat(64);

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Test
	void validPairReconstructsIdentityAndCannotBeReplayed() {
		HmacAssistantInternalCredentialService service = service(NOW);
		var signed = service.issue(identity("11"), "run-1", "matrix-knowledge", Purpose.PROVIDER_SEARCH, HASH);

		var claims = service.verify("Bearer " + signed.serviceBearerToken(), signed.delegationToken(),
				"matrix-knowledge", Purpose.PROVIDER_SEARCH, "run-1", HASH);

		assertEquals(identity("11"), claims.identity());
		assertThrows(InternalAuthenticationException.class,
				() -> service.verify("Bearer " + signed.serviceBearerToken(), signed.delegationToken(),
						"matrix-knowledge", Purpose.PROVIDER_SEARCH, "run-1", HASH));
	}

	@Test
	void audiencePurposeRunAndBodyHashAreCryptographicallyBound() {
		assertRejected("matrix-work", Purpose.PROVIDER_SEARCH, "run-1", HASH);
		assertRejected("matrix-knowledge", Purpose.MODEL_STREAM, "run-1", HASH);
		assertRejected("matrix-knowledge", Purpose.PROVIDER_SEARCH, "run-2", HASH);
		assertRejected("matrix-knowledge", Purpose.PROVIDER_SEARCH, "run-1", "b".repeat(64));
	}

	@Test
	void expiredDelegationIsRejected() {
		HmacAssistantInternalCredentialService issuer = service(NOW);
		var signed = issuer.issue(identity("11"), "run-1", "matrix-knowledge", Purpose.PROVIDER_SEARCH, HASH);
		HmacAssistantInternalCredentialService later = service(NOW.plus(Duration.ofMinutes(4)));

		assertThrows(InternalAuthenticationException.class, () -> later.verify("Bearer " + signed.serviceBearerToken(),
				signed.delegationToken(), "matrix-knowledge", Purpose.PROVIDER_SEARCH, "run-1", HASH));
	}

	private void assertRejected(String audience, Purpose purpose, String runId, String hash) {
		HmacAssistantInternalCredentialService service = service(NOW);
		var signed = service.issue(identity("11"), "run-1", "matrix-knowledge", Purpose.PROVIDER_SEARCH, HASH);
		assertThrows(InternalAuthenticationException.class, () -> service
			.verify("Bearer " + signed.serviceBearerToken(), signed.delegationToken(), audience, purpose, runId, hash));
	}

	private HmacAssistantInternalCredentialService service(Instant instant) {
		AssistantInternalAuthProperties properties = new AssistantInternalAuthProperties();
		properties.setSecret("test-only-secret-material-with-at-least-32-bytes");
		properties.setIssuer("matrix-assistant");
		properties.setWorkloadId("matrix-discover");
		properties.setTtl(Duration.ofMinutes(3));
		return new HmacAssistantInternalCredentialService(objectMapper, properties,
				Clock.fixed(instant, ZoneOffset.UTC));
	}

	private IdentityEnvelope identity(String tenantId) {
		return new IdentityEnvelope(tenantId, "7", "3", List.of("3"), List.of("5"), List.of("9"), "corr-1",
				"ASSISTANT_CONTEXT");
	}

}
