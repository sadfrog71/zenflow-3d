package com.deepdimen.matrix.discover.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.discover.entity.*;
import com.deepdimen.matrix.discover.mapper.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/** Management-only tenant-owned catalog; not an execution authorization API. */
@Service
@RequiredArgsConstructor
public class ManagedSkillCatalogService {

	private final AiSkillPackageMapper packages;

	private final AiSkillReleaseMapper releases;

	private final com.deepdimen.matrix.discover.skill.AgentSkillPackageParser parser;

	private final com.fasterxml.jackson.databind.ObjectMapper json;

	@PreAuthorize("@pms.hasPermission('knowledge_skill_view')")
	public PackagePreview preview(Long packageId) {
		String tenant = tenant();
		var archive = packages.findManagedArchive(packageId, tenant);
		if (archive == null || !tenant.equals(archive.getTenantId()) || !"MANAGED".equals(archive.getSourceChannel()))
			throw new IllegalArgumentException("托管包不存在");
		var parsed = parser.parse(archive.getArchiveData(), archive.getOriginalFilename());
		var release = new com.deepdimen.matrix.discover.skill.ManagedSkillReleaseSnapshot(json).prompt(parsed,
				archive.getPackageSha256());
		return new PackagePreview(archive.getId(), release.getReleaseDigest(), release.getRuntimeKind(),
				release.getContentSnapshot(), release.getInputSchema(), release.getOutputSchema());
	}

	public record PackagePreview(Long packageId, String releaseDigest, String runtimeKind, String contentSnapshot,
			String inputSchema, String outputSchema) {
	}

	@PreAuthorize("@pms.hasPermission('knowledge_skill_view')")
	public List<AiSkillPackageEntity> packages(Long skillId, Long beforeId) {
		var query = Wrappers.<AiSkillPackageEntity>lambdaQuery()
			.eq(AiSkillPackageEntity::getTenantId, tenant())
			.eq(AiSkillPackageEntity::getSourceChannel, "MANAGED")
			.eq(skillId != null, AiSkillPackageEntity::getSkillId, skillId)
			.lt(beforeId != null, AiSkillPackageEntity::getId, beforeId)
			.orderByDesc(AiSkillPackageEntity::getId)
			.last("LIMIT 50");
		return packages.selectList(query);
	}

	@PreAuthorize("@pms.hasPermission('knowledge_skill_view')")
	public List<AiSkillReleaseEntity> releases(Long skillId, Long beforeId) {
		if (skillId == null || skillId <= 0)
			throw new IllegalArgumentException("Skill 标识无效");
		return releases.listOwned(tenant(), skillId, beforeId);
	}

	private String tenant() {
		var tenant = TenantContextHolder.getTenantId();
		var user = SecurityUtils.getUser();
		var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (tenant == null || tenant <= 0 || user == null || auth == null || !auth.isAuthenticated())
			throw new IllegalArgumentException("查询需要当前租户和认证身份");
		return tenant.toString();
	}

}
