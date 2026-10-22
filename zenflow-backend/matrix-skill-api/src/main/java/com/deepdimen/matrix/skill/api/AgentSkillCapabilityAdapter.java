package com.deepdimen.matrix.skill.api;

/**
 * Reports actual isolated market runtime capabilities, never inferred from an agent
 * label.
 */
public interface AgentSkillCapabilityAdapter {

	String agentType();

	SkillContract.AgentCapabilities inspect(String tenantId, String agentId);

}
