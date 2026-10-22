package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.skill.api.SkillContract.ExecuteResult;

/**
 * Carries actual execution status without relabelling unknown results or permitting
 * replay.
 */
public final class ManagedTaskDispatchFailure extends IllegalStateException {

	private final String businessRunId;

	private final String skillId;

	private final ExecuteResult result;

	public ManagedTaskDispatchFailure(String businessRunId, String skillId, ExecuteResult result, Throwable cause) {
		super(cause.getMessage(), cause);
		this.businessRunId = businessRunId;
		this.skillId = skillId;
		this.result = result;
	}

	public String businessRunId() {
		return businessRunId;
	}

	public String skillId() {
		return skillId;
	}

	public ExecuteResult result() {
		return result;
	}

}
