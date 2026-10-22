package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.skill.api.SkillResultStore;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import org.springframework.stereotype.Component;

@Component
public class ZenflowPreviewResultStore implements SkillResultStore {

	private final ZenflowPreviewRuntime runtime;

	public ZenflowPreviewResultStore(ZenflowPreviewRuntime runtime) {
		this.runtime = runtime;
	}

	public String consumerType() {
		return ZenflowPreviewSkillPolicy.TYPE;
	}

	public String authorizeInput(Identity identity, Consumer consumer, String runId, String inputJson) {
		return runtime.authorizeInput(identity, consumer, runId, inputJson);
	}

	public String save(Identity identity, Consumer consumer, String runId, String skillRunId, String output) {
		return runtime.save(identity, consumer, runId, skillRunId, output);
	}

	public String read(Identity identity, Consumer consumer, String runId, String skillRunId, String reference) {
		return runtime.read(identity, consumer, runId, skillRunId, reference);
	}

}
