package com.deepdimen.matrix.skill.api;

import com.deepdimen.matrix.skill.api.SkillContract.*;

/** Optional business model port; it must enforce its own current business identity. */
public interface SkillPromptInvocationPort {

	String consumerType();

	boolean available(Consumer consumer, ResolvedSkillSnapshot snapshot);

	Output execute(String runId, Identity identity, Consumer consumer, ResolvedSkillSnapshot snapshot, String inputJson,
			Runnable beforeInvocation);

	/**
	 * modelId may be absent when the provider does not report it; never substitute an app
	 * ID.
	 */
	enum InvocationMode {

		MODEL, FIXED_REPLY

	}

	record Output(String outputJson, String modelId, String executorVersion, InvocationMode invocationMode) {
		public Output {
			java.util.Objects.requireNonNull(invocationMode);
			if (invocationMode == InvocationMode.FIXED_REPLY && modelId != null)
				throw new IllegalArgumentException("FIXED_REPLY_MUST_NOT_REPORT_MODEL");
		}

		public Output(String outputJson, String modelId, String executorVersion) {
			this(outputJson, modelId, executorVersion, InvocationMode.MODEL);
		}

		public Output(String outputJson, String modelId) {
			this(outputJson, modelId, null);
		}
	}

}
