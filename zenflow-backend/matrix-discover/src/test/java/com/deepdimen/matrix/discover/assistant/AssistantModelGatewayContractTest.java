package com.deepdimen.matrix.discover.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantEgress;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DeploymentDomain;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.discover.assistant.AssistantModelDelegationSigner.SignedModelDelegation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class AssistantModelGatewayContractTest {

	private final AssistantModelGateway gateway = mock(AssistantModelGateway.class);

	private final MatrixAssistantProfileResolver resolver = new MatrixAssistantProfileResolver(gateway);

	@Test
	void serverConfiguredAssistantModelWorksWithoutPreviewOrAgentRoute() {
		when(gateway.resolve(any())).thenReturn(model("77"));

		var resolved = resolver.resolve("run-server-config", identity(), false, DataClassification.PUBLIC, false);

		assertNull(resolved.profileId());
		assertEquals("77", resolved.model().modelId());
	}

	@Test
	void modelResolutionReceivesAuthenticatedTenantAndRequestPolicy() {
		when(gateway.resolve(any())).thenReturn(model("991"));

		resolver.resolve("run-policy", identity(), true, DataClassification.SENSITIVE, true);

		ArgumentCaptor<ModelResolutionRequest> request = ArgumentCaptor.forClass(ModelResolutionRequest.class);
		verify(gateway).resolve(request.capture());
		assertEquals("11", request.getValue().identity().tenantId());
		assertEquals("7", request.getValue().identity().userId());
		assertEquals(DataClassification.SENSITIVE, request.getValue().contextClassification());
		assertEquals(true, request.getValue().containsBusinessContext());
		assertNull(request.getValue().requestedModelId());
		assertEquals("run-policy", request.getValue().runId());
	}

	@Test
	void managedModelFailureIsNotMaskedByProfileOrRouteErrors() {
		when(gateway.resolve(any())).thenThrow(new AssistantModelException("MODEL_NOT_CONFIGURED",
				"当前租户未配置统一助手模型"));

		AssistantModelException error = assertThrows(AssistantModelException.class,
				() -> resolver.resolve("run-no-model", identity(), true, DataClassification.PUBLIC, false));
		assertEquals("MODEL_NOT_CONFIGURED", error.errorCode());
	}

	@Test
	void modelResolutionRequiresTenantAndUserIdentity() {
		IdentityEnvelope missingUser = new IdentityEnvelope("11", "", "3", List.of("3"), List.of(), List.of(),
				"gateway-test", "ASSISTANT_MODEL");

		AssistantModelException error = assertThrows(AssistantModelException.class,
				() -> resolver.resolve("run-missing-user", missingUser, true, DataClassification.PUBLIC, false));

		assertEquals("ASSISTANT_IDENTITY_REQUIRED", error.errorCode());
		verifyZeroInteractions(gateway);
	}

	@Test
	void remoteGatewayRejectsUrlsOutsideControlledServiceNamespace() {
		assertThrows(IllegalArgumentException.class, () -> new RemoteAssistantModelGateway(WebClient.builder(),
				new ObjectMapper(), mock(AssistantModelDelegationSigner.class), "https://attacker.example"));
	}

	@Test
	void remoteGatewayUsesDedicatedServiceHeaderWithoutPlatformInnerSpoofingHeader() throws Exception {
		AtomicReference<ClientRequest> captured = new AtomicReference<>();
		ResolvedAssistantModel response = model("991");
		var exchange = (org.springframework.web.reactive.function.client.ExchangeFunction) request -> {
			captured.set(request);
			try {
				return Mono.just(ClientResponse.create(HttpStatus.OK)
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.body(new ObjectMapper().writeValueAsString(response))
					.build());
			}
			catch (Exception ex) {
				return Mono.error(ex);
			}
		};
		AssistantModelDelegationSigner signer = mock(AssistantModelDelegationSigner.class);
		when(signer.sign(any(), any(), any(), any(), any()))
			.thenReturn(new SignedModelDelegation("service-token", "delegation-token"));
		RemoteAssistantModelGateway remote = new RemoteAssistantModelGateway(
				WebClient.builder().exchangeFunction(exchange), new ObjectMapper(), signer, "matrix-knowledge");

		remote.resolve(new ModelResolutionRequest(AssistantModelContract.VERSION, "run-1", identity(), "991", true,
				DataClassification.PUBLIC, false));

		assertEquals("Bearer service-token",
				captured.get().headers().getFirst(AssistantInternalAuthContract.SERVICE_AUTHORIZATION_HEADER));
		assertNull(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION));
		assertNull(captured.get().headers().getFirst("from"));
	}

	private IdentityEnvelope identity() {
		return new IdentityEnvelope("11", "7", "3", List.of("3"), List.of(), List.of(), "gateway-test",
				"ASSISTANT_MODEL");
	}

	private ResolvedAssistantModel model(String id) {
		return new ResolvedAssistantModel(AssistantModelContract.VERSION, id, "model", "openai",
				new ModelCapabilities(true, 8192, 1024, true),
				new AssistantEgress(DeploymentDomain.INTERNAL, "cn", true, DataClassification.SENSITIVE, "p1"));
	}

}
