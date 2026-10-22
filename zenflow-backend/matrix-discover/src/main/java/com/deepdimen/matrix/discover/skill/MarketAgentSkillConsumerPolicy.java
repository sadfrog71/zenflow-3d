package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.common.security.component.PermissionService;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Market bindings are independent of legacy/unified-assistant bindings. */
@Component
@RequiredArgsConstructor
public class MarketAgentSkillConsumerPolicy implements SkillConsumerPolicy {

	private final AiAgentService agents;

	private final TenantUserDirectory users;

	private final MarketSkillCapabilities capabilities;

	private final ObjectProvider<SkillExecutionService> executors;

	private final ObjectMapper json;

	private final MarketSkillRequestStore requests;

	public String consumerType() {
		return "MARKET_AGENT";
	}

	public void authorizeBinding(String tenant, String actor, Consumer consumer, BindingConfiguration binding) {
		var user = SecurityUtils.getUser();
		var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated() || user == null || user.getId() == null
				|| !user.getId().toString().equals(actor) || TenantContextHolder.getTenantId() == null
				|| !TenantContextHolder.getTenantId().toString().equals(tenant)
				|| !users.isAssignableUser(TenantContextHolder.getTenantId(), user.getId())
				|| (!new PermissionService().hasPermission("knowledge_skill_enable")
						|| !new PermissionService().hasPermission("knowledge_agent_edit")))
			throw denied();
		requireAgent(tenant, consumer);
		configuration(binding);
	}

	private void configuration(BindingConfiguration binding) {
		try {
			var reader = json.reader()
				.with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
			if (!"EXPLICIT".equals(binding.activationMode())
					|| !reader.readTree(binding.audienceJson()).equals(json.readTree("{\"scope\":\"AGENT\"}"))
					|| !reader.readTree(binding.parameterJson()).isObject()
					|| !reader.readTree(binding.fallbackPolicyJson()).equals(json.readTree("{\"mode\":\"NONE\"}")))
				throw denied();
		}
		catch (java.io.IOException | NullPointerException failure) {
			throw denied();
		}
	}

	public void validateActivation(String tenant, String actor, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		authorizeBinding(tenant, actor, consumer, binding);
		compatible(tenant, consumer, release);
	}

	@Override
	public void authorizeExecution(Identity identity, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		if (!binding.enabled() || !binding.releaseId().equals(release.releaseId())
				|| !binding.revisionId().equals(release.bindingRevisionId()))
			throw denied();
		configuration(binding);
		String correlation = identity.correlationId();
		if (correlation == null || !correlation.startsWith("market-skill:"))
			throw denied();
		String business = correlation.substring("market-skill:".length());
		if (requests.load(identity, consumer, business) == null)
			throw denied();
		compatible(identity.tenantId(), consumer, release);
	}

	private void compatible(String tenant, Consumer consumer, ResolvedSkillSnapshot release) {
		var agent = requireAgent(tenant, consumer);
		if (!"published".equals(agent.getStatus()))
			throw new IllegalArgumentException("RELEASE_NOT_AVAILABLE");
		var supported = capabilities.inspect(tenant, consumer.id(), agent.getAgentType());
		if (supported.status() != CompatibilityStatus.SUPPORTED)
			throw new IllegalArgumentException(
					supported.reasonCode() == null ? "AGENT_ADAPTER_UNAVAILABLE" : supported.reasonCode().name());
		if (!supported.runtimeKinds().contains(release.runtimeKind())
				|| (release.runtimeKind() == RuntimeKind.PROMPT && !supported.instructions())
				|| (release.runtimeKind() != RuntimeKind.PROMPT && !supported.independentActions()))
			throw new IllegalArgumentException("AGENT_SKILL_UNSUPPORTED");
		var executor = executors.getIfAvailable();
		if (executor == null || !executor.available(consumer, release))
			throw new IllegalArgumentException("EXECUTOR_UNAVAILABLE");
	}

	private AiAgentEntity requireAgent(String tenant, Consumer consumer) {
		if (!consumerType().equals(consumer.type()) || !"CHAT".equals(consumer.slotCode())
				|| !consumer.bindingKey().matches("[A-Za-z0-9_-]{1,64}"))
			throw denied();
		Long id;
		try {
			id = Long.valueOf(consumer.id());
		}
		catch (NumberFormatException failure) {
			throw denied();
		}
		if (id <= 0 || AiAgentService.DEFAULT_AGENT_ID.equals(id))
			throw denied();
		var agent = agents.getById(id);
		if (agent == null || !id.equals(agent.getId()) || !tenant.equals(agent.getTenantId()))
			throw denied();
		var detail = agents.getAgentDetail(id);
		if (detail == null || !id.equals(detail.getId()) || !Boolean.TRUE.equals(detail.getAccessible()))
			throw denied();
		return agent;
	}

	private static IllegalArgumentException denied() {
		return new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
	}

}
