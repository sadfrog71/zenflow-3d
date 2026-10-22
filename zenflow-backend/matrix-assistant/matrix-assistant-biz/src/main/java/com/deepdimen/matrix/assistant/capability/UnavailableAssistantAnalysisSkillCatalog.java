package com.deepdimen.matrix.assistant.capability;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(AssistantAnalysisSkillCatalog.class)
public class UnavailableAssistantAnalysisSkillCatalog implements AssistantAnalysisSkillCatalog {

	@Override
	public AnalysisSkillSnapshot resolveDefault(IdentityEnvelope identity) {
		throw new IllegalArgumentException("ASSISTANT_ANALYSIS_SKILL_NOT_PUBLISHED");
	}

}
