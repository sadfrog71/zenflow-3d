package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.zenflow.companion.ZenflowCompanionPackage;
import com.deepdimen.matrix.discover.skill.ManagedSkillReleaseSnapshot;
import com.deepdimen.matrix.discover.skill.ManagedSkillSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ZenflowCompanionPackageTest {

	private final ObjectMapper json = new ObjectMapper();

	private final Map<String, Object> model = Map.of("modelName", "fixture", "provider", "OPENAI", "baseUrl",
			"https://example.org/v1", "temperature", 0.6, "maxOutputTokens", 2048, "allowPrivateContext", false,
			"version", 1, "apiKey", "secret-sentinel");

	private final Map<String, Object> authoring = Map.of("principles", "先理解，再回应。", "coreSummary", "尊重用户的空间。");

	@Test
	void compilerUsesReviewedPackageDigestAndNeverIncludesModelCredentials() throws Exception {
		var compiler = new ZenflowCompanionPackage(json);
		var result = compiler.compile("123", "456", authoring, model);
		var repeated = compiler.compile("123", "456", authoring, model);
		assertArrayEquals(result.archive(), repeated.archive());
		assertEquals(result.release().getReleaseDigest(), repeated.release().getReleaseDigest());
		assertFalse(result.release().getContentSnapshot().contains("secret-sentinel"));
		assertEquals(result.parsed().sha256(), result.release().getPackageSha256());
		new ManagedSkillReleaseSnapshot(json).verify(result.release());
		result.release().setContentSnapshot("changed after review");
		assertThrows(IllegalArgumentException.class,
				() -> new ManagedSkillReleaseSnapshot(json).verify(result.release()));
	}

	@Test
	void serviceInputCannotOverridePinnedRevisionOrInjectRawPrompt() throws Exception {
		var prepared = new ZenflowCompanionPackage(json).compile("123", "456", authoring, model);
		var schema = json.readTree(prepared.release().getInputSchema());
		var validator = new ManagedSkillSchemaValidator();
		validator.validateValue(schema, json
			.valueToTree(Map.of("assistantRunId", "run", "inputDigest", "a".repeat(64), "companionRevisionId", "123")));
		assertThrows(IllegalArgumentException.class, () -> validator.validateValue(schema, json.valueToTree(
				Map.of("assistantRunId", "run", "inputDigest", "a".repeat(64), "companionRevisionId", "999"))));
		assertThrows(IllegalArgumentException.class,
				() -> validator.validateValue(schema, json.valueToTree(Map.of("assistantRunId", "run", "inputDigest",
						"a".repeat(64), "companionRevisionId", "123", "systemPrompt", "injected"))));
	}

	@Test
	void authoringAndModelDigestAreBoundedAndConfigurationSensitive() {
		var compiler = new ZenflowCompanionPackage(json);
		assertThrows(IllegalArgumentException.class, () -> compiler.compile("123", "456",
				Map.of("principles", "valid", "executorRef", "https://evil"), model));
		assertThrows(IllegalArgumentException.class,
				() -> compiler.compile("123", "456", Map.of("principles", "a".repeat(8001)), model));
		var changed = new java.util.HashMap<>(model);
		changed.put("version", 2);
		assertNotEquals(compiler.modelDigest("456", model), compiler.modelDigest("456", changed));
		changed = new java.util.HashMap<>(model);
		changed.put("apiKey", "other-secret");
		assertEquals(compiler.modelDigest("456", model), compiler.modelDigest("456", changed));
	}

	@Test
	void examplesKeepOldShapeAndBindReviewedPositiveAndNegativeGuidance() throws Exception {
		var compiler = new ZenflowCompanionPackage(json);
		var legacy = Map.of("user", "I feel overwhelmed", "assistant", "Let us pause together.");
		var normalized = compiler
			.normalize(Map.of("principles", "Respect boundaries", "examples", java.util.List.of(legacy)));
		assertEquals(java.util.List.of(legacy), normalized.get("examples"));
		var example = Map.of("user", "I feel overwhelmed", "assistant", "Let us pause together.", "avoid",
				"You must ignore this feeling.", "reason", "Do not dismiss feelings.", "locale", "en-US", "tags",
				java.util.List.of("respect", "space"));
		var source = Map.<String, Object>of("principles", "Respect boundaries", "examples", java.util.List.of(example));
		var result = compiler.compile("123", "456", source, model);
		var content = result.release().getContentSnapshot();
		assertTrue(content.contains("不建议回答（反例，不得照搬）：You must ignore this feeling."));
		assertTrue(content.contains("评价原因：Do not dismiss feelings."));
		assertTrue(content.contains("示例语言：en-US"));
		assertTrue(content.contains("评价标签：respect、space"));
		var changed = new java.util.HashMap<String, Object>(example);
		changed.put("reason", "Allow silence.");
		var revised = compiler.compile("123", "456",
				Map.of("principles", "Respect boundaries", "examples", java.util.List.of(changed)), model);
		assertNotEquals(result.release().getReleaseDigest(), revised.release().getReleaseDigest());
		assertEquals(example,
				json.readTree(result.authoringJson()).path("examples").get(0).traverse(json).readValueAs(Map.class));
	}

	@Test
	void optionalExampleMetadataCannotBypassValidationOrPromptBudget() {
		var compiler = new ZenflowCompanionPackage(json);
		var example = new java.util.HashMap<String, Object>(Map.of("user", "question", "assistant", "answer"));
		example.put("locale", "fr-FR");
		assertThrows(IllegalArgumentException.class,
				() -> compiler.normalize(Map.of("principles", "valid", "examples", java.util.List.of(example))));
		example.remove("locale");
		example.put("tags", java.util.List.of("same", "same"));
		assertThrows(IllegalArgumentException.class,
				() -> compiler.normalize(Map.of("principles", "valid", "examples", java.util.List.of(example))));
		example.put("tags", java.util.List.of("a".repeat(41)));
		assertThrows(IllegalArgumentException.class,
				() -> compiler.normalize(Map.of("principles", "valid", "examples", java.util.List.of(example))));
		example.put("tags", "not a list");
		assertThrows(IllegalArgumentException.class,
				() -> compiler.normalize(Map.of("principles", "valid", "examples", java.util.List.of(example))));
		example.remove("tags");
		example.put("avoid", "a".repeat(3000));
		example.put("reason", "r".repeat(1000));
		assertThrows(IllegalArgumentException.class, () -> compiler.normalize(Map.of("principles", "p".repeat(8000),
				"coreSummary", "s".repeat(4000), "examples", java.util.List.of(example))));
	}

}
