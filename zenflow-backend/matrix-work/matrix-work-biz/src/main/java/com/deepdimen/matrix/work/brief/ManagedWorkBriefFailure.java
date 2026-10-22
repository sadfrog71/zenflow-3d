package com.deepdimen.matrix.work.brief;

/** Preserves actual execution provenance without exposing the resolution token. */
public final class ManagedWorkBriefFailure extends IllegalStateException {

	private final String skillId;

	private final String releaseId;

	private final String skillRunId;

	public ManagedWorkBriefFailure(String skillId, String releaseId, String skillRunId, Throwable cause) {
		super(cause.getMessage(), cause);
		this.skillId = skillId;
		this.releaseId = releaseId;
		this.skillRunId = skillRunId;
	}

	public String skillId() {
		return skillId;
	}

	public String releaseId() {
		return releaseId;
	}

	public String skillRunId() {
		return skillRunId;
	}

}
