package com.deepdimen.matrix.assistant.identity;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;

/** Integration-owned bridge to the authenticated Matrix identity. */
public interface AssistantIdentityResolver {

	IdentityEnvelope currentIdentity();

}
