package com.deepdimen.matrix.discover.assistant;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.Purpose;

/** Supplies short-lived workload and end-user delegation credentials. */
public interface AssistantModelDelegationSigner {

	SignedModelDelegation sign(IdentityEnvelope identity, String runId, String audience, String requestHash,
			Purpose purpose);

	record SignedModelDelegation(String serviceBearerToken, String delegationToken) {

		public SignedModelDelegation {
			if (serviceBearerToken == null || serviceBearerToken.isBlank() || delegationToken == null
					|| delegationToken.isBlank()) {
				throw new IllegalArgumentException("INNER_MODEL_CREDENTIALS_UNAVAILABLE");
			}
		}
	}

}
