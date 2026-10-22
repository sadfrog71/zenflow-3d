package com.deepdimen.matrix.discover.assistant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.capability.AssistantPromptSkillCatalog;
import com.deepdimen.matrix.assistant.capability.AssistantPromptSkillCatalog.PromptSkillDescriptor;
import com.deepdimen.matrix.assistant.capability.AssistantPromptSkillCatalog.PromptSkillSnapshot;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.service.AiAgentSkillService;
import com.deepdimen.matrix.discover.service.AiUserSkillPrefService;
import com.deepdimen.matrix.discover.vo.AiAgentSkillVO;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Published Prompt Skill catalog for the native unified assistant. */
@Component
public class MatrixAssistantSkillCatalogProvider implements AssistantPromptSkillCatalog {

	private static final String SECURITY_POLICY_VERSION = "prompt-skill/p0-v1";

	private final AiAgentSkillService agentSkillService;

	private final AiUserSkillPrefService userSkillPrefService;

	public MatrixAssistantSkillCatalogProvider(AiAgentSkillService agentSkillService,
			AiUserSkillPrefService userSkillPrefService) {
		this.agentSkillService = agentSkillService;
		this.userSkillPrefService = userSkillPrefService;
	}

	@Override
	public List<PromptSkillDescriptor> listPromptSkills(IdentityEnvelope identity) {
		requireIdentity(identity);
		Map<Long, Boolean> preferences = userSkillPrefService.getByUserAndAgent(AiAgentService.DEFAULT_AGENT_ID)
			.stream()
			.collect(Collectors.toMap(item -> Long.valueOf(item.get("skillId").toString()),
					item -> Integer.valueOf(item.get("isEnabled").toString()) == 1));
		List<AiAgentSkillVO> available = promptSkills();
		Long selectedSkillId = available.stream()
			.filter(skill -> preferences.getOrDefault(skill.getSkillId(),
					Integer.valueOf(1).equals(skill.getIsDefaultEnabled())))
			.map(AiAgentSkillVO::getSkillId)
			.findFirst()
			.orElse(null);
		return available.stream().map(skill -> descriptor(skill, selectedSkillId)).toList();
	}

	@Override
	public PromptSkillSnapshot resolvePromptSkill(IdentityEnvelope identity, String skillId) {
		if (!StringUtils.hasText(skillId)) {
			throw new IllegalArgumentException("PROMPT_SKILL_NOT_AVAILABLE");
		}
		requireIdentity(identity);
		AiAgentSkillVO skill = promptSkills().stream()
			.filter(item -> skillId.equals(String.valueOf(item.getSkillId())) || skillId.equals(item.getSkillCode()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("PROMPT_SKILL_NOT_AVAILABLE"));
		if (!skill.getInstructionDigest().equals(sha256(skill.getPromptSnapshot()))) {
			throw new IllegalArgumentException("PROMPT_SKILL_SNAPSHOT_INVALID");
		}
		return new PromptSkillSnapshot(String.valueOf(skill.getSkillId()), String.valueOf(skill.getSkillReleaseId()),
				skill.getReleaseVersion(), skill.getSkillName(), skill.getIcon(), skill.getPromptSnapshot(),
				skill.getInstructionDigest(), skill.getPackageSha256(), SECURITY_POLICY_VERSION,
				Set.copyOf(skill.getRequiredProviders() == null ? List.of() : skill.getRequiredProviders()));
	}

	@Override
	public void savePromptSkillPreference(IdentityEnvelope identity, List<String> enabledSkillCodes) {
		requireIdentity(identity);
		Set<String> selected = Set.copyOf(enabledSkillCodes == null ? List.of() : enabledSkillCodes);
		if (selected.size() > 1) {
			throw new IllegalArgumentException("PROMPT_SKILL_SINGLE_SELECTION_REQUIRED");
		}
		List<AiAgentSkillVO> available = promptSkills();
		Map<String, AiAgentSkillVO> byIdentity = available.stream()
			.collect(Collectors.toMap(item -> String.valueOf(item.getSkillId()), Function.identity()));
		available.forEach(item -> byIdentity.putIfAbsent(item.getSkillCode(), item));
		if (!byIdentity.keySet().containsAll(selected)) {
			throw new IllegalArgumentException("PROMPT_SKILL_NOT_AVAILABLE");
		}
		List<Map<String, Object>> preferences = available.stream()
			.map(skill -> Map.<String, Object>of("skillId", skill.getSkillId(), "isEnabled",
					selected.contains(String.valueOf(skill.getSkillId())) || selected.contains(skill.getSkillCode()) ? 1
							: 0))
			.toList();
		userSkillPrefService.saveUserPrefs(AiAgentService.DEFAULT_AGENT_ID, preferences);
	}

	private PromptSkillDescriptor descriptor(AiAgentSkillVO skill, Long selectedSkillId) {
		return new PromptSkillDescriptor(String.valueOf(skill.getSkillId()), String.valueOf(skill.getSkillReleaseId()),
				skill.getReleaseVersion(), skill.getSkillName(), skill.getDescription(), skill.getIcon(),
				skill.getSkillId().equals(selectedSkillId), skill.getInstructionDigest(),
				Set.copyOf(skill.getRequiredProviders() == null ? List.of() : skill.getRequiredProviders()));
	}

	private List<AiAgentSkillVO> promptSkills() {
		return agentSkillService.getPublishedByAgentId(AiAgentService.DEFAULT_AGENT_ID)
			.stream()
			.filter(this::isPromptSkill)
			.sorted(Comparator.comparing(AiAgentSkillVO::getSkillId))
			.toList();
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

	private boolean isPromptSkill(AiAgentSkillVO skill) {
		return skill != null && "prompt_injection".equals(skill.getImplType())
				&& "PRIMARY_APP_INSTRUCTION".equals(normalizedStrategy(skill))
				&& "PROMPT_INJECTION".equals(skill.getRuntimeKind()) && StringUtils.hasText(skill.getPromptSnapshot())
				&& skill.getSkillReleaseId() != null && StringUtils.hasText(skill.getReleaseVersion())
				&& StringUtils.hasText(skill.getPackageSha256()) && StringUtils.hasText(skill.getInstructionDigest());
	}

	private String normalizedStrategy(AiAgentSkillVO skill) {
		return skill.getExecutionStrategy() == null ? "PRIMARY_APP_INSTRUCTION" : skill.getExecutionStrategy();
	}

	private void requireIdentity(IdentityEnvelope identity) {
		if (identity == null || !StringUtils.hasText(identity.tenantId()) || !StringUtils.hasText(identity.userId())) {
			throw new IllegalArgumentException("ASSISTANT_IDENTITY_REQUIRED");
		}
	}

}
