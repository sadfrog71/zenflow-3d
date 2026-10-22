package com.deepdimen.matrix.assistant.run;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisArtifact;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisClaim;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisResult;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisSection;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClaimType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Parses model JSON, rejects unsupported claims, then renders only validated claims. */
@Component
public class AssistantAnalysisResultValidator {

	private final ObjectMapper objectMapper;

	private final AssistantJsonSchemaValidator schemaValidator;

	public AssistantAnalysisResultValidator(ObjectMapper objectMapper, AssistantJsonSchemaValidator schemaValidator) {
		this.objectMapper = objectMapper;
		this.schemaValidator = schemaValidator;
	}

	public AnalysisResult parseAndValidate(String runId, String rawJson, Object frozenSchema, String expectedSchemaId,
			Set<String> availableCitationKeys, List<AnalysisArtifact> artifacts) {
		if (frozenSchema == null || !StringUtils.hasText(expectedSchemaId) || !StringUtils.hasText(rawJson)) {
			throw invalid("SCHEMA_OR_OUTPUT_MISSING");
		}
		try {
			String normalized = stripFence(rawJson.trim());
			JsonNode schema = objectMapper.valueToTree(frozenSchema);
			if (!schemaId(schema).equals(expectedSchemaId)) {
				throw invalid("SCHEMA_DIGEST_MISMATCH");
			}
			JsonNode output = objectMapper.reader()
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.readTree(normalized);
			try {
				schemaValidator.validate(schema, output);
			}
			catch (AssistantJsonSchemaValidator.SchemaViolationException ex) {
				throw invalid("OUTPUT_SCHEMA_MISMATCH", ex);
			}
			AnalysisResult parsed = objectMapper.treeToValue(output, AnalysisResult.class);
			return validate(runId, parsed, availableCitationKeys, artifacts);
		}
		catch (JsonProcessingException ex) {
			throw invalid("OUTPUT_NOT_VALID_JSON", ex);
		}
	}

	public String schemaId(Object frozenSchema) {
		if (frozenSchema == null) {
			throw invalid("SCHEMA_OR_OUTPUT_MISSING");
		}
		return "sha256:" + sha256(canonical(objectMapper.valueToTree(frozenSchema)));
	}

	private String canonical(JsonNode node) {
		if (node.isObject()) {
			Map<String, String> fields = new java.util.TreeMap<>();
			node.fields().forEachRemaining(field -> fields.put(field.getKey(), canonical(field.getValue())));
			return fields.entrySet()
				.stream()
				.map(field -> objectMapper.getNodeFactory().textNode(field.getKey()) + ":" + field.getValue())
				.collect(java.util.stream.Collectors.joining(",", "{", "}"));
		}
		if (node.isArray()) {
			List<String> values = new ArrayList<>();
			node.forEach(value -> values.add(canonical(value)));
			return "[" + String.join(",", values) + "]";
		}
		return node.toString();
	}

	public AnalysisResult validate(String runId, AnalysisResult proposed, Set<String> availableCitationKeys,
			List<AnalysisArtifact> artifacts) {
		if (proposed == null || !runId.equals(proposed.runId())
				|| !AssistantExecutionContract.ANALYSIS_VERSION.equals(proposed.contractVersion())
				|| !StringUtils.hasText(proposed.summary())) {
			throw invalid("RUN_OR_CONTRACT_MISMATCH");
		}
		validateEvidence(runId, proposed.claims(), proposed.sections(), availableCitationKeys, artifacts);
		if (!proposed.claims().isEmpty()) {
			AnalysisSection summarySection = proposed.sections()
				.stream()
				.filter(section -> "analysis-summary".equals(section.sectionId()))
				.findFirst()
				.orElseThrow(() -> invalid("SUMMARY_EVIDENCE_REQUIRED"));
			if (!"分析总结".equals(summarySection.title()) || summarySection.claimIds().size() != 1 || !proposed.summary()
				.equals(claimsById(proposed.claims()).get(summarySection.claimIds().get(0)).text())) {
				throw invalid("SUMMARY_EVIDENCE_REQUIRED");
			}
		}
		if (proposed.claims().size() > 100 || proposed.sections().size() > 12 || proposed.warnings().size() > 20) {
			throw invalid("ANALYSIS_RESULT_LIMIT_EXCEEDED");
		}
		AnalysisResult undigested = new AnalysisResult(AssistantExecutionContract.ANALYSIS_VERSION, runId,
				proposed.summary(), proposed.sections(), proposed.claims(), proposed.warnings(),
				proposed.sourceCoverage(), null);
		String digest = sha256(write(undigested));
		return new AnalysisResult(AssistantExecutionContract.ANALYSIS_VERSION, runId, proposed.summary(),
				proposed.sections(), proposed.claims(), proposed.warnings(), proposed.sourceCoverage(), digest);
	}

