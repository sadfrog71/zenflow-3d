package com.deepdimen.matrix.knowledge.skill;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.assistant.LocalAssistantModelGateway;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.*;

/**
 * Read-only readiness for the isolated knowledge adapter; a binding still pins versions.
 */
@Component
public class KnowledgeMarketSkillCapabilities implements AgentSkillCapabilityAdapter {

	private final KnowledgeSkillResources resources;

	private final ObjectProvider<LocalAssistantModelGateway> models;

	private final ObjectProvider<AgentSkillRequestReader> requests;

	private final ObjectProvider<AgentSkillStreamPort> streams;

	private final ObjectProvider<AgentSkillResultContextReader> contexts;

	@Value("${matrix.skill.market.enabled:false}")
	private boolean enabled;

	public KnowledgeMarketSkillCapabilities(KnowledgeSkillResources resources,
			ObjectProvider<LocalAssistantModelGateway> models, ObjectProvider<AgentSkillRequestReader> requests,
			ObjectProvider<AgentSkillStreamPort> streams, ObjectProvider<AgentSkillResultContextReader> contexts) {
		this.resources = resources;
		this.models = models;
		this.requests = requests;
		this.streams = streams;
		this.contexts = contexts;
	}

	@Override
	public String agentType() {
		return "knowledge";
	}

	@Override
	public AgentCapabilities inspect(String tenant, String agentId) {
		if (TenantContextHolder.getTenantId() == null || !TenantContextHolder.getTenantId().toString().equals(tenant)
				|| agentId == null || !agentId.matches("[1-9][0-9]{0,18}") || "2026".equals(agentId))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		if (!enabled || models.getIfAvailable() == null || requests.getIfAvailable() == null
				|| streams.getIfAvailable() == null || contexts.getIfAvailable() == null)
			return unavailable();
		try {
			var fixed = resources.captureConfigured(new Consumer("MARKET_AGENT", agentId, "CHAT", "capability-check"));
			var model = models.getIfAvailable()
				.resolve(new ModelResolutionRequest(AssistantModelContract.VERSION, "knowledge-capability-check",
						fixed.identity(), fixed.modelId(), true, DataClassification.SENSITIVE, true));
			if (!fixed.modelId().equals(model.modelId()) || !model.capabilities().supportsSystemMessage()
					|| !model.capabilities().supportsNativeStreaming()
					|| !model.assistantEgress().allows(DataClassification.SENSITIVE, true))
				return unavailable();
			return new AgentCapabilities(CompatibilityStatus.SUPPORTED, List.of(RuntimeKind.PROMPT), true, false, true,
					true, false, null, null, null);
		}
		catch (RuntimeException failure) {
			return unavailable();
		}
	}

	private static AgentCapabilities unavailable() {
		return new AgentCapabilities(CompatibilityStatus.UNAVAILABLE, List.of(), false, false, false, false, false,
				null, null, ErrorCode.AGENT_ADAPTER_UNAVAILABLE);
	}

}
