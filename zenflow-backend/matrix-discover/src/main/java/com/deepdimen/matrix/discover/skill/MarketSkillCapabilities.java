package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.skill.api.AgentSkillCapabilityAdapter;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MarketSkillCapabilities {

	private final List<AgentSkillCapabilityAdapter> adapters;

	public MarketSkillCapabilities(List<AgentSkillCapabilityAdapter> adapters) {
		this.adapters = List.copyOf(adapters);
	}

	public AgentCapabilities inspect(String tenant, String agentId, String agentType) {
		var matches = adapters.stream().filter(a -> java.util.Objects.equals(agentType, a.agentType())).toList();
		if (matches.isEmpty())
			return unavailable(CompatibilityStatus.UNAVAILABLE, ErrorCode.AGENT_ADAPTER_UNAVAILABLE);
		if (matches.size() != 1)
			return unavailable(CompatibilityStatus.UNKNOWN, ErrorCode.ADAPTER_AMBIGUOUS);
		try {
			var capability = matches.get(0).inspect(tenant, agentId);
			if (capability == null)
				return unavailable(CompatibilityStatus.UNKNOWN, ErrorCode.AGENT_ADAPTER_UNAVAILABLE);
			return capability;
		}
		catch (RuntimeException failure) {
			return unavailable(CompatibilityStatus.UNKNOWN, ErrorCode.AGENT_ADAPTER_UNAVAILABLE);
		}
	}

	private static AgentCapabilities unavailable(CompatibilityStatus status, ErrorCode reason) {
		return new AgentCapabilities(status, List.of(), false, false, false, false, false, null, null, reason);
	}

}
