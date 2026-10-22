package com.deepdimen.matrix.skill.api;

import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import com.deepdimen.matrix.skill.api.SkillContract.Identity;
import com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot;
import com.deepdimen.matrix.skill.api.SkillPromptInvocationPort.Output;

/** A market type adapter behind the single MARKET_AGENT business port. */
public interface AgentSkillPromptAdapter {

	String agentType();

	boolean available(Consumer consumer, ResolvedSkillSnapshot snapshot);

	Output execute(String runId, Identity identity, Consumer consumer, ResolvedSkillSnapshot snapshot, String inputJson,
			Runnable beforeInvocation);

}
