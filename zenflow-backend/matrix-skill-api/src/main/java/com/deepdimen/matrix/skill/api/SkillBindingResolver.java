package com.deepdimen.matrix.skill.api;

/** Implementations must authorize consumer and current release state before resolving. */
public interface SkillBindingResolver {

	SkillContract.Resolution resolve(SkillContract.ResolveRequest request);

	/**
	 * Authorizes and reads the current snapshot without issuing a token or starting a
	 * run.
	 */
	default SkillContract.ResolvedSkillSnapshot inspect(SkillContract.Identity identity,
			SkillContract.Consumer consumer) {
		throw new IllegalStateException("EXECUTOR_UNAVAILABLE");
	}

}
