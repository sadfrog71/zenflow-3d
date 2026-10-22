package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.discover.entity.AiSkillEntity;
import com.deepdimen.matrix.discover.entity.AiSkillPackageEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * Immutable runtime material frozen from an approved Skill package.
 *
 * <p>
 * The snapshot deliberately does not read {@code ai_skill.config}; later edits to catalog
 * metadata therefore cannot change an already published release.
 * </p>
 */
public record PromptSkillReleaseSnapshot(String runtimeKind, String promptSnapshot, String instructionDigest,
		String packageSha256, String requiredProvidersJson) {

	public static final String LEGACY_RUNTIME_KIND = "LEGACY_UNFROZEN";

	public static PromptSkillReleaseSnapshot from(AiSkillPackageEntity skillPackage, ObjectMapper objectMapper) {
		if (skillPackage == null || objectMapper == null) {
			throw new IllegalArgumentException("Skill 发布快照缺少包或 JSON 解析器");
		}
		if (!StringUtils.hasText(skillPackage.getPackageSha256())) {
			throw new IllegalArgumentException("Skill 包缺少 SHA-256，无法发布");
		}
		if (!StringUtils.hasText(skillPackage.getManifestJson())) {
			throw new IllegalArgumentException("Skill 包缺少可发布的元数据");
		}
		try {
			JsonNode manifest = objectMapper.readTree(skillPackage.getManifestJson());
			if (manifest == null || !manifest.isObject()) {
				throw new IllegalArgumentException("Skill 包元数据不是有效对象");
			}
			String runtimeKind = manifest.path("runtimeKind").asText("").trim();
			if (!"PROMPT_INJECTION".equals(runtimeKind)) {
				throw new IllegalArgumentException("当前运行时只支持 PROMPT_INJECTION Skill");
			}
			String prompt = manifest.path("config").path("promptTemplate").asText(null);
			JsonNode requiredProviders = manifest.path("declared").path("requiredProviders");
			if (requiredProviders.isMissingNode() || requiredProviders.isNull()) {
				requiredProviders = manifest.path("config").path("requiredProviders");
			}
			return snapshot(prompt, skillPackage.getPackageSha256(), requiredProviders, objectMapper,
					"Skill 包缺少可执行的 promptTemplate");
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("Skill 包元数据损坏，无法冻结发布快照", ex);
		}
	}

	/**
	 * Freeze a trusted system-owned prompt that predates ZIP package support.
	 */
	public static PromptSkillReleaseSnapshot fromBuiltIn(AiSkillEntity skill, ObjectMapper objectMapper) {
		if (skill == null || objectMapper == null) {
			throw new IllegalArgumentException("系统内置 Skill 发布快照缺少目录记录或 JSON 解析器");
		}
		if (!"0".equals(skill.getTenantId()) || !"builtin".equals(skill.getSkillType())
				|| !"prompt_injection".equals(skill.getImplType())) {
			throw new IllegalArgumentException("只有系统内置 Prompt Skill 可以从目录配置冻结快照");
		}
		if (!StringUtils.hasText(skill.getConfig())) {
			throw new IllegalArgumentException("系统内置 Prompt Skill 缺少配置");
		}
		try {
			JsonNode config = objectMapper.readTree(skill.getConfig());
			if (config == null || !config.isObject()) {
				throw new IllegalArgumentException("系统内置 Prompt Skill 配置不是有效对象");
			}
			String prompt = config.path("promptTemplate").asText(null);
			String packageSha256 = sha256("builtin:" + skill.getId() + ":" + skill.getConfig());
			return snapshot(prompt, packageSha256, config.path("requiredProviders"), objectMapper,
					"系统内置 Prompt Skill 缺少可执行的 promptTemplate");
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("系统内置 Prompt Skill 配置损坏，无法冻结发布快照", ex);
		}
	}

	private static PromptSkillReleaseSnapshot snapshot(String prompt, String packageSha256,
			JsonNode requiredProviders, ObjectMapper objectMapper, String missingPromptMessage)
			throws JsonProcessingException {
		if (!StringUtils.hasText(prompt)) {
			throw new IllegalArgumentException(missingPromptMessage);
		}
		if (requiredProviders == null || requiredProviders.isMissingNode() || requiredProviders.isNull()) {
			requiredProviders = objectMapper.createArrayNode();
		}
		if (!requiredProviders.isArray()) {
			throw new IllegalArgumentException("requiredProviders 必须是数组");
		}
		Set<ProviderId> normalizedProviders = new LinkedHashSet<>();
		for (JsonNode providerNode : requiredProviders) {
			if (!providerNode.isTextual() || !StringUtils.hasText(providerNode.asText())) {
				throw new IllegalArgumentException("requiredProviders 只能包含 Provider 名称");
			}
			try {
				normalizedProviders.add(ProviderId.of(providerNode.asText().trim()));
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("不支持的 requiredProvider: " + providerNode.asText(), ex);
			}
		}
		return new PromptSkillReleaseSnapshot("PROMPT_INJECTION", prompt, sha256(prompt), packageSha256,
				objectMapper.writeValueAsString(normalizedProviders.stream().map(ProviderId::value).toList()));
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("JVM 缺少 SHA-256", ex);
		}
	}

}
