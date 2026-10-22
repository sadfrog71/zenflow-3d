package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.discover.entity.AiSkillReleaseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

/**
 * Constructs content from the reviewed archive, never from a publish request's overrides.
 */
public final class ManagedSkillReleaseSnapshot {

	private final ObjectMapper json;

	public ManagedSkillReleaseSnapshot(ObjectMapper json) {
		this.json = json;
	}

	public AiSkillReleaseEntity prompt(AgentSkillPackageParser.ParsedPackage parsed, String expectedArchiveDigest) {
		if (!parsed.sha256().equals(expectedArchiveDigest)) {
			throw new IllegalArgumentException("审核包摘要不匹配");
		}
		if (!"PROMPT_INJECTION".equals(parsed.manifest().path("runtimeKind").asText())) {
			throw new IllegalArgumentException("该运行类型尚未接入托管发布");
		}
		JsonNode declaration = parsed.manifest().path("declared").path("managedRelease");
		if (!declaration.isObject()) {
			throw new IllegalArgumentException("审核包缺少 managedRelease 发布契约");
		}
		JsonNode input = declaration.path("inputSchema");
		JsonNode output = declaration.path("outputSchema");
		requireObjectSchema(input);
		requireObjectSchema(output);
		new ManagedSkillSchemaValidator().validateSchema(input);
		new ManagedSkillSchemaValidator().validateSchema(output);
		JsonNode executor = declaration.path("executorRef");
		if (!executor.isObject() || !"PROMPT".equals(executor.path("executorKey").asText())) {
			throw new IllegalArgumentException("提示词包只能声明 PROMPT 执行器");
		}
		boolean dify = "DIFY".equals(executor.path("provider").asText());
		boolean knowledge = "KNOWLEDGE".equals(executor.path("provider").asText());
		var executorFields = executor.fieldNames();
		while (executorFields.hasNext()) {
			if (!((dify || knowledge) ? java.util.Set.of("executorKey", "provider", "resourceId", "versionDigest")
					: java.util.Set.of("executorKey", "resourceId"))
				.contains(executorFields.next()))
				throw new IllegalArgumentException("执行器仅允许引用固定模型标识，不能声明地址、凭证或覆盖提供方");
		}
		if (executor.has("resourceId") && (!executor.get("resourceId").isTextual()
				|| !executor.get("resourceId").textValue().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")))
			throw new IllegalArgumentException("固定模型必须使用平台标识，不能使用地址或空值");
		JsonNode parameters = declaration.path("allowedParameters");
		if (!parameters.isArray()) {
			throw new IllegalArgumentException("allowedParameters 必须是数组");
		}
		java.util.Set<String> seen = new java.util.HashSet<>();
		for (JsonNode parameter : parameters) {
			String name = parameter.asText();
			if (!parameter.isTextual() || !name.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")
					|| !input.path("properties").has(name) || !seen.add(name)
					|| java.util.Set
						.of("systemPrompt", "instructions", "executorRef", "tenantId", "userId", "outputSchema",
								"model", "apiKey", "permissions")
						.contains(name)) {
				throw new IllegalArgumentException("发布包包含未声明或保留参数");
			}
		}
		JsonNode resources = declaration.has("resourceRefs") ? declaration.path("resourceRefs")
				: json.createArrayNode();
		if (dify) {
			DifyMarketRelease.validateDeclaration(executor, resources);
			if (!parameters.isEmpty())
				throw new IllegalArgumentException("Dify 聊天绑定不接受业务输入之外的覆盖参数");
		}
		else if (knowledge) {
			if (executor.size() != 4 || !executor.path("versionDigest").isTextual() || !resources.isArray()
					|| !parameters.isEmpty())
				throw new IllegalArgumentException("知识库发布必须固定模型与资源配置，不能覆盖参数");
			var refs = new java.util.ArrayList<com.deepdimen.matrix.skill.api.SkillContract.ResourceRef>();
			for (JsonNode resource : resources) {
				if (!resource.isObject() || resource.size() != 3 || !resource.path("resourceId").isTextual()
						|| !resource.path("version").isTextual() || !resource.path("digest").isTextual())
					throw new IllegalArgumentException("知识库资源引用格式无效");
				refs.add(new com.deepdimen.matrix.skill.api.SkillContract.ResourceRef(
						resource.path("resourceId").asText(), resource.path("version").asText(),
						resource.path("digest").asText()));
			}
			com.deepdimen.matrix.skill.api.KnowledgeSkillRelease
				.from(new com.deepdimen.matrix.skill.api.SkillContract.ExecutorRef("PROMPT", "KNOWLEDGE",
						executor.path("resourceId").asText(), executor.path("versionDigest").asText()), refs);
		}
		else if (!resources.isArray() || !resources.isEmpty()) {
			throw new IllegalArgumentException("该执行器尚未接入外部资源版本冻结");
		}
		String instructions = parsed.manifest().has("managedInstructionSnapshot")
				? parsed.manifest().path("managedInstructionSnapshot").asText() : parsed.body();
		if (!instructions.trim().equals(parsed.body()))
			throw new IllegalArgumentException("托管指令与审核包正文不一致");
		ObjectNode material = json.createObjectNode();
		material.put("runtimeKind", "PROMPT");
		material.put("instructionSnapshot", instructions);
		material.put("packageSha256", parsed.sha256());
		material.set("inputSchema", input);
		material.set("outputSchema", output);
		material.set("executorRef", executor);
		material.set("allowedParameters", parameters);
		material.set("resourceRefs", resources);
		// Limits are owned by the platform; a package cannot raise runtime limits.
		material.putObject("limits");
		AiSkillReleaseEntity release = new AiSkillReleaseEntity();
		release.setRuntimeKind("PROMPT");
		release.setContentSnapshot(instructions);
		release.setPackageSha256(parsed.sha256());
		release.setInputSchema(canonical(input));
		release.setOutputSchema(canonical(output));
		release.setExecutorRef(canonical(executor));
		release.setAllowedParameters(canonical(parameters));
		release.setResourceRefs(canonical(resources));
		release.setLimitsJson("{}");
		release.setReleaseDigest(sha256(canonical(material)));
		return release;
	}

	public void verify(AiSkillReleaseEntity release) {
		try {
			ObjectNode material = json.createObjectNode();
			material.put("runtimeKind", release.getRuntimeKind());
			material.put("instructionSnapshot", release.getContentSnapshot());
			material.put("packageSha256", release.getPackageSha256());
			material.set("inputSchema", json.readTree(release.getInputSchema()));
			material.set("outputSchema", json.readTree(release.getOutputSchema()));
			material.set("executorRef", json.readTree(release.getExecutorRef()));
			material.set("allowedParameters", json.readTree(release.getAllowedParameters()));
			material.set("resourceRefs", json.readTree(release.getResourceRefs()));
			material.set("limits", json.readTree(release.getLimitsJson()));
			if (!sha256(canonical(material)).equals(release.getReleaseDigest())) {
				throw new IllegalArgumentException("RELEASE_DIGEST_MISMATCH");
			}
		}
		catch (java.io.IOException ex) {
			throw new IllegalArgumentException("发布快照无效", ex);
		}
	}

	private void requireObjectSchema(JsonNode schema) {
		if (!schema.isObject() || !"object".equals(schema.path("type").asText())
				|| !schema.path("properties").isObject()) {
			throw new IllegalArgumentException("输入输出契约必须声明 object 和 properties");
		}
	}

	private String canonical(JsonNode value) {
		try {
			return json.writeValueAsString(sorted(value));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("发布内容序列化失败", ex);
		}
	}

	private JsonNode sorted(JsonNode node) {
		if (node.isObject()) {
			TreeMap<String, JsonNode> fields = new TreeMap<>();
			node.fields().forEachRemaining(field -> fields.put(field.getKey(), sorted(field.getValue())));
			ObjectNode result = json.createObjectNode();
			fields.forEach(result::set);
			return result;
		}
		if (node.isArray()) {
			var result = json.createArrayNode();
			node.forEach(item -> result.add(sorted(item)));
			return result;
		}
		return node;
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
