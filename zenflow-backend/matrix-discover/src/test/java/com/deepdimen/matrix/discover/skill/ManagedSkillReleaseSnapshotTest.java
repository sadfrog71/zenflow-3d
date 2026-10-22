package com.deepdimen.matrix.discover.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManagedSkillReleaseSnapshotTest {

	private final ObjectMapper json = new ObjectMapper();

	@Test
	void freezesContentAndIncludesContractInDigest() throws Exception {
		var factory = new ManagedSkillReleaseSnapshot(json);
		var parsed = parsed();
		var release = factory.prompt(parsed, "archive-hash");
		assertEquals("Reviewed instructions", release.getContentSnapshot());
		assertEquals("PROMPT", release.getRuntimeKind());
		assertEquals(64, release.getReleaseDigest().length());
		factory.verify(release);
		release.setContentSnapshot("tampered");
		assertThrows(IllegalArgumentException.class, () -> factory.verify(release));
		release.setContentSnapshot("Reviewed instructions");
		((ObjectNode) parsed.manifest().path("declared").path("managedRelease").path("outputSchema"))
			.put("additionalProperties", false);
		assertNotEquals(release.getReleaseDigest(), factory.prompt(parsed, "archive-hash").getReleaseDigest());
		assertFalse(release.getOutputSchema().contains("additionalProperties"));
	}

	@Test
	void refusesArchiveDriftAndUnfrozenExternalResources() throws Exception {
		var factory = new ManagedSkillReleaseSnapshot(json);
		var parsed = parsed();
		assertThrows(IllegalArgumentException.class, () -> factory.prompt(parsed, "other-hash"));
		((ObjectNode) parsed.manifest().path("declared").path("managedRelease")).putArray("resourceRefs")
			.addObject()
			.put("resourceId", "mutable-template");
		assertThrows(IllegalArgumentException.class, () -> factory.prompt(parsed, "archive-hash"));
	}

	@Test
	void refusesExecutorOverrideAndReservedParameter() throws Exception {
		var factory = new ManagedSkillReleaseSnapshot(json);
		var parsed = parsed();
		ObjectNode declaration = (ObjectNode) parsed.manifest().path("declared").path("managedRelease");
		((ObjectNode) declaration.path("executorRef")).put("url", "http://untrusted");
		assertThrows(IllegalArgumentException.class, () -> factory.prompt(parsed, "archive-hash"));
		((ObjectNode) declaration.path("executorRef")).remove("url");
		((ObjectNode) declaration.path("inputSchema").path("properties")).putObject("systemPrompt")
			.put("type", "string");
		declaration.putArray("allowedParameters").add("systemPrompt");
		assertThrows(IllegalArgumentException.class, () -> factory.prompt(parsed, "archive-hash"));
	}

	@Test
	void reviewedFixedModelIsFrozenIntoReleaseAndDigest() throws Exception {
		var factory = new ManagedSkillReleaseSnapshot(json);
		var parsed = parsed();
		var executor = (ObjectNode) parsed.manifest().path("declared").path("managedRelease").path("executorRef");
		executor.put("resourceId", "fixed-model");
		var release = factory.prompt(parsed, "archive-hash");
		assertEquals("fixed-model", json.readTree(release.getExecutorRef()).path("resourceId").asText());
		factory.verify(release);
		executor.put("resourceId", "other-model");
		assertNotEquals(release.getReleaseDigest(), factory.prompt(parsed, "archive-hash").getReleaseDigest());
		release.setExecutorRef(executor.toString());
		assertThrows(IllegalArgumentException.class, () -> factory.verify(release));
	}

	@Test
	void modelReferenceCannotBecomeAddressOrCredential() throws Exception {
		var factory = new ManagedSkillReleaseSnapshot(json);
		var parsed = parsed();
		var executor = (ObjectNode) parsed.manifest().path("declared").path("managedRelease").path("executorRef");
		for (String invalid : List.of("", "https://untrusted/model", "model with spaces")) {
			executor.put("resourceId", invalid);
			assertThrows(IllegalArgumentException.class, () -> factory.prompt(parsed, "archive-hash"));
		}
		executor.put("resourceId", "123");
		executor.put("apiKey", "secret");
		assertThrows(IllegalArgumentException.class, () -> factory.prompt(parsed, "archive-hash"));
	}

	@Test
	void difyAppAndConfigurationAreFrozenTogetherAndCannotDrift() throws Exception {
		var factory = new ManagedSkillReleaseSnapshot(json);
		var parsed = difyParsed();
		var release = factory.prompt(parsed, "archive-hash");
		release.setId(2L);
		release.setSkillId(1L);
		factory.verify(release);
		var snapshot = new ManagedSkillReleaseCodec(json).decode("9", release);
		var config = DifyMarketRelease.configuration(snapshot);
		assertEquals("app-1", config.appId());
		assertEquals("config-1", config.modelConfigId());
		assertEquals("a".repeat(64), config.digest());
		assertThrows(IllegalArgumentException.class,
				() -> config.requireSame(new DifyChatConfigurationSnapshot("app-1", "config-2", config.digest())));
		release.setResourceRefs(release.getResourceRefs().replace("config-1", "config-2"));
		assertThrows(IllegalArgumentException.class, () -> factory.verify(release));
	}

	@Test
	void difyReferencesRejectUnpinnedResourcesAndSecrets() throws Exception {
		var factory = new ManagedSkillReleaseSnapshot(json);
		for (String field : List.of("resourceId", "versionDigest")) {
			var parsed = difyParsed();
			((ObjectNode) parsed.manifest().path("declared").path("managedRelease").path("executorRef")).remove(field);
			assertThrows(IllegalArgumentException.class, () -> factory.prompt(parsed, "archive-hash"));
		}
		var parsed = difyParsed();
		var declaration = (ObjectNode) parsed.manifest().path("declared").path("managedRelease");
		((ObjectNode) declaration.path("resourceRefs").get(0)).put("digest", "b".repeat(64));
		var mismatched = parsed;
		assertThrows(IllegalArgumentException.class, () -> factory.prompt(mismatched, "archive-hash"));
		parsed = difyParsed();
		declaration = (ObjectNode) parsed.manifest().path("declared").path("managedRelease");
		((ObjectNode) declaration.path("executorRef")).put("apiKey", "secret");
		var secretPackage = parsed;
		assertThrows(IllegalArgumentException.class, () -> factory.prompt(secretPackage, "archive-hash"));
	}

	private AgentSkillPackageParser.ParsedPackage difyParsed() throws Exception {
		var parsed = parsed();
		var declaration = (ObjectNode) parsed.manifest().path("declared").path("managedRelease");
		declaration.putArray("allowedParameters");
		declaration.putObject("executorRef")
			.put("executorKey", "PROMPT")
			.put("provider", "DIFY")
			.put("resourceId", "app-1")
			.put("versionDigest", "a".repeat(64));
		declaration.putArray("resourceRefs")
			.addObject()
			.put("resourceId", "app-1")
			.put("version", "config-1")
			.put("digest", "a".repeat(64));
		return parsed;
	}

	@Test
	void knowledgeReleaseFreezesModelAgentAndDatasetConfigurationTogether() throws Exception {
		var parsed = parsed();
		var declaration = (ObjectNode) parsed.manifest().path("declared").path("managedRelease");
		declaration.putArray("allowedParameters");
		var refs = List.of(
				new com.deepdimen.matrix.skill.api.SkillContract.ResourceRef("agent:88", "a".repeat(64),
						"a".repeat(64)),
				new com.deepdimen.matrix.skill.api.SkillContract.ResourceRef("model:77", "b".repeat(64),
						"b".repeat(64)),
				new com.deepdimen.matrix.skill.api.SkillContract.ResourceRef("dataset:31", "c".repeat(64),
						"c".repeat(64)));
		var digest = com.deepdimen.matrix.skill.api.KnowledgeSkillRelease.configurationDigest(refs);
		declaration.putObject("executorRef")
			.put("executorKey", "PROMPT")
			.put("provider", "KNOWLEDGE")
			.put("resourceId", "77")
			.put("versionDigest", digest);
		declaration.set("resourceRefs", json.valueToTree(refs));
		var factory = new ManagedSkillReleaseSnapshot(json);
		var release = factory.prompt(parsed, "archive-hash");
		release.setId(2L);
		release.setSkillId(1L);
		var fixed = com.deepdimen.matrix.skill.api.KnowledgeSkillRelease
			.from(new ManagedSkillReleaseCodec(json).decode("9", release));
		assertEquals("77", fixed.modelId());
		assertEquals("88", fixed.agentId());
		assertEquals(digest, fixed.digest());
		factory.verify(release);
		((ObjectNode) declaration.path("resourceRefs").get(2)).put("digest", "d".repeat(64));
		assertThrows(IllegalArgumentException.class, () -> factory.prompt(parsed, "archive-hash"));
		declaration.set("resourceRefs", json.valueToTree(refs));
		((ObjectNode) declaration.path("executorRef")).put("apiKey", "secret");
		assertThrows(IllegalArgumentException.class, () -> factory.prompt(parsed, "archive-hash"));
	}

	private AgentSkillPackageParser.ParsedPackage parsed() throws Exception {
		ObjectNode manifest = (ObjectNode) json.readTree("""
				{"runtimeKind":"PROMPT_INJECTION","declared":{"managedRelease":{
				  "inputSchema":{"type":"object","properties":{"topic":{"type":"string"}}},
				  "outputSchema":{"type":"object","properties":{"summary":{"type":"string"}}},
				  "executorRef":{"executorKey":"PROMPT"},"allowedParameters":["topic"]
				}}}
				""");
		return new AgentSkillPackageParser.ParsedPackage("skill", "test", Map.of(), "Reviewed instructions", manifest,
				json.createObjectNode(), List.of(), "archive-hash", 100, "skill.zip", List.of());
	}

}
