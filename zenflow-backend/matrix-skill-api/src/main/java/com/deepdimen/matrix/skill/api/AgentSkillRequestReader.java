package com.deepdimen.matrix.skill.api;

import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import com.deepdimen.matrix.skill.api.SkillContract.Identity;

/** Reads existing authorized market input; never creates or grants an execution. */
public interface AgentSkillRequestReader {

	String authorizeInput(Identity identity, Consumer consumer, String businessRunId, String inputJson);

}
