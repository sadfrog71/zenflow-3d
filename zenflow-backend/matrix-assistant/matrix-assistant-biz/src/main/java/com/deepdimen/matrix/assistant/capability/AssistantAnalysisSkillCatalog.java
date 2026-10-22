package com.deepdimen.matrix.assistant.capability;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

/** Runtime access to one immutable Skill that governs structured analysis and output. */
public interface AssistantAnalysisSkillCatalog {

	AnalysisSkillSnapshot resolveDefault(IdentityEnvelope identity);

	default AnalysisSkillSnapshot resolve(IdentityEnvelope identity, String skillId) {
		if (!StringUtils.hasText(skillId)) {
			return resolveDefault(identity);
		}
		throw new IllegalArgumentException("ASSISTANT_ANALYSIS_SKILL_NOT_FOUND");
	}

	record AnalysisSkillSnapshot(String skillId, long releaseId, String releaseVersion, String displayName,
			String instruction, String instructionDigest, String packageSha256, Map<String, Object> policy,
			String policyDigest, Map<String, Object> resultSchema, String resultSchemaId,
			Set<String> requiredProviders) {

		public AnalysisSkillSnapshot {
			policy = policy == null ? Map.of() : Map.copyOf(policy);
			resultSchema = resultSchema == null ? Map.of() : Map.copyOf(resultSchema);
			requiredProviders = requiredProviders == null ? Set.of() : Set.copyOf(requiredProviders);
		}
	}

}