	private void validateEvidence(String runId, List<AnalysisClaim> claims, List<AnalysisSection> sections,
			Set<String> availableCitationKeys, List<AnalysisArtifact> artifacts) {
		Set<String> citations = availableCitationKeys == null ? Set.of() : Set.copyOf(availableCitationKeys);
		Map<String, AnalysisArtifact> artifactsById = (artifacts == null ? List.<AnalysisArtifact>of() : artifacts)
			.stream()
			.collect(java.util.stream.Collectors.toMap(AnalysisArtifact::artifactId, item -> item));
		Set<String> artifactIds = artifactsById.keySet();
		Set<String> claimIds = new HashSet<>();
		for (AnalysisClaim claim : claims) {
			if (claim == null || !StringUtils.hasText(claim.claimId()) || !StringUtils.hasText(claim.text())
					|| claim.type() == null || !claimIds.add(claim.claimId())
					|| !citations.containsAll(claim.citationKeys()) || !artifactIds.containsAll(claim.artifactIds())
					|| claim.artifactIds().stream().anyMatch(id -> !runId.equals(artifactsById.get(id).runId()))) {
				throw invalid("CLAIM_REFERENCE_INVALID");
			}
			boolean hasWorkflowEvidence = claim.artifactIds()
				.stream()
				.map(artifactsById::get)
				.anyMatch(artifact -> artifact != null
						&& artifact.kind() == AssistantExecutionContract.ArtifactKind.WORKFLOW_RESULT);
			if (claim.type() == ClaimType.FACT && claim.citationKeys().isEmpty() && !hasWorkflowEvidence) {
				throw invalid("FACT_EVIDENCE_REQUIRED");
			}
			if (claim.type() == ClaimType.INFERENCE && claim.citationKeys().isEmpty() && !hasWorkflowEvidence) {
				throw invalid("INFERENCE_EVIDENCE_REQUIRED");
			}
		}
		Set<String> sectionIds = new HashSet<>();
		for (AnalysisSection section : sections) {
			if (section == null || !StringUtils.hasText(section.sectionId()) || !StringUtils.hasText(section.title())
					|| !sectionIds.add(section.sectionId()) || !claimIds.containsAll(section.claimIds())) {
				throw invalid("SECTION_REFERENCE_INVALID");
			}
		}

	}

	/**
	 * Validate frozen per-item constraints and original evidence rules before a section
	 * can be visible.
	 */
	public void validateSection(String runId, AnalysisSection section, List<AnalysisClaim> claims, Object frozenSchema,
			String expectedSchemaId, Set<String> citationKeys, List<AnalysisArtifact> artifacts) {
		if (!schemaId(frozenSchema).equals(expectedSchemaId))
			throw invalid("SCHEMA_DIGEST_MISMATCH");
		JsonNode schema = objectMapper.valueToTree(frozenSchema);
		JsonNode sectionSchema = schema.path("properties").path("sections").path("items");
		JsonNode claimSchema = schema.path("properties").path("claims").path("items");
		if (!sectionSchema.isObject() || !claimSchema.isObject())
			throw invalid("STREAM_SCHEMA_UNSUPPORTED");
		if (section == null || claims == null || claims.isEmpty() || claims.size() > 100
				|| section.claimIds().isEmpty())
			throw invalid("SECTION_REFERENCE_INVALID");
		try {
			schemaValidator.validate(sectionSchema, objectMapper.valueToTree(section));
			for (AnalysisClaim claim : claims)
				schemaValidator.validate(claimSchema, objectMapper.valueToTree(claim));
		}
		catch (AssistantJsonSchemaValidator.SchemaViolationException ex) {
			throw invalid("OUTPUT_SCHEMA_MISMATCH", ex);
		}
		validateEvidence(runId, claims, List.of(section), citationKeys, artifacts);
		Set<String> used = Set.copyOf(section.claimIds());
		if (used.size() != section.claimIds().size() || used.size() != claims.size()
				|| claims.stream().anyMatch(claim -> !used.contains(claim.claimId())))
			throw invalid("SECTION_REFERENCE_INVALID");
	}

