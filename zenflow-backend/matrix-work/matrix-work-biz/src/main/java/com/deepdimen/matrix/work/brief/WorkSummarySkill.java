package com.deepdimen.matrix.work.brief;

public interface WorkSummarySkill {

	String code();

	String version();

	WorkBriefDraft execute(WorkBriefSnapshot snapshot);
}
