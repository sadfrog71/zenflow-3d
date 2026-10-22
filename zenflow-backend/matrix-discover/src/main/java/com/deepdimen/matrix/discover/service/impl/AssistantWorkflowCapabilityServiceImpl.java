package com.deepdimen.matrix.discover.service.impl;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.capability.AssistantWorkflowCatalog.SideEffectClass;
import com.deepdimen.matrix.common.xss.utils.XssUtil;
import com.deepdimen.matrix.discover.dify.api.DifyAppApi;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityEntity;
import com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityReleaseEntity;
import com.deepdimen.matrix.discover.mapper.AiAgentMapper;
import com.deepdimen.matrix.discover.mapper.AiAssistantWorkflowCapabilityMapper;
import com.deepdimen.matrix.discover.mapper.AiAssistantWorkflowCapabilityReleaseMapper;
import com.deepdimen.matrix.discover.service.AssistantWorkflowCapabilityService;
import com.deepdimen.matrix.discover.service.AssistantWorkflowCapabilityService.WorkflowCapabilityInspection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Publishes immutable, fail-closed Workflow Capability releases. */
@Service
public class AssistantWorkflowCapabilityServiceImpl implements AssistantWorkflowCapabilityService {

	private static final Pattern CAPABILITY_CODE = Pattern.compile("[a-z][a-z0-9-]{1,63}");

	private static final Pattern URL = Pattern.compile("https?://[^\\s'\\\"<>]+", Pattern.CASE_INSENSITIVE);

	private static final Pattern NODE_DATA = Pattern.compile("(?m)^([ \\t]*)-[ \\t]+data[ \\t]*:[ \\t]*$");

	private static final Set<String> FORBIDDEN_NODE_TYPES = Set.of("knowledge-retrieval", "knowledge_retrieval",
			"agent");

	private final AiAssistantWorkflowCapabilityMapper capabilityMapper;

	private final AiAssistantWorkflowCapabilityReleaseMapper releaseMapper;

	private final AiAgentMapper agentMapper;

	private final DifyAppApi difyAppApi;

	private final ObjectMapper objectMapper;

