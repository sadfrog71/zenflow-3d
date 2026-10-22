package com.deepdimen.matrix.discover.assistant.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.InternalCredentials;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.PlatformStatus;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderDefinition;
import com.deepdimen.matrix.assistant.provider.security.AssistantInternalRequestHasher;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

class AssistantProviderCloudIntegrationTest {

	private static final List<ProviderId> PROVIDERS = List.of(ProviderId.KNOWLEDGE, ProviderId.WORK,
			ProviderId.INFORMATION, ProviderId.CAPSULE);

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Test
	void allFourProvidersUseTheGenericProxyAndCapsuleIsHostedByMatrixWork() {
		List<ClientRequest> requests = new ArrayList<>();
		Map<String, List<ProviderDescriptor>> descriptors = Map.of("matrix-knowledge",
				List.of(descriptor(ProviderId.KNOWLEDGE)), "matrix-work",
				List.of(descriptor(ProviderId.WORK), descriptor(ProviderId.INFORMATION), descriptor(ProviderId.CAPSULE)));
		ExchangeFunction exchange = request -> respond(request, requests, descriptors);
		AssistantInternalCredentialService credentials = mock(AssistantInternalCredentialService.class);
		when(credentials.issue(any(), anyString(), anyString(), any(), anyString()))
			.thenReturn(new InternalCredentials("service-token", "delegation-token"));
		IdentityEnvelope identity = new IdentityEnvelope("11", "7", "3", List.of("3"), List.of(), List.of(),
				"cloud-provider-test", "ASSISTANT_CONTEXT");

		for (ProviderId providerId : PROVIDERS) {
			String serviceId = ProviderId.KNOWLEDGE.equals(providerId) ? "matrix-knowledge" : "matrix-work";
			RemoteAssistantContextProvider provider = new RemoteAssistantContextProvider(
					WebClient.builder().exchangeFunction(exchange), credentials,
					new AssistantInternalRequestHasher(objectMapper), definition(providerId, serviceId), identity, "run-1");
			assertEquals(providerId, provider.describe().providerId());
			assertEquals(providerId, provider.query(query(providerId, identity)).provider());
		}

		assertEquals(8, requests.size());
		assertEquals(6, requests.stream().filter(request -> "matrix-work".equals(request.url().getHost())).count());
		assertEquals(2,
				requests.stream().filter(request -> "matrix-knowledge".equals(request.url().getHost())).count());
		assertTrue(requests.stream()
			.anyMatch(request -> request.url().getPath().endsWith("/CAPSULE/search")
					&& "matrix-work".equals(request.url().getHost())));
		assertTrue(requests.stream().allMatch(request -> request.headers().containsKey("X-Assistant-Delegation")));
	}

	private Mono<ClientResponse> respond(ClientRequest request, List<ClientRequest> requests,
			Map<String, List<ProviderDescriptor>> descriptors) {
		requests.add(request);
		try {
			String json;
			if (request.url().getPath().endsWith("/inner/assistant/providers")) {
				json = objectMapper.writeValueAsString(descriptors.get(request.url().getHost()));
			}
			else {
				String[] parts = request.url().getPath().split("/");
				ProviderId providerId = ProviderId.of(parts[parts.length - 2]);
				json = objectMapper.writeValueAsString(new ProviderResult(AssistantContextContract.VERSION, providerId,
						ProviderStatus.NO_MATCH, Instant.now(), 0, 0, false, List.of(), List.of()));
			}
			return Mono.just(ClientResponse.create(HttpStatus.OK)
				.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.body(json)
				.build());
		}
		catch (Exception ex) {
			return Mono.error(ex);
		}
	}

	private ProviderDefinition definition(ProviderId providerId, String serviceId) {
		return new ProviderDefinition(providerId, serviceId, PlatformStatus.ACTIVE, true, true,
				DataClassification.SENSITIVE, "{}", null, null, 1, Instant.now(), Instant.now());
	}

	private ProviderDescriptor descriptor(ProviderId providerId) {
		return new ProviderDescriptor(AssistantContextContract.VERSION, providerId, providerId.value(), "test",
				"generic", new ProviderCapabilities(true, true, true, true, true, false, false, List.of(), List.of()),
				new ProviderLimits(20, 12000, 3000));
	}

	private ProviderQuery query(ProviderId providerId, IdentityEnvelope identity) {
		return new ProviderQuery(AssistantContextContract.VERSION, "request-" + providerId.value(), "run-1",
				identity.correlationId(), identity, "hello", null, null, List.of());
	}

}
