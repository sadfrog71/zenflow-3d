package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import com.deepdimen.matrix.skill.api.SkillContract.Identity;
import com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot;
import com.deepdimen.matrix.skill.api.SkillConsumerPolicy.BindingConfiguration;

/**
 * Implemented only by the Engine-leased, thread-scoped administrator preview executor.
 */
public interface ZenflowPreviewLease {

	boolean permits(Identity identity, Consumer consumer, String runId);

	String pinned(Identity identity, Consumer consumer, String runId);

	void authorize(Identity identity, Consumer consumer, BindingConfiguration binding, ResolvedSkillSnapshot release);

}