	public AssistantWorkflowCapabilityServiceImpl(AiAssistantWorkflowCapabilityMapper capabilityMapper,
			AiAssistantWorkflowCapabilityReleaseMapper releaseMapper, AiAgentMapper agentMapper, DifyAppApi difyAppApi,
			ObjectMapper objectMapper) {
		this.capabilityMapper = capabilityMapper;
		this.releaseMapper = releaseMapper;
		this.agentMapper = agentMapper;
		this.difyAppApi = difyAppApi;
		this.objectMapper = objectMapper;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public AiAssistantWorkflowCapabilityEntity saveDraft(IdentityEnvelope identity, Long agentId,
			WorkflowCapabilityDraftRequest request) {
		AiAgentEntity agent = requireWorkflowAgent(identity, agentId);
		if (request == null) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_DRAFT_REQUIRED");
		}
		String code = StringUtils.hasText(request.capabilityCode()) ? request.capabilityCode().trim()
				: "agent-" + agentId;
		if (!CAPABILITY_CODE.matcher(code).matches()) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_CODE_INVALID");
		}
		List<String> intents = boundedStrings(request.intentExamples(), 20, 500, "intentExamples");
		if (intents.isEmpty()) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_INTENT_EXAMPLES_REQUIRED");
		}
		String inputSchema = normalizeSchema(request.inputSchemaJson(), "inputSchemaJson");
		String outputSchema = normalizeSchema(request.outputSchemaJson(), "outputSchemaJson");
		List<String> providers = normalizeProviders(request.requiredProviderCodes());
		List<String> egress = normalizeEgress(request.egressAllowlist());
		boolean allowCodeNode = Boolean.TRUE.equals(request.allowCodeNode());
		List<String> toolAllowlist = normalizeToolAllowlist(request.toolAllowlist());
		SideEffectClass sideEffectClass = request.sideEffectClass() == null ? SideEffectClass.UNKNOWN
				: request.sideEffectClass();
		if (sideEffectClass == SideEffectClass.WRITE || sideEffectClass == SideEffectClass.UNKNOWN) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_SIDE_EFFECT_NOT_ALLOWED");
		}
		int timeoutMs = request.timeoutMs() == null ? 30000 : request.timeoutMs();
		if (timeoutMs < 1000 || timeoutMs > 120000) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_TIMEOUT_INVALID");
		}
		AiAssistantWorkflowCapabilityEntity entity = capabilityMapper
			.selectOne(Wrappers.<AiAssistantWorkflowCapabilityEntity>lambdaQuery()
				.eq(AiAssistantWorkflowCapabilityEntity::getAgentId, agentId)
				.last("LIMIT 1"));
		if (entity == null) {
			entity = new AiAssistantWorkflowCapabilityEntity();
			entity.setTenantId(identity.tenantId());
			entity.setAgentId(agentId);
		}
		else {
			requireTenant(identity, entity.getTenantId());
		}
		entity.setCapabilityCode(code);
		entity.setIntentExamplesJson(write(intents));
		entity.setInputSchemaJson(inputSchema);
		entity.setOutputSchemaJson(outputSchema);
		entity.setRequiredProvidersJson(write(providers));
		entity.setDataClassification(
				(request.dataClassification() == null ? DataClassification.SENSITIVE : request.dataClassification())
					.name());
		entity.setSideEffectClass(sideEffectClass.name());
		entity.setEgressAllowlistJson(write(egress));
		entity.setAllowCodeNode(allowCodeNode ? 1 : 0);
		entity.setToolAllowlistJson(write(toolAllowlist));
		entity.setTimeoutMs(timeoutMs);
		String currentSourceDigest = sourceDigest(agent, entity);
		if (!StringUtils.hasText(entity.getActiveSourceDigest())) {
			entity.setStatus("DRAFT");
		}
		else {
			entity.setStatus(entity.getActiveSourceDigest().equals(currentSourceDigest) ? "PUBLISHED" : "STALE");
		}
		if (entity.getId() == null) {
			capabilityMapper.insert(entity);
		}
		else {
			capabilityMapper.updateById(entity);
		}
		return entity;
	}

	@Override
	public WorkflowCapabilityDetail get(IdentityEnvelope identity, Long capabilityId) {
		AiAssistantWorkflowCapabilityEntity draft = requireCapability(identity, capabilityId, false);
		List<AiAssistantWorkflowCapabilityReleaseEntity> releases = releases(draft.getId());
		AiAssistantWorkflowCapabilityReleaseEntity active = releases.stream()
			.filter(item -> "ACTIVE".equals(item.getReleaseStatus()))
			.findFirst()
			.orElse(null);
		boolean stale = StringUtils.hasText(draft.getActiveSourceDigest())
				&& !sourceDigest(requireWorkflowAgent(identity, draft.getAgentId()), draft)
					.equals(draft.getActiveSourceDigest());
		return new WorkflowCapabilityDetail(draft, active, stale, releases);
	}

	@Override
	public List<WorkflowCapabilityDetail> list(IdentityEnvelope identity) {
		return capabilityMapper
			.selectList(Wrappers.<AiAssistantWorkflowCapabilityEntity>lambdaQuery()
				.eq(AiAssistantWorkflowCapabilityEntity::getTenantId, identity.tenantId())
				.orderByAsc(AiAssistantWorkflowCapabilityEntity::getId))
			.stream()
			.map(item -> get(identity, item.getId()))
			.toList();
	}

	@Override
	public WorkflowCapabilityInspection inspect(IdentityEnvelope identity, Long agentId) {
		AiAgentEntity agent = requireWorkflowAgent(identity, agentId);
		JsonNode app = difyAppApi.getApp(agent.getDifyAppId());
		if (app == null || !"workflow".equals(app.path("mode").asText(agent.getDifyMode()))) {
			throw new IllegalArgumentException("DIFY_WORKFLOW_NOT_INSPECTABLE");
		}
		String definition = exportedDefinition(difyAppApi.exportApp(agent.getDifyAppId()));
		WorkflowStructure structure = structure(definition);
		return new WorkflowCapabilityInspection(sha256(definition), structure.codeNodePresent(),
				structure.toolIdentities(), structure.forbiddenNodeTypes(), structure.httpEgressHosts());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public AiAssistantWorkflowCapabilityReleaseEntity publish(IdentityEnvelope identity, Long capabilityId) {
		AiAssistantWorkflowCapabilityEntity draft = requireCapability(identity, capabilityId, true);
		AiAgentEntity agent = requireWorkflowAgent(identity, draft.getAgentId());
		String cleanedHtml = bounded(XssUtil.clean(defaultString(agent.getOperationGuide())), 16000);
		List<String> operationRules = operationRules(cleanedHtml);
		if (operationRules.isEmpty()) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_OPERATION_GUIDE_REQUIRED");
		}
		String guideText = bounded(normalizeText(Jsoup.parse(cleanedHtml).text()), 8000);
		JsonNode app = difyAppApi.getApp(agent.getDifyAppId());
		if (app == null || !"workflow".equals(app.path("mode").asText(agent.getDifyMode()))) {
			throw new IllegalArgumentException("DIFY_PUBLISHED_WORKFLOW_NOT_INSPECTABLE");
		}
		List<String> egress = readList(draft.getEgressAllowlistJson());
		List<String> toolAllowlist = readList(draft.getToolAllowlistJson());
		boolean allowCodeNode = Integer.valueOf(1).equals(draft.getAllowCodeNode());
		String definition = exportedDefinition(difyAppApi.exportApp(agent.getDifyAppId()));
		PolicyAudit audit = audit(definition, egress, allowCodeNode, toolAllowlist);
		if (!audit.passed()) {
			throw new IllegalArgumentException(
					"WORKFLOW_CAPABILITY_POLICY_AUDIT_FAILED:" + String.join(",", audit.reasons()));
		}
		JsonNode published = difyAppApi.publishWorkflow(agent.getDifyAppId());
		String publishedDefinition = exportedDefinition(difyAppApi.exportApp(agent.getDifyAppId()));
		PolicyAudit publishedAudit = audit(publishedDefinition, egress, allowCodeNode, toolAllowlist);
		if (!publishedAudit.passed()) {
			throw new IllegalArgumentException(
					"WORKFLOW_CAPABILITY_POLICY_AUDIT_FAILED:" + String.join(",", publishedAudit.reasons()));
		}
		definition = publishedDefinition;
		audit = publishedAudit;
		String workflowDefinitionDigest = sha256(definition);
		String publishedWorkflowId = firstText(published, "id", "workflow_id");
		if (!StringUtils.hasText(publishedWorkflowId)) {
			publishedWorkflowId = firstText(app, "workflow_id", "workflow.id");
		}
		if (!StringUtils.hasText(publishedWorkflowId)) {
			// Dify editions that do not expose a release ID still get an immutable exact
			// identity by binding the app to the exported definition digest.
			publishedWorkflowId = agent.getDifyAppId() + "@sha256:" + workflowDefinitionDigest;
		}
		String sourceDigest = sourceDigest(agent, draft);
		Map<String, Object> routingProfile = new LinkedHashMap<>();
		routingProfile.put("displayName", bounded(normalizeText(agent.getName()), 200));
		routingProfile.put("description", bounded(normalizeText(agent.getDescription()), 1000));
		routingProfile.put("intentExamples", readList(draft.getIntentExamplesJson()));
		routingProfile.put("operationRules", operationRules);
		routingProfile.put("requiredProviders", readList(draft.getRequiredProvidersJson()));
		routingProfile.put("sideEffectClass", draft.getSideEffectClass());
		String routingProfileDigest = sha256(write(routingProfile));

		AiAssistantWorkflowCapabilityReleaseEntity release = new AiAssistantWorkflowCapabilityReleaseEntity();
		release.setTenantId(identity.tenantId());
		release.setCapabilityId(draft.getId());
		release.setAgentId(draft.getAgentId());
		release.setCapabilityCode(draft.getCapabilityCode());
		release.setReleaseVersion(nextReleaseVersion(draft.getId()));
		release.setReleaseStatus("ACTIVE");
		release.setDisplayName(String.valueOf(routingProfile.get("displayName")));
		release.setDescription(String.valueOf(routingProfile.get("description")));
		release.setOperationGuideHtml(cleanedHtml);
		release.setOperationGuideText(guideText);
		release.setOperationRulesJson(write(operationRules));
		release.setOperationGuideHtmlDigest(sha256(cleanedHtml));
		release.setOperationGuideTextDigest(sha256(guideText));
		release.setRoutingProfileDigest(routingProfileDigest);
		release.setSourceDigest(sourceDigest);
		release.setIntentExamplesJson(draft.getIntentExamplesJson());
		release.setInputSchemaJson(draft.getInputSchemaJson());
		release.setInputSchemaId("sha256:" + sha256(draft.getInputSchemaJson()));
		release.setOutputSchemaJson(draft.getOutputSchemaJson());
		release.setOutputSchemaId("sha256:" + sha256(draft.getOutputSchemaJson()));
		release.setRequiredProvidersJson(draft.getRequiredProvidersJson());
		release.setDataClassification(draft.getDataClassification());
		release.setSideEffectClass(draft.getSideEffectClass());
		release.setEgressAllowlistJson(draft.getEgressAllowlistJson());
		release.setAllowCodeNode(draft.getAllowCodeNode());
		release.setToolAllowlistJson(draft.getToolAllowlistJson());
		release.setTimeoutMs(draft.getTimeoutMs());
		release.setDifyAppId(agent.getDifyAppId());
		release.setPublishedWorkflowId(publishedWorkflowId);
		release.setWorkflowDefinitionDigest(workflowDefinitionDigest);
		release.setPolicyAuditPassed(1);
		release.setPolicyAuditJson(write(audit));
		release.setReleasedAt(LocalDateTime.now());
		release.setReleaseDigest(releaseDigest(release));

		releaseMapper.update(null,
				Wrappers.<AiAssistantWorkflowCapabilityReleaseEntity>lambdaUpdate()
					.eq(AiAssistantWorkflowCapabilityReleaseEntity::getCapabilityId, draft.getId())
					.eq(AiAssistantWorkflowCapabilityReleaseEntity::getReleaseStatus, "ACTIVE")
					.set(AiAssistantWorkflowCapabilityReleaseEntity::getReleaseStatus, "INACTIVE"));
		releaseMapper.insert(release);
		draft.setActiveReleaseId(release.getId());
		draft.setActiveSourceDigest(sourceDigest);
		draft.setStatus("PUBLISHED");
		capabilityMapper.updateById(draft);
		return release;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public AiAssistantWorkflowCapabilityReleaseEntity rollback(IdentityEnvelope identity, Long capabilityId,
			Long releaseId) {
		AiAssistantWorkflowCapabilityEntity capability = requireCapability(identity, capabilityId, true);
		AiAssistantWorkflowCapabilityReleaseEntity target = releaseMapper.selectById(releaseId);
		if (target == null || !capabilityId.equals(target.getCapabilityId())) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_RELEASE_NOT_FOUND");
		}
		requireTenant(identity, target.getTenantId());
		if (target.getRevokedAt() != null || "REVOKED".equals(target.getReleaseStatus())) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_RELEASE_REVOKED");
		}
		releaseMapper.update(null,
				Wrappers.<AiAssistantWorkflowCapabilityReleaseEntity>lambdaUpdate()
					.eq(AiAssistantWorkflowCapabilityReleaseEntity::getCapabilityId, capabilityId)
					.eq(AiAssistantWorkflowCapabilityReleaseEntity::getReleaseStatus, "ACTIVE")
					.set(AiAssistantWorkflowCapabilityReleaseEntity::getReleaseStatus, "INACTIVE"));
		target.setReleaseStatus("ACTIVE");
		releaseMapper.updateById(target);
		capability.setActiveReleaseId(target.getId());
		capability.setActiveSourceDigest(target.getSourceDigest());
		capability.setStatus("PUBLISHED");
		capabilityMapper.updateById(capability);
		return target;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void revoke(IdentityEnvelope identity, Long capabilityId) {
		AiAssistantWorkflowCapabilityEntity capability = requireCapability(identity, capabilityId, true);
		releaseMapper.update(null,
				Wrappers.<AiAssistantWorkflowCapabilityReleaseEntity>lambdaUpdate()
					.eq(AiAssistantWorkflowCapabilityReleaseEntity::getCapabilityId, capabilityId)
					.eq(AiAssistantWorkflowCapabilityReleaseEntity::getReleaseStatus, "ACTIVE")
					.set(AiAssistantWorkflowCapabilityReleaseEntity::getReleaseStatus, "REVOKED")
					.set(AiAssistantWorkflowCapabilityReleaseEntity::getRevokedAt, LocalDateTime.now()));
		capability.setStatus("REVOKED");
		capability.setActiveReleaseId(null);
		capabilityMapper.updateById(capability);
	}

	private AiAssistantWorkflowCapabilityEntity requireCapability(IdentityEnvelope identity, Long id, boolean lock) {
		if (id == null) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_NOT_FOUND");
		}
		var query = Wrappers.<AiAssistantWorkflowCapabilityEntity>lambdaQuery()
			.eq(AiAssistantWorkflowCapabilityEntity::getId, id)
			.last(lock ? "LIMIT 1 FOR UPDATE" : "LIMIT 1");
		AiAssistantWorkflowCapabilityEntity capability = capabilityMapper.selectOne(query);
		if (capability == null) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_NOT_FOUND");
		}
		requireTenant(identity, capability.getTenantId());
		return capability;
	}

	private AiAgentEntity requireWorkflowAgent(IdentityEnvelope identity, Long agentId) {
		AiAgentEntity agent = agentId == null ? null : agentMapper.selectById(agentId);
		if (agent == null) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_AGENT_NOT_FOUND");
		}
		requireTenant(identity, agent.getTenantId());
		if (!"dify".equals(agent.getAgentType()) || !"workflow".equals(agent.getDifyMode())
				|| !StringUtils.hasText(agent.getDifyAppId())) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_REQUIRES_DIFY_WORKFLOW_AGENT");
		}
		return agent;
	}

	private void requireTenant(IdentityEnvelope identity, String tenantId) {
		if (identity == null || !StringUtils.hasText(identity.tenantId()) || !identity.tenantId().equals(tenantId)) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_NOT_FOUND");
		}
	}

	private String normalizeSchema(String json, String field) {
		if (!StringUtils.hasText(json) || json.length() > 32768) {
			throw new IllegalArgumentException(field + " is required and must be <= 32768 characters");
		}
		try {
			JsonNode schema = objectMapper.readTree(json);
			if (schema == null || !schema.isObject() || !"object".equals(schema.path("type").asText())) {
				throw new IllegalArgumentException(field + " must be a JSON object schema with type=object");
			}
			return objectMapper.writeValueAsString(schema);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException(field + " must be valid JSON", ex);
		}
	}

	private List<String> normalizeProviders(List<String> values) {
		List<String> normalized = boundedStrings(values, 16, 64, "requiredProviderCodes").stream()
			.map(String::toUpperCase)
			.peek(ProviderId::of)
			.toList();
		return List.copyOf(new LinkedHashSet<>(normalized));
	}

	private List<String> normalizeEgress(List<String> values) {
		List<String> normalized = boundedStrings(values, 16, 253, "egressAllowlist").stream()
			.map(value -> value.toLowerCase(Locale.ROOT))
			.toList();
		for (String host : normalized) {
			if (!host.matches("[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?")) {
				throw new IllegalArgumentException("WORKFLOW_CAPABILITY_EGRESS_HOST_INVALID");
			}
		}
		return List.copyOf(new LinkedHashSet<>(normalized));
	}

	private List<String> normalizeToolAllowlist(List<String> values) {
		List<String> normalized = boundedStrings(values, 64, 512, "toolAllowlist").stream()
			.map(this::normalizeToolIdentity)
			.toList();
		return List.copyOf(new LinkedHashSet<>(normalized));
	}

	private List<String> boundedStrings(List<String> values, int maxItems, int maxLength, String field) {
		if (values == null) {
			return List.of();
		}
		if (values.size() > maxItems) {
			throw new IllegalArgumentException(field + " exceeds item limit");
		}
		List<String> normalized = values.stream()
			.filter(StringUtils::hasText)
			.map(this::normalizeText)
			.filter(StringUtils::hasText)
			.toList();
		if (normalized.stream().anyMatch(value -> value.length() > maxLength)) {
			throw new IllegalArgumentException(field + " exceeds text limit");
		}
		return List.copyOf(new LinkedHashSet<>(normalized));
	}

	private List<String> operationRules(String cleanedHtml) {
		Set<String> rules = new LinkedHashSet<>();
		for (Element element : Jsoup.parse(cleanedHtml).select("h1,h2,h3,h4,h5,h6,p,li")) {
			String text = normalizeText(element.text());
			if (StringUtils.hasText(text)) {
				rules.add(bounded(text, 500));
			}
			if (rules.size() >= 20) {
				break;
			}
		}
		if (rules.isEmpty()) {
			String text = normalizeText(Jsoup.parse(cleanedHtml).text());
			if (StringUtils.hasText(text)) {
				rules.add(bounded(text, 500));
			}
		}
		return List.copyOf(rules);
	}

	private PolicyAudit audit(String definition, List<String> egressAllowlist, boolean allowCodeNode,
			List<String> toolAllowlist) {
		WorkflowStructure structure = structure(definition);
		List<String> reasons = new ArrayList<>();
		for (String type : structure.forbiddenNodeTypes()) {
			reasons.add("FORBIDDEN_NODE:" + type);
		}
		if (structure.codeNodePresent() && !allowCodeNode) {
			reasons.add("CODE_NODE_NOT_APPROVED");
		}
		if (structure.uninspectableToolNode()) {
			reasons.add("TOOL_IDENTITY_NOT_INSPECTABLE");
		}
		Set<String> approvedTools = new LinkedHashSet<>(normalizeToolAllowlist(toolAllowlist));
		for (String toolIdentity : structure.toolIdentities()) {
			if (!approvedTools.contains(toolIdentity)) {
				reasons.add("TOOL_NOT_APPROVED:" + bounded(toolIdentity, 160));
			}
		}
		for (NodeDefinition node : structure.nodes()) {
			if (!"http-request".equals(node.type()) && !"httprequest".equals(node.type())) {
				continue;
			}
			String method = nodeScalar(node, "method");
			if (!StringUtils.hasText(method)) {
				method = nodeScalar(node, "http_method");
			}
			if (Set.of("post", "put", "patch", "delete").contains(defaultString(method).toLowerCase(Locale.ROOT))) {
				reasons.add("STATE_CHANGING_HTTP_METHOD");
			}
			if (!Set.of("get", "head").contains(defaultString(method).toLowerCase(Locale.ROOT))) {
				reasons.add("HTTP_METHOD_NOT_EXPLICITLY_SAFE");
			}
			Matcher urls = URL.matcher(defaultString(nodeScalar(node, "url")));
			boolean targetInspectable = false;
			while (urls.find()) {
				try {
					String host = URI.create(urls.group()).getHost();
					if (host != null) {
						targetInspectable = true;
					}
					if (host == null || !egressAllowlist.contains(host.toLowerCase(Locale.ROOT))) {
						reasons.add("EGRESS_HOST_NOT_ALLOWED");
					}
				}
				catch (IllegalArgumentException ex) {
					reasons.add("EGRESS_URL_INVALID");
				}
			}
			if (!targetInspectable) {
				reasons.add("HTTP_TARGET_NOT_INSPECTABLE");
			}
		}
		return new PolicyAudit(reasons.isEmpty(), List.copyOf(new LinkedHashSet<>(reasons)),
				structure.codeNodePresent(), structure.toolIdentities(), structure.forbiddenNodeTypes(),
				structure.httpEgressHosts());
	}

	private WorkflowStructure structure(String definition) {
		if (!StringUtils.hasText(definition)) {
			throw new IllegalArgumentException("DIFY_PUBLISHED_WORKFLOW_NOT_INSPECTABLE");
		}
		List<NodeDefinition> nodes = workflowNodes(definition);
		if (nodes.isEmpty()) {
			throw new IllegalArgumentException("DIFY_PUBLISHED_WORKFLOW_NOT_INSPECTABLE");
		}
		boolean codeNodePresent = nodes.stream().anyMatch(node -> "code".equals(node.type()));
		Set<String> forbiddenNodeTypes = new LinkedHashSet<>();
		Set<String> toolIdentities = new LinkedHashSet<>();
		Set<String> httpEgressHosts = new LinkedHashSet<>();
		boolean uninspectableToolNode = false;
		for (NodeDefinition node : nodes) {
			if (FORBIDDEN_NODE_TYPES.contains(node.type())) {
				forbiddenNodeTypes.add(node.type());
			}
			if ("tool".equals(node.type())) {
				String toolName = nodeScalar(node, "tool_name");
				String providerId = nodeScalar(node, "provider_id");
				if (!StringUtils.hasText(providerId)) {
					providerId = nodeScalar(node, "provider_name");
				}
				if (!StringUtils.hasText(toolName)) {
					uninspectableToolNode = true;
				}
				else {
					String identity = StringUtils.hasText(providerId) ? providerId + "/" + toolName : toolName;
					toolIdentities.add(normalizeToolIdentity(identity));
				}
			}
			if ("http-request".equals(node.type()) || "httprequest".equals(node.type())) {
				Matcher urls = URL.matcher(defaultString(nodeScalar(node, "url")));
				while (urls.find()) {
					try {
						String host = URI.create(urls.group()).getHost();
						if (host != null) {
							httpEgressHosts.add(host.toLowerCase(Locale.ROOT));
						}
					}
					catch (IllegalArgumentException ignored) {
						// Audit reports the invalid URL without exposing the original
						// value.
					}
				}
			}
		}
		return new WorkflowStructure(List.copyOf(nodes), codeNodePresent, List.copyOf(toolIdentities),
				uninspectableToolNode, List.copyOf(forbiddenNodeTypes), List.copyOf(httpEgressHosts));
	}

	private List<NodeDefinition> workflowNodes(String definition) {
		Matcher matcher = NODE_DATA.matcher(definition);
		List<NodeStart> starts = new ArrayList<>();
		while (matcher.find()) {
			starts.add(new NodeStart(matcher.start(), matcher.group(1).length()));
		}
		List<NodeDefinition> nodes = new ArrayList<>();
		for (int index = 0; index < starts.size(); index++) {
			NodeStart nodeStart = starts.get(index);
			int start = nodeStart.start();
			int end = index + 1 < starts.size() ? starts.get(index + 1).start() : definition.length();
			String block = definition.substring(start, end);
			NodeDefinition candidate = new NodeDefinition("", block, nodeStart.dataIndent() + 4);
			String type = nodeScalar(candidate, "type");
			if (StringUtils.hasText(type)) {
				nodes.add(new NodeDefinition(type.toLowerCase(Locale.ROOT), block, candidate.fieldIndent()));
			}
		}
		return List.copyOf(nodes);
	}

	private String nodeScalar(NodeDefinition node, String key) {
		Pattern scalar = Pattern.compile("(?m)^[ \\t]{" + node.fieldIndent() + "}" + Pattern.quote(key)
				+ "\\s*:\\s*(?:['\\\"]([^'\\\"]*)['\\\"]|([^#\\r\\n]+))");
		Matcher matcher = scalar.matcher(node.definition());
		if (!matcher.find()) {
			return null;
		}
		String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
		value = normalizeText(value);
		return "null".equalsIgnoreCase(value) ? null : value;
	}

	private String normalizeToolIdentity(String value) {
		return normalizeText(value).toLowerCase(Locale.ROOT);
	}

	private String exportedDefinition(JsonNode exported) {
		if (exported == null || exported.isNull()) {
			return null;
		}
		if (exported.isTextual()) {
			return exported.asText();
		}
		for (String field : List.of("data", "yaml_content", "content")) {
			if (exported.path(field).isTextual()) {
				return exported.path(field).asText();
			}
		}
		return null;
	}

	private String firstText(JsonNode node, String... paths) {
		if (node == null) {
			return null;
		}
		for (String path : paths) {
			JsonNode current = node;
			for (String segment : path.split("\\.")) {
				current = current.path(segment);
			}
			if (current.isTextual() && StringUtils.hasText(current.asText())) {
				return current.asText();
			}
		}
		return null;
	}

	private String nextReleaseVersion(Long capabilityId) {
		List<AiAssistantWorkflowCapabilityReleaseEntity> existing = releases(capabilityId);
		int max = existing.stream()
			.map(AiAssistantWorkflowCapabilityReleaseEntity::getReleaseVersion)
			.mapToInt(this::versionNumber)
			.max()
			.orElse(0);
		return "v" + (max + 1);
	}

	private List<AiAssistantWorkflowCapabilityReleaseEntity> releases(Long capabilityId) {
		return releaseMapper.selectList(Wrappers.<AiAssistantWorkflowCapabilityReleaseEntity>lambdaQuery()
			.eq(AiAssistantWorkflowCapabilityReleaseEntity::getCapabilityId, capabilityId)
			.orderByDesc(AiAssistantWorkflowCapabilityReleaseEntity::getId));
	}

	private int versionNumber(String value) {
		try {
			return value != null && value.startsWith("v") ? Integer.parseInt(value.substring(1)) : 0;
		}
		catch (NumberFormatException ex) {
			return 0;
		}
	}

	private String sourceDigest(AiAgentEntity agent, AiAssistantWorkflowCapabilityEntity draft) {
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("agentId", agent.getId());
		source.put("name", normalizeText(agent.getName()));
		source.put("description", normalizeText(agent.getDescription()));
		source.put("useScenes", defaultString(agent.getUseScenes()));
		source.put("operationGuide", XssUtil.clean(defaultString(agent.getOperationGuide())));
		source.put("difyAppId", agent.getDifyAppId());
		source.put("difyMode", agent.getDifyMode());
		source.put("capabilityCode", draft.getCapabilityCode());
		source.put("intentExamples", draft.getIntentExamplesJson());
		source.put("inputSchema", draft.getInputSchemaJson());
		source.put("outputSchema", draft.getOutputSchemaJson());
		source.put("requiredProviders", draft.getRequiredProvidersJson());
		source.put("dataClassification", draft.getDataClassification());
		source.put("sideEffectClass", draft.getSideEffectClass());
		source.put("egressAllowlist", draft.getEgressAllowlistJson());
		source.put("allowCodeNode", draft.getAllowCodeNode());
		source.put("toolAllowlist", draft.getToolAllowlistJson());
		source.put("timeoutMs", draft.getTimeoutMs());
		return sha256(write(source));
	}

	private String releaseDigest(AiAssistantWorkflowCapabilityReleaseEntity release) {
		Map<String, Object> material = new LinkedHashMap<>();
		material.put("capabilityId", release.getCapabilityId());
		material.put("capabilityCode", release.getCapabilityCode());
		material.put("releaseVersion", release.getReleaseVersion());
		material.put("sourceDigest", release.getSourceDigest());
		material.put("routingProfileDigest", release.getRoutingProfileDigest());
		material.put("inputSchemaId", release.getInputSchemaId());
		material.put("outputSchemaId", release.getOutputSchemaId());
		material.put("publishedWorkflowId", release.getPublishedWorkflowId());
		material.put("workflowDefinitionDigest", release.getWorkflowDefinitionDigest());
		material.put("policyAudit", release.getPolicyAuditJson());
		return sha256(write(material));
	}

	private List<String> readList(String json) {
		try {
			JsonNode node = objectMapper.readTree(json);
			if (node == null || !node.isArray()) {
				return List.of();
			}
			List<String> result = new ArrayList<>();
			for (JsonNode item : node) {
				if (item.isTextual() && StringUtils.hasText(item.asText())) {
					result.add(item.asText());
				}
			}
			return List.copyOf(result);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_SNAPSHOT_INVALID", ex);
		}
	}

	private String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("WORKFLOW_CAPABILITY_SERIALIZATION_FAILED", ex);
		}
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

	private String defaultString(String value) {
		return value == null ? "" : value;
	}

	private String normalizeText(String value) {
		return defaultString(value).trim().replaceAll("\\s+", " ");
	}

	private String bounded(String value, int maxLength) {
		return value.length() <= maxLength ? value : value.substring(0, maxLength);
	}

	private record NodeStart(int start, int dataIndent) {
	}

	private record NodeDefinition(String type, String definition, int fieldIndent) {
	}

	private record WorkflowStructure(List<NodeDefinition> nodes, boolean codeNodePresent, List<String> toolIdentities,
			boolean uninspectableToolNode, List<String> forbiddenNodeTypes, List<String> httpEgressHosts) {
	}

	private record PolicyAudit(boolean passed, List<String> reasons, boolean codeNodePresent,
			List<String> toolIdentities, List<String> forbiddenNodeTypes, List<String> httpEgressHosts) {
	}

}
