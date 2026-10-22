package com.deepdimen.matrix.discover.skill;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parses the open Agent Skills directory format from a ZIP upload.
 *
 * <p>
 * The parser deliberately treats scripts, hooks and MCP configuration as package content
 * only. Nothing from the archive is executed during import or approval.
 * </p>
 */
@Component
public final class AgentSkillPackageParser {

	public static final long MAX_ARCHIVE_BYTES = 10L * 1024 * 1024;

	private static final long MAX_UNCOMPRESSED_BYTES = 20L * 1024 * 1024;

	private static final int MAX_ENTRIES = 256;

	private static final int MAX_ENTRY_BYTES = 5 * 1024 * 1024;

	private static final Pattern SKILL_NAME = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

	private static final Set<String> BLOCKED_SUFFIXES = Set.of(".env", ".pem", ".key", ".p12", ".pfx", ".jks");

	private final ObjectMapper objectMapper;

	public AgentSkillPackageParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public ParsedPackage parse(byte[] archive, String originalFilename) {
		if (archive == null || archive.length == 0) {
			throw new IllegalArgumentException("Skill 包不能为空");
		}
		if (archive.length > MAX_ARCHIVE_BYTES) {
			throw new IllegalArgumentException("Skill 包不能超过 10MB");
		}
		Map<String, byte[]> files = readZip(archive);
		String skillPath = locate(files, "SKILL.md");
		if (skillPath == null) {
			throw new IllegalArgumentException("Skill 包必须包含 SKILL.md");
		}
		String source = new String(files.get(skillPath), StandardCharsets.UTF_8);
		Frontmatter frontmatter = parseFrontmatter(source);
		String name = frontmatter.values().get("name");
		String description = frontmatter.values().get("description");
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("SKILL.md 缺少 name");
		}
		name = name.trim();
		if (name.length() > 64 || !SKILL_NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("Skill name 必须是 1-64 位小写字母、数字和连字符");
		}
		if (description == null || description.isBlank()) {
			throw new IllegalArgumentException("SKILL.md 缺少 description");
		}
		description = description.trim();
		if (description.length() > 1024) {
			throw new IllegalArgumentException("Skill description 不能超过 1024 个字符");
		}
		String body = frontmatter.body().trim();
		if (body.isBlank()) {
			throw new IllegalArgumentException("SKILL.md 指令正文不能为空");
		}

		ObjectNode packageManifest = objectMapper.createObjectNode();
		packageManifest.put("schemaVersion", "matrix.skill/v1");
		packageManifest.put("entrypoint", skillPath);
		packageManifest.put("runtimeKind", "PROMPT_INJECTION");
		packageManifest.put("executionStrategy", "PRIMARY_APP_INSTRUCTION");
		packageManifest.put("sideEffectClass", "READ_ONLY");
		packageManifest.put("requiresApproval", false);
		packageManifest.put("sourceType", "AGENT_SKILLS_ZIP");
		JsonNode declaredManifest = firstJson(files, "skill-package.json");
		AnalysisContract analysisContract = null;
		if (declaredManifest != null) {
			packageManifest.set("declared", declaredManifest);
			// Managed migration preserves the reviewed instruction bytes after
			// frontmatter
			// (line endings normalized by the existing parser). Legacy body/config stay
			// trimmed.
			if (declaredManifest.path("managedRelease").isObject()) {
				packageManifest.put("managedInstructionSnapshot", frontmatter.body());
			}
			String schemaVersion = declaredManifest.path("schemaVersion").asText("").trim();
			String runtimeKind = declaredManifest.path("runtimeKind").asText("").trim();
			if ("matrix-assistant-skill/v2".equals(schemaVersion)) {
				analysisContract = validateAnalysisContract(files, skillPath, declaredManifest);
				packageManifest.put("schemaVersion", schemaVersion);
				packageManifest.put("runtimeKind", "ASSISTANT_ANALYSIS");
				packageManifest.put("executionStrategy", "ASSISTANT_ANALYSIS");
				packageManifest.put("requiresApproval", true);
				packageManifest.put("analysisPolicyDigest", analysisContract.policyDigest());
				packageManifest.put("analysisResultSchemaId", analysisContract.outputSchemaId());
			}
			else if (!runtimeKind.isBlank() && !Set.of("PROMPT_INJECTION", "PROMPT").contains(runtimeKind)) {
				throw new IllegalArgumentException("当前版本仅支持 PROMPT_INJECTION Skill，暂不支持 " + runtimeKind);
			}
		}

