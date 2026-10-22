package com.deepdimen.matrix.knowledge.assistant;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;

/** Validates both workload identity and the short-lived end-user delegation. */
public interface AssistantModelInvocationAuthorizer {

	IdentityEnvelope authorize(String serviceAuthorization, String delegationToken, String contractVersion,
			String requestHash, String audience, String purpose, String runId);

}
