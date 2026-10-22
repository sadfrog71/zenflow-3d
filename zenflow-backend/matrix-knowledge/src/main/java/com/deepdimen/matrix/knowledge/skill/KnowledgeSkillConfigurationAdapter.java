package com.deepdimen.matrix.knowledge.skill;

import org.springframework.stereotype.Component;
import com.deepdimen.matrix.skill.api.AgentSkillConfigurationAdapter;
import com.deepdimen.matrix.skill.api.KnowledgeSkillRelease;
import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import com.deepdimen.matrix.skill.api.SkillContract.ExecutorRef;

/** Returns identifiers and fingerprints only; never raw model/storage credentials. */
@Component
public class KnowledgeSkillConfigurationAdapter implements AgentSkillConfigurationAdapter {

	private final KnowledgeSkillResources resources;

	public KnowledgeSkillConfigurationAdapter(KnowledgeSkillResources resources) {
		this.resources = resources;
	}

	@Override
	public String agentType() {
		return "knowledge";
	}

	@Override
	public Configuration inspect(Consumer consumer) {
		var fixed = resources.captureConfigured(consumer);
		var executor = new ExecutorRef("PROMPT", "KNOWLEDGE", fixed.modelId(), fixed.digest());
		KnowledgeSkillRelease.from(executor, fixed.resources());
		return new Configuration(executor, fixed.resources());
	}

}
