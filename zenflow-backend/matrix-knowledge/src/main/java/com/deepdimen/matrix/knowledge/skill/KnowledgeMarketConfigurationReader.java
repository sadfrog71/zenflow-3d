package com.deepdimen.matrix.knowledge.skill;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import com.deepdimen.matrix.skill.api.AgentSkillTargetReader;
import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Uses the market's own authorized configuration reader; no cross-module reflection or
 * copied table.
 */
@Component
public class KnowledgeMarketConfigurationReader {

	private final ObjectProvider<AgentSkillTargetReader> targets;

	private final ObjectMapper json;

	public KnowledgeMarketConfigurationReader(ObjectProvider<AgentSkillTargetReader> targets, ObjectMapper json) {
		this.targets = targets;
		this.json = json;
	}

	public KnowledgeMarketConfigurationSnapshot read(Consumer consumer) {
		var reader = targets.getIfAvailable();
		if (reader == null)
			throw new IllegalStateException("AGENT_ADAPTER_UNAVAILABLE");
		var target = reader.read(consumer);
		if (consumer == null || target == null || !java.util.Objects.equals(consumer.id(), target.agentId()))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		return KnowledgeMarketConfigurationSnapshot.capture(target, json);
	}

}
