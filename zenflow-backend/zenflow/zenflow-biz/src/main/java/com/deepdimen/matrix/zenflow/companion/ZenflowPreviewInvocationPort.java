package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.skill.api.SkillPromptInvocationPort;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import org.springframework.stereotype.Component;

@Component
public class ZenflowPreviewInvocationPort implements SkillPromptInvocationPort {

	private final ZenflowPreviewRuntime runtime;

	public ZenflowPreviewInvocationPort(ZenflowPreviewRuntime runtime) {
		this.runtime = runtime;
	}

	public String consumerType() {
		return ZenflowPreviewSkillPolicy.TYPE;
	}

	public boolean available(Consumer consumer, ResolvedSkillSnapshot snapshot) {
		return runtime.available(consumer, snapshot);
	}

	public Output execute(String skillRunId, Identity identity, Consumer consumer, ResolvedSkillSnapshot snapshot,
			String inputJson, Runnable beforeInvocation) {
		return runtime.invoke(skillRunId, identity, consumer, snapshot, inputJson, beforeInvocation);
	}

}
