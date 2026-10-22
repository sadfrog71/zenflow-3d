package com.deepdimen.matrix.skill.api;

import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import com.deepdimen.matrix.skill.api.SkillContract.Identity;

/**
 * Business-owned durable result storage; the Skill run index keeps only an authorized
 * reference.
 */
public interface SkillResultStore {

	String consumerType();

	/** A store can support only some slots during a staged business migration. */
	default boolean supportsConsumer(Consumer consumer) {
		return consumerType().equals(consumer.type());
	}

	/** Business authorization of the exact input, not just its JSON shape. */
	default String authorizeInput(Identity identity, Consumer consumer, String businessRunId, String inputJson) {
		throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
	}

	String save(Identity identity, Consumer consumer, String businessRunId, String skillRunId, String outputJson);

	String read(Identity identity, Consumer consumer, String businessRunId, String skillRunId, String resultRef);

}
