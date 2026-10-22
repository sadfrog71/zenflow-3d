package com.deepdimen.matrix.work.ai;

/**
 * Retains business and actual Skill execution provenance without exposing snapshot
 * tokens.
 */
public final class ManagedTaskParseFailure extends IllegalStateException {

	private final String businessRunId;

	private final String skillId;

	private final String actualReleaseId;

	private final String skillRunId;

	public ManagedTaskParseFailure(String businessRunId, String skillId, String actualReleaseId, String skillRunId,
			Throwable cause) {
		super(cause.getMessage(), cause);
		this.businessRunId = businessRunId;
		this.skillId = skillId;
		this.actualReleaseId = actualReleaseId;
		this.skillRunId = skillRunId;
	}

	public String businessRunId() {
		return businessRunId;
	}

	public String skillId() {
		return skillId;
	}

	public String actualReleaseId() {
		return actualReleaseId;
	}

	public String skillRunId() {
		return skillRunId;
	}

}
