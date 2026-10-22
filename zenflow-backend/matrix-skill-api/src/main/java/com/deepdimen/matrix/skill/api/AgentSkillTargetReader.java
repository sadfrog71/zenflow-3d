package com.deepdimen.matrix.skill.api;

import com.deepdimen.matrix.skill.api.SkillContract.Consumer;

/**
 * Server-side view of the existing market configuration; never a second configuration
 * store.
 */
public interface AgentSkillTargetReader {

	Target read(Consumer consumer);

	record Target(String agentId, String agentType, String configurationJson) {
	}

}
