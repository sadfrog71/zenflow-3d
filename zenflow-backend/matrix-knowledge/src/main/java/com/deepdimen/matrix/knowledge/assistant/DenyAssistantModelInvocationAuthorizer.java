package com.deepdimen.matrix.knowledge.assistant;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import org.springframework.stereotype.Component;

/** Secure default: cloud model endpoints stay closed until workload auth is wired. */
@Component
public class DenyAssistantModelInvocationAuthorizer implements AssistantModelInvocationAuthorizer {

	@Override
	public IdentityEnvelope authorize(String serviceAuthorization, String delegationToken, String contractVersion,
			String requestHash, String audience, String purpose, String runId) {
		throw new SecurityException("INNER_MODEL_UNAUTHORIZED");
	}

}
