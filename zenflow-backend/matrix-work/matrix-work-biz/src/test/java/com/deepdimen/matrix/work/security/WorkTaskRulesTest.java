package com.deepdimen.matrix.work.security;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import org.junit.jupiter.api.Test;

class WorkTaskRulesTest {

	@Test
	void rejectsUpwardAndUnconfiguredAssignment() {
		assertTrue(WorkTaskRules.canAssign(2, 2));
		assertTrue(WorkTaskRules.canAssign(2, 3));
		assertFalse(WorkTaskRules.canAssign(3, 2));
		assertFalse(WorkTaskRules.canAssign(null, 3));
		assertFalse(WorkTaskRules.canAssign(1, null));
		assertFalse(WorkTaskRules.canAssign(0, 1));
	}

	@Test
	void keepsAcceptorAndExecutorResponsibilitiesSeparate() {
		assertTrue(WorkTaskRules.canExecute(WorkTaskStatus.TODO, "start", true, false));
		assertFalse(WorkTaskRules.canExecute(WorkTaskStatus.TODO, "start", false, true));
		assertFalse(WorkTaskRules.canExecute(WorkTaskStatus.TODO, "accept", false, true));
		assertTrue(WorkTaskRules.canExecute(WorkTaskStatus.WAITING_ACCEPTANCE, "accept", false, true));
		assertTrue(WorkTaskRules.canExecute(WorkTaskStatus.WAITING_ACCEPTANCE, "accept", true, true));
	}

	@Test
	void rejectsClosedAndInvalidTransitions() {
		assertFalse(WorkTaskRules.canExecute(WorkTaskStatus.DONE, "progress", true, true));
		assertFalse(WorkTaskRules.canExecute(WorkTaskStatus.BLOCKED, "submit", true, false));
		assertTrue(WorkTaskRules.canExecute(WorkTaskStatus.BLOCKED, "resume", true, false));
		assertTrue(WorkTaskRules.canExecute(WorkTaskStatus.IN_PROGRESS, "submit", true, false));
		assertFalse(WorkTaskRules.canExecute(WorkTaskStatus.IN_PROGRESS, "arbitrary", true, true));
	}

}
