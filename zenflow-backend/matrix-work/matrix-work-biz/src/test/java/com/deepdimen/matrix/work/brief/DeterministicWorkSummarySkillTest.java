package com.deepdimen.matrix.work.brief;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.ScheduleFact;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.TaskFact;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicWorkSummarySkillTest {

	private final DeterministicWorkSummarySkill skill = new DeterministicWorkSummarySkill();

	@Test
	void summarizesVisibleTasksSchedulesAndPreviousMemory() {
		LocalDateTime now = LocalDateTime.of(2026, 8, 5, 9, 0);
		WorkBriefSnapshot snapshot = new WorkBriefSnapshot(10L, 20L, now,
				List.of(
						new TaskFact(1L, "逾期任务", "", "URGENT", "TODO", now.minusHours(2), "MANUAL"),
						new TaskFact(2L, "同步任务", "", "HIGH", "IN_PROGRESS", now.plusDays(1), "NC")),
				List.of(new ScheduleFact(3L, "项目会", now.plusHours(2), now.plusHours(3), "会议室", "CONFIRMED")),
				List.of(), List.of(), List.of(),
				"上期有一项风险");

		WorkBriefDraft draft = skill.execute(snapshot);

		assertEquals(2, draft.metrics().get("pendingTasks"));
		assertEquals(1, draft.metrics().get("overdueTasks"));
		assertEquals(1, draft.metrics().get("externalTasks"));
		assertEquals(1, draft.metrics().get("upcomingSchedules"));
		assertEquals(3, draft.citations().size());
		assertTrue(draft.executiveSummary().contains("2 项未完成任务"));
		assertTrue(draft.continuityNote().contains("上期有一项风险"));
	}
}
