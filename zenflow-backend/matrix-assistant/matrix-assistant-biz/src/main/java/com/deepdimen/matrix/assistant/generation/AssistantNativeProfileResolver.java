package com.deepdimen.matrix.assistant.generation;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel;

/** The run engine's neutral port to the tenant-managed Assistant model resolver. */
public interface AssistantNativeProfileResolver {

	ResolvedNativeProfile resolve(String runId, IdentityEnvelope identity, boolean requireNativeStreaming,
			DataClassification contextClassification, boolean containsBusinessContext);

	/** Profile metadata is optional; model resolution must not depend on a preview or Agent record. */
	record ResolvedNativeProfile(String profileId, String displayName, String requestedModelId,
			ResolvedAssistantModel model) {
	}

}
