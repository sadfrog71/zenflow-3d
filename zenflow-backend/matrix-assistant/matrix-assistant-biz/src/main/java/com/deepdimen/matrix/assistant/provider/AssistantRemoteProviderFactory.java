package com.deepdimen.matrix.assistant.provider;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderDefinition;

/** Cloud transport port. Implementations resolve only trusted logical service IDs. */
public interface AssistantRemoteProviderFactory {

	AssistantContextProvider create(ProviderDefinition definition, IdentityEnvelope identity, String runId);

}
