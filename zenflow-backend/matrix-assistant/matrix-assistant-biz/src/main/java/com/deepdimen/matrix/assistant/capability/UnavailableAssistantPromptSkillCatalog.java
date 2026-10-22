package com.deepdimen.matrix.assistant.capability;

import java.util.List;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(AssistantPromptSkillCatalog.class)
public class UnavailableAssistantPromptSkillCatalog implements AssistantPromptSkillCatalog {

	@Override
	public List<PromptSkillDescriptor> listPromptSkills(IdentityEnvelope identity) {
		return List.of();
	}

	@Override
	public PromptSkillSnapshot resolvePromptSkill(IdentityEnvelope identity, String skillId) {
		throw new IllegalArgumentException("PROMPT_SKILL_CATALOG_UNAVAILABLE");
	}

	@Override
	public void savePromptSkillPreference(IdentityEnvelope identity, List<String> selectedSkillIds) {
		throw new IllegalArgumentException("PROMPT_SKILL_CATALOG_UNAVAILABLE");
	}

}
