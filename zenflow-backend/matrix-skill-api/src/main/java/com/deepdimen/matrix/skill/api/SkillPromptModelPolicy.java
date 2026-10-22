package com.deepdimen.matrix.skill.api;

import com.deepdimen.matrix.skill.api.SkillContract.*;

/**
 * Business-owned model selection, invoked only after the managed service authorizes
 * input.
 */
public interface SkillPromptModelPolicy {

	String consumerType();

	Selection resolve(Identity identity, Consumer consumer, ResolvedSkillSnapshot snapshot);

	record Selection(String modelId, String purpose, String userPrompt) {
		public Selection {
			if (modelId == null || modelId.isBlank() || purpose == null || purpose.isBlank() || userPrompt == null
					|| userPrompt.isBlank())
				throw new IllegalArgumentException("MODEL_BINDING_REQUIRED");
		}
	}

}
