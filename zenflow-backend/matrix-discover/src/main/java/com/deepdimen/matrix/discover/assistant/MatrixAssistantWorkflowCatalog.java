package com.deepdimen.matrix.discover.assistant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.WorkflowCapabilitySnapshot;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.capability.AssistantWorkflowCatalog;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.mapper.AiAgentMapper;
import com.deepdimen.matrix.discover.service.AiAgentPermissionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Projects the same published, tenant-scoped and ACL-filtered Dify Workflow agents shown
 * by the Agent marketplace into the native Assistant routing catalog.
 */
@Component
public class MatrixAssistantWorkflowCatalog implements AssistantWorkflowCatalog {

	public static final Set<String> MARKET_DIFY_MODES = Set.of("workflow", "advanced-chat", "agent-chat", "chat",
			"completion");

	public static final String MARKET_CAPABILITY_PREFIX = "market-agent-";

	public static final String MARKET_INPUT_SCHEMA_ID = "market-query-input/v1";

	public static final String MARKET_OUTPUT_SCHEMA_ID = "market-workflow-output/v1";

	@org.springframework.beans.factory.annotation.Value("${matrix.assistant.routing.candidate-retrieval-enabled:false}")
	private boolean candidateRetrievalEnabled;

	private final AiAgentMapper agentMapper;

	private final AiAgentPermissionService permissionService;

	private final ObjectMapper objectMapper;

	public MatrixAssistantWorkflowCatalog(AiAgentMapper agentMapper, AiAgentPermissionService permissionService,
			ObjectMapper objectMapper) {
		this.agentMapper = agentMapper;
		this.permissionService = permissionService;
		this.objectMapper = objectMapper;
	}

	@Override
	public List<PublishedWorkflowCapability> listPublished(IdentityEnvelope identity) {
		if (identity == null || !StringUtils.hasText(identity.tenantId()) || !StringUtils.hasText(identity.userId())) {
			return List.of();
		}
		Long previousTenant = TenantContextHolder.getTenantId();
		try {
			TenantContextHolder.setTenantId(Long.valueOf(identity.tenantId()));
			Map<String, AiAgentEntity> latestByName = new LinkedHashMap<>();
			agentMapper
				.selectList(Wrappers.<AiAgentEntity>lambdaQuery()
					.eq(AiAgentEntity::getTenantId, identity.tenantId())
					.eq(AiAgentEntity::getStatus, "published")
					.eq(AiAgentEntity::getDelFlag, "0")
					.eq(AiAgentEntity::getAgentType, "dify")
					.in(AiAgentEntity::getDifyMode, MARKET_DIFY_MODES)
					.orderByDesc(AiAgentEntity::getSort)
					.orderByDesc(AiAgentEntity::getCreateTime))
				.stream()
				.filter(agent -> permissionService.isAccessible(agent.getId(), identity))
				.filter(agent -> StringUtils.hasText(agent.getName()))
				.forEach(agent -> latestByName.putIfAbsent(
						candidateRetrievalEnabled ? String.valueOf(agent.getId()) : logicalName(agent.getName()),
						agent));
			return latestByName.values()
				.stream()
				.map(agent -> map(identity, agent))
				.filter(java.util.Objects::nonNull)
				.toList();
		}
		catch (NumberFormatException ex) {
			return List.of();
		}
		finally {
			if (previousTenant == null) {
				TenantContextHolder.clear();
			}
			else {
				TenantContextHolder.setTenantId(previousTenant);
			}
		}
	}

	private String logicalName(String value) {
		return normalizeText(value).toLowerCase(Locale.ROOT);
	}

	private PublishedWorkflowCapability map(IdentityEnvelope identity, AiAgentEntity agent) {
		if (agent == null || agent.getId() == null || !identity.tenantId().equals(agent.getTenantId())
				|| !StringUtils.hasText(agent.getName()) || !StringUtils.hasText(agent.getDifyAppId())
				|| !StringUtils.hasText(agent.getDifyApiKey())) {
			return null;
		}
		List<String> operationRules = operationRules(agent.getOperationGuide());
		List<String> intents = intentExamples(agent, operationRules);
		String releaseDigest = sha256(String.join("|", identity.tenantId(), String.valueOf(agent.getId()),
				agent.getDifyAppId(), defaultString(agent.getDifyMode()), defaultString(agent.getName()),
				defaultString(agent.getDescription()), defaultString(agent.getUseScenes()),
				defaultString(agent.getOperationGuide()), String.valueOf(agent.getUpdateTime())));
		WorkflowCapabilitySnapshot snapshot = new WorkflowCapabilitySnapshot(MARKET_CAPABILITY_PREFIX + agent.getId(),
				agent.getId(), "market", agent.getName(), bounded(defaultString(agent.getDescription()), 1000), intents,
				operationRules, MARKET_INPUT_SCHEMA_ID, MARKET_OUTPUT_SCHEMA_ID, agent.getDifyAppId(), releaseDigest,
				List.of(), candidateRetrievalEnabled ? agent.getTags() : List.of());
		return new PublishedWorkflowCapability(agent.getTenantId(), snapshot, true, false, true, true,
				SideEffectClass.READ_ONLY, DataClassification.SENSITIVE, List.of());
	}

	private List<String> intentExamples(AiAgentEntity agent, List<String> operationRules) {
		Set<String> values = new LinkedHashSet<>(readTextValues(agent.getUseScenes()));
		operationRules.stream()
			.filter(value -> value.endsWith("？") || value.endsWith("?") || value.startsWith("示例"))
			.forEach(values::add);
		if (values.isEmpty()) {
			values.add("使用" + agent.getName());
		}
		return values.stream().map(value -> bounded(value, 500)).limit(20).toList();
	}

	private List<String> operationRules(String guide) {
		if (!StringUtils.hasText(guide)) {
			return List.of();
		}
		Set<String> rules = new LinkedHashSet<>();
		for (Element element : Jsoup.parse(guide).select("h1,h2,h3,h4,h5,h6,p,li")) {
			String text = normalizeText(element.text());
			if (StringUtils.hasText(text)) {
				rules.add(bounded(text, 500));
			}
			if (rules.size() >= 20) {
				break;
			}
		}
		if (rules.isEmpty()) {
			String text = normalizeText(Jsoup.parse(guide).text());
			if (StringUtils.hasText(text)) {
				rules.add(bounded(text, 500));
			}
		}
		return List.copyOf(rules);
	}

	private List<String> readTextValues(String value) {
		if (!StringUtils.hasText(value)) {
			return List.of();
		}
		try {
			JsonNode root = objectMapper.readTree(value);
			List<String> result = new ArrayList<>();
			collectText(root, result);
			return result.stream().filter(StringUtils::hasText).map(this::normalizeText).distinct().limit(20).toList();
		}
		catch (JsonProcessingException ex) {
			return List.of(normalizeText(value));
		}
	}

	private void collectText(JsonNode node, List<String> result) {
		if (node == null || result.size() >= 40) {
			return;
		}
		if (node.isTextual()) {
			result.add(node.asText());
		}
		else if (node.isArray()) {
			node.forEach(item -> collectText(item, result));
		}
		else if (node.isObject()) {
			node.elements().forEachRemaining(item -> collectText(item, result));
		}
	}

	private String normalizeText(String value) {
		return defaultString(value).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
	}

	private String bounded(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}

	private String defaultString(String value) {
		return value == null ? "" : value;
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

}
