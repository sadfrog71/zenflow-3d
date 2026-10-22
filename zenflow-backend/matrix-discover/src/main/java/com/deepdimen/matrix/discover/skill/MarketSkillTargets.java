package com.deepdimen.matrix.discover.skill;

import org.springframework.stereotype.Component;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.skill.api.AgentSkillTargetReader;
import com.deepdimen.matrix.skill.api.SkillContract.Consumer;

/** Reuses the existing market record and ACL; no configuration is written here. */
@Component
public class MarketSkillTargets implements AgentSkillTargetReader {

	private final AiAgentService agents;

	public MarketSkillTargets(AiAgentService agents) {
		this.agents = agents;
	}

	@Override
	public Target read(Consumer consumer) {
		Long tenant = TenantContextHolder.getTenantId();
		if (tenant == null || tenant <= 0 || consumer == null || !"MARKET_AGENT".equals(consumer.type())
				|| !"CHAT".equals(consumer.slotCode()) || consumer.id() == null
				|| !consumer.id().matches("[1-9][0-9]{0,18}")) {
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		}
		Long id = Long.valueOf(consumer.id());
		if (AiAgentService.DEFAULT_AGENT_ID.equals(id)) {
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		}
		var agent = agents.getById(id);
		var detail = agents.getAgentDetail(id);
		if (agent == null || !id.equals(agent.getId()) || !tenant.toString().equals(agent.getTenantId())
				|| !"published".equals(agent.getStatus()) || detail == null || !id.equals(detail.getId())
				|| !Boolean.TRUE.equals(detail.getAccessible())) {
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		}
		return new Target(consumer.id(), agent.getAgentType(), agent.getConfig());
	}

}
