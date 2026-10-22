package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.discover.controller.DifyProxyController;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.skill.api.AgentSkillPromptAdapter;
import com.deepdimen.matrix.skill.api.SkillPromptInvocationPort.Output;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Calls the existing transport only after the center has acquired the authorized run. */
@Component
@RequiredArgsConstructor
public class DifyMarketSkillPromptPort implements AgentSkillPromptAdapter {

	private final MarketSkillRequestStore requests;

	private final DifyMarketConfigurationReader configurations;

	private final ObjectProvider<DifyProxyController> transports;

	private final ObjectMapper json;

	public String agentType() {
		return "dify";
	}

	public boolean available(Consumer consumer, ResolvedSkillSnapshot snapshot) {
		try {
			Long id = agentId(consumer);
			var frozen = DifyMarketRelease.configuration(snapshot);
			if (transports.getIfAvailable() == null)
				return false;
			frozen.requireSame(configurations.read(id));
			return true;
		}
		catch (IllegalArgumentException | org.springframework.security.access.AccessDeniedException failure) {
			return false;
		}
	}

	public Output execute(String runId, Identity identity, Consumer consumer, ResolvedSkillSnapshot snapshot,
			String inputJson, Runnable beforeInvocation) {
		Long id = agentId(consumer);
		if (runId == null || !runId.matches("[1-9][0-9]{0,18}"))
			throw new IllegalArgumentException("INPUT_INVALID");
		String prefix = "market-skill:";
		if (identity.correlationId() == null || !identity.correlationId().startsWith(prefix))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		String businessId = identity.correlationId().substring(prefix.length());
		String body = requests.authorizeInput(identity, consumer, businessId, inputJson);
		var frozen = DifyMarketRelease.configuration(snapshot);
		frozen.requireSame(configurations.read(id));
		var transport = transports.getIfAvailable();
		if (transport == null)
			throw new IllegalArgumentException("EXECUTOR_UNAVAILABLE");
		if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
				|| attributes.getResponse() == null || attributes.getResponse().isCommitted())
			throw new IllegalArgumentException("MARKET_REQUEST_CONTEXT_REQUIRED");
		try {
			requests.requireConversation(identity, consumer, businessId, snapshot);
			var exchange = new DifyManagedChatExchange(json, snapshot.instructionSnapshot(), () -> {
				requests.requireConversation(identity, consumer, businessId, snapshot);
				frozen.requireSame(configurations.read(id));
				beforeInvocation.run();
			}, requests.conversationObserver(identity, consumer, businessId));
			attributes.getResponse().setHeader("X-Skill-Run-Id", runId);
			attributes.getResponse().setHeader("X-Skill-Release-Id", snapshot.releaseId());
			transport.invokeManagedChat(id, body, attributes.getRequest(), attributes.getResponse(), exchange);
			frozen.requireSame(configurations.read(id));
			requests.requireConversation(identity, consumer, businessId, snapshot);
			return new Output(exchange.result(), null, frozen.digest());
		}
		catch (java.io.IOException failure) {
			throw new IllegalStateException("DIFY_RESULT_UNKNOWN", failure);
		}
	}

	private Long agentId(Consumer consumer) {
		if (consumer == null || !"MARKET_AGENT".equals(consumer.type()) || !"CHAT".equals(consumer.slotCode())
				|| !consumer.bindingKey().matches("[A-Za-z0-9_-]{1,64}"))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		Long id = Long.valueOf(consumer.id());
		if (id <= 0 || AiAgentService.DEFAULT_AGENT_ID.equals(id))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		return id;
	}

}
