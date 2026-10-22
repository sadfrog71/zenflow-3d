package com.deepdimen.matrix.skill.api;

/**
 * Revalidates token, identity, compatibility and revocation; UNKNOWN must not be blindly
 * replayed.
 */
public interface SkillExecutionService {

	default boolean available(SkillContract.Consumer consumer, SkillContract.ResolvedSkillSnapshot snapshot) {
		return false;
	}

	/**
	 * Rechecks the current binding and business input before a local business fallback.
	 */
	default boolean fallbackAllowed(SkillContract.ExecuteRequest request, String mode) {
		return false;
	}

	SkillContract.ExecuteResult execute(SkillContract.ExecuteRequest request);

}
