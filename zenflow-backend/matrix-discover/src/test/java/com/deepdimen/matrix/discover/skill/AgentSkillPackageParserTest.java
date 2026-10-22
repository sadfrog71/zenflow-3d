package com.deepdimen.matrix.discover.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class AgentSkillPackageParserTest {

	private final AgentSkillPackageParser parser = new AgentSkillPackageParser(new ObjectMapper());

	@Test
	void parsesClaudeCodeSkillDirectoryAndKeepsScriptsNonExecutable() throws Exception {
		byte[] archive = zip(Map.of(
				"water-report/SKILL.md", "---\nname: water-report\ndescription: Generate a water report\n---\n\nUse the supplied context to produce a concise report.",
				"water-report/scripts/run.sh", "#!/bin/sh\necho unsafe",
				"water-report/references/guide.md", "reference"));

		AgentSkillPackageParser.ParsedPackage parsed = parser.parse(archive, "water-report.zip");

		assertEquals("water-report", parsed.name());
		assertEquals("Generate a water report", parsed.description());
		assertEquals("water-report/SKILL.md", parsed.manifest().path("entrypoint").asText());
		assertEquals("PROMPT_INJECTION", parsed.manifest().path("runtimeKind").asText());
		assertTrue(parsed.warnings().stream().anyMatch(value -> value.contains("scripts/")));
		assertTrue(parsed.config().path("promptTemplate").asText().contains("supplied context"));
	}

	@Test
	void rejectsPathTraversalAndSensitiveFiles() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> parser.parse(zip(Map.of(
				"../SKILL.md", "---\nname: unsafe\ndescription: unsafe\n---\nbody")), "unsafe.zip"));
		assertThrows(IllegalArgumentException.class, () -> parser.parse(zip(Map.of(
				"SKILL.md", "---\nname: unsafe\ndescription: unsafe\n---\nbody",
				".env", "TOKEN=secret")), "unsafe.zip"));
	}

	@Test
	void rejectsUnsupportedRuntimeExtensions() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> parser.parse(zip(Map.of(
				"SKILL.md", "---\nname: code-runner\ndescription: Execute code\n---\nbody",
				"hooks/pre-run.json", "{}")), "code-runner.zip"));
	}

	@Test
	void parsesDeclarativeAssistantAnalysisV2WithFrozenPolicyAndSchemaDigest() throws Exception {
		byte[] archive = zip(Map.of("unified-source-summary/SKILL.md",
				"---\nname: unified-source-summary\ndescription: Unified evidence summary\n---\nRender only validated claims.",
				"unified-source-summary/skill-package.json", analysisManifest(""),
				"unified-source-summary/references/analysis-result-v1.schema.json",
				"{\"type\":\"object\",\"required\":[\"claims\"]}"));

		AgentSkillPackageParser.ParsedPackage parsed = parser.parse(archive, "unified-source-summary.zip");

		assertEquals("matrix-assistant-skill/v2", parsed.manifest().path("schemaVersion").asText());
		assertEquals("ASSISTANT_ANALYSIS", parsed.manifest().path("runtimeKind").asText());
		assertEquals(64, parsed.config().path("analysisPolicyDigest").asText().length());
		assertTrue(parsed.config().path("analysisResultSchemaId").asText().startsWith("sha256:"));
		assertEquals("KNOWLEDGE", parsed.config().path("requiredProviders").get(0).asText());
	}

	@Test
	void rejectsAnalysisSkillThatCopiesMutableAgentIdentity() throws Exception {
		byte[] archive = zip(Map.of("unified-source-summary/SKILL.md",
				"---\nname: unified-source-summary\ndescription: Unified evidence summary\n---\nRender only validated claims.",
				"unified-source-summary/skill-package.json", analysisManifest(",\"agentId\":88"),
				"unified-source-summary/references/analysis-result-v1.schema.json", "{\"type\":\"object\"}"));

		assertThrows(IllegalArgumentException.class,
				() -> parser.parse(archive, "unified-source-summary.zip"));
	}

	private String analysisManifest(String extra) {
		return """
				{
				  "schemaVersion":"matrix-assistant-skill/v2",
				  "runtimeKind":"ASSISTANT_ANALYSIS",
				  "analysisResultSchema":"references/analysis-result-v1.schema.json",
				  "sourcePolicy":{"allowedProviders":["KNOWLEDGE","WORK"],"requiredProviders":["KNOWLEDGE"]},
				  "capabilityRoutingPolicy":{"copyAgentOperationGuideIntoSkill":false},
				  "runtimePolicy":{"difyUsage":"WORKFLOW_ONLY","difyKnowledgeRetrieval":"FORBIDDEN","sideEffectClass":"READ_ONLY"},
				  "outputContract":{"format":"MARKDOWN"},
				  "citationPolicy":{"businessFactsRequireCitation":true,"preserveCitationKey":true},
				  "failurePolicy":{"allSourcesEmpty":"EXPLAIN_AND_STOP"}%s
				}
				""".formatted(extra);
	}

	private byte[] zip(Map<String, String> entries) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream output = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
			for (Map.Entry<String, String> entry : entries.entrySet()) {
				output.putNextEntry(new ZipEntry(entry.getKey()));
				output.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
				output.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

}
