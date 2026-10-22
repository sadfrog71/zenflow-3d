package com.deepdimen.matrix.knowledge.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.Purpose;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantEgress;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DeploymentDomain;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.assistant.provider.security.AssistantInternalAuthProperties;
import com.deepdimen.matrix.assistant.provider.security.AssistantInternalRequestHasher;
import com.deepdimen.matrix.assistant.provider.security.HmacAssistantInternalCredentialService;
import com.fasterxml.jackson.databind.ObjectMapper;

class InnerAssistantModelSignedDelegationTest {

	@Test
	void signedDelegationAuthorizesExactlyOneMatchingModelRequest() {
		ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
		AssistantInternalAuthProperties properties = new AssistantInternalAuthProperties();
		properties.setSecret("test-only-secret-material-with-at-least-32-bytes");
		properties.setWorkloadId("matrix-discover");
		properties.setTtl(Duration.ofMinutes(3));
		HmacAssistantInternalCredentialService credentials = new HmacAssistantInternalCredentialService(objectMapper,
				properties);
		AssistantModelGateway gateway = mock(AssistantModelGateway.class);
		ModelResolutionRequest request = request();
		ResolvedAssistantModel expected = model();
		when(gateway.resolve(request)).thenReturn(expected);
		InnerAssistantModelController controller = new InnerAssistantModelController(gateway,
				new AssistantInternalModelInvocationAuthorizer(credentials), objectMapper, "matrix-knowledge");
		AssistantInternalRequestHasher requestHasher = new AssistantInternalRequestHasher(objectMapper);
		String serializedBody = requestHasher.serialize(request);
		String hash = requestHasher.hashSerializedBody(serializedBody);
		var signed = credentials.issue(request.identity(), request.runId(), "matrix-knowledge", Purpose.MODEL_RESOLVE,
				hash);

		assertEquals(expected, controller.resolve("Bearer " + signed.serviceBearerToken(), signed.delegationToken(),
				AssistantModelContract.VERSION, serializedBody));
		assertThrows(ResponseStatusException.class, () -> controller.resolve("Bearer " + signed.serviceBearerToken(),
				signed.delegationToken(), AssistantModelContract.VERSION, serializedBody));
	}

	private ModelResolutionRequest request() {
		IdentityEnvelope identity = new IdentityEnvelope("11", "7", "3", List.of("3"), List.of(), List.of(), "corr-1",
				"ASSISTANT_MODEL");
		return new ModelResolutionRequest(AssistantModelContract.VERSION, "run-1", identity, "99", true,
				DataClassification.SENSITIVE, true);
	}

	private ResolvedAssistantModel model() {
		return new ResolvedAssistantModel(AssistantModelContract.VERSION, "99", "model", "local",
				new ModelCapabilities(true, 8192, 1024, true),
				new AssistantEgress(DeploymentDomain.INTERNAL, "cn", true, DataClassification.SENSITIVE, "policy"));
	}

}
