package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.dify.DifyServiceClient;
import com.deepdimen.matrix.skill.api.AgentSkillCapabilityAdapter;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Read-only inspection. A declared input alone never proves a published app consumes it.
 */
@Component
@RequiredArgsConstructor
public class DifyMarketSkillCapabilities implements AgentSkillCapabilityAdapter {

	private final AiAgentService agents;

	private final ObjectProvider<DifyServiceClient> clients;

	private final DifyMarketConfigurationReader configurations;

	private final ObjectProvider<com.deepdimen.matrix.discover.controller.DifyProxyController> transports;

	@org.springframework.beans.factory.annotation.Value("${matrix.skill.market.enabled:false}")
	private boolean enabled;

	public String agentType() {
		return "dify";
	}

	public AgentCapabilities inspect(String tenant, String agentId) {
		Long current = TenantContextHolder.getTenantId();
		if (current == null || !current.toString().equals(tenant))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		Long id = Long.valueOf(agentId);
		if (AiAgentService.DEFAULT_AGENT_ID.equals(id))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		var agent = agents.getById(id);
		if (agent == null || !id.equals(agent.getId()) || !tenant.equals(agent.getTenantId())
				|| !"dify".equals(agent.getAgentType()))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		if (!"published".equals(agent.getStatus()))
			return state(CompatibilityStatus.UNAVAILABLE, ErrorCode.RELEASE_NOT_AVAILABLE);
		if (!"chat".equals(agent.getDifyMode()))
			return state(CompatibilityStatus.UNAVAILABLE, ErrorCode.AGENT_ADAPTER_UNAVAILABLE);
		var client = clients.getIfAvailable();
		if (client == null || agent.getDifyApiKey() == null || agent.getDifyApiKey().isBlank())
			return state(CompatibilityStatus.UNAVAILABLE, ErrorCode.AGENT_ADAPTER_UNAVAILABLE);
		JsonNode parameters = client.getParameters(agent.getDifyApiKey()).block(Duration.ofSeconds(5));
		if (parameters == null || !parameters.path("user_input_form").isArray())
			return state(CompatibilityStatus.UNKNOWN, ErrorCode.AGENT_ADAPTER_UNAVAILABLE);
		boolean declared = false;
		for (JsonNode form : parameters.path("user_input_form")) {
			if ("extended_system_prompt".equals(form.path("variable").asText()))
				declared = true;
			var fields = form.elements();
			while (fields.hasNext())
				if ("extended_system_prompt".equals(fields.next().path("variable").asText()))
					declared = true;
		}
		if (!declared)
			return state(CompatibilityStatus.UNSUPPORTED, ErrorCode.AGENT_SKILL_UNSUPPORTED);
		if (!enabled || transports.getIfAvailable() == null)
			return state(CompatibilityStatus.UNAVAILABLE, ErrorCode.AGENT_ADAPTER_UNAVAILABLE);
		// Reuse the execution configuration reader: declaration alone is never
		// sufficient.
		// A specific binding is still checked against its frozen release by the consumer
		// policy.
		var configuration = configurations.read(id);
		if (configuration == null)
			return state(CompatibilityStatus.UNKNOWN, ErrorCode.AGENT_ADAPTER_UNAVAILABLE);
		return new AgentCapabilities(CompatibilityStatus.SUPPORTED, List.of(RuntimeKind.PROMPT), true, false, false,
				true, false, null, null, null);
	}

	private static AgentCapabilities state(CompatibilityStatus status, ErrorCode reason) {
		return new AgentCapabilities(status, List.of(), false, false, false, false, false, null, null, reason);
	}

}
