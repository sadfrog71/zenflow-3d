package com.deepdimen.matrix.discover.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deepdimen.matrix.discover.entity.AiSkillPackageEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class AssistantSkillReleaseSnapshotTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void freezesAndVerifiesAssistantAnalysisPackage() throws Exception {
		AgentSkillPackageParser.ParsedPackage parsed = parsedPackage();
		ObjectNode staged = parsed.manifest().deepCopy();
		staged.set("config", parsed.config());
		AiSkillPackageEntity skillPackage = new AiSkillPackageEntity();
		skillPackage.setPackageSha256(parsed.sha256());
		skillPackage.setManifestJson(staged.toString());

		AssistantSkillReleaseSnapshot snapshot = AssistantSkillReleaseSnapshot.from(skillPackage, objectMapper);

		assertEquals("ASSISTANT_ANALYSIS", snapshot.runtimeKind());
		assertTrue(snapshot.instructionSnapshot().contains("validated claims"));
		assertEquals(64, snapshot.analysisPolicyDigest().length());
		assertTrue(snapshot.analysisResultSchemaId().startsWith("sha256:"));
		assertEquals("[\"KNOWLEDGE\"]", snapshot.requiredProvidersJson());
	}

	@Test
	void rejectsPolicyChangedAfterImport() throws Exception {
		AgentSkillPackageParser.ParsedPackage parsed = parsedPackage();
		ObjectNode staged = parsed.manifest().deepCopy();
		ObjectNode config = parsed.config().deepCopy();
		config.withObject("analysisPolicySnapshot").withObject("runtimePolicy").put("sideEffectClass", "WRITE");
		staged.set("config", config);
		AiSkillPackageEntity skillPackage = new AiSkillPackageEntity();
		skillPackage.setPackageSha256(parsed.sha256());
		skillPackage.setManifestJson(staged.toString());

		assertThrows(IllegalArgumentException.class,
				() -> AssistantSkillReleaseSnapshot.from(skillPackage, objectMapper));
	}

	private AgentSkillPackageParser.ParsedPackage parsedPackage() throws Exception {
		String manifest = """
				{"schemaVersion":"matrix-assistant-skill/v2","runtimeKind":"ASSISTANT_ANALYSIS",
				 "analysisResultSchema":"references/result.schema.json",
				 "sourcePolicy":{"allowedProviders":["KNOWLEDGE","WORK"],"requiredProviders":["KNOWLEDGE"]},
				 "capabilityRoutingPolicy":{"copyAgentOperationGuideIntoSkill":false},
				 "runtimePolicy":{"difyUsage":"WORKFLOW_ONLY","difyKnowledgeRetrieval":"FORBIDDEN","sideEffectClass":"READ_ONLY"},
				 "outputContract":{"format":"MARKDOWN"},
				 "citationPolicy":{"businessFactsRequireCitation":true,"preserveCitationKey":true},
				 "failurePolicy":{"allSourcesEmpty":"EXPLAIN_AND_STOP"}}
				""";
		byte[] archive = zip(Map.of("analysis/SKILL.md",
				"---\nname: analysis\ndescription: Analysis\n---\nRender only validated claims.",
				"analysis/skill-package.json", manifest, "analysis/references/result.schema.json",
				"{\"type\":\"object\",\"required\":[\"claims\"]}"));
		return new AgentSkillPackageParser(objectMapper).parse(archive, "analysis.zip");
	}

	private byte[] zip(Map<String, String> entries) throws Exception {
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
