package com.deepdimen.matrix.assistant.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisArtifact;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisClaim;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisResult;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisSection;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ArtifactKind;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClaimType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssistantAnalysisResultValidatorTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final AssistantAnalysisResultValidator validator = new AssistantAnalysisResultValidator(objectMapper,
			new AssistantJsonSchemaValidator());

	@Test
	void rejectsTrailingJsonAndDuplicateKeysBeforeSchemaValidation() {
		var schema = Map.of("type", "object");
		for (String json : List.of("{} {}", "{\"summary\":\"one\",\"summary\":\"two\"}")) {
			var failure = assertThrows(IllegalArgumentException.class, () -> validator.parseAndValidate("run-1", json,
					schema, validator.schemaId(schema), Set.of(), List.of()));
			assertTrue(failure.getMessage().contains("OUTPUT_NOT_VALID_JSON"));
		}
	}

	@Test
	void factsAndInferencesBothRequireVerifiedEvidence() {
		for (ClaimType type : List.of(ClaimType.FACT, ClaimType.INFERENCE)) {
			IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
					() -> validator.validate("run-1", result(type, List.of()), Set.of("citation-1"), List.of()));
			assertTrue(failure.getMessage()
				.contains(type == ClaimType.FACT ? "FACT_EVIDENCE_REQUIRED" : "INFERENCE_EVIDENCE_REQUIRED"));
		}
	}

	@Test
	void workflowArtifactCanSupportFactsWithoutPretendingToBeKnowledgeCitation() {
		AnalysisArtifact workflow = new AnalysisArtifact("assistant-analysis-artifact/v1", "artifact-1", "run-1",
				"workflow-1", ArtifactKind.WORKFLOW_RESULT, Map.of("_matrixCapabilityId", "pump-analysis"), List.of(),
				"digest", Instant.now());
		AnalysisClaim claim = new AnalysisClaim("claim-1", ClaimType.FACT, "泵房存在报警", List.of(), List.of("artifact-1"));
		AnalysisSection section = new AnalysisSection("analysis-summary", "分析总结", "", List.of("claim-1"));
		AnalysisResult proposed = new AnalysisResult("assistant-analysis-result/v1", "run-1", "泵房存在报警",
				List.of(section), List.of(claim), List.of(), Map.of("WORKFLOW", "AVAILABLE"), null);

		AnalysisResult validated = validator.validate("run-1", proposed, Set.of(), List.of(workflow));

		assertEquals("### 分析总结\n\n泵房存在报警\n\n### 数据来源\n\n- 业务分析：pump-analysis",
				validator.render(validated, List.of(workflow)));
	}

	@Test
	void providerArtifactCannotReplaceItsOriginalCitationKey() {
		AnalysisArtifact provider = new AnalysisArtifact("assistant-analysis-artifact/v1", "artifact-1", "run-1",
				"provider-1", ArtifactKind.PROVIDER_CONTEXT, Map.of(), List.of(), "digest", Instant.now());
		AnalysisClaim claim = new AnalysisClaim("claim-1", ClaimType.FACT, "知识事实", List.of(), List.of("artifact-1"));
		AnalysisSection section = new AnalysisSection("analysis-summary", "分析总结", "", List.of("claim-1"));
		AnalysisResult proposed = new AnalysisResult("assistant-analysis-result/v1", "run-1", "知识事实", List.of(section),
				List.of(claim), List.of(), Map.of(), null);

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> validator.validate("run-1", proposed, Set.of(), List.of(provider)));

		assertTrue(failure.getMessage().contains("FACT_EVIDENCE_REQUIRED"));
	}

	@Test
	void validClaimIsDigestedAndRenderedWithItsCitation() {
		AnalysisResult validated = validator.validate("run-1", result(ClaimType.FACT, List.of("citation-1")),
				Set.of("citation-1"), List.of());

		assertTrue(validated.resultDigest().matches("[0-9a-f]{64}"));
		assertEquals("### 分析总结\n\n水质达标", validator.render(validated));
	}

	@Test
	void rendererUsesFriendlyWorkflowNameAndHidesTechnicalEvidenceMarkers() {
		AnalysisArtifact workflow = new AnalysisArtifact("assistant-analysis-artifact/v1", "artifact-1", "run-1",
				"workflow-1", ArtifactKind.WORKFLOW_RESULT,
				Map.of("_matrixCapabilityId", "pump-room-alarm-analysis", "_matrixCapabilityName", "二供泵房运行与报警分析"),
				List.of(), "digest", Instant.now());
		AnalysisClaim claim = new AnalysisClaim("claim-1", ClaimType.FACT, "泵房存在报警", List.of(), List.of("artifact-1"));
		AnalysisResult result = new AnalysisResult("assistant-analysis-result/v1", "run-1", "泵房存在报警",
				List.of(new AnalysisSection("analysis-summary", "分析总结", "", List.of("claim-1"))), List.of(claim),
				List.of("工作流结果未提供原始 Citation Key，所有事实均引用 artifactId"), Map.of(), null);

		String rendered = validator.render(validator.validate("run-1", result, Set.of(), List.of(workflow)),
				List.of(workflow));

		assertEquals("### 分析总结\n\n泵房存在报警\n\n### 数据来源\n\n- 业务分析：二供泵房运行与报警分析", rendered);
	}

	@Test
	void rendererSummarizesProviderCitationsOnceInSourceFooter() {
		AnalysisArtifact provider = new AnalysisArtifact("assistant-analysis-artifact/v1", "artifact-1", "run-1",
				"provider-knowledge", ArtifactKind.PROVIDER_CONTEXT, Map.of("providerCode", "KNOWLEDGE"),
				List.of("knowledge-1", "knowledge-2"), "digest", Instant.now());
		AnalysisClaim claim = new AnalysisClaim("claim-1", ClaimType.FACT, "制度要求复核报警", List.of("knowledge-1"),
				List.of());
		AnalysisResult result = new AnalysisResult("assistant-analysis-result/v1", "run-1", "制度要求复核报警",
				List.of(new AnalysisSection("analysis-summary", "分析总结", "", List.of("claim-1"))), List.of(claim),
				List.of(), Map.of("KNOWLEDGE", "AVAILABLE"), null);

		String rendered = validator
			.render(validator.validate("run-1", result, Set.of("knowledge-1"), List.of(provider)), List.of(provider));

		assertEquals("### 分析总结\n\n制度要求复核报警\n\n### 数据来源\n\n- 企业知识库：1 条可追溯引用", rendered);
	}

	@Test
	void frozenSkillSchemaIsAppliedBeforeClaimValidation() throws Exception {
		Map<String, Object> schema = Map.of("type", "object", "properties",
				Map.of("summary", Map.of("type", "string", "maxLength", 1)), "additionalProperties", true);
		String raw = objectMapper.writeValueAsString(result(ClaimType.FACT, List.of("citation-1")));

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> validator
			.parseAndValidate("run-1", raw, schema, validator.schemaId(schema), Set.of("citation-1"), List.of()));

		assertTrue(failure.getMessage().contains("OUTPUT_SCHEMA_MISMATCH"));
	}

	private AnalysisResult result(ClaimType type, List<String> citations) {
		AnalysisClaim claim = new AnalysisClaim("claim-1", type, "水质达标", citations, List.of());
		AnalysisSection section = new AnalysisSection("analysis-summary", "分析总结", "", List.of("claim-1"));
		return new AnalysisResult("assistant-analysis-result/v1", "run-1", "水质达标", List.of(section), List.of(claim),
				List.of(), Map.of("KNOWLEDGE", "AVAILABLE"), null);
	}

}
