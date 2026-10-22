package com.deepdimen.matrix.work.service;

import com.deepdimen.matrix.work.api.entity.InformationPolicy;

final class InformationSkillPolicyState {

	private InformationSkillPolicyState() {
	}

	static void prepareSave(InformationPolicy input, InformationPolicy existing) {
		if (input.getId() == null) {
			input.setSkillManagementMode("LEGACY");
			input.setSkillPolicyVersion(0);
			return;
		}
		if (existing == null || !input.getId().equals(existing.getId()) || existing.getSkillPolicyVersion() == null
				|| existing.getSkillPolicyVersion() < 0 || existing.getSkillPolicyVersion() == Integer.MAX_VALUE
				|| !("LEGACY".equals(existing.getSkillManagementMode())
						|| "MANAGED".equals(existing.getSkillManagementMode())))
			throw new IllegalArgumentException("INFORMATION_POLICY_VERSION_CONFLICT");
		input.setSkillManagementMode(existing.getSkillManagementMode());
		input.setSkillPolicyVersion(existing.getSkillPolicyVersion() + 1);
	}

}
