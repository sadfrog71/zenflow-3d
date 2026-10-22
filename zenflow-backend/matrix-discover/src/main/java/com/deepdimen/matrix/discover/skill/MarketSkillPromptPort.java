package com.deepdimen.matrix.discover.skill;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.skill.api.AgentSkillPromptAdapter;
import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import com.deepdimen.matrix.skill.api.SkillContract.Identity;
import com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot;
import com.deepdimen.matrix.skill.api.SkillPromptInvocationPort;

/**
 * Selects only the server-owned market type; it does not replace the center or the chat
 * engines.
 */
@Component
public class MarketSkillPromptPort implements SkillPromptInvocationPort {

	private final com.deepdimen.matrix.skill.api.AgentSkillTargetReader targets;

	private final List<AgentSkillPromptAdapter> adapters;

	public MarketSkillPromptPort(AiAgentService agents, List<AgentSkillPromptAdapter> adapters) {
		this(new MarketSkillTargets(agents), adapters);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public MarketSkillPromptPort(com.deepdimen.matrix.skill.api.AgentSkillTargetReader targets,
			List<AgentSkillPromptAdapter> adapters) {
		this.targets = targets;
		this.adapters = List.copyOf(adapters);
	}

	@Override
	public String consumerType() {
		return "MARKET_AGENT";
	}

	@Override
	public boolean available(Consumer consumer, ResolvedSkillSnapshot snapshot) {
		try {
			return adapter(consumer).available(consumer, snapshot);
		}
		catch (IllegalArgumentException | org.springframework.security.access.AccessDeniedException failure) {
			return false;
		}
	}

	@Override
	public Output execute(String runId, Identity identity, Consumer consumer, ResolvedSkillSnapshot snapshot,
			String inputJson, Runnable beforeInvocation) {
		Long tenant = TenantContextHolder.getTenantId();
		if (identity == null || tenant == null || !tenant.toString().equals(identity.tenantId())
				|| !Objects.equals(identity.actorId(), identity.subjectId())) {
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		}
		Objects.requireNonNull(beforeInvocation);
		AgentSkillPromptAdapter selected = adapter(consumer);
		if (!selected.available(consumer, snapshot)) {
			throw new IllegalArgumentException("EXECUTOR_UNAVAILABLE");
		}
		Output output = selected.execute(runId, identity, consumer, snapshot, inputJson, () -> {
			if (adapter(consumer) != selected) {
				throw new IllegalArgumentException("EXECUTOR_VERSION_DRIFT");
			}
			beforeInvocation.run();
		});
		if (adapter(consumer) != selected) {
			throw new IllegalStateException("MARKET_AGENT_TYPE_CHANGED");
		}
		return output;
	}

	private AgentSkillPromptAdapter adapter(Consumer consumer) {
		var target = targets.read(consumer);
		var matches = adapters.stream()
			.filter(adapter -> Objects.equals(target.agentType(), adapter.agentType()))
			.toList();
		if (matches.size() != 1) {
			throw new IllegalArgumentException(matches.isEmpty() ? "AGENT_ADAPTER_UNAVAILABLE" : "ADAPTER_AMBIGUOUS");
		}
		return matches.get(0);
	}

}
