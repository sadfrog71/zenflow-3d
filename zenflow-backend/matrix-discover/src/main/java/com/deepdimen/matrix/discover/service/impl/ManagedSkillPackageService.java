package com.deepdimen.matrix.discover.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.discover.entity.AiSkillEntity;
import com.deepdimen.matrix.discover.entity.AiSkillPackageEntity;
import com.deepdimen.matrix.discover.mapper.AiSkillMapper;
import com.deepdimen.matrix.discover.mapper.AiSkillPackageMapper;
import com.deepdimen.matrix.discover.skill.AgentSkillPackageParser;
import com.deepdimen.matrix.discover.skill.ManagedSkillReleaseSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Managed imports reuse the directory identity without updating its legacy content. */
@Service
@RequiredArgsConstructor
public class ManagedSkillPackageService {

	private final AiSkillMapper skills;

	private final AiSkillPackageMapper packages;

	private final AgentSkillPackageParser parser;

	private final ObjectMapper json;

	@Transactional(rollbackFor = Exception.class)
	@PreAuthorize("@pms.hasPermission('knowledge_skill_import')")
	public Long importPackage(byte[] archive, String filename) {
		var identity = identity();
		var parsed = parser.parse(archive, filename);
		var snapshot = new ManagedSkillReleaseSnapshot(json).prompt(parsed, parsed.sha256());
		AiSkillEntity skill = skills.selectOne(Wrappers.<AiSkillEntity>lambdaQuery()
			.eq(AiSkillEntity::getTenantId, identity.tenant())
			.eq(AiSkillEntity::getSkillCode, parsed.name()));
		if (skill == null) {
			if (skills.selectIncludingDeleted(identity.tenant(), parsed.name()) != null) {
				throw new IllegalArgumentException("同标识 Skill 已删除，请先处理原目录，不创建同名副本");
			}
			skill = new AiSkillEntity();
			skill.setId(IdWorker.getId());
			skill.setTenantId(identity.tenant());
			skill.setSkillCode(parsed.name());
			skill.setSkillName(parsed.frontmatter().getOrDefault("name", parsed.name()));
			skill.setDescription(parsed.description());
			skill.setSkillType("custom");
			skill.setManagementMode("MANAGED");
			skill.setImplType("prompt_injection");
			skill.setConfig("{}");
			skill.setIsEnabled(0);
			skill.setSort(100);
			skill.setDelFlag("0");
			skill.setCreateBy(identity.actor());
			skill.setUpdateBy(identity.actor());
			skill.setCreateTime(LocalDateTime.now());
			skill.setUpdateTime(skill.getCreateTime());
			if (skills.insert(skill) != 1) {
				throw new IllegalStateException("Skill 目录创建失败");
			}
		}
		// Existing LEGACY rows are deliberately left byte-for-byte unchanged.
		AiSkillPackageEntity entry = new AiSkillPackageEntity();
		entry.setId(IdWorker.getId());
		entry.setTenantId(identity.tenant());
		entry.setSkillId(skill.getId());
		entry.setSourceChannel("MANAGED");
		entry.setPackageVersion("m-" + entry.getId());
		entry.setOriginalFilename(filename);
		entry.setPackageSha256(parsed.sha256());
		entry.setPackageSize(parsed.size());
		entry.setManifestJson(parsed.manifest().toString());
		entry.setValidationReport(json.createObjectNode()
			.put("releaseDigest", snapshot.getReleaseDigest())
			.put("runtimeKind", "PROMPT")
			.put("scriptsExecution", "DISABLED")
			.toString());
		entry.setPackageStatus("PENDING_REVIEW");
		entry.setArchiveData(archive.clone());
		entry.setCreateBy(identity.actor());
		entry.setUpdateBy(identity.actor());
		entry.setCreateTime(LocalDateTime.now());
		entry.setUpdateTime(entry.getCreateTime());
		if (packages.insert(entry) != 1) {
			throw new IllegalStateException("托管包保存失败");
		}
		return entry.getId();
	}

	@Transactional(rollbackFor = Exception.class)
	@PreAuthorize("@pms.hasPermission('knowledge_skill_approve')")
	public void approve(Long packageId) {
		var identity = identity();
		var entry = packages.lockManagedPackage(packageId, identity.tenant());
		if (entry == null || !identity.tenant().equals(entry.getTenantId())
				|| !"MANAGED".equals(entry.getSourceChannel()) || !"PENDING_REVIEW".equals(entry.getPackageStatus())) {
			throw new IllegalArgumentException("当前租户没有对应待审核托管包");
		}
		var parsed = parser.parse(entry.getArchiveData(), entry.getOriginalFilename());
		var snapshot = new ManagedSkillReleaseSnapshot(json).prompt(parsed, entry.getPackageSha256());
		var report = json.createObjectNode()
			.put("releaseDigest", snapshot.getReleaseDigest())
			.put("runtimeKind", "PROMPT")
			.put("scriptsExecution", "DISABLED")
			.put("reviewedBy", identity.actor())
			.put("reviewedAt", LocalDateTime.now().toString());
		if (packages.approveManaged(packageId, identity.tenant(), entry.getPackageSha256(), report.toString(),
				identity.actor()) != 1) {
			throw new IllegalStateException("审核状态已变化，请刷新后重试");
		}
	}

	private Identity identity() {
		Long tenant = TenantContextHolder.getTenantId();
		var user = SecurityUtils.getUser();
		if (tenant == null || tenant == 0L || user == null) {
			throw new IllegalArgumentException("托管包管理需要当前租户和已认证操作者");
		}
		return new Identity(tenant.toString(), user.getUsername());
	}

	private record Identity(String tenant, String actor) {
	}

}
