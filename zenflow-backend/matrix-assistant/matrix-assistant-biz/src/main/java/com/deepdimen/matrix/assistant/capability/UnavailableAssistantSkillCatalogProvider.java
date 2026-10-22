package com.deepdimen.matrix.assistant.capability;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSkillCapability;

@Component
@ConditionalOnMissingBean(AssistantSkillCatalogProvider.class)
public class UnavailableAssistantSkillCatalogProvider implements AssistantSkillCatalogProvider {

	@Override
	public List<AssistantSkillCapability> listSkills(IdentityEnvelope identity) {
		return List.of();
	}

	@Override
	public void savePreferences(IdentityEnvelope identity, List<String> enabledSkillCodes) {
		// No catalog is available, so there is no preference state to persist.
	}

}
