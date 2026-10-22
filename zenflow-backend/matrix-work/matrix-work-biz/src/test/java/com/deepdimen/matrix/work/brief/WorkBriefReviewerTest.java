package com.deepdimen.matrix.work.brief;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deepdimen.matrix.work.brief.WorkBriefDraft.Item;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.TaskFact;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkBriefReviewerTest {

	private final WorkBriefReviewer reviewer = new WorkBriefReviewer();

	@Test
	void approvesTraceableDraft() {
		WorkBriefSnapshot snapshot = snapshot();
		Item citation = new Item("TASK", 1L, "任务", "截止今天", "HIGH", snapshot.generatedAt());
		WorkBriefDraft draft = new WorkBriefDraft("今日简报", "1 项任务", "首次", metrics(), List.of(citation),
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of(citation));

		assertTrue(reviewer.review(snapshot, draft).approved());
	}

	@Test
	void rejectsCitationOutsideSnapshot() {
		WorkBriefSnapshot snapshot = snapshot();
		Item hallucinated = new Item("TASK", 999L, "未知任务", "", "HIGH", snapshot.generatedAt());
		WorkBriefDraft draft = new WorkBriefDraft("今日简报", "1 项任务", "首次", metrics(), List.of(hallucinated),
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of(hallucinated));

		assertFalse(reviewer.review(snapshot, draft).approved());
	}

	private WorkBriefSnapshot snapshot() {
		LocalDateTime now = LocalDateTime.of(2026, 8, 5, 9, 0);
		return new WorkBriefSnapshot(10L, 20L, now,
				List.of(new TaskFact(1L, "任务", "", "HIGH", "TODO", now.plusDays(1), "MANUAL")),
				List.of(), List.of(), List.of(), List.of(), null);
	}

	private Map<String, Integer> metrics() {
		return Map.of("pendingTasks", 1, "upcomingSchedules", 0, "overdueTasks", 0, "externalTasks", 0,
				"informationItems", 0, "unreadMessages", 0, "recentEmails", 0);
	}
}