	public void validateSectionNodes(JsonNode section, JsonNode claims, Object frozenSchema) {
		JsonNode schema = objectMapper.valueToTree(frozenSchema);
		JsonNode sectionSchema = schema.path("properties").path("sections").path("items");
		JsonNode claimSchema = schema.path("properties").path("claims").path("items");
		if (!sectionSchema.isObject() || !claimSchema.isObject() || claims == null || !claims.isArray())
			throw invalid("STREAM_SCHEMA_UNSUPPORTED");
		try {
			schemaValidator.validate(sectionSchema, section);
			for (JsonNode claim : claims)
				schemaValidator.validate(claimSchema, claim);
		}
		catch (AssistantJsonSchemaValidator.SchemaViolationException ex) {
			throw invalid("OUTPUT_SCHEMA_MISMATCH", ex);
		}
	}

	public String renderSection(AnalysisSection section, List<AnalysisClaim> claims) {
		Map<String, AnalysisClaim> byId = claimsById(claims);
		StringBuilder output = new StringBuilder("### ").append(section.title());
		for (String id : section.claimIds()) {
			AnalysisClaim claim = byId.get(id);
			if (claim == null)
				throw invalid("SECTION_REFERENCE_INVALID");
			output.append("\n\n- ");
			if (claim.type() == ClaimType.INFERENCE)
				output.append("可能关联：");
			else if (claim.type() == ClaimType.OPINION)
				output.append("建议：");
			output.append(claim.text());
		}
		return output.toString();
	}

	public String renderStreaming(AnalysisResult result, List<AnalysisArtifact> artifacts) {
		StringBuilder body = new StringBuilder();
		for (AnalysisSection section : result.sections()) {
			if ("analysis-summary".equals(section.sectionId()) || section.claimIds().isEmpty())
				continue;
			if (!body.isEmpty())
				body.append("\n\n");
			body.append(renderSection(section, result.claims()));
		}
		AnalysisResult tail = new AnalysisResult(result.contractVersion(), result.runId(), result.summary(),
				result.sections().stream().filter(section -> "analysis-summary".equals(section.sectionId())).toList(),
				result.claims(), result.warnings(), result.sourceCoverage(), result.resultDigest());
		if (!body.isEmpty())
			body.append("\n\n");
		return body.append(render(tail, artifacts)).toString();
	}

	public String render(AnalysisResult result) {
		return render(result, List.of());
	}

	public String render(AnalysisResult result, List<AnalysisArtifact> artifacts) {
		Map<String, AnalysisClaim> claims = new LinkedHashMap<>();
		result.claims().forEach(claim -> claims.put(claim.claimId(), claim));
		StringBuilder output = new StringBuilder();
		output.append("### 分析总结\n\n").append(result.summary().trim());
		for (AnalysisSection section : result.sections()) {
			if ("analysis-summary".equals(section.sectionId())) {
				continue;
			}
			List<AnalysisClaim> sectionClaims = section.claimIds()
				.stream()
				.map(claims::get)
				.filter(java.util.Objects::nonNull)
				.toList();
			if (sectionClaims.isEmpty()) {
				continue;
			}
			output.append("\n\n");
			output.append("### ").append(section.title());
			for (AnalysisClaim claim : sectionClaims) {
				output.append("\n\n- ");
				if (claim.type() == ClaimType.INFERENCE) {
					output.append("可能关联：");
				}
				else if (claim.type() == ClaimType.OPINION) {
					output.append("建议：");
				}
				output.append(claim.text());
			}
		}
		List<String> visibleWarnings = result.warnings().stream().filter(this::isUserFacingWarning).toList();
		if (!visibleWarnings.isEmpty()) {
			output.append("\n\n");
			output.append("### 缺失与不确定性");
			visibleWarnings.forEach(warning -> output.append("\n\n- ").append(warning));
		}
		appendWorkflowCharts(output, result, artifacts == null ? List.of() : artifacts);
		appendSourceSummary(output, result, artifacts == null ? List.of() : artifacts);
		return output.toString();
	}

