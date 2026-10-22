package com.deepdimen.matrix.discover.skill;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.discover.entity.*;
import com.deepdimen.matrix.discover.mapper.*;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Saves authorized business input; a stored request does not grant model execution. */
@Component
@RequiredArgsConstructor
public class MarketSkillRequestStore implements com.deepdimen.matrix.skill.api.AgentSkillRequestReader {

	private final MarketSkillRequestMapper requests;

	private final DifyChatRecordMapper conversations;

	private final AiAgentService agents;

	private final TenantUserDirectory users;

	private final ObjectMapper json;

	public MarketSkillRequestEntity prepare(Long agentId, String bindingKey, String clientRequestId, String body) {
		if (bindingKey == null || !bindingKey.matches("[A-Za-z0-9_-]{1,64}") || clientRequestId == null
				|| !clientRequestId.matches("[A-Za-z0-9_-]{1,64}"))
			throw invalid();
		var access = authorizeCurrent(agentId, body);
		Long tenant = access.tenant(), actor = access.actor();
		JsonNode input = access.input();
		var proposed = new MarketSkillRequestEntity();
		proposed.setId(IdWorker.getId());
		proposed.setTenantId(tenant);
		proposed.setActorId(actor);
		proposed.setAgentId(agentId);
		proposed.setBindingKey(bindingKey);
		proposed.setClientRequestId(clientRequestId);
		proposed.setInputSnapshot(sort(input).toString());
		proposed.setInputDigest(digest(proposed.getInputSnapshot()));
		requests.reserve(proposed);
		var saved = requests.findRequest(tenant, actor, clientRequestId);
		if (saved == null || !tenant.equals(saved.getTenantId()) || !actor.equals(saved.getActorId())
				|| !agentId.equals(saved.getAgentId()) || !bindingKey.equals(saved.getBindingKey())
				|| !clientRequestId.equals(saved.getClientRequestId())
				|| !proposed.getInputDigest().equals(saved.getInputDigest())
				|| !parse(saved.getInputSnapshot()).equals(input))
			throw new IllegalArgumentException("IDEMPOTENCY_CONFLICT");
		return saved;
	}

	/**
	 * Capture authorized IDs before Reactor switches threads; record before exposing a
	 * remote conversation.
	 */
	public java.util.function.Consumer<String> conversationObserver(
			com.deepdimen.matrix.skill.api.SkillContract.Identity identity,
			com.deepdimen.matrix.skill.api.SkillContract.Consumer consumer, String businessRunId) {
		var saved = load(identity, consumer, businessRunId);
		final Long id = saved.getId(), tenant = saved.getTenantId(), actor = saved.getActorId(),
				agent = saved.getAgentId();
		final String digest = saved.getInputDigest();
		return conversation -> {
			if (conversation == null || conversation.isBlank() || conversation.length() > 128)
				throw new IllegalStateException("DIFY_CONVERSATION_RECORD_UNKNOWN");
			Long prior = TenantContextHolder.getTenantId();
			try {
				TenantContextHolder.setTenantId(tenant);
				if (requests.observeConversation(id, tenant, actor, agent, digest, conversation) != 1)
					throw new IllegalStateException("DIFY_CONVERSATION_RECORD_UNKNOWN");
			}
			finally {
				if (prior == null)
					TenantContextHolder.clear();
				else
					TenantContextHolder.setTenantId(prior);
			}
		};
	}

	/**
	 * Existing business records prevent a managed session silently taking the legacy
	 * route.
	 */
	public boolean isManagedConversation(Long agentId, String conversation, boolean allowMissingSchema) {
		if (conversation == null || conversation.isBlank() || AiAgentService.DEFAULT_AGENT_ID.equals(agentId))
			return false;
		var user = SecurityUtils.getUser();
		var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		Long tenant = TenantContextHolder.getTenantId();
		if (auth == null || !auth.isAuthenticated() || user == null || user.getId() == null || tenant == null
				|| !users.isAssignableUser(tenant, user.getId()))
			throw denied();
		try {
			return requests.hasManagedConversation(tenant, user.getId(), agentId, conversation);
		}
		catch (org.springframework.jdbc.BadSqlGrammarException failure) {
			// Legacy installations have no market table. Only that exact MySQL condition
			// may bypass inspection while disabled; outages and partial migrations fail
			// closed.
			var sql = failure.getSQLException();
			if (allowMissingSchema && sql != null && sql.getErrorCode() == 1146 && "42S02".equals(sql.getSQLState()))
				return false;
			throw failure;
		}
	}

	private record Access(Long tenant, Long actor, JsonNode input) {
	}

	private record Actor(Long tenant, Long actor, String username) {
	}

