package com.deepdimen.matrix.skill.api;

import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot;
import java.util.Objects;

/** Implemented by business modules without depending on discover persistence entities. */
public interface SkillConsumerPolicy {

	String consumerType();

	/** Authorizes an inactive identity before a business draft has a prepared release. */
	default void authorizeEmptyBinding(String tenantId, String actorId, Consumer consumer) {
		throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
	}

	/** Only a business-registered run with a current trusted lease may pin an immutable revision. */
	default String pinnedBindingRevision(SkillContract.Identity identity, Consumer consumer, String businessRunId) {
		return null;
	}

	/**
	 * Only business-authorized targets are returned; never a global consumer directory.
	 */
	default java.util.List<BindingTarget> bindingTargets(String tenantId, String actorId, String beforeId) {
		return java.util.List.of();
	}

	record BindingTarget(Consumer consumer, String displayName, boolean enabled) {
	}

	/**
	 * Check ownership, frozen references, audience and business configuration even for
	 * drafts.
	 */
	void authorizeBinding(String tenantId, String actorId, Consumer consumer, BindingConfiguration binding);

	/** Check execution compatibility and deployment only when enabling the binding. */
	void validateActivation(String tenantId, String actorId, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release);

	/**
	 * Only a trusted in-process worker scope with a current durable lease may opt in.
	 * Supplied IDs or a correlation string alone never prove worker identity. This hook
	 * supplements, rather than replaces, authorizeExecution and input checks.
	 */
	default boolean permitsBackgroundExecution(SkillContract.Identity identity, Consumer consumer,
			String businessRunId) {
		return false;
	}

	/**
	 * Current business/data authorization; management permission does not imply execution
	 * access.
	 */
	default void authorizeExecution(SkillContract.Identity identity, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
	}

	/**
	 * Read-only capability inspection. It cannot issue a token or authorize execution.
	 */
	default void authorizeInspection(SkillContract.Identity identity, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		authorizeExecution(identity, consumer, binding, release);
	}

	/**
	 * Persist consumer migration in the same local transaction as activation; no external
	 * side effects.
	 */
	default void bindingActivated(String tenantId, String actorId, Consumer consumer, BindingConfiguration binding) {
	}

	/** Receives the exact release snapshot checked by the activation transaction. */
	default void bindingActivated(String tenantId, String actorId, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		bindingActivated(tenantId, actorId, consumer, binding);
	}

	record BindingConfiguration(String revisionId, String releaseId, boolean enabled, String audienceJson,
			String activationMode, String parameterJson, String fallbackPolicyJson) {
		public BindingConfiguration {
			Objects.requireNonNull(revisionId);
			Objects.requireNonNull(releaseId);
		}
	}

}
