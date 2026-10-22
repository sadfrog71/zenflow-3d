package com.deepdimen.matrix.discover.dify;

import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSkillCapability;
import com.deepdimen.matrix.assistant.capability.AssistantPromptSkillCatalog;
import com.deepdimen.matrix.assistant.capability.AssistantPromptSkillCatalog.PromptSkillDescriptor;
import com.deepdimen.matrix.assistant.capability.AssistantSkillCatalogProvider;

@Primary
@Component
public class DifyAssistantSkillCatalogProvider implements AssistantSkillCatalogProvider {

	private final AssistantPromptSkillCatalog promptSkillCatalog;

	public DifyAssistantSkillCatalogProvider(AssistantPromptSkillCatalog promptSkillCatalog) {
		this.promptSkillCatalog = promptSkillCatalog;
	}

	@Override
	public List<AssistantSkillCapability> listSkills(IdentityEnvelope identity) {
		return promptSkillCatalog.listPromptSkills(identity).stream().map(this::nativeCapability).toList();
	}

	@Override
	public void savePreferences(IdentityEnvelope identity, List<String> enabledSkillCodes) {
		promptSkillCatalog.savePromptSkillPreference(identity, enabledSkillCodes);
	}

	private AssistantSkillCapability nativeCapability(PromptSkillDescriptor skill) {
		return new AssistantSkillCapability(skill.skillId(), skill.displayName(), skill.description(), skill.icon(),
				skill.defaultSelected(), false, "PRIMARY_APP_INSTRUCTION");
	}

}
