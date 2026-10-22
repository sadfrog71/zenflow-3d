package com.deepdimen.matrix.skill.api;

/**
 * Internal metadata read port. The business caller must authorize access to its run
 * first.
 */
public interface SkillRunMetadataReader {

	Metadata find(String tenantId, String actorId, SkillContract.Consumer consumer, String businessRunId);

	record Metadata(String skillRunId, String releaseId, String bindingRevisionId, String status, String errorCode) {
	}

}
