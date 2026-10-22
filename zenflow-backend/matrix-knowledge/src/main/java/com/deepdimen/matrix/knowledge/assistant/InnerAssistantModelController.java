package com.deepdimen.matrix.knowledge.assistant;

import java.util.Objects;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantModelRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelChunk;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.assistant.provider.security.AssistantInternalRequestHasher;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

@Inner(value = false)
@RestController
@RequestMapping("/inner/assistant/models")
public class InnerAssistantModelController {

	private static final String CONTRACT_HEADER = "X-Assistant-Contract-Version";

	private static final String DELEGATION_HEADER = "X-Assistant-Delegation";

	private final AssistantModelGateway gateway;

	private final AssistantModelInvocationAuthorizer authorizer;

	private final AssistantInternalRequestHasher requestHasher;

	private final ObjectMapper objectMapper;

	private final String audience;

	public InnerAssistantModelController(AssistantModelGateway gateway, AssistantModelInvocationAuthorizer authorizer,
			ObjectMapper objectMapper, @Value("${spring.application.name:matrix-knowledge}") String audience) {
		this.gateway = gateway;
		this.authorizer = authorizer;
		this.requestHasher = new AssistantInternalRequestHasher(objectMapper);
		this.objectMapper = objectMapper;
		this.audience = audience;
	}

	@PostMapping("/resolve")
	public ResolvedAssistantModel resolve(
			@RequestHeader(AssistantInternalAuthContract.SERVICE_AUTHORIZATION_HEADER) String serviceAuthorization,
			@RequestHeader(DELEGATION_HEADER) String delegation, @RequestHeader(CONTRACT_HEADER) String contractVersion,
			@RequestBody String serializedBody) {
		ModelResolutionRequest request = deserialize(serializedBody, ModelResolutionRequest.class);
		IdentityEnvelope verified = authorize(serviceAuthorization, delegation, contractVersion, serializedBody,
				request, request.runId(), "MODEL_RESOLVE");
		requireSameIdentity(verified, request.identity());
		return gateway.resolve(new ModelResolutionRequest(request.contractVersion(), request.runId(), verified,
				request.requestedModelId(), request.requireNativeStreaming(), request.contextClassification(),
				request.containsBusinessContext()));
	}

	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ModelChunk> stream(
			@RequestHeader(AssistantInternalAuthContract.SERVICE_AUTHORIZATION_HEADER) String serviceAuthorization,
			@RequestHeader(DELEGATION_HEADER) String delegation, @RequestHeader(CONTRACT_HEADER) String contractVersion,
			@RequestBody String serializedBody) {
		AssistantModelRequest request = deserialize(serializedBody, AssistantModelRequest.class);
		IdentityEnvelope verified = authorize(serviceAuthorization, delegation, contractVersion, serializedBody,
				request, request.runId(), "MODEL_STREAM");
		requireSameIdentity(verified, request.identity());
		AssistantModelRequest verifiedRequest = new AssistantModelRequest(request.contractVersion(), request.runId(),
				request.correlationId(), verified, request.model(), request.systemPrompt(), request.history(),
				request.contextFragments(), request.userMessage(), request.temperature(), request.topP(),
				request.maxOutputTokens(), request.requireNativeStreaming(), request.attachments());
		return gateway.stream(verifiedRequest);
	}

	private IdentityEnvelope authorize(String serviceAuthorization, String delegation, String contractVersion,
			String serializedBody, Object body, String runId, String purpose) {
		String bodyVersion = body instanceof ModelResolutionRequest request ? request.contractVersion()
				: ((AssistantModelRequest) body).contractVersion();
		if (!AssistantModelContract.VERSION.equals(contractVersion) || !contractVersion.equals(bodyVersion)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INNER_MODEL_CONTRACT_INCOMPATIBLE");
		}
		try {
			return authorizer.authorize(serviceAuthorization, delegation, contractVersion,
					requestHasher.hashSerializedBody(serializedBody), audience, purpose, runId);
		}
		catch (RuntimeException ex) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "INNER_MODEL_UNAUTHORIZED");
		}
	}

	private <T> T deserialize(String serializedBody, Class<T> type) {
		try {
			return objectMapper.readValue(serializedBody, type);
		}
		catch (JsonProcessingException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INNER_MODEL_REQUEST_INVALID");
		}
	}

	private void requireSameIdentity(IdentityEnvelope verified, IdentityEnvelope body) {
		if (verified == null || body == null || !Objects.equals(verified.tenantId(), body.tenantId())
				|| !Objects.equals(verified.userId(), body.userId())
				|| !Objects.equals(verified.currentOrgId(), body.currentOrgId())
				|| !Objects.equals(verified.purpose(), body.purpose())) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "INNER_MODEL_IDENTITY_MISMATCH");
		}
	}

}
