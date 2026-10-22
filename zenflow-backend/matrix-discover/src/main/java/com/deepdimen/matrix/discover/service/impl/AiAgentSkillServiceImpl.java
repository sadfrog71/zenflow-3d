package com.deepdimen.matrix.discover.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.discover.dify.DifyAgentBridge;
import com.deepdimen.matrix.discover.dify.DifyServiceClient;
import com.deepdimen.matrix.discover.dto.AiAgentSkillDTO;
import com.deepdimen.matrix.discover.dto.AiAgentVersionGrayDTO;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.entity.AiAgentSkillEntity;
import com.deepdimen.matrix.discover.entity.AiAgentSkillReleaseEntity;
import com.deepdimen.matrix.discover.entity.AiAgentSkillReleaseHistoryEntity;
import com.deepdimen.matrix.discover.entity.AiSkillEntity;
import com.deepdimen.matrix.discover.entity.AiSkillPackageEntity;
import com.deepdimen.matrix.discover.entity.AiTenantAgentRouteEntity;
import com.deepdimen.matrix.discover.mapper.AiAgentSkillMapper;
import com.deepdimen.matrix.discover.mapper.AiAgentSkillReleaseMapper;
import com.deepdimen.matrix.discover.mapper.AiAgentSkillReleaseHistoryMapper;
import com.deepdimen.matrix.discover.mapper.AiSkillPackageMapper;
import com.deepdimen.matrix.discover.mapper.AiTenantAgentRouteMapper;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.service.AiAgentSkillService;
import com.deepdimen.matrix.discover.service.AiSkillService;
import com.deepdimen.matrix.discover.skill.AssistantSkillReleaseSnapshot;
import com.deepdimen.matrix.discover.vo.AiAgentSkillVO;
import com.deepdimen.matrix.discover.vo.AiSkillVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 智能体技能关联Service实现
 *
 * @author matrix
 * @date 2026-04-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentSkillServiceImpl extends ServiceImpl<AiAgentSkillMapper, AiAgentSkillEntity>
		implements AiAgentSkillService {

	private final AiSkillService aiSkillService;

	private final AiAgentSkillReleaseMapper releaseMapper;

	private final AiAgentSkillReleaseHistoryMapper releaseHistoryMapper;

	private final AiSkillPackageMapper skillPackageMapper;

	private final AiTenantAgentRouteMapper tenantAgentRouteMapper;

	@Lazy
	private final AiAgentService aiAgentService;

	@Lazy
	private final DifyAgentBridge difyAgentBridge;

	private final ObjectProvider<DifyServiceClient> difyServiceClientProvider;

	private static final String ARTIFACT_SKILL_CODE = "artifact";

	private static final String CHAT_DB_SKILL_CODE = "chat-db";

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private static final Duration DIFY_VALIDATION_TIMEOUT = Duration.ofSeconds(30);

	private static final String PROMPT_SKILL_REIMPORT_REQUIRED = "PROMPT_SKILL_REIMPORT_REQUIRED: "
			+ "旧版 Prompt Skill 发布快照不完整，请重新导入、审核并发布 Skill";

	@Override
	public List<AiAgentSkillVO> getByAgentId(Long agentId) {
		String tenantId = currentTenantId();
		List<AiAgentSkillEntity> relations = AiAgentService.DEFAULT_AGENT_ID.equals(agentId)
				? TenantBroker.noneAs(() -> listRelationsByAgentId(agentId, tenantId))
				: listRelationsByAgentId(agentId);
		return toViews(relations);
	}

	@Override
	public List<AiAgentSkillVO> getPublishedByAgentId(Long agentId) {
		String tenantId = currentTenantId();
		List<AiAgentSkillReleaseEntity> relations = AiAgentService.DEFAULT_AGENT_ID.equals(agentId)
				? TenantBroker.noneAs(() -> listPublishedRelationsByAgentId(agentId, tenantId))
				: listPublishedRelationsByAgentId(agentId);
		List<AiAgentSkillEntity> available = relations.stream()
			.filter(relation -> isReleaseAvailable((AiAgentSkillReleaseEntity) relation))
			.collect(Collectors.toList());
		return toViews(available, true);
	}

	private List<AiAgentSkillVO> toViews(List<? extends AiAgentSkillEntity> relations) {
		return toViews(relations, false);
	}

	private List<AiAgentSkillVO> toViews(List<? extends AiAgentSkillEntity> relations, boolean enabledOnly) {
		if (relations.isEmpty()) {
			return List.of();
		}
		List<Long> skillIds = relations.stream().map(AiAgentSkillEntity::getSkillId).collect(Collectors.toList());
		Set<String> readableTenantIds = new HashSet<>(readableTenantIds(currentTenantId()));
		List<AiSkillEntity> skills = TenantBroker.noneAs(() -> aiSkillService.listByIds(skillIds))
			.stream()
			.filter(skill -> readableTenantIds.contains(skill.getTenantId()))
			.filter(skill -> !enabledOnly || Integer.valueOf(1).equals(skill.getIsEnabled()))
			.toList();
		Map<String, AiSkillEntity> preferredByCode = skills.stream()
			.collect(Collectors.toMap(this::skillIdentity, skill -> skill,
					(left, right) -> preferTenantSkill(left, right, currentTenantId())));
		Map<Long, AiSkillEntity> skillMap = preferredByCode.values()
			.stream()
			.collect(Collectors.toMap(AiSkillEntity::getId, skill -> skill));

		List<AiAgentSkillVO> result = new ArrayList<>();
		for (AiAgentSkillEntity rel : relations) {
			AiSkillEntity skill = skillMap.get(rel.getSkillId());
			if (skill == null) {
				continue;
			}
			AiAgentSkillVO vo = new AiAgentSkillVO();
			vo.setSkillId(skill.getId());
			vo.setSkillCode(skill.getSkillCode());
			vo.setSkillName(skill.getSkillName());
			vo.setDescription(skill.getDescription());
			vo.setIcon(skill.getIcon());
			vo.setImplType(skill.getImplType());
			vo.setIsDefaultEnabled(rel.getIsDefaultEnabled());
			vo.setSort(rel.getSort());
			vo.setSkillConfig(parseSkillConfig(rel.getSkillConfig()));
			vo.setExecutionStrategy(rel.getExecutionStrategy());
			vo.setWorkflowAgentId(rel.getWorkflowAgentId());
			vo.setInputMapping(parseStringMap(rel.getInputMapping()));
			vo.setOutputMapping(parseStringMap(rel.getOutputMapping()));
			vo.setResultMode(rel.getResultMode());
			vo.setTimeoutMs(rel.getTimeoutMs());
			vo.setRetryPolicy(rel.getRetryPolicy());
			vo.setSideEffectClass(rel.getSideEffectClass());
			if (rel instanceof AiAgentSkillReleaseEntity release) {
				vo.setSkillReleaseId(release.getId());
				vo.setReleaseVersion(release.getReleaseVersion());
				vo.setReleaseStatus(release.getReleaseStatus());
				vo.setGrayConfig(parseSkillConfig(release.getGrayConfig()));
				vo.setRuntimeKind(release.getRuntimeKind());
				vo.setPromptSnapshot(release.getPromptSnapshot());
				vo.setInstructionDigest(release.getInstructionDigest());
				vo.setPackageSha256(release.getPackageSha256());
				vo.setRequiredProviders(parseStringList(release.getRequiredProvidersJson()));
				vo.setAnalysisPolicySnapshot(parseSkillConfig(release.getAnalysisPolicySnapshot()));
				vo.setAnalysisPolicyDigest(release.getAnalysisPolicyDigest());
				vo.setAnalysisResultSchema(parseSkillConfig(release.getAnalysisResultSchema()));
				vo.setAnalysisResultSchemaId(release.getAnalysisResultSchemaId());
				vo.setReleasedAt(release.getReleasedAt());
				vo.setDisabledAt(release.getDisabledAt());
			}
			result.add(vo);
		}
		return result;
	}

	private String skillIdentity(AiSkillEntity skill) {
		return StringUtils.hasText(skill.getSkillCode()) ? skill.getSkillCode() : "#" + skill.getId();
	}

	private AiSkillEntity preferTenantSkill(AiSkillEntity left, AiSkillEntity right, String tenantId) {
		boolean leftOwned = tenantId.equals(left.getTenantId());
		boolean rightOwned = tenantId.equals(right.getTenantId());
		if (leftOwned != rightOwned) {
			return leftOwned ? left : right;
		}
		if (left.getId() == null) {
			return right;
		}
		if (right.getId() == null) {
			return left;
		}
		return left.getId() <= right.getId() ? left : right;
	}

	/**
	 * 按 agentId 查询技能关联记录（按 sort 升序），租户过滤由调用方控制
	 */
	private List<AiAgentSkillEntity> listRelationsByAgentId(Long agentId) {
		return list(Wrappers.<AiAgentSkillEntity>lambdaQuery()
			.eq(AiAgentSkillEntity::getAgentId, agentId)
			.orderByAsc(AiAgentSkillEntity::getSort));
	}

	private List<AiAgentSkillEntity> listRelationsByAgentId(Long agentId, String tenantId) {
		return list(Wrappers.<AiAgentSkillEntity>lambdaQuery()
			.eq(AiAgentSkillEntity::getAgentId, agentId)
			.in(AiAgentSkillEntity::getTenantId, readableTenantIds(tenantId))
			.orderByAsc(AiAgentSkillEntity::getSort));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveAgentSkills(Long agentId, List<AiAgentSkillDTO> skills) {
		validateSkillBindings(agentId, skills);
		baseMapper.delete(Wrappers.<AiAgentSkillEntity>lambdaQuery().eq(AiAgentSkillEntity::getAgentId, agentId));
		if (skills == null || skills.isEmpty()) {
			return;
		}
		List<AiAgentSkillEntity> entities = new ArrayList<>();
		for (AiAgentSkillDTO dto : skills) {
			if (dto.getSkillId() == null) {
				throw new IllegalArgumentException("技能 ID 不能为空");
			}
			AiAgentSkillEntity entity = new AiAgentSkillEntity();
			entity.setAgentId(agentId);
			entity.setSkillId(dto.getSkillId());
			entity.setIsDefaultEnabled(dto.getIsDefaultEnabled() != null ? dto.getIsDefaultEnabled() : 1);
			entity.setSort(dto.getSort() != null ? dto.getSort() : 0);
			entity.setSkillConfig(serializeSkillConfig(dto.getSkillConfig()));
			applyRuntimeBinding(entity, dto);
			entities.add(entity);
		}
		saveBatch(entities);
	}

	private List<AiAgentSkillReleaseEntity> listPublishedRelationsByAgentId(Long agentId) {
		return releaseMapper.selectList(Wrappers.<AiAgentSkillReleaseEntity>lambdaQuery()
			.eq(AiAgentSkillReleaseEntity::getAgentId, agentId)
			.orderByAsc(AiAgentSkillReleaseEntity::getSort));
	}

	private List<AiAgentSkillReleaseEntity> listPublishedRelationsByAgentId(Long agentId, String tenantId) {
		return releaseMapper.selectList(Wrappers.<AiAgentSkillReleaseEntity>lambdaQuery()
			.eq(AiAgentSkillReleaseEntity::getAgentId, agentId)
			.in(AiAgentSkillReleaseEntity::getTenantId, readableTenantIds(tenantId))
			.orderByAsc(AiAgentSkillReleaseEntity::getSort));
	}

	private List<String> readableTenantIds(String tenantId) {
		return "0".equals(tenantId) ? List.of("0") : List.of("0", tenantId);
	}

	private void applyRuntimeBinding(AiAgentSkillEntity entity, AiAgentSkillDTO dto) {
		String strategy = StringUtils.hasText(dto.getExecutionStrategy()) ? dto.getExecutionStrategy()
				: "PRIMARY_APP_INSTRUCTION";
		if (!Set.of("PRIMARY_APP_INSTRUCTION", "DEDICATED_DIFY_WORKFLOW").contains(strategy)) {
			throw new IllegalArgumentException("不支持的 Skill 执行策略: " + strategy);
		}
		if ("DEDICATED_DIFY_WORKFLOW".equals(strategy) && dto.getWorkflowAgentId() == null) {
			throw new IllegalArgumentException("专用 Dify Workflow 不能为空");
		}
		String resultMode = StringUtils.hasText(dto.getResultMode()) ? dto.getResultMode() : "FINAL_RESPONSE";
		if (!Set.of("FINAL_RESPONSE", "INTERMEDIATE_RESULT").contains(resultMode)) {
			throw new IllegalArgumentException("不支持的 Workflow 结果模式: " + resultMode);
		}
		String retryPolicy = StringUtils.hasText(dto.getRetryPolicy()) ? dto.getRetryPolicy() : "NONE";
		if (!Set.of("NONE", "SAFE_ONCE").contains(retryPolicy)) {
			throw new IllegalArgumentException("不支持的 Workflow 重试策略: " + retryPolicy);
		}
		String sideEffectClass = StringUtils.hasText(dto.getSideEffectClass()) ? dto.getSideEffectClass() : "READ_ONLY";
		if (!Set.of("READ_ONLY", "PROPOSAL_ONLY").contains(sideEffectClass)) {
			throw new IllegalArgumentException("DIFY_WORKFLOW_SIDE_EFFECT_FORBIDDEN");
		}
		int timeoutMs = dto.getTimeoutMs() == null ? 90000 : dto.getTimeoutMs();
		if (timeoutMs < 1000 || timeoutMs > 300000) {
			throw new IllegalArgumentException("Workflow 超时必须在 1000 到 300000 毫秒之间");
		}
		entity.setExecutionStrategy(strategy);
		entity.setWorkflowAgentId("DEDICATED_DIFY_WORKFLOW".equals(strategy) ? dto.getWorkflowAgentId() : null);
		entity.setInputMapping(serializeStringMap(dto.getInputMapping()));
		entity.setOutputMapping(serializeStringMap(dto.getOutputMapping()));
		entity.setResultMode(resultMode);
		entity.setTimeoutMs(timeoutMs);
		entity.setRetryPolicy(retryPolicy);
		entity.setSideEffectClass(sideEffectClass);
	}

	@Override
	public void validateForPublish(Long agentId) {
		AiAgentEntity parent = aiAgentService.getById(agentId);
		if (parent == null) {
			throw new IllegalArgumentException("智能体不存在: " + agentId);
		}
		List<AiAgentSkillVO> draftSkills = getByAgentId(agentId);
		Set<String> enabledCodes = aiSkillService
			.findEnabledByCodes(draftSkills.stream().map(AiAgentSkillVO::getSkillCode).distinct().toList())
			.stream()
			.map(AiSkillEntity::getSkillCode)
			.collect(Collectors.toSet());
		if (enabledCodes.size() != draftSkills.stream().map(AiAgentSkillVO::getSkillCode).distinct().count()) {
			throw new IllegalArgumentException("SKILL_NOT_ALLOWED: 智能体包含未审核或已停用的 Skill");
		}
		long workflowCount = draftSkills.stream()
			.filter(skill -> "DEDICATED_DIFY_WORKFLOW".equals(skill.getExecutionStrategy()))
			.count();
		if (workflowCount > 1) {
			throw new IllegalArgumentException("MATRIX_COMPOSITE_NOT_SUPPORTED: 一个智能体最多绑定一个 Dify Workflow Skill");
		}
		for (AiAgentSkillEntity binding : listRelationsByAgentId(agentId)) {
			if (!"DEDICATED_DIFY_WORKFLOW".equals(binding.getExecutionStrategy())) {
				continue;
			}
			validateDedicatedWorkflow(parent, binding);
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void projectForPublish(Long agentId) {
		List<AiAgentSkillVO> skills = getByAgentId(agentId);
		boolean artifactEnabled = skills.stream().anyMatch(skill -> ARTIFACT_SKILL_CODE.equals(skill.getSkillCode()));
		syncArtifactToDify(agentId, artifactEnabled);

		AiAgentSkillVO chatDb = skills.stream()
			.filter(skill -> CHAT_DB_SKILL_CODE.equals(skill.getSkillCode()))
			.findFirst()
			.orElse(null);
		Map<String, Object> config = chatDb == null ? null : chatDb.getSkillConfig();
		Long dataId = config == null ? null : parseLong(config.get("dataId"));
		String dataName = config == null ? null : Objects.toString(config.get("dataName"), "");
		syncChatDbToDify(agentId, dataId, dataName);
		publishRuntimeBindings(agentId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void syncDefaultAgentSkill(Long skillId, boolean enabled) {
		if (skillId == null) {
			throw new IllegalArgumentException("技能 ID 不能为空");
		}
		String tenantId = currentTenantId();
		TenantBroker.noneAs(() -> {
			AiSkillEntity skill = aiSkillService.getById(skillId);
			if (skill == null) {
				throw new IllegalArgumentException("技能不存在: " + skillId);
			}
			LocalDateTime now = LocalDateTime.now();
			AiAgentSkillEntity draft = baseMapper.selectOne(Wrappers.<AiAgentSkillEntity>lambdaQuery()
				.eq(AiAgentSkillEntity::getAgentId, AiAgentService.DEFAULT_AGENT_ID)
				.eq(AiAgentSkillEntity::getSkillId, skillId));
			if (draft == null) {
				draft = defaultDraft(skill, tenantId, enabled, now);
				baseMapper.insert(draft);
			}
			else {
				applyDefaultBinding(draft, skill, enabled, now);
				baseMapper.updateById(draft);
			}

			AiAgentSkillReleaseEntity release = releaseMapper
				.selectOne(Wrappers.<AiAgentSkillReleaseEntity>lambdaQuery()
					.eq(AiAgentSkillReleaseEntity::getAgentId, AiAgentService.DEFAULT_AGENT_ID)
					.eq(AiAgentSkillReleaseEntity::getSkillId, skillId));
			if (release == null) {
				release = defaultRelease(draft, tenantId, enabled, now);
				releaseMapper.insert(release);
			}
			else {
				archiveAndAdvanceRelease(release);
				applyDefaultRelease(release, draft, enabled, now);
				releaseMapper.updateById(release);
			}
			syncEnabledTenantRoutes(skill, enabled, tenantId, now);
			return null;
		});
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void syncEnabledSkillsToAgent(Long agentId) {
		if (agentId == null) {
			throw new IllegalArgumentException("智能体 ID 不能为空");
		}
		String tenantId = currentTenantId();
		List<String> skillCodes = aiSkillService.listAvailableSkills()
			.stream()
			.map(AiSkillVO::getSkillCode)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		List<AiSkillEntity> skills = aiSkillService.findEnabledByCodes(skillCodes)
			.stream()
			.filter(skill -> "prompt_injection".equals(skill.getImplType()))
			.filter(this::hasPublishablePromptSnapshot)
			.toList();
		LocalDateTime now = LocalDateTime.now();
		TenantBroker.noneAs(() -> {
			for (AiSkillEntity skill : skills) {
				syncAgentSkill(skill, agentId, true, tenantId, now);
			}
			return null;
		});
	}

	/**
	 * The unified assistant may be routed to a tenant-owned published agent instead of
	 * the shared default agent (for example the Wujiang route). Keep the route's draft
	 * and release projection in sync when a Skill is approved or disabled, otherwise the
	 * admin catalog changes but the portal still sees an old Skill set.
	 */
	private void syncEnabledTenantRoutes(AiSkillEntity skill, boolean enabled, String tenantId, LocalDateTime now) {
		List<AiTenantAgentRouteEntity> routes = tenantAgentRouteMapper
			.selectList(Wrappers.<AiTenantAgentRouteEntity>lambdaQuery()
				.eq(AiTenantAgentRouteEntity::getTenantId, Long.valueOf(tenantId))
				.eq(AiTenantAgentRouteEntity::getEnabled, 1)
				.in(AiTenantAgentRouteEntity::getChannelCode, List.of("UNIFIED_ASSISTANT", "WORK_BRIEF")));
		Set<Long> agentIds = routes.stream()
			.map(AiTenantAgentRouteEntity::getAgentId)
			.filter(Objects::nonNull)
			.filter(agentId -> !AiAgentService.DEFAULT_AGENT_ID.equals(agentId))
			.collect(Collectors.toCollection(HashSet::new));
		for (Long agentId : agentIds) {
			syncAgentSkill(skill, agentId, enabled, tenantId, now);
		}
	}

	private void syncAgentSkill(AiSkillEntity skill, Long agentId, boolean enabled, String tenantId,
			LocalDateTime now) {
		AiAgentSkillEntity draft = baseMapper.selectOne(Wrappers.<AiAgentSkillEntity>lambdaQuery()
			.eq(AiAgentSkillEntity::getAgentId, agentId)
			.eq(AiAgentSkillEntity::getSkillId, skill.getId()));
		if (draft == null) {
			draft = defaultDraft(skill, tenantId, enabled, now);
			draft.setAgentId(agentId);
			baseMapper.insert(draft);
		}
		else {
			applyDefaultBinding(draft, skill, enabled, now);
			draft.setAgentId(agentId);
			baseMapper.updateById(draft);
		}

		AiAgentSkillReleaseEntity release = releaseMapper.selectOne(Wrappers.<AiAgentSkillReleaseEntity>lambdaQuery()
			.eq(AiAgentSkillReleaseEntity::getAgentId, agentId)
			.eq(AiAgentSkillReleaseEntity::getSkillId, skill.getId()));
		if (release == null) {
			release = defaultRelease(draft, tenantId, enabled, now);
			release.setAgentId(agentId);
			releaseMapper.insert(release);
		}
		else {
			archiveAndAdvanceRelease(release);
			applyDefaultRelease(release, draft, enabled, now);
			release.setAgentId(agentId);
			releaseMapper.updateById(release);
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void removeDefaultAgentSkill(Long skillId) {
		if (skillId == null) {
			return;
		}
		Set<Long> managedAgentIds = managedDefaultAgentIds();
		TenantBroker.noneAs(() -> {
			releaseMapper.delete(Wrappers.<AiAgentSkillReleaseEntity>lambdaQuery()
				.in(AiAgentSkillReleaseEntity::getAgentId, managedAgentIds)
				.eq(AiAgentSkillReleaseEntity::getSkillId, skillId));
			baseMapper.delete(Wrappers.<AiAgentSkillEntity>lambdaQuery()
				.in(AiAgentSkillEntity::getAgentId, managedAgentIds)
				.eq(AiAgentSkillEntity::getSkillId, skillId));
			return null;
		});
	}

	@Override
	public boolean hasNonDefaultBindings(Long skillId) {
		if (skillId == null) {
			return false;
		}
		Set<Long> managedAgentIds = managedDefaultAgentIds();
		long draftCount = count(Wrappers.<AiAgentSkillEntity>lambdaQuery()
			.eq(AiAgentSkillEntity::getSkillId, skillId)
			.notIn(AiAgentSkillEntity::getAgentId, managedAgentIds));
		long releaseCount = releaseMapper.selectCount(Wrappers.<AiAgentSkillReleaseEntity>lambdaQuery()
			.eq(AiAgentSkillReleaseEntity::getSkillId, skillId)
			.notIn(AiAgentSkillReleaseEntity::getAgentId, managedAgentIds));
		return draftCount > 0 || releaseCount > 0;
	}

	/**
	 * Shared default agent and current tenant's enabled unified/work-brief routes are
	 * lifecycle-managed Skill consumers. Their bindings must not make a tenant-owned
	 * Skill look like it is still attached to an unrelated agent.
	 */
	private Set<Long> managedDefaultAgentIds() {
		Set<Long> agentIds = new HashSet<>();
		agentIds.add(AiAgentService.DEFAULT_AGENT_ID);
		String tenantId = currentTenantId();
		if (!"0".equals(tenantId)) {
			List<AiTenantAgentRouteEntity> routes = tenantAgentRouteMapper
				.selectList(Wrappers.<AiTenantAgentRouteEntity>lambdaQuery()
					.eq(AiTenantAgentRouteEntity::getTenantId, Long.valueOf(tenantId))
					.eq(AiTenantAgentRouteEntity::getEnabled, 1)
					.in(AiTenantAgentRouteEntity::getChannelCode, List.of("UNIFIED_ASSISTANT", "WORK_BRIEF")));
			routes.stream().map(AiTenantAgentRouteEntity::getAgentId).filter(Objects::nonNull).forEach(agentIds::add);
		}
		return agentIds;
	}

	private AiAgentSkillEntity defaultDraft(AiSkillEntity skill, String tenantId, boolean enabled, LocalDateTime now) {
		AiAgentSkillEntity draft = new AiAgentSkillEntity();
		draft.setTenantId(tenantId);
		draft.setAgentId(AiAgentService.DEFAULT_AGENT_ID);
		draft.setSkillId(skill.getId());
		draft.setCreateBy(operator());
		draft.setCreateTime(now);
		applyDefaultBinding(draft, skill, enabled, now);
		return draft;
	}

	private void applyDefaultBinding(AiAgentSkillEntity binding, AiSkillEntity skill, boolean enabled,
			LocalDateTime now) {
		binding.setTenantId(currentTenantId());
		binding.setAgentId(AiAgentService.DEFAULT_AGENT_ID);
		binding.setSkillId(skill.getId());
		binding.setIsDefaultEnabled(enabled ? 1 : 0);
		binding.setSkillConfig(null);
		binding.setExecutionStrategy("PRIMARY_APP_INSTRUCTION");
		binding.setWorkflowAgentId(null);
		binding.setInputMapping(null);
		binding.setOutputMapping(null);
		binding.setResultMode("FINAL_RESPONSE");
		binding.setTimeoutMs(90000);
		binding.setRetryPolicy("NONE");
		binding.setSideEffectClass("READ_ONLY");
		binding.setSort(skill.getSort() == null ? 0 : skill.getSort());
		binding.setUpdateBy(operator());
		binding.setUpdateTime(now);
	}

	private AiAgentSkillReleaseEntity defaultRelease(AiAgentSkillEntity draft, String tenantId, boolean enabled,
			LocalDateTime now) {
		AiAgentSkillReleaseEntity release = new AiAgentSkillReleaseEntity();
		release.setTenantId(tenantId);
		release.setAgentId(AiAgentService.DEFAULT_AGENT_ID);
		release.setSkillId(draft.getSkillId());
		release.setReleaseVersion("v1");
		release.setCreateBy(operator());
		release.setCreateTime(now);
		applyDefaultRelease(release, draft, enabled, now);
		return release;
	}

	private void applyDefaultRelease(AiAgentSkillReleaseEntity release, AiAgentSkillEntity draft, boolean enabled,
			LocalDateTime now) {
		release.setTenantId(draft.getTenantId());
		release.setAgentId(AiAgentService.DEFAULT_AGENT_ID);
		release.setSkillId(draft.getSkillId());
		release
			.setReleaseVersion(StringUtils.hasText(release.getReleaseVersion()) ? release.getReleaseVersion() : "v1");
		release.setReleaseStatus(enabled ? "ACTIVE" : "DISABLED");
		release.setGrayConfig(null);
		release.setIsDefaultEnabled(draft.getIsDefaultEnabled());
		release.setSkillConfig(draft.getSkillConfig());
		release.setExecutionStrategy(draft.getExecutionStrategy());
		release.setWorkflowAgentId(draft.getWorkflowAgentId());
		release.setInputMapping(draft.getInputMapping());
		release.setOutputMapping(draft.getOutputMapping());
		release.setResultMode(draft.getResultMode());
		release.setTimeoutMs(draft.getTimeoutMs());
		release.setRetryPolicy(draft.getRetryPolicy());
		release.setSideEffectClass(draft.getSideEffectClass());
		release.setSort(draft.getSort());
		release.setReleasedAt(enabled ? now : release.getReleasedAt());
		release.setDisabledAt(enabled ? null : now);
		release.setUpdateBy(operator());
		release.setUpdateTime(now);
		freezeRuntimeSnapshot(release);
	}

	private void publishRuntimeBindings(Long agentId) {
		List<AiAgentSkillReleaseEntity> previous = releaseMapper.selectList(
				Wrappers.<AiAgentSkillReleaseEntity>lambdaQuery().eq(AiAgentSkillReleaseEntity::getAgentId, agentId));
		String nextVersion = nextReleaseVersion(previous);
		for (AiAgentSkillReleaseEntity release : previous) {
			releaseHistoryMapper.insert(toHistory(release));
		}
		releaseMapper.delete(
				Wrappers.<AiAgentSkillReleaseEntity>lambdaQuery().eq(AiAgentSkillReleaseEntity::getAgentId, agentId));
		LocalDateTime releasedAt = LocalDateTime.now();
		for (AiAgentSkillEntity draft : listRelationsByAgentId(agentId)) {
			AiAgentSkillReleaseEntity release = new AiAgentSkillReleaseEntity();
			release.setTenantId(draft.getTenantId());
			release.setAgentId(draft.getAgentId());
			release.setSkillId(draft.getSkillId());
			release.setIsDefaultEnabled(draft.getIsDefaultEnabled());
			release.setSkillConfig(draft.getSkillConfig());
			release.setExecutionStrategy(draft.getExecutionStrategy());
			release.setWorkflowAgentId(draft.getWorkflowAgentId());
			release.setInputMapping(draft.getInputMapping());
			release.setOutputMapping(draft.getOutputMapping());
			release.setResultMode(draft.getResultMode());
			release.setTimeoutMs(draft.getTimeoutMs());
			release.setRetryPolicy(draft.getRetryPolicy());
			release.setSideEffectClass(draft.getSideEffectClass());
			release.setSort(draft.getSort());
			freezeRuntimeSnapshot(release);
			release.setReleaseVersion(nextVersion);
			release.setReleaseStatus("ACTIVE");
			release.setReleasedAt(releasedAt);
			releaseMapper.insert(release);
		}
	}

	@Override
	public List<AiAgentSkillVO> listReleaseGovernance(Long agentId) {
		List<AiAgentSkillReleaseEntity> releases = releaseMapper
			.selectList(Wrappers.<AiAgentSkillReleaseEntity>lambdaQuery()
				.eq(AiAgentSkillReleaseEntity::getAgentId, agentId)
				.orderByAsc(AiAgentSkillReleaseEntity::getSort));
		return toViews(releases);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void setReleaseGray(Long agentId, String releaseVersion, AiAgentVersionGrayDTO dto) {
		if (dto == null || (dto.getPercent() == null && (dto.getUserIds() == null || dto.getUserIds().isEmpty()))) {
			throw new IllegalArgumentException("灰度规则不能为空");
		}
		List<AiAgentSkillReleaseEntity> releases = findRelease(agentId, releaseVersion);
		if (releases.isEmpty()) {
			throw new IllegalArgumentException("Skill 发布版本不存在: " + releaseVersion);
		}
		validatePromptReleaseSnapshots(releases);
		try {
			String grayConfig = OBJECT_MAPPER.writeValueAsString(dto);
			for (AiAgentSkillReleaseEntity release : releases) {
				release.setReleaseStatus("GRAY");
				release.setGrayConfig(grayConfig);
				releaseMapper.updateById(release);
			}
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("灰度规则序列化失败", ex);
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> disableRelease(Long agentId, String releaseVersion) {
		List<AiAgentSkillReleaseEntity> releases = findRelease(agentId, releaseVersion);
		if (releases.isEmpty()) {
			throw new IllegalArgumentException("Skill 发布版本不存在: " + releaseVersion);
		}
		LocalDateTime disabledAt = LocalDateTime.now();
		for (AiAgentSkillReleaseEntity release : releases) {
			release.setReleaseStatus("DISABLED");
			release.setDisabledAt(disabledAt);
			releaseMapper.updateById(release);
		}
		List<String> skillCodes = toViews(releases).stream().map(AiAgentSkillVO::getSkillCode).toList();
		Map<String, Object> impact = new HashMap<>();
		impact.put("agentId", agentId);
		impact.put("releaseVersion", releaseVersion);
		impact.put("disabledSkillCount", releases.size());
		impact.put("disabledSkillCodes", skillCodes);
		impact.put("runtimeEffect", "NEW_CONVERSATIONS_EXCLUDED");
		return impact;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void rollbackRelease(Long agentId, String releaseVersion) {
		List<AiAgentSkillReleaseHistoryEntity> snapshots = releaseHistoryMapper
			.selectList(Wrappers.<AiAgentSkillReleaseHistoryEntity>lambdaQuery()
				.eq(AiAgentSkillReleaseHistoryEntity::getAgentId, agentId)
				.eq(AiAgentSkillReleaseHistoryEntity::getReleaseVersion, releaseVersion)
				.orderByAsc(AiAgentSkillReleaseHistoryEntity::getSkillId));
		if (snapshots.isEmpty()) {
			throw new IllegalArgumentException("没有找到可回滚的 Skill 发布版本: " + releaseVersion);
		}
		validatePromptHistorySnapshots(snapshots);
		Set<Long> snapshotSkillIds = snapshots.stream()
			.map(AiAgentSkillReleaseHistoryEntity::getSkillId)
			.collect(Collectors.toSet());
		List<AiAgentSkillReleaseEntity> current = releaseMapper
			.selectList(Wrappers.<AiAgentSkillReleaseEntity>lambdaQuery()
				.eq(AiAgentSkillReleaseEntity::getAgentId, agentId)
				.in(AiAgentSkillReleaseEntity::getSkillId, snapshotSkillIds));
		for (AiAgentSkillReleaseEntity release : current) {
			releaseHistoryMapper.insert(toHistory(release));
		}
		releaseMapper.delete(Wrappers.<AiAgentSkillReleaseEntity>lambdaQuery()
			.eq(AiAgentSkillReleaseEntity::getAgentId, agentId)
			.in(AiAgentSkillReleaseEntity::getSkillId, snapshotSkillIds));
		LocalDateTime releasedAt = LocalDateTime.now();
		for (AiAgentSkillReleaseHistoryEntity snapshot : snapshots) {
			AiAgentSkillReleaseEntity release = new AiAgentSkillReleaseEntity();
			release.setTenantId(snapshot.getTenantId());
			release.setAgentId(snapshot.getAgentId());
			release.setSkillId(snapshot.getSkillId());
			release.setReleaseVersion(snapshot.getReleaseVersion());
			release.setReleaseStatus("ACTIVE");
			release.setGrayConfig(null);
			release.setRuntimeKind(snapshot.getRuntimeKind());
			release.setPromptSnapshot(snapshot.getPromptSnapshot());
			release.setInstructionDigest(snapshot.getInstructionDigest());
			release.setPackageSha256(snapshot.getPackageSha256());
			release.setRequiredProvidersJson(snapshot.getRequiredProvidersJson());
			release.setAnalysisPolicySnapshot(snapshot.getAnalysisPolicySnapshot());
			release.setAnalysisPolicyDigest(snapshot.getAnalysisPolicyDigest());
			release.setAnalysisResultSchema(snapshot.getAnalysisResultSchema());
			release.setAnalysisResultSchemaId(snapshot.getAnalysisResultSchemaId());
			release.setIsDefaultEnabled(snapshot.getIsDefaultEnabled());
			release.setSkillConfig(snapshot.getSkillConfig());
			release.setExecutionStrategy(snapshot.getExecutionStrategy());
			release.setWorkflowAgentId(snapshot.getWorkflowAgentId());
			release.setInputMapping(snapshot.getInputMapping());
			release.setOutputMapping(snapshot.getOutputMapping());
			release.setResultMode(snapshot.getResultMode());
			release.setTimeoutMs(snapshot.getTimeoutMs());
			release.setRetryPolicy(snapshot.getRetryPolicy());
			release.setSideEffectClass(snapshot.getSideEffectClass());
			release.setSort(snapshot.getSort());
			release.setReleasedAt(releasedAt);
			releaseMapper.insert(release);
		}
	}

	private List<AiAgentSkillReleaseEntity> findRelease(Long agentId, String releaseVersion) {
		if (!StringUtils.hasText(releaseVersion)) {
			throw new IllegalArgumentException("Skill 发布版本不能为空");
		}
		return releaseMapper.selectList(Wrappers.<AiAgentSkillReleaseEntity>lambdaQuery()
			.eq(AiAgentSkillReleaseEntity::getAgentId, agentId)
			.eq(AiAgentSkillReleaseEntity::getReleaseVersion, releaseVersion));
	}

	private void validatePromptReleaseSnapshots(List<AiAgentSkillReleaseEntity> releases) {
		Set<Long> promptSkillIds = promptSkillIds(
				releases.stream().map(AiAgentSkillReleaseEntity::getSkillId).toList());
		for (AiAgentSkillReleaseEntity release : releases) {
			validatePromptSnapshot(promptSkillIds, release.getSkillId(), release.getRuntimeKind(),
					release.getPromptSnapshot(), release.getInstructionDigest(), release.getPackageSha256(),
					release.getReleaseVersion());
			validateAnalysisSnapshot(release.getRuntimeKind(), release.getAnalysisPolicySnapshot(),
					release.getAnalysisPolicyDigest(), release.getAnalysisResultSchema(),
					release.getAnalysisResultSchemaId());
		}
	}

	private void validatePromptHistorySnapshots(List<AiAgentSkillReleaseHistoryEntity> snapshots) {
		Set<Long> promptSkillIds = promptSkillIds(
				snapshots.stream().map(AiAgentSkillReleaseHistoryEntity::getSkillId).toList());
		for (AiAgentSkillReleaseHistoryEntity snapshot : snapshots) {
			validatePromptSnapshot(promptSkillIds, snapshot.getSkillId(), snapshot.getRuntimeKind(),
					snapshot.getPromptSnapshot(), snapshot.getInstructionDigest(), snapshot.getPackageSha256(),
					snapshot.getReleaseVersion());
			validateAnalysisSnapshot(snapshot.getRuntimeKind(), snapshot.getAnalysisPolicySnapshot(),
					snapshot.getAnalysisPolicyDigest(), snapshot.getAnalysisResultSchema(),
					snapshot.getAnalysisResultSchemaId());
		}
	}

	private void validateAnalysisSnapshot(String runtimeKind, String policySnapshot, String policyDigest,
			String resultSchema, String resultSchemaId) {
		if (!"ASSISTANT_ANALYSIS".equals(runtimeKind)) {
			return;
		}
		if (!StringUtils.hasText(policySnapshot) || !StringUtils.hasText(policyDigest)
				|| !StringUtils.hasText(resultSchema) || !StringUtils.hasText(resultSchemaId)) {
			throw new IllegalArgumentException("ANALYSIS_SKILL_REIMPORT_REQUIRED: 分析 Skill 缺少冻结策略或输出 Schema");
		}
	}

	private Set<Long> promptSkillIds(List<Long> skillIds) {
		List<Long> nonNullSkillIds = skillIds.stream().filter(Objects::nonNull).distinct().toList();
		if (nonNullSkillIds.isEmpty()) {
			return Set.of();
		}
		return TenantBroker.noneAs(() -> aiSkillService.listByIds(nonNullSkillIds))
			.stream()
			.filter(skill -> "prompt_injection".equals(skill.getImplType()))
			.map(AiSkillEntity::getId)
			.collect(Collectors.toSet());
	}

	private void validatePromptSnapshot(Set<Long> promptSkillIds, Long skillId, String runtimeKind,
			String promptSnapshot, String instructionDigest, String packageSha256, String releaseVersion) {
		if (!promptSkillIds.contains(skillId)) {
			return;
		}
		if (!"PROMPT_INJECTION".equals(runtimeKind) || !StringUtils.hasText(promptSnapshot)
				|| !StringUtils.hasText(instructionDigest) || !StringUtils.hasText(packageSha256)
				|| !StringUtils.hasText(releaseVersion)) {
			throw new IllegalArgumentException(PROMPT_SKILL_REIMPORT_REQUIRED);
		}
	}

	private boolean isReleaseAvailable(AiAgentSkillReleaseEntity release) {
		String status = StringUtils.hasText(release.getReleaseStatus()) ? release.getReleaseStatus() : "ACTIVE";
		if ("DISABLED".equals(status)) {
			return false;
		}
		if (!"GRAY".equals(status)) {
			return true;
		}
		MatrixUser user = SecurityUtils.getUser();
		if (user == null || user.getId() == null) {
			return false;
		}
		try {
			AiAgentVersionGrayDTO gray = OBJECT_MAPPER.readValue(release.getGrayConfig(), AiAgentVersionGrayDTO.class);
			if (gray.getUserIds() != null && gray.getUserIds().contains(user.getId())) {
				return true;
			}
			return gray.getPercent() != null && gray.getPercent() > 0
					&& Math.abs(user.getId()) % 100 < gray.getPercent();
		}
		catch (Exception ex) {
			log.warn("解析 Skill 灰度规则失败, agentId={}, skillId={}", release.getAgentId(), release.getSkillId());
			return false;
		}
	}

	private String nextReleaseVersion(List<AiAgentSkillReleaseEntity> releases) {
		int max = releases.stream()
			.map(AiAgentSkillReleaseEntity::getReleaseVersion)
			.mapToInt(this::parseReleaseNumber)
			.max()
			.orElse(0);
		return "v" + (max + 1);
	}

	private int parseReleaseNumber(String version) {
		if (!StringUtils.hasText(version)) {
			return 0;
		}
		try {
			return Integer.parseInt(version.replaceFirst("^[^0-9]+", ""));
		}
		catch (NumberFormatException ex) {
			return 0;
		}
	}

	private void archiveAndAdvanceRelease(AiAgentSkillReleaseEntity release) {
		releaseHistoryMapper.insert(toHistory(release));
		release.setReleaseVersion("v" + (parseReleaseNumber(release.getReleaseVersion()) + 1));
	}

	private AiAgentSkillReleaseHistoryEntity toHistory(AiAgentSkillReleaseEntity release) {
		AiAgentSkillReleaseHistoryEntity history = new AiAgentSkillReleaseHistoryEntity();
		history.setTenantId(release.getTenantId());
		history.setAgentId(release.getAgentId());
		history.setSkillId(release.getSkillId());
		history.setReleaseVersion(release.getReleaseVersion());
		history.setReleaseStatus(release.getReleaseStatus());
		history.setGrayConfig(release.getGrayConfig());
		history.setRuntimeKind(release.getRuntimeKind());
		history.setPromptSnapshot(release.getPromptSnapshot());
		history.setInstructionDigest(release.getInstructionDigest());
		history.setPackageSha256(release.getPackageSha256());
		history.setRequiredProvidersJson(release.getRequiredProvidersJson());
		history.setAnalysisPolicySnapshot(release.getAnalysisPolicySnapshot());
		history.setAnalysisPolicyDigest(release.getAnalysisPolicyDigest());
		history.setAnalysisResultSchema(release.getAnalysisResultSchema());
		history.setAnalysisResultSchemaId(release.getAnalysisResultSchemaId());
		history.setIsDefaultEnabled(release.getIsDefaultEnabled());
		history.setSkillConfig(release.getSkillConfig());
		history.setExecutionStrategy(release.getExecutionStrategy());
		history.setWorkflowAgentId(release.getWorkflowAgentId());
		history.setInputMapping(release.getInputMapping());
		history.setOutputMapping(release.getOutputMapping());
		history.setResultMode(release.getResultMode());
		history.setTimeoutMs(release.getTimeoutMs());
		history.setRetryPolicy(release.getRetryPolicy());
		history.setSideEffectClass(release.getSideEffectClass());
		history.setSort(release.getSort());
		history.setCreateBy(release.getCreateBy());
		history.setCreateTime(LocalDateTime.now());
		return history;
	}

	void freezeRuntimeSnapshot(AiAgentSkillReleaseEntity release) {
		AiSkillEntity skill = aiSkillService.getById(release.getSkillId());
		AssistantSkillReleaseSnapshot snapshot = latestApprovedPackage(skill)
			.map(skillPackage -> AssistantSkillReleaseSnapshot.from(skillPackage, OBJECT_MAPPER))
			.orElseGet(() -> {
				if (isTrustedBuiltInPromptSkill(skill)) {
					return AssistantSkillReleaseSnapshot.fromBuiltIn(skill, OBJECT_MAPPER);
				}
				throw new IllegalArgumentException("PROMPT_SKILL_REIMPORT_REQUIRED: Skill 缺少已审核包，请重新导入、审核并发布");
			});
		release.setRuntimeKind(snapshot.runtimeKind());
		release.setPromptSnapshot(snapshot.instructionSnapshot());
		release.setInstructionDigest(snapshot.instructionDigest());
		release.setPackageSha256(snapshot.packageSha256());
		release.setRequiredProvidersJson(snapshot.requiredProvidersJson());
		release.setAnalysisPolicySnapshot(snapshot.analysisPolicySnapshot());
		release.setAnalysisPolicyDigest(snapshot.analysisPolicyDigest());
		release.setAnalysisResultSchema(snapshot.analysisResultSchema());
		release.setAnalysisResultSchemaId(snapshot.analysisResultSchemaId());
	}

	private boolean hasPublishablePromptSnapshot(AiSkillEntity skill) {
		if (latestApprovedPackage(skill).isPresent()) {
			return true;
		}
		if (!isTrustedBuiltInPromptSkill(skill)) {
			return false;
		}
		try {
			AssistantSkillReleaseSnapshot.fromBuiltIn(skill, OBJECT_MAPPER);
			return true;
		}
		catch (IllegalArgumentException ex) {
			log.warn("系统内置 Prompt Skill 配置无效，跳过同步: skillId={}, reason={}", skill.getId(), ex.getMessage());
			return false;
		}
	}

	private boolean isTrustedBuiltInPromptSkill(AiSkillEntity skill) {
		return skill != null && "0".equals(skill.getTenantId()) && "builtin".equals(skill.getSkillType())
				&& "prompt_injection".equals(skill.getImplType());
	}

	private java.util.Optional<AiSkillPackageEntity> latestApprovedPackage(AiSkillEntity skill) {
		if (skill == null || skill.getId() == null || !StringUtils.hasText(skill.getTenantId())) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.ofNullable(skillPackageMapper.selectOne(Wrappers.<AiSkillPackageEntity>lambdaQuery()
			.eq(AiSkillPackageEntity::getSourceChannel, "LEGACY")
			.eq(AiSkillPackageEntity::getSkillId, skill.getId())
			.eq(AiSkillPackageEntity::getTenantId, skill.getTenantId())
			.eq(AiSkillPackageEntity::getPackageStatus, "APPROVED")
			.orderByDesc(AiSkillPackageEntity::getUpdateTime)
			.orderByDesc(AiSkillPackageEntity::getId)
			.last("LIMIT 1")));
	}

	private void validateDedicatedWorkflow(AiAgentEntity parent, AiAgentSkillEntity binding) {
		if (binding.getWorkflowAgentId() == null || binding.getWorkflowAgentId().equals(parent.getId())) {
			throw new IllegalArgumentException("DIFY_WORKFLOW_NOT_PUBLISHED: 专用 Workflow 绑定无效");
		}
		AiAgentEntity workflow = aiAgentService.getById(binding.getWorkflowAgentId());
		if (workflow == null || !"dify".equals(workflow.getAgentType()) || !"workflow".equals(workflow.getDifyMode())
				|| !"published".equals(workflow.getStatus()) || !StringUtils.hasText(workflow.getDifyAppId())
				|| !StringUtils.hasText(workflow.getDifyApiKey())) {
			throw new IllegalArgumentException("DIFY_WORKFLOW_NOT_PUBLISHED: 专用 Workflow 未发布或未配置 API Key");
		}
		if (StringUtils.hasText(parent.getTenantId()) && StringUtils.hasText(workflow.getTenantId())
				&& !parent.getTenantId().equals(workflow.getTenantId())) {
			throw new IllegalArgumentException("DIFY_WORKFLOW_NOT_PUBLISHED: 不能跨租户绑定 Workflow");
		}
		if (!Set.of("READ_ONLY", "PROPOSAL_ONLY").contains(binding.getSideEffectClass())) {
			throw new IllegalArgumentException("DIFY_WORKFLOW_SIDE_EFFECT_FORBIDDEN");
		}
		if (!"FINAL_RESPONSE".equals(binding.getResultMode())) {
			throw new IllegalArgumentException("DIFY_OUTPUT_MAPPING_FAILED: MVP 仅支持 FINAL_RESPONSE");
		}
		Map<String, String> inputMapping = parseStringMap(binding.getInputMapping());
		for (String required : List.of("matrix_query", "matrix_context_json", "matrix_request_id")) {
			if (!StringUtils.hasText(inputMapping.get(required))) {
				throw new IllegalArgumentException("DIFY_INPUT_CONTRACT_MISMATCH: 缺少 " + required + " 映射");
			}
		}
		Map<String, String> outputMapping = parseStringMap(binding.getOutputMapping());
		if (!StringUtils.hasText(outputMapping.get("answer"))) {
			throw new IllegalArgumentException("DIFY_OUTPUT_MAPPING_FAILED: 缺少 answer 映射");
		}

		DifyServiceClient difyServiceClient = difyServiceClientProvider.getIfAvailable();
		if (difyServiceClient == null) {
			throw new IllegalArgumentException("DIFY_RUNTIME_CALL_FAILED: Dify Service API 未启用");
		}
		JsonNode parameters = difyServiceClient.getParameters(workflow.getDifyApiKey()).block(DIFY_VALIDATION_TIMEOUT);
		Set<String> variables = extractInputVariables(parameters);
		for (String target : inputMapping.values()) {
			if (!variables.contains(target)) {
				throw new IllegalArgumentException("DIFY_INPUT_CONTRACT_MISMATCH: Dify 不存在输入变量 " + target);
			}
		}
	}

	static Set<String> extractInputVariables(JsonNode parameters) {
		Set<String> variables = new HashSet<>();
		JsonNode forms = parameters == null ? null : parameters.path("user_input_form");
		if (forms == null || !forms.isArray()) {
			return variables;
		}
		for (JsonNode form : forms) {
			// Dify Service API 返回的是 [{"variable":"query", ...}]。
			// 旧版导出格式可能把同类字段再包一层对象，因此保留嵌套对象兼容。
			String variable = form.path("variable").asText(null);
			if (StringUtils.hasText(variable)) {
				variables.add(variable);
				continue;
			}
			form.fields().forEachRemaining(field -> {
				String nestedVariable = field.getValue().path("variable").asText(null);
				if (StringUtils.hasText(nestedVariable)) {
					variables.add(nestedVariable);
				}
			});
		}
		return variables;
	}

	/**
	 * 只有当前租户可见且已审核启用的 Skill 才能进入智能体草稿，避免通过直接调用接口绕过后台审核。
	 */
	private void validateSkillBindings(Long agentId, List<AiAgentSkillDTO> skills) {
		if (agentId == null) {
			throw new IllegalArgumentException("智能体 ID 不能为空");
		}
		AiAgentEntity agent = AiAgentService.DEFAULT_AGENT_ID.equals(agentId)
				? TenantBroker.noneAs(() -> aiAgentService.getById(agentId)) : aiAgentService.getById(agentId);
		if (agent == null) {
			throw new IllegalArgumentException("智能体不存在: " + agentId);
		}
		if (skills == null || skills.isEmpty()) {
			return;
		}
		Set<Long> requestedIds = new HashSet<>();
		for (AiAgentSkillDTO dto : skills) {
			if (dto == null || dto.getSkillId() == null || !requestedIds.add(dto.getSkillId())) {
				throw new IllegalArgumentException("Skill 不能为空且不能重复绑定");
			}
		}
		String tenantId = currentTenantId();
		List<AiSkillEntity> enabled = aiSkillService.list(Wrappers.<AiSkillEntity>lambdaQuery()
			.in(AiSkillEntity::getId, requestedIds)
			.in(AiSkillEntity::getTenantId, List.of("0", tenantId))
			.eq(AiSkillEntity::getIsEnabled, 1));
		Set<Long> enabledIds = enabled.stream().map(AiSkillEntity::getId).collect(Collectors.toSet());
		if (enabledIds.size() != requestedIds.size()) {
			throw new IllegalArgumentException("只能绑定当前租户已审核启用的 Skill");
		}
	}

	private String currentTenantId() {
		Long tenantId = TenantContextHolder.getTenantId();
		return tenantId == null ? "0" : String.valueOf(tenantId);
	}

	private String operator() {
		MatrixUser user = SecurityUtils.getUser();
		return user == null ? "system" : user.getUsername();
	}

	private void syncArtifactToDify(Long agentId, boolean bind) {
		AiAgentEntity agent = aiAgentService.getById(agentId);
		if (agent == null || !"dify".equals(agent.getAgentType())) {
			return;
		}
		String difyAppId = agent.getDifyAppId();
		if (difyAppId == null || difyAppId.isEmpty()) {
			return;
		}
		difyAgentBridge.syncArtifactVariable(difyAppId, bind);
	}

	private void syncChatDbToDify(Long agentId, Long dataId, String dataName) {
		AiAgentEntity agent = aiAgentService.getById(agentId);
		if (agent == null) {
			return;
		}
		String difyAppId = agent.getDifyAppId();
		if (!StringUtils.hasText(difyAppId)) {
			return;
		}
		difyAgentBridge.syncChat2biTool(difyAppId, agent.getDifyMode(), dataId, dataName);
	}

	private Map<String, Object> parseSkillConfig(String json) {
		if (!StringUtils.hasText(json)) {
			return null;
		}
		try {
			return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
			});
		}
		catch (Exception e) {
			log.warn("[AiAgentSkillService] 解析 skillConfig 失败: {}", json);
			return null;
		}
	}

	private Map<String, String> parseStringMap(String json) {
		if (!StringUtils.hasText(json)) {
			return Map.of();
		}
		try {
			return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
			});
		}
		catch (Exception e) {
			throw new IllegalStateException("解析 Workflow 变量映射失败", e);
		}
	}

	private List<String> parseStringList(String json) {
		if (!StringUtils.hasText(json)) {
			return List.of();
		}
		try {
			return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
			});
		}
		catch (Exception ex) {
			throw new IllegalStateException("解析 Skill Provider 要求失败", ex);
		}
	}

	private String serializeSkillConfig(Map<String, Object> config) {
		if (config == null || config.isEmpty()) {
			return null;
		}
		try {
			return OBJECT_MAPPER.writeValueAsString(config);
		}
		catch (Exception e) {
			log.warn("[AiAgentSkillService] 序列化 skillConfig 失败");
			return null;
		}
	}

	private String serializeStringMap(Map<String, String> mapping) {
		if (mapping == null || mapping.isEmpty()) {
			return null;
		}
		try {
			return OBJECT_MAPPER.writeValueAsString(mapping);
		}
		catch (Exception e) {
			throw new IllegalArgumentException("Workflow 变量映射序列化失败", e);
		}
	}

	private Long parseLong(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		try {
			return Long.parseLong(value.toString());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
