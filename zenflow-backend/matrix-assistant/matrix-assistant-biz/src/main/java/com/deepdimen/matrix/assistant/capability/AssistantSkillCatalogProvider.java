package com.deepdimen.matrix.assistant.capability;

import java.util.List;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSkillCapability;

public interface AssistantSkillCatalogProvider {

	List<AssistantSkillCapability> listSkills(IdentityEnvelope identity);

	void savePreferences(IdentityEnvelope identity, List<String> enabledSkillCodes);

}
