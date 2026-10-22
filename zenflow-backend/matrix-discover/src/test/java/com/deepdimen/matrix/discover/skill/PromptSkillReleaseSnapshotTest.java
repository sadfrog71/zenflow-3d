package com.deepdimen.matrix.discover.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.deepdimen.matrix.discover.entity.AiSkillPackageEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class PromptSkillReleaseSnapshotTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void freezesExactApprovedPackageInstructionAndProvenance() throws Exception {
		String prompt = "Keep this exact instruction.\nDo not read mutable ai_skill.config.";
		AiSkillPackageEntity skillPackage = skillPackage(prompt, "package-sha-256");

		PromptSkillReleaseSnapshot snapshot = PromptSkillReleaseSnapshot.from(skillPackage, objectMapper);

		assertEquals("PROMPT_INJECTION", snapshot.runtimeKind());
		assertEquals(prompt, snapshot.promptSnapshot());
		assertEquals(sha256(prompt), snapshot.instructionDigest());
		assertEquals("package-sha-256", snapshot.packageSha256());
		assertEquals("[\"KNOWLEDGE\",\"WORK\"]", snapshot.requiredProvidersJson());
	}

	@Test
	void digestChangesOnlyWhenFrozenInstructionChanges() {
		PromptSkillReleaseSnapshot first = PromptSkillReleaseSnapshot
			.from(skillPackage("instruction-v1", "same-package"), objectMapper);
		PromptSkillReleaseSnapshot second = PromptSkillReleaseSnapshot
			.from(skillPackage("instruction-v2", "same-package"), objectMapper);

		assertNotEquals(first.instructionDigest(), second.instructionDigest());
	}

	@Test
	void rejectsPackageWithoutPromptInstruction() {
		AiSkillPackageEntity skillPackage = new AiSkillPackageEntity();
		skillPackage.setPackageSha256("package-sha-256");
		skillPackage.setManifestJson("{\"runtimeKind\":\"PROMPT_INJECTION\",\"config\":{}}");

		assertThrows(IllegalArgumentException.class, () -> PromptSkillReleaseSnapshot.from(skillPackage, objectMapper));
	}

	@Test
	void acceptsAWellFormedFutureProviderWithoutChangingTheCoreContract() {
		AiSkillPackageEntity skillPackage = skillPackage("instruction", "package-sha-256");
		skillPackage.setManifestJson("""
				{
				  "runtimeKind": "PROMPT_INJECTION",
				  "declared": {"requiredProviders": ["NOT_A_PROVIDER"]},
				  "config": {"promptTemplate": "instruction"}
				}
				""");

		PromptSkillReleaseSnapshot snapshot = PromptSkillReleaseSnapshot.from(skillPackage, objectMapper);

		assertEquals("[\"NOT_A_PROVIDER\"]", snapshot.requiredProvidersJson());
	}

	@Test
	void rejectsMalformedRequiredProviderBeforePublication() {
		AiSkillPackageEntity skillPackage = skillPackage("instruction", "package-sha-256");
		skillPackage.setManifestJson("""
				{
				  "runtimeKind": "PROMPT_INJECTION",
				  "declared": {"requiredProviders": ["not-a-provider"]},
				  "config": {"promptTemplate": "instruction"}
				}
				""");

		assertThrows(IllegalArgumentException.class, () -> PromptSkillReleaseSnapshot.from(skillPackage, objectMapper));
	}

	private AiSkillPackageEntity skillPackage(String prompt, String packageSha256) {
		AiSkillPackageEntity skillPackage = new AiSkillPackageEntity();
		skillPackage.setPackageSha256(packageSha256);
		skillPackage.setManifestJson("""
				{
				  "runtimeKind": "PROMPT_INJECTION",
				  "declared": {"requiredProviders": ["KNOWLEDGE", "WORK"]},
				  "config": {"promptTemplate": %s}
				}
				""".formatted(toJson(prompt)));
		return skillPackage;
	}

	private String toJson(String value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String sha256(String value) throws Exception {
		return HexFormat.of()
			.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
	}

}
