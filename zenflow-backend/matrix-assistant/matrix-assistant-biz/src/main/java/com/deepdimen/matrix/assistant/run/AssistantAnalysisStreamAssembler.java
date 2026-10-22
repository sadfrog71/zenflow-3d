package com.deepdimen.matrix.assistant.run;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisArtifact;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisClaim;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisResult;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisSection;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One run's validated stream. Summary stays private until transport EOF and full-schema
 * validation.
 */
public final class AssistantAnalysisStreamAssembler {

	private final ObjectMapper mapper;

	private final AssistantAnalysisResultValidator validator;

	private final AssistantAnalysisFrameParser parser;

	private final String runId;

	private final Object schema;

	private final String schemaId;

	private final Set<String> citations;

	private final List<AnalysisArtifact> artifacts;

	private final Consumer<ValidatedSection> onSection;

	private final List<AnalysisSection> sections = new ArrayList<>();

	private final List<AnalysisClaim> claims = new ArrayList<>();

	private final Set<String> claimIds = new HashSet<>();

	private AnalysisClaim summary;

	private List<String> warnings = List.of();

	private Map<String, String> coverage = Map.of();

	private String suppliedDigest;

	public record ValidatedSection(AnalysisSection section, List<AnalysisClaim> claims, String text) {
		public ValidatedSection {
			claims = List.copyOf(claims);
		}
	}

	public AssistantAnalysisStreamAssembler(ObjectMapper mapper, AssistantAnalysisResultValidator validator,
			String runId, Object frozenSchema, String schemaId, Set<String> citations, List<AnalysisArtifact> artifacts,
			Consumer<ValidatedSection> onSection) {
		this.mapper = mapper;
		this.validator = validator;
		this.runId = runId;
		this.schema = mapper.valueToTree(frozenSchema);
		this.schemaId = schemaId;
		if (!validator.schemaId(schema).equals(schemaId))
			throw invalid("SCHEMA_DIGEST_MISMATCH");
		this.citations = Set.copyOf(citations);
		this.artifacts = List.copyOf(artifacts);
		this.onSection = java.util.Objects.requireNonNull(onSection);
		this.parser = new AssistantAnalysisFrameParser(mapper, runId, this::frame);
	}

	public void accept(String chunk) {
		parser.accept(chunk);
	}

	public boolean hasVisibleSections() {
		return !sections.isEmpty();
	}

	public AssistantAnalysisFrameParser.Mode mode() {
		return parser.mode();
	}

	public AnalysisResult finish() {
		String legacy = parser.finish();
		if (legacy != null)
			return validator.parseAndValidate(runId, legacy, schema, schemaId, citations, artifacts);
		if (summary == null)
			throw invalid("SUMMARY_MISSING");
		var completeSections = new ArrayList<>(sections);
		completeSections.add(new AnalysisSection("analysis-summary", "分析总结", "", List.of(summary.claimId())));
		var completeClaims = new ArrayList<>(claims);
		completeClaims.add(summary);
		AnalysisResult proposed = new AnalysisResult(AssistantExecutionContract.ANALYSIS_VERSION, runId, summary.text(),
				completeSections, completeClaims, warnings, coverage, null);
		try {
			AnalysisResult result = validator.parseAndValidate(runId, mapper.writeValueAsString(proposed), schema,
					schemaId, citations, artifacts);
			// null delegates hashing to the server; an explicitly supplied checksum must
			// agree.
			if (suppliedDigest != null && !suppliedDigest.equals(result.resultDigest()))
				throw invalid("DIGEST_MISMATCH");
			return result;
		}
		catch (IOException ex) {
			throw invalid("OUTPUT_NOT_VALID_JSON");
		}
	}

