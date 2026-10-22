package com.deepdimen.matrix.discover.assistant;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.AssistantInternalCredentialService;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.InternalCredentials;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.Purpose;

@Component
public class AssistantInternalModelDelegationSigner implements AssistantModelDelegationSigner {

	private final AssistantInternalCredentialService credentials;

	public AssistantInternalModelDelegationSigner(AssistantInternalCredentialService credentials) {
		this.credentials = credentials;
	}

	@Override
	public SignedModelDelegation sign(IdentityEnvelope identity, String runId, String audience, String requestHash,
			Purpose purpose) {
		InternalCredentials signed = credentials.issue(identity, runId, audience, purpose, requestHash);
		return new SignedModelDelegation(signed.serviceBearerToken(), signed.delegationToken());
	}

}
