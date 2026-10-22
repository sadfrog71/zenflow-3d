package com.deepdimen.matrix.discover.skill;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.discover.entity.AiSkillEntity;
import com.deepdimen.matrix.discover.entity.AiSkillPackageEntity;
import com.deepdimen.matrix.discover.mapper.AiSkillMapper;
import com.deepdimen.matrix.discover.mapper.AiSkillPackageMapper;
import com.deepdimen.matrix.work.api.service.TaskSkillResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reuses approved Skill packages; personal/default-agent preferences are not consulted.
 */
@Component
@RequiredArgsConstructor
public class MatrixTaskSkillResolver implements TaskSkillResolver {

	private final AiSkillMapper skillMapper;

	private final AiSkillPackageMapper packageMapper;

	private final ObjectMapper objectMapper;

	@Override
	public TaskSkill resolve(Long tenantId, Long packageId) {
		if (tenantId == null || packageId == null)
			throw new IllegalArgumentException("任务 Skill 尚未配置");
		String tenant = tenantId.toString();
		AiSkillPackageEntity pkg = packageMapper.selectOne(Wrappers.<AiSkillPackageEntity>lambdaQuery()
			.eq(AiSkillPackageEntity::getTenantId, tenant)
			.eq(AiSkillPackageEntity::getId, packageId));
		if (pkg == null || !tenant.equals(pkg.getTenantId()) || !"APPROVED".equals(pkg.getPackageStatus())) {
			throw new IllegalArgumentException("任务 Skill 包未审核或不可用");
		}
		AiSkillEntity skill = skillMapper.selectOne(Wrappers.<AiSkillEntity>lambdaQuery()
			.eq(AiSkillEntity::getTenantId, tenant)
			.eq(AiSkillEntity::getId, pkg.getSkillId())
			.eq(AiSkillEntity::getIsEnabled, 1));
		if (skill == null || !tenant.equals(skill.getTenantId()) || !Objects.equals(skill.getIsEnabled(), 1)) {
			throw new IllegalArgumentException("任务 Skill 已下架或不可用");
		}
		PromptSkillReleaseSnapshot snapshot = PromptSkillReleaseSnapshot.from(pkg, objectMapper);
		if (!"[]".equals(snapshot.requiredProvidersJson())) {
			throw new IllegalArgumentException("任务 Skill 只使用已授权任务输入，不支持额外上下文 Provider");
		}
		return new TaskSkill(pkg.getId(), pkg.getPackageVersion(), snapshot.promptSnapshot(),
				snapshot.instructionDigest(), snapshot.packageSha256());
	}

}
