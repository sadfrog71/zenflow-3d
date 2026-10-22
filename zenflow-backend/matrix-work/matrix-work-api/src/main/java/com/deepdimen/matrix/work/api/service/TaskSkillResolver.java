package com.deepdimen.matrix.work.api.service;

/** Resolves an approved immutable package from the existing Skill catalog. */
public interface TaskSkillResolver {

	TaskSkill resolve(Long tenantId, Long packageId);

	record TaskSkill(Long packageId, String version, String prompt, String digest, String packageSha256) {
	}

}
