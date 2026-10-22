package com.deepdimen.matrix.assistant.provider.security;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.AssistantInternalCredentialService;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.DelegationClaims;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.InternalAuthenticationException;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.InternalCredentials;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.Purpose;

public class DenyAssistantInternalCredentialService implements AssistantInternalCredentialService {

	@Override
	public InternalCredentials issue(IdentityEnvelope identity, String runId, String audience, Purpose purpose,
			String requestHash) {
		throw new InternalAuthenticationException();
	}

	@Override
	public DelegationClaims verify(String serviceAuthorization, String delegationToken, String audience,
			Purpose purpose, String runId, String requestHash) {
		throw new InternalAuthenticationException();
	}

}
