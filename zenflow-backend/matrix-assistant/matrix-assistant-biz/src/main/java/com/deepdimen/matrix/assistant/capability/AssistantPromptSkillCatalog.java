package com.deepdimen.matrix.assistant.capability;

import java.util.List;
import java.util.Set;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;

/** Native P0/P1 catalog. It is deliberately separate from the legacy Dify catalog. */
public interface AssistantPromptSkillCatalog {

	List<PromptSkillDescriptor> listPromptSkills(IdentityEnvelope identity);

	PromptSkillSnapshot resolvePromptSkill(IdentityEnvelope identity, String skillId);

	void savePromptSkillPreference(IdentityEnvelope identity, List<String> selectedSkillIds);

	record PromptSkillDescriptor(String skillId, String releaseId, String releaseVersion, String displayName,
			String description, String icon, boolean defaultSelected, String instructionDigest,
			Set<String> requiredProviders) {

		public PromptSkillDescriptor {
			requiredProviders = requiredProviders == null ? Set.of() : Set.copyOf(requiredProviders);
		}
	}

	record PromptSkillSnapshot(String skillId, String releaseId, String releaseVersion, String displayName, String icon,
			String prompt, String instructionDigest, String packageSha256, String securityPolicyVersion,
			Set<String> requiredProviders) {

		public PromptSkillSnapshot {
			if (prompt == null || prompt.isBlank() || instructionDigest == null || instructionDigest.isBlank()) {
				throw new IllegalArgumentException("PROMPT_SKILL_SNAPSHOT_INVALID");
			}
			requiredProviders = requiredProviders == null ? Set.of() : Set.copyOf(requiredProviders);
		}
	}

}
