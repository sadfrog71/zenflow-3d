package com.deepdimen.matrix.discover.assistant.provider;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.AssistantInternalCredentialService;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderDefinition;
import com.deepdimen.matrix.assistant.provider.AssistantRemoteProviderFactory;
import com.deepdimen.matrix.assistant.provider.security.AssistantInternalRequestHasher;

@Component
public class RemoteAssistantProviderFactory implements AssistantRemoteProviderFactory {

	private final WebClient.Builder webClientBuilder;

	private final AssistantInternalCredentialService credentials;

	private final AssistantInternalRequestHasher requestHasher;

	public RemoteAssistantProviderFactory(
			@Qualifier("assistantProviderLoadBalancedWebClientBuilder") WebClient.Builder webClientBuilder,
			AssistantInternalCredentialService credentials, AssistantInternalRequestHasher requestHasher) {
		this.webClientBuilder = webClientBuilder;
		this.credentials = credentials;
		this.requestHasher = requestHasher;
	}

	@Override
	public RemoteAssistantContextProvider create(ProviderDefinition definition, IdentityEnvelope identity,
			String runId) {
		return new RemoteAssistantContextProvider(webClientBuilder, credentials, requestHasher, definition, identity,
				runId);
	}

}
