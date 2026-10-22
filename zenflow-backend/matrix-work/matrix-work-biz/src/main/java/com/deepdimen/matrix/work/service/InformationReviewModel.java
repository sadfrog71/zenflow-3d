package com.deepdimen.matrix.work.service;

/**
 * Optional model port for the information review pipeline.
 *
 * <p>
 * The matrix-work service must not load matrix-knowledge implementation classes. A cloud
 * adapter can implement this port through the platform's signed model boundary; without
 * one, candidates remain quarantined.
 * </p>
 */
public interface InformationReviewModel {

	String chat(String conversationId, String systemPrompt, String userPrompt);

	/** Existing adapters do not imply fixed-model execution support. */
	default boolean supportsManagedModel(String modelId) {
		return false;
	}

	default ManagedResponse chatManaged(String conversationId, String modelId, String systemPrompt, String userPrompt) {
		throw new IllegalStateException("INFORMATION_MODEL_UNAVAILABLE");
	}

	record ManagedResponse(String outputJson, String actualModelId) {
	}

}
