package com.deepdimen.matrix.discover.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.discover.entity.AiSkillEntity;
import com.deepdimen.matrix.discover.entity.AiSkillPackageEntity;
import com.deepdimen.matrix.discover.mapper.AiSkillPackageMapper;
import com.deepdimen.matrix.discover.mapper.AiSkillMapper;
import com.deepdimen.matrix.discover.service.AiAgentSkillService;
import com.deepdimen.matrix.discover.service.AiSkillPackageService;
import com.deepdimen.matrix.discover.skill.AgentSkillPackageParser;
import com.deepdimen.matrix.discover.vo.AiSkillAdminVO;
import com.deepdimen.matrix.discover.vo.AiSkillImportVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiSkillPackageServiceImpl extends ServiceImpl<AiSkillPackageMapper, AiSkillPackageEntity>
		implements AiSkillPackageService {

	private static final String SYSTEM_TENANT_ID = "0";

	private static final String PENDING_REVIEW = "PENDING_REVIEW";

	private final AiSkillMapper skillMapper;

	private final ObjectMapper objectMapper;

	private final AgentSkillPackageParser parser;

	private final AiAgentSkillService aiAgentSkillService;

	@Override
	public Page<AiSkillAdminVO> page(Page<AiSkillAdminVO> page, String keyword, String packageStatus) {
		String tenantId = tenantId();
		var query = Wrappers.<AiSkillEntity>lambdaQuery()
			.in(AiSkillEntity::getTenantId, Arrays.asList(SYSTEM_TENANT_ID, tenantId));
		if (StringUtils.hasText(keyword)) {
			query.and(nested -> nested.like(AiSkillEntity::getSkillCode, keyword)
				.or()
				.like(AiSkillEntity::getSkillName, keyword)
				.or()
				.like(AiSkillEntity::getDescription, keyword));
		}
		List<AiSkillEntity> skills = skillMapper.selectList(query.orderByAsc(AiSkillEntity::getSort)
			.orderByAsc(AiSkillEntity::getId));
		Map<Long, AiSkillPackageEntity> latestPackages = latestBySkill(skills);
		List<AiSkillAdminVO> rows = skills.stream()
			.map(skill -> toVO(skill, latestPackages.get(skill.getId())))
			.filter(row -> !StringUtils.hasText(packageStatus) || packageStatus.equals(row.getPackageStatus()))
			.toList();
		long current = Math.max(1, page.getCurrent());
		long size = page.getSize() > 0 ? page.getSize() : 10;
		long from = Math.min((current - 1) * size, rows.size());
		long to = Math.min(from + size, rows.size());
		page.setTotal(rows.size());
		page.setRecords(rows.subList((int) from, (int) to));
		return page;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void reorderSkills(List<Long> skillIds) {
		if (skillIds == null || skillIds.isEmpty() || skillIds.stream().anyMatch(java.util.Objects::isNull)) {
			throw new IllegalArgumentException("Skill 排序列表不能为空");
		}
		Set<Long> requestedIds = new HashSet<>(skillIds);
		if (requestedIds.size() != skillIds.size()) {
			throw new IllegalArgumentException("Skill 排序列表包含重复项");
		}
		String tenantId = tenantId();
		List<AiSkillEntity> visibleSkills = TenantBroker.noneAs(() -> skillMapper.selectList(
			Wrappers.<AiSkillEntity>lambdaQuery()
				.in(AiSkillEntity::getTenantId, Arrays.asList(SYSTEM_TENANT_ID, tenantId))
				.orderByAsc(AiSkillEntity::getSort)
				.orderByAsc(AiSkillEntity::getId)));
		Set<Long> visibleIds = visibleSkills.stream().map(AiSkillEntity::getId).collect(java.util.stream.Collectors.toSet());
		if (!visibleIds.equals(requestedIds)) {
			throw new IllegalArgumentException("Skill 列表已变化，请刷新后重新排序");
		}
		Map<Long, AiSkillEntity> byId = visibleSkills.stream()
			.collect(java.util.stream.Collectors.toMap(AiSkillEntity::getId, skill -> skill));
		for (int index = 0; index < skillIds.size(); index++) {
			AiSkillEntity skill = byId.get(skillIds.get(index));
			skill.setSort(index);
			TenantBroker.noneAs(() -> skillMapper.updateById(skill));
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public AiSkillImportVO importPackage(MultipartFile file) {
		return importPackageInternal(file, null);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public AiSkillImportVO updatePackage(Long skillId, MultipartFile file) {
		AiSkillEntity skill = requireTenantSkill(skillId);
		if (!"custom".equals(skill.getSkillType())) {
			throw new IllegalArgumentException("系统内置 Skill 不支持更新");
		}
		return importPackageInternal(file, skillId);
	}

	private AiSkillImportVO importPackageInternal(MultipartFile file, Long expectedSkillId) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("请选择 Skill ZIP 文件");
		}
		if (!StringUtils.hasText(file.getOriginalFilename())
				|| !file.getOriginalFilename().toLowerCase().endsWith(".zip")) {
			throw new IllegalArgumentException("只支持 .zip 格式的 Skill 包");
		}
		try {
			byte[] archive = file.getBytes();
			AgentSkillPackageParser.ParsedPackage parsed = parser.parse(archive, file.getOriginalFilename());
			String tenantId = tenantId();
			AiSkillEntity skill = skillMapper.selectOne(Wrappers.<AiSkillEntity>lambdaQuery()
				.eq(AiSkillEntity::getTenantId, tenantId)
				.eq(AiSkillEntity::getSkillCode, parsed.name()));
			boolean restored = false;
			if (skill == null) {
				AiSkillEntity deletedSkill = skillMapper.selectIncludingDeleted(tenantId, parsed.name());
				if (deletedSkill != null) {
					skill = deletedSkill;
					restored = true;
				}
			}
			if (expectedSkillId != null) {
				if (skill == null || !expectedSkillId.equals(skill.getId())) {
					throw new IllegalArgumentException("更新包中的 Skill 标识必须与原 Skill 一致");
				}
			}
			if (skill != null) {
				requireLegacyManagement(skill);
			}
			boolean existingSkill = skill != null && !restored;
			String operator = operator();
			LocalDateTime now = LocalDateTime.now();
			if (skill == null) {
				skill = new AiSkillEntity();
				skill.setTenantId(tenantId);
				skill.setManagementMode("LEGACY");
				skill.setSkillCode(parsed.name());
				skill.setSkillType("custom");
				skill.setSort(100);
				skill.setCreateBy(operator);
				skill.setCreateTime(now);
			}
			if (restored) {
				applyPackageMetadata(skill, parsed);
				skill.setSkillType("custom");
				skill.setIsEnabled(0);
				skill.setSort(100);
				skill.setUpdateBy(operator);
				skill.setUpdateTime(now);
				if (skillMapper.restoreDeleted(skill) != 1) {
					throw new IllegalArgumentException("恢复已删除 Skill 失败，请稍后重试");
				}
			}
			if (skill.getId() == null) {
				applyPackageMetadata(skill, parsed);
				// 新 Skill 只有审核通过后才能进入运行时。
				skill.setIsEnabled(0);
				skill.setUpdateBy(operator);
				skill.setUpdateTime(now);
				skillMapper.insert(skill);
			}

			AiSkillPackageEntity duplicate = getOne(Wrappers.<AiSkillPackageEntity>lambdaQuery()
				.eq(AiSkillPackageEntity::getSourceChannel, "LEGACY")
				.eq(AiSkillPackageEntity::getTenantId, tenantId)
				.eq(AiSkillPackageEntity::getSkillId, skill.getId())
				.eq(AiSkillPackageEntity::getPackageSha256, parsed.sha256()));
			if (duplicate != null && !"ARCHIVED".equals(duplicate.getPackageStatus())) {
				throw new IllegalArgumentException("相同 Skill 包已经导入");
			}
			if (existingSkill) {
				skill.setIsEnabled(0);
				skill.setUpdateBy(operator);
				skill.setUpdateTime(now);
				skillMapper.updateById(skill);
				aiAgentSkillService.syncDefaultAgentSkill(skill.getId(), false);
			}
			AiSkillPackageEntity packageEntity = duplicate == null ? new AiSkillPackageEntity() : duplicate;
			packageEntity.setTenantId(tenantId);
			packageEntity.setSourceChannel("LEGACY");
			packageEntity.setSkillId(skill.getId());
			if (duplicate == null) {
				packageEntity.setPackageVersion(nextVersion(skill.getId()));
			}
			packageEntity.setOriginalFilename(parsed.originalFilename());
			packageEntity.setPackageSha256(parsed.sha256());
			packageEntity.setPackageSize(parsed.size());
			packageEntity.setManifestJson(writeJson(stagedManifest(parsed)));
			packageEntity.setValidationReport(writeJson(validationReport(parsed)));
			packageEntity.setPackageStatus(PENDING_REVIEW);
			packageEntity.setArchiveData(archive);
			if (duplicate == null) {
				packageEntity.setCreateBy(operator);
				packageEntity.setCreateTime(now);
			}
			packageEntity.setUpdateBy(operator);
			packageEntity.setUpdateTime(now);
			saveOrUpdate(packageEntity);

			AiSkillImportVO result = new AiSkillImportVO();
			result.setSkillId(skill.getId());
			result.setPackageId(packageEntity.getId());
			result.setSkillCode(parsed.name());
			result.setSkillName(parsed.description().isBlank() ? parsed.name() : skill.getSkillName());
			result.setPackageVersion(packageEntity.getPackageVersion());
			result.setPackageStatus(PENDING_REVIEW);
			result.setValidationReport(packageEntity.getValidationReport());
			return result;
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("读取 Skill 包失败", ex);
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void approve(Long skillId) {
		AiSkillEntity skill = requireTenantSkill(skillId);
		AiSkillPackageEntity latest = latestPackage(skillId);
		if (latest == null || !PENDING_REVIEW.equals(latest.getPackageStatus())) {
			throw new IllegalArgumentException("没有待审核的 Skill 包");
		}
		latest.setPackageStatus("APPROVED");
		latest.setUpdateBy(operator());
		latest.setUpdateTime(LocalDateTime.now());
		updateById(latest);
		applyApprovedPackage(skill, latest);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void disable(Long skillId) {
		unpublish(skillId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void enable(Long skillId) {
		publish(skillId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void publish(Long skillId) {
		AiSkillEntity skill = requireTenantSkill(skillId);
		if (!"custom".equals(skill.getSkillType())) {
			throw new IllegalArgumentException("系统内置 Skill 不支持租户上架操作");
		}
		AiSkillPackageEntity latest = latestPackage(skillId);
		if (latest == null || !"APPROVED".equals(latest.getPackageStatus())) {
			throw new IllegalArgumentException("只能启用已审核通过的 Skill");
		}
		applyApprovedPackage(skill, latest);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void unpublish(Long skillId) {
		AiSkillEntity skill = requireTenantSkill(skillId);
		if (!"custom".equals(skill.getSkillType())) {
			throw new IllegalArgumentException("系统内置 Skill 不支持租户下架操作");
		}
		skill.setIsEnabled(0);
		skill.setUpdateBy(operator());
		skill.setUpdateTime(LocalDateTime.now());
		skillMapper.updateById(skill);
		aiAgentSkillService.syncDefaultAgentSkill(skillId, false);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void delete(Long skillId) {
		AiSkillEntity skill = requireTenantSkill(skillId);
		if (!"custom".equals(skill.getSkillType())) {
			throw new IllegalArgumentException("系统内置 Skill 不允许删除");
		}
		if (aiAgentSkillService.hasNonDefaultBindings(skillId)) {
			throw new IllegalArgumentException("Skill 仍绑定在其他智能体，请先解除绑定后再删除");
		}
		if (count(Wrappers.<AiSkillPackageEntity>lambdaQuery()
			.eq(AiSkillPackageEntity::getTenantId, tenantId())
			.eq(AiSkillPackageEntity::getSkillId, skillId)
			.eq(AiSkillPackageEntity::getSourceChannel, "MANAGED")) > 0) {
			throw new IllegalArgumentException("Skill 存在托管包，不能通过旧入口删除，请管理具体绑定");
		}
		aiAgentSkillService.removeDefaultAgentSkill(skillId);
		update(Wrappers.<AiSkillPackageEntity>lambdaUpdate()
			.eq(AiSkillPackageEntity::getSourceChannel, "LEGACY")
			.eq(AiSkillPackageEntity::getTenantId, tenantId())
			.eq(AiSkillPackageEntity::getSkillId, skillId)
			.set(AiSkillPackageEntity::getPackageStatus, "ARCHIVED")
			.set(AiSkillPackageEntity::getUpdateBy, operator())
			.set(AiSkillPackageEntity::getUpdateTime, LocalDateTime.now()));
		skillMapper.deleteById(skillId);
	}

	@Override
	public SkillPackageDownload download(Long packageId) {
		if (packageId == null) {
			throw new IllegalArgumentException("Skill 包 ID 不能为空");
		}
		AiSkillPackageEntity skillPackage = baseMapper.selectArchiveById(packageId, tenantId());
		if (skillPackage == null || skillPackage.getArchiveData() == null) {
			throw new IllegalArgumentException("Skill 包不存在或归档内容不可用");
		}
		String filename = StringUtils.hasText(skillPackage.getOriginalFilename())
				? skillPackage.getOriginalFilename().replaceAll("[\\r\\n\\\\/]", "_") : "skill-package.zip";
		return new SkillPackageDownload(skillPackage.getArchiveData(), filename, skillPackage.getPackageSha256(),
				skillPackage.getArchiveData().length);
	}

	private void applyApprovedPackage(AiSkillEntity skill, AiSkillPackageEntity packageEntity) {
		try {
			JsonNode manifest = objectMapper.readTree(packageEntity.getManifestJson());
			String skillName = manifest.path("skillName").asText(null);
			String description = manifest.path("description").asText(null);
			JsonNode config = manifest.get("config");
			if (StringUtils.hasText(skillName)) {
				skill.setSkillName(skillName);
			}
			if (description != null) {
				skill.setDescription(description);
			}
			if (config != null && !config.isNull()) {
				skill.setImplType(implType(manifest.path("runtimeKind").asText()));
				skill.setConfig(config.toString());
			}
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("Skill 包元数据损坏，无法发布", ex);
		}
		skill.setIsEnabled(1);
		skill.setUpdateBy(operator());
		skill.setUpdateTime(LocalDateTime.now());
		skillMapper.updateById(skill);
		aiAgentSkillService.syncDefaultAgentSkill(skill.getId(), true);
	}

	private ObjectNode stagedManifest(AgentSkillPackageParser.ParsedPackage parsed) {
		ObjectNode manifest = parsed.manifest().deepCopy();
		manifest.put("skillName", parsed.frontmatter().getOrDefault("name", parsed.name()));
		manifest.put("description", parsed.description());
		manifest.set("config", parsed.config().deepCopy());
		return manifest;
	}

	private void applyPackageMetadata(AiSkillEntity skill, AgentSkillPackageParser.ParsedPackage parsed) {
		skill.setSkillName(parsed.frontmatter().getOrDefault("name", parsed.name()));
		skill.setDescription(parsed.description());
		skill.setImplType(implType(parsed.manifest().path("runtimeKind").asText()));
		skill.setConfig(parsed.config().toString());
	}

	private String implType(String runtimeKind) {
		return "ASSISTANT_ANALYSIS".equals(runtimeKind) ? "assistant_analysis" : "prompt_injection";
	}

	private AiSkillEntity requireTenantSkill(Long skillId) {
		if (skillId == null) {
			throw new IllegalArgumentException("技能 ID 不能为空");
		}
		AiSkillEntity skill = skillMapper.selectOne(Wrappers.<AiSkillEntity>lambdaQuery()
			.eq(AiSkillEntity::getId, skillId)
			.eq(AiSkillEntity::getTenantId, tenantId()));
		if (skill == null) {
			throw new IllegalArgumentException("只能管理当前租户创建的 Skill");
		}
		requireLegacyManagement(skill);
		return skill;
	}

	private void requireLegacyManagement(AiSkillEntity skill) {
		if (skill.getManagementMode() != null && !"LEGACY".equals(skill.getManagementMode())) {
			throw new IllegalArgumentException("托管 Skill 请通过版本与绑定管理，不能使用旧管理入口");
		}
	}

	private Map<Long, AiSkillPackageEntity> latestBySkill(List<AiSkillEntity> skills) {
		if (skills.isEmpty()) {
			return Map.of();
		}
		List<Long> ids = skills.stream().map(AiSkillEntity::getId).toList();
		return list(Wrappers.<AiSkillPackageEntity>lambdaQuery()
			.eq(AiSkillPackageEntity::getSourceChannel, "LEGACY")
			.in(AiSkillPackageEntity::getSkillId, ids)
			.in(AiSkillPackageEntity::getTenantId, Arrays.asList(SYSTEM_TENANT_ID, tenantId()))).stream()
			.collect(
					java.util.stream.Collectors.groupingBy(AiSkillPackageEntity::getSkillId,
							java.util.stream.Collectors.collectingAndThen(
									java.util.stream.Collectors
										.maxBy(Comparator.comparing(AiSkillPackageEntity::getUpdateTime,
												Comparator.nullsFirst(Comparator.naturalOrder()))),
									value -> value.orElse(null))));
	}

	private AiSkillPackageEntity latestPackage(Long skillId) {
		return list(Wrappers.<AiSkillPackageEntity>lambdaQuery()
			.eq(AiSkillPackageEntity::getSourceChannel, "LEGACY")
			.eq(AiSkillPackageEntity::getTenantId, tenantId())
			.eq(AiSkillPackageEntity::getSkillId, skillId)
			.orderByDesc(AiSkillPackageEntity::getUpdateTime)
			.last("limit 1")).stream().findFirst().orElse(null);
	}

	private String nextVersion(Long skillId) {
		int count = (int) count(Wrappers.<AiSkillPackageEntity>lambdaQuery()
			.eq(AiSkillPackageEntity::getSourceChannel, "LEGACY")
			.eq(AiSkillPackageEntity::getTenantId, tenantId())
			.eq(AiSkillPackageEntity::getSkillId, skillId));
		return "v" + (count + 1);
	}

	private AiSkillAdminVO toVO(AiSkillEntity skill, AiSkillPackageEntity packageEntity) {
		AiSkillAdminVO vo = new AiSkillAdminVO();
		vo.setId(skill.getId());
		vo.setTenantId(skill.getTenantId());
		vo.setSkillCode(skill.getSkillCode());
		vo.setSkillName(skill.getSkillName());
		vo.setDescription(skill.getDescription());
		vo.setIcon(skill.getIcon());
		vo.setSkillType(skill.getSkillType());
		vo.setImplType(skill.getImplType());
		vo.setIsEnabled(skill.getIsEnabled());
		vo.setSort(skill.getSort());
		vo.setUpdateTime(skill.getUpdateTime());
		if (packageEntity != null) {
			vo.setPackageId(packageEntity.getId());
			vo.setPackageStatus(packageEntity.getPackageStatus());
			vo.setPackageVersion(packageEntity.getPackageVersion());
			vo.setPackageSha256(packageEntity.getPackageSha256());
			vo.setOriginalFilename(packageEntity.getOriginalFilename());
			vo.setPackageSize(packageEntity.getPackageSize());
			vo.setValidationReport(packageEntity.getValidationReport());
		}
		return vo;
	}

	private Map<String, Object> validationReport(AgentSkillPackageParser.ParsedPackage parsed) {
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("status", PENDING_REVIEW);
		report.put("files", parsed.files());
		report.put("warnings", parsed.warnings());
		report.put("scriptsExecution", "DISABLED");
		return report;
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("Skill 包元数据序列化失败", ex);
		}
	}

	private String tenantId() {
		Long tenant = TenantContextHolder.getTenantId();
		return tenant == null ? SYSTEM_TENANT_ID : String.valueOf(tenant);
	}

	private String operator() {
		MatrixUser user = SecurityUtils.getUser();
		return user == null ? "system" : user.getUsername();
	}

}