		List<String> warnings = new ArrayList<>();
		boolean hasScripts = files.keySet()
			.stream()
			.anyMatch(path -> path.startsWith("scripts/") || path.contains("/scripts/"));
		if (hasScripts) {
			warnings.add("包内包含 scripts/，平台只保存不执行脚本");
		}
		if (files.keySet()
			.stream()
			.anyMatch(path -> path.contains("/.mcp.json") || path.endsWith("/.mcp.json") || path.contains("/hooks/")
					|| path.startsWith("hooks/"))) {
			throw new IllegalArgumentException("Skill 包包含 hooks 或 MCP 配置，当前版本禁止导入高权限运行扩展");
		}
		String root = rootOf(skillPath);
		if (root != null && !root.equals(name)) {
			warnings.add("Skill 目录名与 frontmatter.name 不一致，已以 frontmatter.name 为准");
		}
		ObjectNode config = objectMapper.createObjectNode();
		config.put("injectField", "inputs.extended_system_prompt");
		config.put("injectMode", "append");
		config.put("promptTemplate", body);
		config.put("entrypoint", skillPath);
		config.put("packageFormat", "agent-skills");
		if (analysisContract != null) {
			config.set("analysisPolicySnapshot", analysisContract.policySnapshot());
			config.put("analysisPolicyDigest", analysisContract.policyDigest());
			config.set("analysisResultSchema", analysisContract.outputSchema());
			config.put("analysisResultSchemaId", analysisContract.outputSchemaId());
			config.set("requiredProviders", objectMapper.valueToTree(analysisContract.requiredProviders()));
		}
		if (!warnings.isEmpty()) {
			packageManifest.putPOJO("warnings", warnings);
		}
		return new ParsedPackage(name, description, frontmatter.values(), body, packageManifest, config, warnings,
				sha256(archive), archive.length, originalFilename == null ? "skill.zip" : originalFilename,
				files.keySet().stream().sorted().toList());
	}

	private AnalysisContract validateAnalysisContract(Map<String, byte[]> files, String skillPath,
			JsonNode declaredManifest) {
		if (!"ASSISTANT_ANALYSIS".equals(declaredManifest.path("runtimeKind").asText())) {
			throw new IllegalArgumentException("matrix-assistant-skill/v2 只支持 ASSISTANT_ANALYSIS 声明式运行时");
		}
		for (String section : List.of("sourcePolicy", "capabilityRoutingPolicy", "runtimePolicy", "outputContract",
				"citationPolicy", "failurePolicy")) {
			if (!declaredManifest.path(section).isObject()) {
				throw new IllegalArgumentException("ASSISTANT_ANALYSIS Skill 缺少策略段: " + section);
			}
		}
		if (!"WORKFLOW_ONLY".equals(declaredManifest.path("runtimePolicy").path("difyUsage").asText())
				|| !"FORBIDDEN".equals(declaredManifest.path("runtimePolicy").path("difyKnowledgeRetrieval").asText())
				|| !"READ_ONLY".equals(declaredManifest.path("runtimePolicy").path("sideEffectClass").asText())) {
			throw new IllegalArgumentException("ASSISTANT_ANALYSIS Skill 必须约束为 Dify Workflow-only 且只读");
		}
		if (!declaredManifest.path("citationPolicy").path("businessFactsRequireCitation").asBoolean(false)
				|| !declaredManifest.path("citationPolicy").path("preserveCitationKey").asBoolean(false)) {
			throw new IllegalArgumentException("ASSISTANT_ANALYSIS Skill 必须保留业务事实 Citation Key");
		}
		if (declaredManifest.path("capabilityRoutingPolicy").path("copyAgentOperationGuideIntoSkill").asBoolean(true)) {
			throw new IllegalArgumentException("Skill 不得复制可变 Agent 操作说明");
		}
		rejectMutableAgentSnapshot(declaredManifest);
		List<String> requiredProviders = providerIds(declaredManifest.path("sourcePolicy").path("requiredProviders"));
		providerIds(declaredManifest.path("sourcePolicy").path("allowedProviders"));
		String schemaPath = declaredManifest.path("analysisResultSchema").asText("").trim();
		if (schemaPath.isBlank()) {
			throw new IllegalArgumentException("ASSISTANT_ANALYSIS Skill 缺少 analysisResultSchema 路径");
		}
		String archivePath = resolveRelative(skillPath, schemaPath);
		byte[] schemaBytes = files.get(archivePath);
		if (schemaBytes == null) {
			throw new IllegalArgumentException("ASSISTANT_ANALYSIS Skill 缺少输出 Schema: " + schemaPath);
		}
		JsonNode outputSchema;
		try {
			outputSchema = objectMapper.readTree(schemaBytes);
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("ASSISTANT_ANALYSIS 输出 Schema 不是合法 JSON", ex);
		}
		if (outputSchema == null || !outputSchema.isObject() || !"object".equals(outputSchema.path("type").asText())) {
			throw new IllegalArgumentException("ASSISTANT_ANALYSIS 输出 Schema 必须是 type=object");
		}
		ObjectNode policy = objectMapper.createObjectNode();
		for (String section : List.of("sourcePolicy", "capabilityRoutingPolicy", "runtimePolicy", "outputContract",
				"citationPolicy", "failurePolicy")) {
			policy.set(section, declaredManifest.path(section));
		}
		String policyDigest = sha256(canonical(policy).getBytes(StandardCharsets.UTF_8));
		String outputSchemaId = "sha256:" + sha256(canonical(outputSchema).getBytes(StandardCharsets.UTF_8));
		return new AnalysisContract(policy, outputSchema, policyDigest, outputSchemaId, requiredProviders);
	}

	private void rejectMutableAgentSnapshot(JsonNode node) {
		if (node == null) {
			return;
		}
		if (node.isObject()) {
			node.fields().forEachRemaining(field -> {
				if (Set.of("agentId", "workflowAgentId", "operationGuide", "operationGuideHtml")
					.contains(field.getKey()) && !field.getValue().isNull()) {
					throw new IllegalArgumentException("Skill 不得内嵌可变 Agent 标识或操作说明");
				}
				rejectMutableAgentSnapshot(field.getValue());
			});
		}
		else if (node.isArray()) {
			node.forEach(this::rejectMutableAgentSnapshot);
		}
	}

	private List<String> providerIds(JsonNode values) {
		if (values == null || !values.isArray()) {
			throw new IllegalArgumentException("ASSISTANT_ANALYSIS Provider 声明必须是数组");
		}
		List<String> result = new ArrayList<>();
		for (JsonNode value : values) {
			if (!value.isTextual()) {
				throw new IllegalArgumentException("ASSISTANT_ANALYSIS Provider 名称无效");
			}
			String providerId = value.asText().trim();
			try {
				com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId.of(providerId);
			}
			catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("不支持的 Provider: " + providerId, ex);
			}
			result.add(providerId);
		}
		return List.copyOf(new java.util.LinkedHashSet<>(result));
	}

	private String resolveRelative(String skillPath, String relative) {
		if (relative.startsWith("/") || relative.indexOf('\\') >= 0 || relative.contains("..")) {
			throw new IllegalArgumentException("analysisResultSchema 路径无效");
		}
		int slash = skillPath.lastIndexOf('/');
		return slash < 0 ? relative : skillPath.substring(0, slash + 1) + relative;
	}

	private String canonical(JsonNode node) {
		if (node.isObject()) {
			Map<String, String> fields = new TreeMap<>();
			node.fields().forEachRemaining(field -> fields.put(field.getKey(), canonical(field.getValue())));
			return fields.entrySet()
				.stream()
				.map(field -> quote(field.getKey()) + ":" + field.getValue())
				.collect(java.util.stream.Collectors.joining(",", "{", "}"));
		}
		if (node.isArray()) {
			List<String> values = new ArrayList<>();
			node.forEach(value -> values.add(canonical(value)));
			return String.join(",", values).transform(value -> "[" + value + "]");
		}
		return node.toString();
	}

	private String quote(String value) {
		return objectMapper.getNodeFactory().textNode(value).toString();
	}

	private Map<String, byte[]> readZip(byte[] archive) {
		Map<String, byte[]> files = new LinkedHashMap<>();
		long totalBytes = 0;
		try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			int count = 0;
			while ((entry = input.getNextEntry()) != null) {
				if (++count > MAX_ENTRIES) {
					throw new IllegalArgumentException("Skill 包文件数量不能超过 256 个");
				}
				String name = normalizeEntry(entry.getName());
				if (name == null || entry.isDirectory()) {
					continue;
				}
				String lower = name.toLowerCase(Locale.ROOT);
				for (String suffix : BLOCKED_SUFFIXES) {
					if (lower.endsWith(suffix)) {
						throw new IllegalArgumentException("Skill 包包含禁止上传的敏感文件: " + name);
					}
				}
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				byte[] chunk = new byte[8192];
				int read;
				int entryBytes = 0;
				while ((read = input.read(chunk)) != -1) {
					entryBytes += read;
					totalBytes += read;
					if (entryBytes > MAX_ENTRY_BYTES || totalBytes > MAX_UNCOMPRESSED_BYTES) {
						throw new IllegalArgumentException("Skill 包解压后超过大小限制");
					}
					buffer.write(chunk, 0, read);
				}
				if (files.containsKey(name)) {
					throw new IllegalArgumentException("Skill ZIP 包含重复文件路径: " + name);
				}
				files.put(name, buffer.toByteArray());
			}
		}
		catch (IOException ex) {
			throw new IllegalArgumentException("Skill ZIP 无法读取", ex);
		}
		return files;
	}

	private String normalizeEntry(String raw) {
		if (raw == null || raw.isBlank() || raw.startsWith("/") || raw.indexOf('\\') >= 0) {
			throw new IllegalArgumentException("Skill ZIP 包含非法文件路径");
		}
		String[] parts = raw.split("/");
		for (String part : parts) {
			if (part.isBlank() || ".".equals(part) || "..".equals(part)) {
				throw new IllegalArgumentException("Skill ZIP 包含路径穿越: " + raw);
			}
		}
		return String.join("/", parts);
	}

	private String locate(Map<String, byte[]> files, String filename) {
		List<String> matches = files.keySet()
			.stream()
			.filter(path -> path.equals(filename) || path.endsWith("/" + filename))
			.toList();
		if (matches.size() > 1) {
			throw new IllegalArgumentException("Skill ZIP 包含多个 " + filename);
		}
		if (matches.isEmpty()) {
			return null;
		}
		return matches.get(0);
	}

	private JsonNode firstJson(Map<String, byte[]> files, String filename) {
		String path = locate(files, filename);
		if (path == null) {
			return null;
		}
		try {
			return objectMapper.readTree(files.get(path));
		}
		catch (IOException ex) {
			throw new IllegalArgumentException(filename + " 不是合法 JSON", ex);
		}
	}

	private Frontmatter parseFrontmatter(String source) {
		String normalized = source.replace("\r\n", "\n");
		if (!normalized.startsWith("---\n")) {
			throw new IllegalArgumentException("SKILL.md 必须以 YAML frontmatter 开始");
		}
		int end = normalized.indexOf("\n---", 4);
		if (end < 0) {
			throw new IllegalArgumentException("SKILL.md 缺少 frontmatter 结束标记");
		}
		String header = normalized.substring(4, end);
		String body = normalized.substring(end + 4);
		if (body.startsWith("\n")) {
			body = body.substring(1);
		}
		Map<String, String> values = new LinkedHashMap<>();
		for (String line : header.split("\n")) {
			if (line.isBlank() || line.stripLeading().startsWith("#")) {
				continue;
			}
			int separator = line.indexOf(':');
			if (separator <= 0) {
				throw new IllegalArgumentException("SKILL.md frontmatter 存在无法解析的字段: " + line);
			}
			String key = line.substring(0, separator).trim();
			String value = line.substring(separator + 1).trim();
			if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
				value = value.substring(1, value.length() - 1);
			}
			values.put(key, value);
		}
		return new Frontmatter(values, body);
	}

	private String rootOf(String path) {
		int slash = path.indexOf('/');
		return slash < 0 ? null : path.substring(0, slash);
	}

	private String sha256(byte[] bytes) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder result = new StringBuilder(64);
			for (byte value : digest) {
				result.append(String.format("%02x", value));
			}
			return result.toString();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 不可用", ex);
		}
	}

	private record AnalysisContract(ObjectNode policySnapshot, JsonNode outputSchema, String policyDigest,
			String outputSchemaId, List<String> requiredProviders) {
	}

	public record ParsedPackage(String name, String description, Map<String, String> frontmatter, String body,
			ObjectNode manifest, ObjectNode config, List<String> warnings, String sha256, long size,
			String originalFilename, List<String> files) {
	}

	private record Frontmatter(Map<String, String> values, String body) {
	}

}
