package com.deepdimen.matrix.discover.assistant.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.AssistantInternalCredentialService;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.InternalCredentials;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.PlatformStatus;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderDefinition;
import com.deepdimen.matrix.assistant.provider.security.AssistantInternalRequestHasher;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

class RemoteAssistantContextProviderTest {

	private static final ProviderId PROVIDER = ProviderId.of("FIFTH");

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Test
	void proxyUsesLogicalLoadBalancedHostAndSecuredHeaders() throws Exception {
		List<ClientRequest> requests = new ArrayList<>();
		ProviderDescriptor descriptor = descriptor();
		ProviderResult result = new ProviderResult(AssistantContextContract.VERSION, PROVIDER, ProviderStatus.NO_MATCH,
				Instant.now(), 0, 0, false, List.of(), List.of());
		ExchangeFunction exchange = request -> {
			requests.add(request);
			try {
				String json = request.url().getPath().endsWith("/search") ? objectMapper.writeValueAsString(result)
						: objectMapper.writeValueAsString(List.of(descriptor));
				return Mono.just(ClientResponse.create(HttpStatus.OK)
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.body(json)
					.build());
			}
			catch (Exception ex) {
				return Mono.error(ex);
			}
		};
		AssistantInternalCredentialService credentials = mock(AssistantInternalCredentialService.class);
		when(credentials.issue(any(), anyString(), anyString(), any(), anyString()))
			.thenReturn(new InternalCredentials("service-token", "delegation-token"));
		IdentityEnvelope identity = identity();
		RemoteAssistantContextProvider remote = new RemoteAssistantContextProvider(
				WebClient.builder().exchangeFunction(exchange), credentials,
				new AssistantInternalRequestHasher(objectMapper), definition(), identity, "run-1");

		assertEquals(PROVIDER, remote.describe().providerId());
		assertEquals(ProviderStatus.NO_MATCH, remote.query(query(identity)).status());

		assertEquals(2, requests.size());
		for (ClientRequest request : requests) {
			assertEquals("matrix-fifth", request.url().getHost());
			assertEquals("Bearer service-token",
					request.headers().getFirst(AssistantInternalAuthContract.SERVICE_AUTHORIZATION_HEADER));
			assertEquals("delegation-token", request.headers().getFirst("X-Assistant-Delegation"));
			assertNull(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
			assertNull(request.headers().getFirst("from"));
			assertEquals("run-1", request.headers().getFirst("X-Assistant-Run-ID"));
			assertTrue(request.url().getPath().startsWith("/inner/assistant/providers"));
		}
	}

	private ProviderDefinition definition() {
		return new ProviderDefinition(PROVIDER, "matrix-fifth", PlatformStatus.ACTIVE, true, true,
				DataClassification.SENSITIVE, "{}", null, null, 1, Instant.now(), Instant.now());
	}

	private ProviderDescriptor descriptor() {
		return new ProviderDescriptor(AssistantContextContract.VERSION, PROVIDER, "Fifth", "test", "generic",
				new ProviderCapabilities(true, true, true, true, true, false, false, List.of(), List.of()),
				new ProviderLimits(20, 12000, 3000));
	}

	private ProviderQuery query(IdentityEnvelope identity) {
		return new ProviderQuery(AssistantContextContract.VERSION, "request-1", "run-1", identity.correlationId(),
				identity, "hello", null, null, List.of());
	}

	private IdentityEnvelope identity() {
		return new IdentityEnvelope("11", "7", "3", List.of("3"), List.of(), List.of(), "corr-1", "ASSISTANT_CONTEXT");
	}

}
