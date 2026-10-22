package com.deepdimen.matrix.skill.api;

import com.deepdimen.matrix.skill.api.SkillContract.*;

/**
 * Explicit authorized result reference, not a provider conversation or client history.
 */
public interface AgentSkillResultContextReader {

	Context read(Identity identity, Consumer consumer, ResolvedSkillSnapshot release, String clientRequestId);

	/**
	 * Content is untrusted conversation data and must never become system instructions.
	 */
	record Context(String question, String answer, String skillRunId, String releaseId) {
	}

}
