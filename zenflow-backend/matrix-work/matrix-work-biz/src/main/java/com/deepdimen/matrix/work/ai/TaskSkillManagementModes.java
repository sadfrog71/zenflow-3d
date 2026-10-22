package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.work.entity.WorkTaskPolicyEntity;

/** Modes are written only by binding activation, never by ordinary task-policy saves. */
final class TaskSkillManagementModes {

	private TaskSkillManagementModes() {
	}

	static String requireMode(String value) {
		if (!"LEGACY".equals(value) && !"MANAGED".equals(value))
			throw new IllegalArgumentException("TASK_SKILL_MANAGEMENT_MODE_INVALID");
		return value;
	}

	static void preserveForSave(WorkTaskPolicyEntity input, WorkTaskPolicyEntity stored) {
		String parse = stored.getId() == null ? "LEGACY" : requireMode(stored.getParseSkillManagementMode());
		String dispatch = stored.getId() == null ? "LEGACY" : requireMode(stored.getDispatchSkillManagementMode());
		input.setParseSkillManagementMode(parse);
		input.setDispatchSkillManagementMode(dispatch);
	}

}
