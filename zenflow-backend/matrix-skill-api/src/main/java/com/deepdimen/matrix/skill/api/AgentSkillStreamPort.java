package com.deepdimen.matrix.skill.api;

import com.deepdimen.matrix.skill.api.SkillContract.Identity;
import com.deepdimen.matrix.skill.api.SkillContract.Consumer;

/**
 * Optional transport of provisional text; only the execution center publishes success.
 */
public interface AgentSkillStreamPort {

	enum Phase {

		RETRIEVING, GENERATING

	}

	interface Observer extends java.util.function.Consumer<String> {

		default void phase(Phase phase) {
		}

	}

	Observer open(Identity identity, Consumer consumer, String businessRunId, String inputJson, String runId,
			String releaseId);

}
