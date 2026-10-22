package com.deepdimen.matrix.assistant.provider.web;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolveRequest;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolution;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.AssistantInternalCredentialService;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.DelegationClaims;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.Purpose;
import com.deepdimen.matrix.assistant.provider.AssistantProviderRegistry;
import com.deepdimen.matrix.assistant.provider.security.AssistantInternalRequestHasher;
import com.deepdimen.matrix.common.security.annotation.Inner;

@Inner(value = false)
@RestController
@RequestMapping("/inner/assistant/providers")
public class InnerAssistantProviderController {

	private final AssistantProviderRegistry registry;

	private final AssistantInternalCredentialService credentials;

	private final AssistantInternalRequestHasher requestHasher;

	private final String audience;

	public InnerAssistantProviderController(AssistantProviderRegistry registry,
			AssistantInternalCredentialService credentials, AssistantInternalRequestHasher requestHasher,
			@Value("${spring.application.name:matrix-assistant}") String audience) {
		this.registry = registry;
		this.credentials = credentials;
		this.requestHasher = requestHasher;
		this.audience = audience;
	}

	@GetMapping
	public List<ProviderDescriptor> descriptors(
			@RequestHeader(AssistantInternalAuthContract.SERVICE_AUTHORIZATION_HEADER) String serviceAuthorization,
			@RequestHeader(AssistantInternalAuthContract.DELEGATION_HEADER) String delegation,
			@RequestHeader(AssistantInternalAuthContract.CONTRACT_VERSION_HEADER) String contractVersion,
			@RequestHeader(AssistantInternalAuthContract.RUN_ID_HEADER) String runId) {
		authorize(serviceAuthorization, delegation, contractVersion, runId, Purpose.PROVIDER_DESCRIBE, null);
		return registry.providerIds().stream().map(registry::require).map(AssistantContextProvider::describe).toList();
	}

	@PostMapping("/{providerCode}/search")
	public ProviderResult search(@PathVariable("providerCode") String providerCode,
			@RequestHeader(AssistantInternalAuthContract.SERVICE_AUTHORIZATION_HEADER) String serviceAuthorization,
			@RequestHeader(AssistantInternalAuthContract.DELEGATION_HEADER) String delegation,
			@RequestHeader(AssistantInternalAuthContract.CONTRACT_VERSION_HEADER) String contractVersion,
			@RequestBody ProviderQuery request) {
		ProviderId providerId = ProviderId.of(providerCode);
		DelegationClaims claims = authorize(serviceAuthorization, delegation, contractVersion, request.runId(),
				Purpose.PROVIDER_SEARCH, request);
		requireSameIdentity(claims.identity(), request.identity());
		ProviderResult result = local(providerId).query(new ProviderQuery(request.contractVersion(),
				request.requestId(), request.runId(), request.correlationId(), claims.identity(), request.query(),
				request.scope(), request.limits(), request.queryTerms()));
		if (!providerId.equals(result.provider())) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "INNER_PROVIDER_CODE_MISMATCH");
		}
		return result;
	}

	@PostMapping("/{providerCode}/citations/resolve")
	public CitationResolution resolve(@PathVariable("providerCode") String providerCode,
			@RequestHeader(AssistantInternalAuthContract.SERVICE_AUTHORIZATION_HEADER) String serviceAuthorization,
			@RequestHeader(AssistantInternalAuthContract.DELEGATION_HEADER) String delegation,
			@RequestHeader(AssistantInternalAuthContract.CONTRACT_VERSION_HEADER) String contractVersion,
			@RequestBody CitationResolveRequest request) {
		ProviderId providerId = ProviderId.of(providerCode);
		if (!providerId.equals(request.citation().provider())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INNER_PROVIDER_CODE_MISMATCH");
		}
		DelegationClaims claims = authorize(serviceAuthorization, delegation, contractVersion, request.runId(),
				Purpose.PROVIDER_CITATION_RESOLVE, request);
		requireSameIdentity(claims.identity(), request.identity());
		return local(providerId)
			.resolve(new CitationResolveRequest(request.runId(), request.citation(), claims.identity()));
	}

	private DelegationClaims authorize(String serviceAuthorization, String delegation, String contractVersion,
			String runId, Purpose purpose, Object body) {
		if (!AssistantContextContract.VERSION.equals(contractVersion)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INNER_PROVIDER_CONTRACT_INCOMPATIBLE");
		}
		try {
			return credentials.verify(serviceAuthorization, delegation, audience, purpose, runId,
					requestHasher.hash(body));
		}
		catch (RuntimeException ex) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "INNER_PROVIDER_UNAUTHORIZED");
		}
	}

	private AssistantContextProvider local(ProviderId providerId) {
		return registry.local(providerId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "INNER_PROVIDER_NOT_REGISTERED"));
	}

	private void requireSameIdentity(IdentityEnvelope claims, IdentityEnvelope body) {
		if (!Objects.equals(claims, body)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "INNER_PROVIDER_IDENTITY_MISMATCH");
		}
	}

}
