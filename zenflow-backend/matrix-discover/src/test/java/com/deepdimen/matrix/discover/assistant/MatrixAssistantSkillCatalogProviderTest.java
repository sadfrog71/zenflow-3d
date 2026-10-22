package com.deepdimen.matrix.discover.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.deepdimen.matrix.discover.service.AiAgentSkillService;
import com.deepdimen.matrix.discover.service.AiUserSkillPrefService;
import com.deepdimen.matrix.discover.vo.AiAgentSkillVO;
import org.junit.jupiter.api.Test;

class MatrixAssistantSkillCatalogProviderTest {

	private final AiAgentSkillService skillService = mock(AiAgentSkillService.class);

	private final AiUserSkillPrefService preferenceService = mock(AiUserSkillPrefService.class);

	private final MatrixAssistantSkillCatalogProvider catalog = new MatrixAssistantSkillCatalogProvider(skillService,
			preferenceService);

	@Test
	void onlyPublishedPromptSnapshotsAreVisibleAndResolvable() {
		when(preferenceService.getByUserAndAgent(AiAgentService.DEFAULT_AGENT_ID)).thenReturn(List.of());
		when(skillService.getPublishedByAgentId(AiAgentService.DEFAULT_AGENT_ID))
			.thenReturn(List.of(promptSkill(), executableWorkflow()));

		var descriptors = catalog.listPromptSkills(identity());
		assertEquals(List.of("7"), descriptors.stream().map(item -> item.skillId()).toList());
		assertEquals("Answers with engineering context", descriptors.get(0).description());
		var frozen = catalog.resolvePromptSkill(identity(), "7");
		assertEquals("700", frozen.releaseId());
		assertEquals("answer as an engineer", frozen.prompt());
		assertEquals("e133a17d14c4d7d333aac2630a514156be1e8d8898db1438ebdd7810ad2af30d", frozen.instructionDigest());
		verify(skillService, times(2)).getPublishedByAgentId(AiAgentService.DEFAULT_AGENT_ID);
	}

	@Test
	void p0PreferencesRejectMultiplePromptSkills() {
		assertThrows(IllegalArgumentException.class,
				() -> catalog.savePromptSkillPreference(identity(), List.of("7", "8")));
	}

	@Test
	void catalogRequiresAuthenticatedTenantAndUser() {
		IdentityEnvelope missingUser = new IdentityEnvelope("11", "", "3", List.of("3"), List.of(), List.of(),
				"skill-test", "ASSISTANT_MODEL");

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> catalog.listPromptSkills(missingUser));
		assertEquals("ASSISTANT_IDENTITY_REQUIRED", error.getMessage());
	}

	@Test
	void tamperedPromptSnapshotIsRejected() {
		AiAgentSkillVO skill = promptSkill();
		skill.setPromptSnapshot("tampered");
		when(skillService.getPublishedByAgentId(AiAgentService.DEFAULT_AGENT_ID)).thenReturn(List.of(skill));

		assertThrows(IllegalArgumentException.class, () -> catalog.resolvePromptSkill(identity(), "7"));
	}

	private AiAgentSkillVO promptSkill() {
		AiAgentSkillVO skill = new AiAgentSkillVO();
		skill.setSkillId(7L);
		skill.setSkillReleaseId(700L);
		skill.setSkillCode("ENGINEER");
		skill.setSkillName("工程师回答");
		skill.setDescription("Answers with engineering context");
		skill.setImplType("prompt_injection");
		skill.setExecutionStrategy("PRIMARY_APP_INSTRUCTION");
		skill.setRuntimeKind("PROMPT_INJECTION");
		skill.setPromptSnapshot("answer as an engineer");
		skill.setInstructionDigest("e133a17d14c4d7d333aac2630a514156be1e8d8898db1438ebdd7810ad2af30d");
		skill.setPackageSha256("package-1");
		skill.setReleaseVersion("v3");
		skill.setRequiredProviders(List.of("KNOWLEDGE"));
		return skill;
	}

	private AiAgentSkillVO executableWorkflow() {
		AiAgentSkillVO skill = promptSkill();
		skill.setSkillId(8L);
		skill.setSkillReleaseId(800L);
		skill.setExecutionStrategy("DEDICATED_DIFY_WORKFLOW");
		return skill;
	}

	private IdentityEnvelope identity() {
		return new IdentityEnvelope("11", "7", "3", List.of("3"), List.of(), List.of(), "skill-test",
				"ASSISTANT_MODEL");
	}

}
