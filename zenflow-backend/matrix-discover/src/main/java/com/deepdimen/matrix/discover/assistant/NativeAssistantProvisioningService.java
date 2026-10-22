package com.deepdimen.matrix.discover.assistant;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.discover.dto.AiAgentDTO;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.entity.AiTenantAgentRouteSwitchAuditEntity;
import com.deepdimen.matrix.discover.entity.AiUserSkillPrefEntity;
import com.deepdimen.matrix.discover.mapper.AiTenantAgentRouteSwitchAuditMapper;
import com.deepdimen.matrix.discover.mapper.AiUserSkillPrefMapper;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.service.AiAgentSkillService;
import com.deepdimen.matrix.discover.service.TenantAgentRouteService;
import com.deepdimen.matrix.discover.service.TenantAgentRouteService.ResolvedTenantAgentRoute;
import com.deepdimen.matrix.discover.service.TenantAgentRouteService.RouteTransition;
import com.deepdimen.matrix.discover.vo.AiAgentVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Explicit tenant-scoped provisioning and audited route transition. */
@Service
@ConditionalOnBean(AssistantModelGateway.class)
public class NativeAssistantProvisioningService {

	private final AiAgentService agentService;

	private final AiAgentSkillService agentSkillService;

	private final AiUserSkillPrefMapper userSkillPrefMapper;

	private final TenantAgentRouteService routeService;

	private final AiTenantAgentRouteSwitchAuditMapper auditMapper;

	private final AssistantModelGateway modelGateway;

	private final NativeAssistantCutoverGate cutoverGate;

	private final ObjectMapper objectMapper;

	public NativeAssistantProvisioningService(AiAgentService agentService, AiAgentSkillService agentSkillService,
			AiUserSkillPrefMapper userSkillPrefMapper, TenantAgentRouteService routeService,
			AiTenantAgentRouteSwitchAuditMapper auditMapper, AssistantModelGateway modelGateway,
			NativeAssistantCutoverGate cutoverGate, ObjectMapper objectMapper) {
		this.agentService = agentService;
		this.agentSkillService = agentSkillService;
		this.userSkillPrefMapper = userSkillPrefMapper;
		this.routeService = routeService;
		this.auditMapper = auditMapper;
		this.modelGateway = modelGateway;
		this.cutoverGate = cutoverGate;
		this.objectMapper = objectMapper;
	}

	@Transactional(rollbackFor = Exception.class)
	public ProvisionedNativeProfile provision(IdentityEnvelope identity, String displayName, String modelId) {
		requireIdentity(identity);
		modelGateway.resolve(new ModelResolutionRequest(AssistantModelContract.VERSION,
				"provision-" + UUID.randomUUID(), identity, modelId, false, DataClassification.PUBLIC, false));
		ResolvedTenantAgentRoute currentRoute = routeService
			.findTenantDefault(identity, TenantAgentRouteService.UNIFIED_ASSISTANT)
			.orElse(null);
		AiAgentDTO dto = new AiAgentDTO();
		dto.setName(StringUtils.hasText(displayName) ? displayName.trim() : "原生统一助手");
		dto.setDescription("基于租户模型和已授权业务上下文的原生统一助手");
		dto.setAgentType("native");
		dto.setSource("local");
		dto.setPermission("public");
		dto.setCategory("general");
		dto.setConfig(config(modelId));
		dto.setMarketVisible(0);
		AiAgentVO created = agentService.createAgent(dto);
		agentSkillService.syncEnabledSkillsToAgent(created.getId());
		copyUserPreferences(currentRoute == null ? null : currentRoute.agent().getId(), created.getId(),
				identity.tenantId());
		agentService.publishAgent(created.getId());
		return new ProvisionedNativeProfile(created.getId(), currentRoute == null ? null : currentRoute.agent().getId(),
				modelId == null || modelId.isBlank() ? null : modelId.trim());
	}

	@Transactional(rollbackFor = Exception.class)
	public AiTenantAgentRouteSwitchAuditEntity switchRoute(IdentityEnvelope identity, Long nativeProfileId,
			String reason) {
		requireIdentity(identity);
		if (!StringUtils.hasText(reason)) {
			throw new IllegalArgumentException("ROUTE_SWITCH_REASON_REQUIRED");
		}
		diagnoseTarget(identity, nativeProfileId);
		cutoverGate.assertReady(identity, nativeProfileId);
		RouteTransition transition = routeService.switchTenantDefaultNative(identity, nativeProfileId);
		AiTenantAgentRouteSwitchAuditEntity audit = new AiTenantAgentRouteSwitchAuditEntity();
		audit.setTenantId(Long.valueOf(identity.tenantId()));
		audit.setRouteType(TenantAgentRouteService.UNIFIED_ASSISTANT);
		audit.setPreviousAgentId(transition.previousAgentId());
		audit.setNewAgentId(transition.newAgentId());
		audit.setOperatorId(identity.userId());
		audit.setReason(reason.trim());
		audit.setCorrelationId(identity.correlationId());
		audit.setSwitchedAt(LocalDateTime.now());
		if (auditMapper.insert(audit) != 1) {
			throw new IllegalStateException("ROUTE_SWITCH_AUDIT_FAILED");
		}
		return audit;
	}

