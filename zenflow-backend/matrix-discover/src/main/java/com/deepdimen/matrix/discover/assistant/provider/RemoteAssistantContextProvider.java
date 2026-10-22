package com.deepdimen.matrix.discover.assistant.provider;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolveRequest;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolution;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.AssistantInternalCredentialService;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.InternalCredentials;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.Purpose;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderDefinition;
import com.deepdimen.matrix.assistant.provider.security.AssistantInternalRequestHasher;

/**
 * Generic provider proxy. Its base URL is derived only from the trusted logical service
 * ID.
 */
public class RemoteAssistantContextProvider implements AssistantContextProvider {

	private static final ParameterizedTypeReference<List<ProviderDescriptor>> DESCRIPTORS = new ParameterizedTypeReference<>() {
	};

	private final WebClient webClient;

	private final AssistantInternalCredentialService credentials;

	private final AssistantInternalRequestHasher requestHasher;

	private final ProviderDefinition definition;

	private final IdentityEnvelope identity;

	private final String runId;

	public RemoteAssistantContextProvider(WebClient.Builder webClientBuilder,
			AssistantInternalCredentialService credentials, AssistantInternalRequestHasher requestHasher,
			ProviderDefinition definition, IdentityEnvelope identity, String runId) {
		this.webClient = webClientBuilder.clone().baseUrl("http://" + definition.serviceId()).build();
		this.credentials = credentials;
		this.requestHasher = requestHasher;
		this.definition = definition;
		this.identity = identity;
		this.runId = runId;
	}

	@Override
	public ProviderDescriptor describe() {
		InternalCredentials signed = credentials.issue(identity, runId, definition.serviceId(),
				Purpose.PROVIDER_DESCRIBE, requestHasher.hash(null));
		List<ProviderDescriptor> descriptors = webClient.get()
			.uri("/inner/assistant/providers")
			.headers(headers -> secureHeaders(headers, signed, runId, identity.correlationId()))
			.accept(MediaType.APPLICATION_JSON)
			.retrieve()
			.bodyToMono(DESCRIPTORS)
			.blockOptional(Duration.ofSeconds(3))
			.orElse(List.of());
		return descriptors.stream()
			.filter(descriptor -> definition.providerId().equals(descriptor.providerId()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("REMOTE_PROVIDER_DESCRIPTOR_MISSING"));
	}

	@Override
	public ProviderResult query(ProviderQuery query) {
		requireIdentityAndRun(query.identity(), query.runId());
		InternalCredentials signed = credentials.issue(identity, runId, definition.serviceId(), Purpose.PROVIDER_SEARCH,
				requestHasher.hash(query));
		return webClient.post()
			.uri("/inner/assistant/providers/{providerCode}/search", definition.providerId().value())
			.headers(headers -> secureHeaders(headers, signed, runId, query.correlationId()))
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(query)
			.retrieve()
			.bodyToMono(ProviderResult.class)
			.blockOptional(Duration.ofMillis(query.limits().timeoutMs()))
			.orElseThrow(() -> new IllegalStateException("REMOTE_PROVIDER_EMPTY_RESPONSE"));
	}

	@Override
	public CitationResolution resolve(CitationResolveRequest request) {
		requireIdentityAndRun(request.identity(), request.runId());
		InternalCredentials signed = credentials.issue(identity, runId, definition.serviceId(),
				Purpose.PROVIDER_CITATION_RESOLVE, requestHasher.hash(request));
		return webClient.post()
			.uri("/inner/assistant/providers/{providerCode}/citations/resolve", definition.providerId().value())
			.headers(headers -> secureHeaders(headers, signed, runId, identity.correlationId()))
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(request)
			.retrieve()
			.bodyToMono(CitationResolution.class)
			.blockOptional(Duration.ofSeconds(3))
			.orElseThrow(() -> new IllegalStateException("REMOTE_PROVIDER_EMPTY_RESPONSE"));
	}

	private void secureHeaders(HttpHeaders headers, InternalCredentials signed, String requestRunId,
			String correlationId) {
		headers.set(AssistantInternalAuthContract.SERVICE_AUTHORIZATION_HEADER,
				"Bearer " + signed.serviceBearerToken());
		headers.set(AssistantInternalAuthContract.DELEGATION_HEADER, signed.delegationToken());
		headers.set(AssistantInternalAuthContract.CONTRACT_VERSION_HEADER, AssistantContextContract.VERSION);
		headers.set(AssistantInternalAuthContract.RUN_ID_HEADER, requestRunId);
		headers.set(AssistantInternalAuthContract.CORRELATION_HEADER, safeCorrelation(correlationId));
	}

	private void requireIdentityAndRun(IdentityEnvelope requestIdentity, String requestRunId) {
		if (!Objects.equals(identity, requestIdentity) || !Objects.equals(runId, requestRunId)) {
			throw new SecurityException("REMOTE_PROVIDER_DELEGATION_MISMATCH");
		}
	}

	private String safeCorrelation(String value) {
		if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) {
			throw new IllegalArgumentException("INVALID_CORRELATION_ID");
		}
		return value;
	}

}
