package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import com.deepdimen.matrix.skill.api.SkillExecutionFailure;

/** Only the original managed execution wrapper may carry a trusted model failure. */
final class ZenflowCompanionFailures {

	private ZenflowCompanionFailures() {
	}

	static AssistantModelException modelFailure(RuntimeException failure) {
		if (failure instanceof AssistantModelException model)
			return model;
		if (failure instanceof SkillExecutionFailure && failure.getCause() instanceof AssistantModelException model)
			return model;
		return null;
	}

}
