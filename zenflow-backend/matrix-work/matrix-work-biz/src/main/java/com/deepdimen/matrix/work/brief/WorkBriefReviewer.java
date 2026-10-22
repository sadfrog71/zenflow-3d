package com.deepdimen.matrix.work.brief;

import com.deepdimen.matrix.work.brief.WorkBriefDraft.Item;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WorkBriefReviewer {

	public ReviewResult review(WorkBriefSnapshot snapshot, WorkBriefDraft draft) {
		if (draft == null || blank(draft.title()) || blank(draft.executiveSummary())) {
			return ReviewResult.rejected("简报标题或摘要为空");
		}
		if (draft.metrics() == null
				|| draft.metrics().getOrDefault("pendingTasks", -1) != snapshot.tasks().size()
				|| draft.metrics().getOrDefault("upcomingSchedules", -1) != snapshot.schedules().size()
				|| draft.metrics().getOrDefault("informationItems", -1) != snapshot.information().size()
				|| draft.metrics().getOrDefault("unreadMessages", -1) != snapshot.messages().size()
				|| draft.metrics().getOrDefault("recentEmails", -1) != snapshot.emails().size()) {
			return ReviewResult.rejected("简报统计与输入快照不一致");
		}
		Set<String> allowed = new HashSet<>();
		snapshot.tasks().forEach(task -> allowed.add("TASK:" + task.id()));
		snapshot.schedules().forEach(schedule -> allowed.add("SCHEDULE:" + schedule.id()));
		snapshot.information().forEach(item -> allowed.add("INFORMATION:" + item.id()));
		snapshot.messages().forEach(item -> allowed.add("MESSAGE:" + item.id()));
		snapshot.emails().forEach(item -> allowed.add("EMAIL:" + item.id()));
		for (Item item : concat(draft.focusItems(), draft.scheduleItems(), draft.informationItems(),
				draft.messageItems(), draft.emailItems(), draft.riskItems(), draft.citations())) {
			if (item == null || item.refId() == null || !allowed.contains(item.refType() + ":" + item.refId())) {
				return ReviewResult.rejected("简报包含无法追溯到当前用户工作快照的引用");
			}
		}
		return new ReviewResult(true, "APPROVED", "统计和引用已通过确定性复核");
	}

	@SafeVarargs
	private final List<Item> concat(List<Item>... groups) {
		return java.util.Arrays.stream(groups).filter(java.util.Objects::nonNull).flatMap(List::stream).toList();
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	public record ReviewResult(boolean approved, String status, String reason) {

		static ReviewResult rejected(String reason) {
			return new ReviewResult(false, "REJECTED", reason);
		}
	}
}