	private Actor authorizeAgent(Long agentId) {
		var user = SecurityUtils.getUser();
		var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		Long tenant = TenantContextHolder.getTenantId();
		if (auth == null || !auth.isAuthenticated() || user == null || user.getId() == null || tenant == null
				|| tenant <= 0 || !users.isAssignableUser(tenant, user.getId()) || agentId == null || agentId <= 0
				|| AiAgentService.DEFAULT_AGENT_ID.equals(agentId))
			throw denied();
		var agent = agents.getById(agentId);
		var detail = agents.getAgentDetail(agentId);
		if (agent == null || !agentId.equals(agent.getId()) || !tenant.toString().equals(agent.getTenantId())
				|| !"published".equals(agent.getStatus()) || detail == null || !agentId.equals(detail.getId())
				|| !Boolean.TRUE.equals(detail.getAccessible()))
			throw denied();
		return new Actor(tenant, user.getId(), user.getUsername());
	}

	private Access authorizeCurrent(Long agentId, String body) {
		var current = authorizeAgent(agentId);
		Long tenant = current.tenant();
		JsonNode input = parse(body);
		if (input.has("result_context_request_id") && !"knowledge".equals(agents.getById(agentId).getAgentType()))
			throw new IllegalArgumentException("KNOWLEDGE_INPUT_UNSUPPORTED");
		String conversation = input.path("conversation_id").asText("");
		if (!conversation.isEmpty() && conversations.selectCount(Wrappers.<DifyChatRecordEntity>query()
			.eq("tenant_id", tenant)
			.eq("agent_id", agentId)
			.eq("username", current.username())
			.eq("conversation_id", conversation)) == 0)
			throw denied();
		return new Access(tenant, current.actor(), input);
	}

	/**
	 * Read-only recovery by the existing client key; never reserves or executes a
	 * request.
	 */
	public MarketSkillRequestEntity findByClientRequest(Long agentId, String clientRequestId) {
		if (clientRequestId == null || !clientRequestId.matches("[A-Za-z0-9_-]{1,64}"))
			throw invalid();
		var actor = authorizeAgent(agentId);
		var row = requests.findRequest(actor.tenant(), actor.actor(), clientRequestId);
		if (row == null)
			return null;
		if (row.getId() == null || row.getId() <= 0 || !actor.tenant().equals(row.getTenantId())
				|| !actor.actor().equals(row.getActorId()) || !agentId.equals(row.getAgentId())
				|| !clientRequestId.equals(row.getClientRequestId()))
			throw denied();
		var identity = new com.deepdimen.matrix.skill.api.SkillContract.Identity(actor.tenant().toString(),
				actor.actor().toString(), actor.actor().toString(), "market-skill:" + row.getId());
		var consumer = new com.deepdimen.matrix.skill.api.SkillContract.Consumer("MARKET_AGENT", agentId.toString(),
				"CHAT", row.getBindingKey());
		return load(identity, consumer, row.getId().toString());
	}

	public MarketSkillRequestEntity load(com.deepdimen.matrix.skill.api.SkillContract.Identity identity,
			com.deepdimen.matrix.skill.api.SkillContract.Consumer consumer, String businessRunId) {
		var user = SecurityUtils.getUser();
		Long tenant = TenantContextHolder.getTenantId();
		if (user == null || user.getId() == null || tenant == null || !tenant.toString().equals(identity.tenantId())
				|| !user.getId().toString().equals(identity.actorId())
				|| !identity.actorId().equals(identity.subjectId()) || !"MARKET_AGENT".equals(consumer.type())
				|| !"CHAT".equals(consumer.slotCode())
				|| !("market-skill:" + businessRunId).equals(identity.correlationId()))
			throw denied();
		Long id;
		try {
			id = Long.valueOf(businessRunId);
		}
		catch (RuntimeException invalidId) {
			throw denied();
		}
		if (id <= 0)
			throw denied();
		var saved = requests.findOwned(tenant, user.getId(), id);
		if (saved == null || !id.equals(saved.getId()) || !tenant.equals(saved.getTenantId())
				|| !user.getId().equals(saved.getActorId()) || saved.getAgentId() == null
				|| !saved.getAgentId().toString().equals(consumer.id())
				|| !Objects.equals(saved.getBindingKey(), consumer.bindingKey()))
			throw denied();
		var access = authorizeCurrent(saved.getAgentId(), saved.getInputSnapshot());
		if (!Objects.equals(saved.getInputDigest(), digest(sort(access.input()).toString())))
			throw new IllegalArgumentException("INPUT_INVALID");
		return saved;
	}

