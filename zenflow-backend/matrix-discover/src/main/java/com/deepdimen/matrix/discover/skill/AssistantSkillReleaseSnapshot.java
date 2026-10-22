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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

/** Immutable runtime material frozen from an approved Assistant Skill package. */
public record AssistantSkillReleaseSnapshot(String runtimeKind, String instructionSnapshot,
		String instructionDigest, String packageSha256, String requiredProvidersJson,
		String analysisPolicySnapshot, String analysisPolicyDigest, String analysisResultSchema,
		String analysisResultSchemaId) {

	public static AssistantSkillReleaseSnapshot from(AiSkillPackageEntity skillPackage, ObjectMapper objectMapper) {
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
			String runtimeKind = manifest.path("runtimeKind").asText("").trim();
			if ("PROMPT_INJECTION".equals(runtimeKind)) {
				PromptSkillReleaseSnapshot legacy = PromptSkillReleaseSnapshot.from(skillPackage, objectMapper);
				return new AssistantSkillReleaseSnapshot(legacy.runtimeKind(), legacy.promptSnapshot(),
						legacy.instructionDigest(), legacy.packageSha256(), legacy.requiredProvidersJson(), null, null,
						null, null);
			}
			if (!"ASSISTANT_ANALYSIS".equals(runtimeKind)) {
				throw new IllegalArgumentException("不支持的 Skill 运行时: " + runtimeKind);
			}
			JsonNode config = manifest.path("config");
			String instruction = config.path("promptTemplate").asText(null);
			JsonNode policy = config.path("analysisPolicySnapshot");
			JsonNode schema = config.path("analysisResultSchema");
			String declaredPolicyDigest = config.path("analysisPolicyDigest").asText("").trim();
			String declaredSchemaId = config.path("analysisResultSchemaId").asText("").trim();
			if (!StringUtils.hasText(instruction) || !policy.isObject() || !schema.isObject()) {
				throw new IllegalArgumentException("ASSISTANT_ANALYSIS Skill 缺少指令、策略或输出 Schema");
			}
			String policyDigest = sha256(canonical(policy, objectMapper));
			String schemaId = "sha256:" + sha256(canonical(schema, objectMapper));
			if (!policyDigest.equals(declaredPolicyDigest) || !schemaId.equals(declaredSchemaId)) {
				throw new IllegalArgumentException("ASSISTANT_ANALYSIS Skill 策略或输出 Schema 摘要不一致");
			}
			List<String> providers = providerIds(config.path("requiredProviders"));
			return new AssistantSkillReleaseSnapshot(runtimeKind, instruction, sha256(instruction),
					skillPackage.getPackageSha256(), objectMapper.writeValueAsString(providers), policy.toString(),
					policyDigest, schema.toString(), schemaId);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("Skill 包元数据损坏，无法冻结发布快照", ex);
		}
	}

	public static AssistantSkillReleaseSnapshot fromBuiltIn(AiSkillEntity skill, ObjectMapper objectMapper) {
		PromptSkillReleaseSnapshot legacy = PromptSkillReleaseSnapshot.fromBuiltIn(skill, objectMapper);
		return new AssistantSkillReleaseSnapshot(legacy.runtimeKind(), legacy.promptSnapshot(),
				legacy.instructionDigest(), legacy.packageSha256(), legacy.requiredProvidersJson(), null, null, null,
				null);
	}

	private static List<String> providerIds(JsonNode values) {
		if (!values.isArray()) {
			throw new IllegalArgumentException("requiredProviders 必须是数组");
		}
		LinkedHashSet<String> providers = new LinkedHashSet<>();
		for (JsonNode value : values) {
			if (!value.isTextual() || !StringUtils.hasText(value.asText())) {
				throw new IllegalArgumentException("requiredProviders 只能包含 Provider 名称");
			}
			providers.add(ProviderId.of(value.asText().trim()).value());
		}
		return List.copyOf(providers);
	}

	private static String canonical(JsonNode node, ObjectMapper objectMapper) {
		if (node.isObject()) {
			Map<String, String> fields = new TreeMap<>();
			node.fields().forEachRemaining(field -> fields.put(field.getKey(), canonical(field.getValue(), objectMapper)));
			return fields.entrySet()
				.stream()
				.map(field -> objectMapper.getNodeFactory().textNode(field.getKey()) + ":" + field.getValue())
				.collect(Collectors.joining(",", "{", "}"));
		}
		if (node.isArray()) {
			List<String> values = new ArrayList<>();
			node.forEach(value -> values.add(canonical(value, objectMapper)));
			return "[" + String.join(",", values) + "]";
		}
		return node.toString();
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
