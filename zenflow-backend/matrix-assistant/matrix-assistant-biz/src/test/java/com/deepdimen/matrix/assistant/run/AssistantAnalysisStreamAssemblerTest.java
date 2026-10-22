package com.deepdimen.matrix.assistant.run;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisArtifact;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisClaim;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ArtifactKind;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClaimType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssistantAnalysisStreamAssemblerTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private final AssistantAnalysisResultValidator validator = new AssistantAnalysisResultValidator(mapper,
			new AssistantJsonSchemaValidator());

	private final List<AssistantAnalysisStreamAssembler.ValidatedSection> visible = new ArrayList<>();

	private Object schema() throws Exception {
		return mapper.readTree(
				"""
						{"type":"object","properties":{
						 "claims":{"type":"array","items":{"type":"object","required":["claimId","type","text","citationKeys","artifactIds"],"additionalProperties":false,"properties":{
						  "claimId":{"type":"string"},"type":{"enum":["FACT","INFERENCE","OPINION"]},"text":{"type":"string","maxLength":200},"citationKeys":{"type":"array","items":{"type":"string"}},"artifactIds":{"type":"array","items":{"type":"string"}}
						 }}},
						 "sections":{"type":"array","items":{"type":"object","required":["sectionId","title","content","claimIds"],"additionalProperties":false,"properties":{
						  "sectionId":{"type":"string"},"title":{"type":"string"},"content":{"type":"string"},"claimIds":{"type":"array","items":{"type":"string"}}
						 }}}
						}}
						""");
	}

	private AssistantAnalysisStreamAssembler assembler(List<AnalysisArtifact> artifacts) throws Exception {
		Object schema = schema();
		return new AssistantAnalysisStreamAssembler(mapper, validator, "r", schema, validator.schemaId(schema),
				Set.of("c"), artifacts, visible::add);
	}

	private String meta(int count) {
		return "{\"type\":\"analysis_meta\",\"protocol\":\"assistant-analysis-stream/v1\",\"runId\":\"r\",\"analysisType\":\"business\",\"plannedSections\":"
				+ count + "}\n";
	}

	private AnalysisClaim claim(String id, String citation) {
		return new AnalysisClaim(id, ClaimType.FACT, "事实 " + id, List.of(citation), List.of());
	}

	private String section(String id, AnalysisClaim claim) throws Exception {
		return mapper.writeValueAsString(Map.of("type", "section", "section",
				Map.of("sectionId", id, "title", "结果 " + id, "content", "", "claimIds", List.of(claim.claimId())),
				"claims", List.of(claim))) + "\n";
	}

	private String summary() throws Exception {
		return mapper.writeValueAsString(Map.of("type", "summary", "claim", claim("summary", "c"), "warnings",
				List.of(), "sourceCoverage", Map.of())) + "\n";
	}

	private String end(int count) {
		return "{\"type\":\"final\",\"resultDigest\":null,\"sectionCount\":" + count + ",\"claimCount\":" + (count + 1)
				+ ",\"complete\":true}\n";
	}

	@Test
	void frozenTotalSectionLimitRejectsStreamBeforeAnyVisibleSection() throws Exception {
		var frozen = (com.fasterxml.jackson.databind.JsonNode) schema();
		((com.fasterxml.jackson.databind.node.ObjectNode) frozen.path("properties").path("sections")).put("maxItems",
				1);
		var stream = new AssistantAnalysisStreamAssembler(mapper, validator, "r", frozen, validator.schemaId(frozen),
				Set.of("c"), List.of(), visible::add);
		assertThrows(IllegalArgumentException.class, () -> stream.accept(meta(1)));
		assertTrue(visible.isEmpty());
	}

	@Test
	void summaryCannotIntroduceNewEvidenceNotUsedByCompletedSections() throws Exception {
		Object frozen = schema();
		var stream = new AssistantAnalysisStreamAssembler(mapper, validator, "r", frozen, validator.schemaId(frozen),
				Set.of("c", "other"), List.of(), visible::add);
		stream.accept(meta(1) + section("s", claim("a", "c")));
		String summary = mapper.writeValueAsString(Map.of("type", "summary", "claim", claim("summary", "other"),
				"warnings", List.of(), "sourceCoverage", Map.of())) + "\n";
		assertThrows(IllegalArgumentException.class, () -> stream.accept(summary));
		assertEquals(1, visible.size());
	}

	@Test
	void twoSectionsAreVisibleBeforeSummaryAndFinalResultMatchesLegacyValidation() throws Exception {
		var stream = assembler(List.of());
		stream.accept(meta(2) + section("s1", claim("a", "c")));
		assertEquals(1, visible.size());
		stream.accept(section("s2", claim("b", "c")));
		assertEquals(2, visible.size());
		stream.accept(summary() + end(2));
		assertEquals(2, visible.size());
		var result = stream.finish();
		assertEquals("事实 summary", result.summary());
		assertEquals(3, result.sections().size());
		assertEquals(64, result.resultDigest().length());
		var legacy = validator.parseAndValidate("r", mapper.writeValueAsString(result), schema(),
				validator.schemaId(schema()), Set.of("c"), List.of());
		assertEquals(result, legacy);
	}

	@Test
	void unknownCitationOrForeignRunArtifactNeverReachesVisibleConsumer() throws Exception {
		var stream = assembler(List.of());
		stream.accept(meta(1));
		assertThrows(IllegalArgumentException.class, () -> stream.accept(section("s", claim("a", "unknown"))));
		assertTrue(visible.isEmpty());
		var foreign = new AnalysisArtifact("assistant-analysis-artifact/v1", "foreign", "other-run", "step",
				ArtifactKind.WORKFLOW_RESULT, Map.of(), List.of(), "digest", Instant.now());
		var other = assembler(List.of(foreign));
		other.accept(meta(1));
		var proposed = new AnalysisClaim("b", ClaimType.FACT, "越权结果", List.of(), List.of("foreign"));
		assertThrows(IllegalArgumentException.class, () -> other.accept(section("s", proposed)));
		assertTrue(visible.isEmpty());
	}

	@Test
	void duplicateClaimInLaterSectionIsNotVisible() throws Exception {
		var stream = assembler(List.of());
		stream.accept(meta(2) + section("s1", claim("a", "c")));
		assertThrows(IllegalArgumentException.class, () -> stream.accept(section("s2", claim("a", "c"))));
		assertEquals(1, visible.size());
	}

	@Test
	void rejectsRawTypeCoercionAndUnknownFieldsBeforeRendering() throws Exception {
		for (String bad : List.of(section("s", claim("a", "c")).replace("\"text\":\"事实 a\"", "\"text\":123"),
				section("s", claim("a", "c")).replace("\"claimId\":", "\"unexpected\":true,\"claimId\":"))) {
			var stream = assembler(List.of());
			stream.accept(meta(1));
			assertThrows(IllegalArgumentException.class, () -> stream.accept(bad));
		}
		assertTrue(visible.isEmpty());
	}

	@Test
	void interruptedSecondSectionRetainsOnlyValidatedFirstSection() throws Exception {
		var stream = assembler(List.of());
		stream.accept(meta(2) + section("s1", claim("a", "c")) + "{\"type\":\"section\"");
		assertThrows(IllegalArgumentException.class, stream::finish);
		assertTrue(stream.hasVisibleSections());
		assertEquals(1, visible.size());
	}

	@Test
	void invalidDigestOrContentAfterFinalCannotReleaseSummary() throws Exception {
		var stream = assembler(List.of());
		stream.accept(meta(1) + section("s", claim("a", "c")) + summary()
				+ end(1).replace("null", "\"" + "0".repeat(64) + "\""));
		assertThrows(IllegalArgumentException.class, stream::finish);
		assertEquals(1, visible.size());
		var other = assembler(List.of());
		other.accept(meta(0) + summary() + end(0));
		assertThrows(IllegalArgumentException.class, () -> other.accept("{}\n"));
		assertThrows(IllegalArgumentException.class, other::finish);
	}

	@Test
	void legacyNeverEmitsASectionAndStillValidatesWholeResult() throws Exception {
		var complete = assembler(List.of());
		complete.accept(meta(0) + summary() + end(0));
		var result = complete.finish();
		var old = assembler(List.of());
		old.accept(mapper.writeValueAsString(result));
		assertEquals(result, old.finish());
		assertTrue(visible.isEmpty());
	}

}
