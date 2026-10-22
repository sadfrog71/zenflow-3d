package com.deepdimen.matrix.discover.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.discover.entity.AiSkillReleaseEntity;
import com.deepdimen.matrix.discover.mapper.AiSkillPackageMapper;
import com.deepdimen.matrix.discover.mapper.AiSkillReleaseMapper;
import com.deepdimen.matrix.discover.skill.AgentSkillPackageParser;
import com.deepdimen.matrix.discover.skill.ManagedSkillReleaseSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Independent managed publication. Never changes legacy directory or agent projections.
 */
@Service
@RequiredArgsConstructor
public class ManagedSkillReleaseService {

	private final AiSkillPackageMapper packages;

	private final AiSkillReleaseMapper releases;

	private final AgentSkillPackageParser parser;

	private final ObjectMapper json;

	@Transactional(rollbackFor = Exception.class)
	@PreAuthorize("@pms.hasPermission('knowledge_skill_approve')")
	public AiSkillReleaseEntity publish(Long packageId, String version) {
		return publishForSkill(null, packageId, version);
	}

	@Transactional(rollbackFor = Exception.class)
	@PreAuthorize("@pms.hasPermission('knowledge_skill_approve')")
	public AiSkillReleaseEntity publishForSkill(Long skillId, Long packageId, String version) {
		Long tenant = TenantContextHolder.getTenantId();
		var actor = SecurityUtils.getUser();
		if (tenant == null || tenant == 0L || actor == null) {
			throw new IllegalArgumentException("发布需要当前租户和已认证操作者");
		}
		if (packageId == null || version == null || !version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
			throw new IllegalArgumentException("发布包或版本格式无效");
		}
		var archive = packages.lockManagedPackage(packageId, tenant.toString());
		if (archive == null || !"MANAGED".equals(archive.getSourceChannel())
				|| !tenant.toString().equals(archive.getTenantId()) || !"APPROVED".equals(archive.getPackageStatus())
				|| archive.getArchiveData() == null) {
			throw new IllegalArgumentException("只能发布当前租户已审核的托管包");
		}
		if (skillId != null && !skillId.equals(archive.getSkillId())) {
			throw new IllegalArgumentException("审核包不属于指定 Skill");
		}
		var parsed = parser.parse(archive.getArchiveData(), archive.getOriginalFilename());
		var release = new ManagedSkillReleaseSnapshot(json).prompt(parsed, archive.getPackageSha256());
		try {
			var evidence = json.readTree(archive.getValidationReport());
			if (evidence == null || !release.getReleaseDigest().equals(evidence.path("releaseDigest").asText())
					|| evidence.path("reviewedBy").asText().isBlank()) {
				throw new IllegalArgumentException("发布内容与审核证据不一致，请重新审核");
			}
		}
		catch (java.io.IOException ex) {
			throw new IllegalArgumentException("审核证据无效", ex);
		}
		release.setId(IdWorker.getId());
		release.setTenantId(tenant.toString());
		release.setSkillId(archive.getSkillId());
		release.setPackageId(archive.getId());
		release.setVersion(version);
		release.setCreatedBy(actor.getUsername());
		release.setStatus("PUBLISHED");
		if (releases.insertRelease(release) != 1) {
			throw new IllegalStateException("发布版本保存失败");
		}
		return release;
	}

	@Transactional(rollbackFor = Exception.class)
	@PreAuthorize("@pms.hasPermission('knowledge_skill_disable')")
	public void revoke(Long releaseId, String expectedDigest, String reason) {
		Long tenant = TenantContextHolder.getTenantId();
		var actor = SecurityUtils.getUser();
		if (tenant == null || tenant <= 0 || actor == null || releaseId == null || releaseId <= 0
				|| expectedDigest == null || !expectedDigest.matches("[a-f0-9]{64}") || reason == null
				|| reason.isBlank() || reason.length() > 1000) {
			throw new IllegalArgumentException("撤销需要当前身份、发布版本、内容摘要及原因");
		}
		if (releases.revoke(tenant.toString(), releaseId, expectedDigest, actor.getUsername(), reason) != 1) {
			throw new IllegalStateException("发布版本不存在、已撤销或摘要已变化");
		}
	}

}
