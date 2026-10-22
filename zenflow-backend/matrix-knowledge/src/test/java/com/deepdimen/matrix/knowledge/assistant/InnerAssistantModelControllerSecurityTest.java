package com.deepdimen.matrix.knowledge.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class InnerAssistantModelControllerSecurityTest {

	private final AssistantModelGateway gateway = mock(AssistantModelGateway.class);

	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@Test
	void hmacEndpointIsPermitAllAtPlatformLayerWithoutTrustedFromHeader() {
		Inner inner = InnerAssistantModelController.class.getAnnotation(Inner.class);

		assertFalse(inner.value());
	}

	@Test
	void forgedInnerHeadersAreDeniedBeforeGatewayInvocation() {
		InnerAssistantModelController controller = new InnerAssistantModelController(gateway,
				new DenyAssistantModelInvocationAuthorizer(), objectMapper, "matrix-knowledge");

		ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller
			.resolve("Bearer forged", "forged", AssistantModelContract.VERSION, json(request(identity("11")))));

		assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
		verify(gateway, never()).resolve(any());
	}

	@Test
	void bodyIdentityCannotOverrideVerifiedDelegationClaims() {
		AssistantModelInvocationAuthorizer authorizer = mock(AssistantModelInvocationAuthorizer.class);
		when(authorizer.authorize(any(), any(), any(), any(), any(), any(), any())).thenReturn(identity("22"));
		InnerAssistantModelController controller = new InnerAssistantModelController(gateway, authorizer, objectMapper,
				"matrix-knowledge");

		ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller
			.resolve("Bearer service", "signed", AssistantModelContract.VERSION, json(request(identity("11")))));

		assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
		verify(gateway, never()).resolve(any());
	}

	@Test
	void contractHeaderCannotDisagreeWithTheBody() {
		AssistantModelInvocationAuthorizer authorizer = mock(AssistantModelInvocationAuthorizer.class);
		InnerAssistantModelController controller = new InnerAssistantModelController(gateway, authorizer, objectMapper,
				"matrix-knowledge");

		ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller
			.resolve("Bearer service", "signed", "assistant-model/v0", json(request(identity("11")))));

		assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
		verify(authorizer, never()).authorize(any(), any(), any(), any(), any(), any(), any());
		verify(gateway, never()).resolve(any());
	}

	private ModelResolutionRequest request(IdentityEnvelope identity) {
		return new ModelResolutionRequest(AssistantModelContract.VERSION, "run-1", identity, "99", true,
				DataClassification.PUBLIC, false);
	}

	private IdentityEnvelope identity(String tenantId) {
		return new IdentityEnvelope(tenantId, "7", "3", List.of("3"), List.of(), List.of(), "inner-test",
				"ASSISTANT_MODEL");
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
