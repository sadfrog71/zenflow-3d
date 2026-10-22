package com.deepdimen.matrix.discover.assistant;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.assistant.generation.AssistantNativeProfileResolver;
import com.deepdimen.matrix.assistant.generation.AssistantNativeProfileResolver.ResolvedNativeProfile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/** Resolves the unified Assistant exclusively from the authenticated tenant's managed model configuration. */
@Component
@ConditionalOnBean(AssistantModelGateway.class)
public class MatrixAssistantProfileResolver implements AssistantNativeProfileResolver {

	private final AssistantModelGateway modelGateway;

	public MatrixAssistantProfileResolver(AssistantModelGateway modelGateway) {
		this.modelGateway = modelGateway;
	}

	@Override
	public ResolvedNativeProfile resolve(String runId, IdentityEnvelope identity, boolean requireNativeStreaming,
			DataClassification contextClassification, boolean containsBusinessContext) {
		requireIdentity(identity);
		ResolvedAssistantModel model = modelGateway.resolve(new ModelResolutionRequest(AssistantModelContract.VERSION,
				runId, identity, null, requireNativeStreaming, contextClassification, containsBusinessContext));
		return new ResolvedNativeProfile(null, "统一助手", null, model);
	}

	private void requireIdentity(IdentityEnvelope identity) {
		try {
			Long.parseLong(identity.tenantId());
			if (identity.userId() == null || identity.userId().isBlank()) {
				throw new IllegalArgumentException("userId is blank");
			}
		}
		catch (RuntimeException ex) {
			throw new AssistantModelException("ASSISTANT_IDENTITY_REQUIRED", "未获取到有效的租户和用户身份");
		}
	}

}