	@Transactional(rollbackFor = Exception.class)
	public void rollbackRoute(IdentityEnvelope identity, Long auditId) {
		requireIdentity(identity);
		AiTenantAgentRouteSwitchAuditEntity audit = auditMapper.selectById(auditId);
		if (audit == null || audit.getRolledBackAt() != null
				|| !Long.valueOf(identity.tenantId()).equals(audit.getTenantId())
				|| !TenantAgentRouteService.UNIFIED_ASSISTANT.equals(audit.getRouteType())) {
			throw new IllegalArgumentException("ROUTE_SWITCH_AUDIT_NOT_AVAILABLE");
		}
		routeService.restoreTenantDefault(identity, audit.getNewAgentId(), audit.getPreviousAgentId());
		audit.setRolledBackAt(LocalDateTime.now());
		if (auditMapper.updateById(audit) != 1) {
			throw new IllegalStateException("ROUTE_ROLLBACK_AUDIT_FAILED");
		}
	}

	private void copyUserPreferences(Long sourceAgentId, Long targetAgentId, String tenantId) {
		if (sourceAgentId == null) {
			return;
		}
		List<AiUserSkillPrefEntity> source = userSkillPrefMapper.selectList(
				Wrappers.<AiUserSkillPrefEntity>lambdaQuery().eq(AiUserSkillPrefEntity::getAgentId, sourceAgentId));
		Set<Long> selectedUsers = new HashSet<>();
		for (AiUserSkillPrefEntity preference : source.stream()
			.sorted(Comparator
				.comparing(AiUserSkillPrefEntity::getUserId, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(AiUserSkillPrefEntity::getSkillId, Comparator.nullsLast(Comparator.naturalOrder())))
			.toList()) {
			AiUserSkillPrefEntity copy = new AiUserSkillPrefEntity();
			copy.setTenantId(tenantId);
			copy.setUserId(preference.getUserId());
			copy.setAgentId(targetAgentId);
			copy.setSkillId(preference.getSkillId());
			boolean selected = Integer.valueOf(1).equals(preference.getIsEnabled()) && preference.getUserId() != null
					&& selectedUsers.add(preference.getUserId());
			copy.setIsEnabled(selected ? 1 : 0);
			copy.setUpdateTime(LocalDateTime.now());
			userSkillPrefMapper.insert(copy);
		}
	}

	private String config(String modelId) {
		try {
			Map<String, Object> config = modelId == null || modelId.isBlank() ? Map.of()
					: Map.of("modelId", modelId.trim());
			return objectMapper.writeValueAsString(config);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("NATIVE_PROFILE_CONFIG_FAILED", ex);
		}
	}

	private void diagnoseTarget(IdentityEnvelope identity, Long nativeProfileId) {
		AiAgentEntity agent = agentService.getById(nativeProfileId);
		if (agent == null || !"native".equals(agent.getAgentType()) || !"published".equals(agent.getStatus())
				|| !identity.tenantId().equals(agent.getTenantId())) {
			throw new IllegalArgumentException("NATIVE_PROFILE_NOT_AVAILABLE");
		}
		String modelId = null;
		if (StringUtils.hasText(agent.getConfig())) {
			try {
				var node = objectMapper.readTree(agent.getConfig()).path("modelId");
				if (node.isIntegralNumber() || node.isTextual() && StringUtils.hasText(node.asText())) {
					modelId = node.asText().trim();
				}
			}
			catch (JsonProcessingException ex) {
				throw new IllegalArgumentException("NATIVE_PROFILE_INVALID", ex);
			}
		}
		modelGateway.resolve(new ModelResolutionRequest(AssistantModelContract.VERSION,
				"route-diagnostic-" + UUID.randomUUID(), identity, modelId, true, DataClassification.PUBLIC, false));
	}

	private void requireIdentity(IdentityEnvelope identity) {
		if (identity == null || !StringUtils.hasText(identity.tenantId()) || !StringUtils.hasText(identity.userId())) {
			throw new IllegalArgumentException("ASSISTANT_IDENTITY_REQUIRED");
		}
	}

	public record ProvisionedNativeProfile(Long profileId, Long copiedFromAgentId, String modelId) {
	}

}
