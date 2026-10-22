package com.deepdimen.matrix.assistant.provider.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.Purpose;
import com.deepdimen.matrix.assistant.provider.AssistantProviderRegistry;
import com.deepdimen.matrix.assistant.provider.security.AssistantInternalAuthProperties;
import com.deepdimen.matrix.assistant.provider.security.AssistantInternalRequestHasher;
import com.deepdimen.matrix.assistant.provider.security.DenyAssistantInternalCredentialService;
import com.deepdimen.matrix.assistant.provider.security.HmacAssistantInternalCredentialService;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.fasterxml.jackson.databind.ObjectMapper;

class InnerAssistantProviderControllerSecurityTest {

	private static final ProviderId PROVIDER = ProviderId.of("FIFTH");

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	private final AssistantInternalRequestHasher hasher = new AssistantInternalRequestHasher(objectMapper);

	@Test
	void hmacEndpointIsPermitAllAtPlatformLayerWithoutTrustedFromHeader() {
		Inner inner = InnerAssistantProviderController.class.getAnnotation(Inner.class);

		assertFalse(inner.value());
	}

	@Test
	void forgedExternalCredentialsAreRejectedBeforeProviderInvocation() {
		CountingProvider provider = new CountingProvider();
		InnerAssistantProviderController controller = new InnerAssistantProviderController(
				new AssistantProviderRegistry(List.of(provider)), new DenyAssistantInternalCredentialService(), hasher,
				"matrix-knowledge");

		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> controller.search(PROVIDER.value(), "Bearer forged", "forged", AssistantContextContract.VERSION,
						query(identity("11"))));

		assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
		assertEquals(0, provider.queryCount);
	}

	@Test
	void bodyIdentityCannotOverrideSignedDelegation() {
		CountingProvider provider = new CountingProvider();
		HmacAssistantInternalCredentialService credentials = credentials();
		InnerAssistantProviderController controller = new InnerAssistantProviderController(
				new AssistantProviderRegistry(List.of(provider)), credentials, hasher, "matrix-knowledge");
		ProviderQuery forgedBody = query(identity("11"));
		var signed = credentials.issue(identity("22"), forgedBody.runId(), "matrix-knowledge", Purpose.PROVIDER_SEARCH,
				hasher.hash(forgedBody));

		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> controller.search(PROVIDER.value(), "Bearer " + signed.serviceBearerToken(),
						signed.delegationToken(), AssistantContextContract.VERSION, forgedBody));

		assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
		assertEquals(0, provider.queryCount);
	}

	private HmacAssistantInternalCredentialService credentials() {
		AssistantInternalAuthProperties properties = new AssistantInternalAuthProperties();
		properties.setSecret("test-only-secret-material-with-at-least-32-bytes");
		properties.setTtl(Duration.ofMinutes(3));
		properties.setWorkloadId("matrix-discover");
		return new HmacAssistantInternalCredentialService(objectMapper, properties);
	}

	private ProviderQuery query(IdentityEnvelope identity) {
		return new ProviderQuery(AssistantContextContract.VERSION, "request-1", "run-1", identity.correlationId(),
				identity, "hello", null, null, List.of());
	}

	private IdentityEnvelope identity(String tenantId) {
		return new IdentityEnvelope(tenantId, "7", "3", List.of("3"), List.of(), List.of(), "corr-1",
				"ASSISTANT_CONTEXT");
	}

	private static final class CountingProvider implements AssistantContextProvider {

		private int queryCount;

		@Override
		public ProviderDescriptor describe() {
			return new ProviderDescriptor(AssistantContextContract.VERSION, PROVIDER, "Fifth", "test", "generic",
					new ProviderCapabilities(true, true, true, true, true, false, false, List.of(), List.of()),
					new ProviderLimits(20, 12000, 3000));
		}

		@Override
		public ProviderResult query(ProviderQuery query) {
			queryCount++;
			return new ProviderResult(AssistantContextContract.VERSION, PROVIDER, ProviderStatus.NO_MATCH,
					Instant.now(), 0, 0, false, List.of(), List.of());
		}

	}

}