	private void appendWorkflowCharts(StringBuilder output, AnalysisResult result, List<AnalysisArtifact> artifacts) {
		if (result.summary().contains("```chart-spec")) {
			return;
		}
		Set<String> referencedArtifacts = result.claims()
			.stream()
			.flatMap(claim -> claim.artifactIds().stream())
			.collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
		Set<String> chartBlocks = new java.util.LinkedHashSet<>();
		artifacts.stream()
			.filter(artifact -> artifact.kind() == AssistantExecutionContract.ArtifactKind.WORKFLOW_RESULT)
			.filter(artifact -> referencedArtifacts.contains(artifact.artifactId()))
			.flatMap(artifact -> AssistantWorkflowContentPolicy.chartBlocks(objectMapper, artifact.payload()).stream())
			.limit(12)
			.forEach(chartBlocks::add);
		if (!chartBlocks.isEmpty()) {
			output.append("\n\n### 图表");
			chartBlocks.forEach(chart -> output.append("\n\n").append(chart));
		}
	}

	private Map<String, AnalysisClaim> claimsById(List<AnalysisClaim> claims) {
		return claims.stream().collect(java.util.stream.Collectors.toMap(AnalysisClaim::claimId, claim -> claim));
	}

	private void appendSourceSummary(StringBuilder output, AnalysisResult result, List<AnalysisArtifact> artifacts) {
		Set<String> referencedArtifacts = result.claims()
			.stream()
			.flatMap(claim -> claim.artifactIds().stream())
			.collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
		Set<String> referencedCitations = result.claims()
			.stream()
			.flatMap(claim -> claim.citationKeys().stream())
			.collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
		Set<String> workflows = new java.util.LinkedHashSet<>();
		Map<String, Integer> providers = new LinkedHashMap<>();
		for (AnalysisArtifact artifact : artifacts) {
			if (artifact.kind() == AssistantExecutionContract.ArtifactKind.WORKFLOW_RESULT
					&& referencedArtifacts.contains(artifact.artifactId())) {
				Object name = artifact.payload().get("_matrixCapabilityName");
				Object fallback = artifact.payload().get("_matrixCapabilityId");
				String label = name == null ? String.valueOf(fallback) : String.valueOf(name);
				if (StringUtils.hasText(label) && !"null".equals(label)) {
					workflows.add(label);
				}
			}
			if (artifact.kind() == AssistantExecutionContract.ArtifactKind.PROVIDER_CONTEXT) {
				int count = (int) artifact.citationKeys().stream().filter(referencedCitations::contains).count();
				if (count > 0) {
					String provider = providerLabel(String.valueOf(artifact.payload().get("providerCode")));
					providers.merge(provider, count, Integer::sum);
				}
			}
		}
		if (workflows.isEmpty() && providers.isEmpty()) {
			return;
		}
		output.append("\n\n### 数据来源");
		if (!workflows.isEmpty()) {
			output.append("\n\n- 业务分析：").append(String.join("、", workflows));
		}
		providers.forEach((provider,
				count) -> output.append("\n\n- ").append(provider).append("：").append(count).append(" 条可追溯引用"));
	}

	private String providerLabel(String providerCode) {
		return switch (providerCode) {
			case "CAPSULE" -> "闪念胶囊";
			case "INFORMATION" -> "资讯信息";
			case "KNOWLEDGE" -> "企业知识库";
			case "WORK" -> "工作任务";
			default -> "框架数据";
		};
	}

	private boolean isUserFacingWarning(String warning) {
		return StringUtils.hasText(warning) && !warning.contains("Citation Key") && !warning.contains("artifactId")
				&& !warning.contains("WORKFLOW_RESULT");
	}

	private String stripFence(String value) {
		if (!value.startsWith("```")) {
			return value;
		}
		int firstLine = value.indexOf('\n');
		int lastFence = value.lastIndexOf("```");
		return firstLine >= 0 && lastFence > firstLine ? value.substring(firstLine + 1, lastFence).trim() : value;
	}

	private String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw invalid("SERIALIZATION_FAILED", ex);
		}
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

	private IllegalArgumentException invalid(String code) {
		return invalid(code, null);
	}

	private IllegalArgumentException invalid(String code, Throwable cause) {
		return new IllegalArgumentException("ANALYSIS_RESULT_INVALID:" + code, cause);
	}

}
