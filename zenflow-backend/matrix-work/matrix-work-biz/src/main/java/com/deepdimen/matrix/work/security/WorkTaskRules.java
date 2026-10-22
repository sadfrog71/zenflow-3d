package com.deepdimen.matrix.work.security;

import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;

/** Task business rules layered on top of the existing platform authorization. */
public final class WorkTaskRules {

	private WorkTaskRules() {
	}

	public static boolean canAssign(Integer actorLevel, Integer targetLevel) {
		return actorLevel != null && targetLevel != null && actorLevel > 0 && targetLevel > 0
				&& actorLevel <= targetLevel;
	}

	public static boolean canExecute(WorkTaskStatus status, String action, boolean executor, boolean acceptor) {
		if (status == null || action == null)
			return false;
		return switch (action) {
			case "start" -> executor && status == WorkTaskStatus.TODO;
			case "progress" -> executor && (status == WorkTaskStatus.IN_PROGRESS || status == WorkTaskStatus.BLOCKED);
			case "block", "submit" -> executor && status == WorkTaskStatus.IN_PROGRESS;
			case "resume" -> executor && status == WorkTaskStatus.BLOCKED;
			case "accept" -> acceptor && status == WorkTaskStatus.WAITING_ACCEPTANCE;
			case "transfer", "children" -> executor && (status == WorkTaskStatus.TODO
					|| status == WorkTaskStatus.IN_PROGRESS || status == WorkTaskStatus.BLOCKED);
			default -> false;
		};
	}

}
