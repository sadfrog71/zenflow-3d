package com.deepdimen.matrix.discover.dify;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSkillCapability;
import com.deepdimen.matrix.assistant.capability.AssistantPromptSkillCatalog;
import com.deepdimen.matrix.assistant.capability.AssistantPromptSkillCatalog.PromptSkillDescriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DifyAssistantSkillCatalogProviderTest {

	private final AssistantPromptSkillCatalog promptSkillCatalog = mock(AssistantPromptSkillCatalog.class);

	private final DifyAssistantSkillCatalogProvider provider = new DifyAssistantSkillCatalogProvider(promptSkillCatalog);

	@Test
	void normalAssistantCatalogComesOnlyFromTenantPromptSkillCatalog() {
		when(promptSkillCatalog.listPromptSkills(any()))
			.thenReturn(List.of(new PromptSkillDescriptor("101", "201", "v1", "报告技能", "desc", "icon", true,
					"digest", Set.of("KNOWLEDGE"))));

		List<AssistantSkillCapability> catalog = provider.listSkills(identity());

		assertEquals(1, catalog.size());
		AssistantSkillCapability skill = catalog.get(0);
		assertEquals("101", skill.skillCode());
		assertEquals("报告技能", skill.displayName());
		assertEquals("desc", skill.description());
		assertEquals("icon", skill.icon());
		assertEquals("PRIMARY_APP_INSTRUCTION", skill.executionStrategy());
		assertFalse(skill.required());
	}

	@Test
	void preferencesAreDelegatedWithoutResolvingAChannelAgent() {
		provider.savePreferences(identity(), List.of("101"));

		verify(promptSkillCatalog).savePromptSkillPreference(any(), eq(List.of("101")));
	}

	private IdentityEnvelope identity() {
		return new IdentityEnvelope("tenant", "1", "org", List.of("org"), List.of("7"), List.of("10"), "corr",
				"ASSISTANT_CONTEXT");
	}

}
