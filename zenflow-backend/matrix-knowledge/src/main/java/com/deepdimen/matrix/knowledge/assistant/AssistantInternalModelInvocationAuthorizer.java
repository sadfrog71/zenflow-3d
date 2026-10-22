package com.deepdimen.matrix.knowledge.assistant;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.AssistantInternalCredentialService;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.Purpose;

@Primary
@Component
public class AssistantInternalModelInvocationAuthorizer implements AssistantModelInvocationAuthorizer {

	private static final Logger log = LoggerFactory.getLogger(AssistantInternalModelInvocationAuthorizer.class);

	private final AssistantInternalCredentialService credentials;

	public AssistantInternalModelInvocationAuthorizer(AssistantInternalCredentialService credentials) {
		this.credentials = credentials;
		log.info("Assistant internal model authorizer initialized with {}", credentials.getClass().getSimpleName());
	}

	@Override
	public IdentityEnvelope authorize(String serviceAuthorization, String delegationToken, String contractVersion,
			String requestHash, String audience, String purpose, String runId) {
		return credentials
			.verify(serviceAuthorization, delegationToken, audience, Purpose.valueOf(purpose), runId, requestHash)
			.identity();
	}

}
