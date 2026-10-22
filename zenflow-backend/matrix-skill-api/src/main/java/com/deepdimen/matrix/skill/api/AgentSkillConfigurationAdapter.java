package com.deepdimen.matrix.skill.api;

import java.util.List;
import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import com.deepdimen.matrix.skill.api.SkillContract.ExecutorRef;
import com.deepdimen.matrix.skill.api.SkillContract.ResourceRef;

/** Read-only release preparation from existing authorized agent resources. */
public interface AgentSkillConfigurationAdapter {

	String agentType();

	Configuration inspect(Consumer consumer);

	record Configuration(ExecutorRef executorRef, List<ResourceRef> resourceRefs) {
		public Configuration {
			resourceRefs = List.copyOf(resourceRefs);
		}
	}

}
