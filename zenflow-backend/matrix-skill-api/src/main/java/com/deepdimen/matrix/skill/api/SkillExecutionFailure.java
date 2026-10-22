package com.deepdimen.matrix.skill.api;

/** An execution failure whose child run outcome was durably recorded. */
public final class SkillExecutionFailure extends IllegalStateException {

	private final SkillContract.ExecuteResult result;

	public SkillExecutionFailure(SkillContract.ExecuteResult result, Throwable cause) {
		super("SKILL_RUN_" + result.status(), cause);
		this.result = result;
	}

	public SkillContract.ExecuteResult result() {
		return result;
	}

}