	public String authorizeInput(com.deepdimen.matrix.skill.api.SkillContract.Identity identity,
			com.deepdimen.matrix.skill.api.SkillContract.Consumer consumer, String businessRunId, String inputJson) {
		var saved = load(identity, consumer, businessRunId);
		if (!parse(saved.getInputSnapshot()).equals(parse(inputJson)))
			throw new IllegalArgumentException("IDEMPOTENCY_CONFLICT");
		return saved.getInputSnapshot();
	}

	/**
	 * Reuses the proven initial request and actual successful run; no parallel session
	 * state.
	 */
	public void requireConversation(com.deepdimen.matrix.skill.api.SkillContract.Identity identity,
			com.deepdimen.matrix.skill.api.SkillContract.Consumer consumer, String businessRunId,
			com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot release) {
		var saved = load(identity, consumer, businessRunId);
		var current = parse(saved.getInputSnapshot());
		String conversation = current.path("conversation_id").asText("");
		if (conversation.isEmpty())
			return;
		var origins = requests.findConversationOrigin(saved.getTenantId(), saved.getActorId(), saved.getAgentId(),
				conversation);
		if (origins == null || origins.size() != 1)
			throw new IllegalArgumentException("DIFY_CONVERSATION_CONFIGURATION_UNVERIFIED");
		var origin = origins.get(0);
		var initial = parse(origin.getInputSnapshot());
		if (!Objects.equals(origin.getInputDigest(), digest(sort(initial).toString()))
				|| !consumer.bindingKey().equals(origin.getBindingKey())
				|| !release.releaseId().equals(origin.getReleaseId())
				|| !DifyMarketRelease.configuration(release).digest().equals(origin.getExecutorVersion())
				|| !initial.path("conversation_id").asText("").isEmpty())
			throw new IllegalArgumentException("DIFY_CONVERSATION_CONFIGURATION_UNVERIFIED");
		JsonNode oldInputs = initial.has("inputs") ? initial.get("inputs") : json.createObjectNode();
		JsonNode newInputs = current.has("inputs") ? current.get("inputs") : json.createObjectNode();
		if (!oldInputs.equals(newInputs))
			throw new IllegalArgumentException("DIFY_CONVERSATION_INPUTS_CHANGED");
	}

	private JsonNode parse(String body) {
		if (body == null || body.length() > 262144)
			throw invalid();
		try {
			JsonNode input = json.reader()
				.with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.readTree(body);
			if (input == null || !input.isObject() || !input.path("query").isTextual()
					|| input.path("query").textValue().isBlank() || input.path("query").textValue().length() > 32768)
				throw invalid();
			var fields = input.fieldNames();
			while (fields.hasNext())
				if (!Set.of("query", "inputs", "files", "conversation_id", "response_mode", "result_context_request_id")
					.contains(fields.next()))
					throw invalid();
			if (input.has("result_context_request_id") && (!input.path("result_context_request_id").isTextual()
					|| !input.path("result_context_request_id").asText().matches("[A-Za-z0-9_-]{1,64}")))
				throw invalid();
			if (input.has("conversation_id") && (!input.get("conversation_id").isTextual()
					|| input.get("conversation_id").textValue().length() > 128))
				throw invalid();
			if (input.has("inputs") && (!input.get("inputs").isObject()
					|| input.get("inputs").has("extended_system_prompt") || input.get("inputs").has("enabled_skills")))
				throw invalid();
			if (input.has("files") && (!input.get("files").isArray() || input.get("files").size() > 20))
				throw invalid();
			if (input.has("files"))
				for (JsonNode file : input.get("files")) {
					if (!file.isObject() || file.size() != 3
							|| !"local_file".equals(file.path("transfer_method").asText())
							|| !Set.of("image", "document", "audio", "video", "custom")
								.contains(file.path("type").asText())
							|| !file.path("upload_file_id").isTextual()
							|| !file.path("upload_file_id")
								.textValue()
								.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
						throw invalid();
				}
			if (input.has("response_mode")
					&& !Set.of("streaming", "blocking").contains(input.get("response_mode").asText()))
				throw invalid();
			return input;
		}
		catch (java.io.IOException failure) {
			throw invalid();
		}
	}

	private JsonNode sort(JsonNode value) {
		if (value.isObject()) {
			var fields = new TreeMap<String, JsonNode>();
			value.fields().forEachRemaining(e -> fields.put(e.getKey(), sort(e.getValue())));
			var result = json.createObjectNode();
			fields.forEach(result::set);
			return result;
		}
		if (value.isArray()) {
			var result = json.createArrayNode();
			value.forEach(v -> result.add(sort(v)));
			return result;
		}
		return value;
	}

	private static String digest(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception failure) {
			throw new IllegalStateException(failure);
		}
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("INPUT_INVALID");
	}

	private static IllegalArgumentException denied() {
		return new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
	}

}