	private void frame(JsonNode frame) {
		try {
			switch (frame.path("type").asText()) {
				case "analysis_meta" -> {
					int totalSections = frame.path("plannedSections").intValue() + 1;
					JsonNode sectionSchema = ((JsonNode) schema).path("properties").path("sections");
					if ((sectionSchema.has("maxItems") && totalSections > sectionSchema.path("maxItems").intValue())
							|| (sectionSchema.has("minItems")
									&& totalSections < sectionSchema.path("minItems").intValue()))
						throw invalid("SECTION_COUNT_SCHEMA_MISMATCH");
				}
				case "section" -> {
					validator.validateSectionNodes(frame.get("section"), frame.get("claims"), schema);
					AnalysisSection section = read(frame.get("section"), AnalysisSection.class);
					List<AnalysisClaim> incoming = new ArrayList<>();
					for (JsonNode node : frame.get("claims"))
						incoming.add(read(node, AnalysisClaim.class));
					validator.validateSection(runId, section, incoming, schema, schemaId, citations, artifacts);
					JsonNode claimsSchema = ((JsonNode) schema).path("properties").path("claims");
					if (claimsSchema.has("maxItems")
							&& claims.size() + incoming.size() + 1 > claimsSchema.path("maxItems").intValue())
						throw invalid("CLAIM_COUNT_SCHEMA_MISMATCH");
					for (AnalysisClaim claim : incoming)
						if (!claimIds.add(claim.claimId()))
							throw invalid("DUPLICATE_CLAIM");
					String rendered = validator.renderSection(section, incoming);
					sections.add(section);
					claims.addAll(incoming);
					onSection.accept(new ValidatedSection(section, incoming, rendered));
				}
				case "summary" -> {
					JsonNode claimNode = frame.get("claim");
					var summaryNode = mapper.createObjectNode();
					summaryNode.put("sectionId", "analysis-summary").put("title", "分析总结").put("content", "");
					summaryNode.putArray("claimIds").add(claimNode.path("claimId"));
					validator.validateSectionNodes(summaryNode, mapper.createArrayNode().add(claimNode), schema);
					AnalysisClaim candidate = read(claimNode, AnalysisClaim.class);
					var section = new AnalysisSection("analysis-summary", "分析总结", "", List.of(candidate.claimId()));
					validator.validateSection(runId, section, List.of(candidate), schema, schemaId, citations,
							artifacts);
					if (!sections.isEmpty()) {
						var usedCitations = claims.stream()
							.flatMap(claim -> claim.citationKeys().stream())
							.collect(java.util.stream.Collectors.toSet());
						var usedArtifacts = claims.stream()
							.flatMap(claim -> claim.artifactIds().stream())
							.collect(java.util.stream.Collectors.toSet());
						if (!usedCitations.containsAll(candidate.citationKeys())
								|| !usedArtifacts.containsAll(candidate.artifactIds()))
							throw invalid("SUMMARY_REFERENCE_NOT_IN_SECTIONS");
					}
					if (!claimIds.add(candidate.claimId()))
						throw invalid("DUPLICATE_CLAIM");
					summary = candidate;
					var rawWarnings = frame.get("warnings");
					var safeWarnings = new ArrayList<String>();
					for (JsonNode node : rawWarnings) {
						if (!node.isTextual())
							throw invalid("WARNING_INVALID");
						safeWarnings.add(node.textValue());
					}
					warnings = List.copyOf(safeWarnings);
					var safeCoverage = new java.util.LinkedHashMap<String, String>();
					frame.get("sourceCoverage").fields().forEachRemaining(entry -> {
						if (!entry.getValue().isTextual())
							throw invalid("COVERAGE_INVALID");
						safeCoverage.put(entry.getKey(), entry.getValue().textValue());
					});
					coverage = Map.copyOf(safeCoverage);
				}
				case "final" -> suppliedDigest = frame.path("resultDigest").isNull() ? null
						: frame.path("resultDigest").textValue();
				default -> throw invalid("TYPE_INVALID");
			}
		}
		catch (IOException ex) {
			throw invalid("FRAME_FIELDS_INVALID");
		}
	}

	private <T> T read(JsonNode node, Class<T> type) throws IOException {
		return mapper.readerFor(type).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(node);
	}

	private IllegalArgumentException invalid(String code) {
		return new IllegalArgumentException("ANALYSIS_STREAM_INVALID:" + code);
	}

}
