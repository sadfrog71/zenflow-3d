package com.deepdimen.matrix.discover.assistant;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.capability.AssistantAnalysisSkillCatalog;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.service.AiAgentSkillService;
import com.deepdimen.matrix.discover.vo.AiAgentSkillVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Immutable ASSISTANT_ANALYSIS Skill projection for native semantic orchestration. */
@Component
public class MatrixAssistantAnalysisSkillCatalog implements AssistantAnalysisSkillCatalog {

	private final AiAgentSkillService agentSkillService;

	public MatrixAssistantAnalysisSkillCatalog(AiAgentSkillService agentSkillService) {
		this.agentSkillService = agentSkillService;
	}

	@Override
	public AnalysisSkillSnapshot resolveDefault(IdentityEnvelope identity) {
		return resolve(identity, null);
	}

	@Override
	public AnalysisSkillSnapshot resolve(IdentityEnvelope identity, String skillId) {
		if (identity == null || !StringUtils.hasText(identity.tenantId()) || !StringUtils.hasText(identity.userId())) {
			throw new IllegalArgumentException("ASSISTANT_IDENTITY_REQUIRED");
		}
		Long previousTenant = TenantContextHolder.getTenantId();
		try {
			TenantContextHolder.setTenantId(Long.valueOf(identity.tenantId()));
			return resolveWithinTenant(skillId);
		}
		catch (NumberFormatException ex) {
			throw new IllegalArgumentException("ASSISTANT_IDENTITY_REQUIRED", ex);
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

	private AnalysisSkillSnapshot resolveWithinTenant(String skillId) {
		AiAgentSkillVO skill = agentSkillService.getPublishedByAgentId(AiAgentService.DEFAULT_AGENT_ID)
			.stream()
			.filter(this::eligible)
			.filter(candidate -> !StringUtils.hasText(skillId)
					|| skillId.equals(String.valueOf(candidate.getSkillId())))
			.sorted(Comparator.comparing(AiAgentSkillVO::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(AiAgentSkillVO::getSkillId))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException(StringUtils.hasText(skillId)
					? "ASSISTANT_ANALYSIS_SKILL_NOT_FOUND" : "ASSISTANT_ANALYSIS_SKILL_NOT_PUBLISHED"));
		if (!skill.getInstructionDigest().equals(sha256(skill.getPromptSnapshot()))) {
			throw new IllegalArgumentException("ASSISTANT_ANALYSIS_SKILL_SNAPSHOT_INVALID");
		}
		return new AnalysisSkillSnapshot(String.valueOf(skill.getSkillId()), skill.getSkillReleaseId(),
				skill.getReleaseVersion(), skill.getSkillName(), skill.getPromptSnapshot(), skill.getInstructionDigest(),
				skill.getPackageSha256(), skill.getAnalysisPolicySnapshot(), skill.getAnalysisPolicyDigest(),
				skill.getAnalysisResultSchema(), skill.getAnalysisResultSchemaId(),
				Set.copyOf(skill.getRequiredProviders() == null ? List.of() : skill.getRequiredProviders()));
	}

	private boolean eligible(AiAgentSkillVO skill) {
		return skill != null && "assistant_analysis".equals(skill.getImplType())
				&& "ASSISTANT_ANALYSIS".equals(skill.getRuntimeKind()) && skill.getSkillReleaseId() != null
				&& StringUtils.hasText(skill.getReleaseVersion()) && StringUtils.hasText(skill.getPromptSnapshot())
				&& StringUtils.hasText(skill.getInstructionDigest()) && StringUtils.hasText(skill.getPackageSha256())
				&& skill.getAnalysisPolicySnapshot() != null && StringUtils.hasText(skill.getAnalysisPolicyDigest())
				&& skill.getAnalysisResultSchema() != null && StringUtils.hasText(skill.getAnalysisResultSchemaId());
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
