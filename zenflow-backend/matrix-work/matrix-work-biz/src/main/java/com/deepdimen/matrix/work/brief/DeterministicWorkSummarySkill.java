package com.deepdimen.matrix.work.brief;

import com.deepdimen.matrix.work.brief.WorkBriefDraft.Item;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.ScheduleFact;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.TaskFact;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.InformationFact;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.MessageFact;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.EmailFact;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DeterministicWorkSummarySkill implements WorkSummarySkill {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("M月d日");

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm");

	@Override
	public String code() {
		return "work-summary";
	}

	@Override
	public String version() {
		return "1.0.0-local";
	}

	@Override
	public WorkBriefDraft execute(WorkBriefSnapshot snapshot) {
		LocalDateTime now = snapshot.generatedAt();
		int pending = snapshot.tasks().size();
		int overdue = (int) snapshot.tasks().stream()
				.filter(task -> task.dueAt() != null && task.dueAt().isBefore(now))
				.count();
		int external = (int) snapshot.tasks().stream()
				.filter(task -> task.sourceType() != null && !"MANUAL".equalsIgnoreCase(task.sourceType()))
				.count();
		int schedules = snapshot.schedules().size();
		int information = snapshot.information().size();
		int messages = snapshot.messages().size();
		int emails = snapshot.emails().size();

		Map<String, Integer> metrics = new LinkedHashMap<>();
		metrics.put("pendingTasks", pending);
		metrics.put("overdueTasks", overdue);
		metrics.put("externalTasks", external);
		metrics.put("upcomingSchedules", schedules);
		metrics.put("informationItems", information);
		metrics.put("unreadMessages", messages);
		metrics.put("recentEmails", emails);

		String summary = pending == 0 && schedules == 0 && information == 0 && messages == 0 && emails == 0
				? "当前没有可摘要的待办、日程、资讯、消息或邮件证据。"
				: "当前有 %d 项未完成任务（%d 项逾期）、%d 个日程、%d 条资讯、%d 条未读消息和 %d 封近期邮件。"
						.formatted(pending, overdue, schedules, information, messages, emails);

		List<Item> focusItems = snapshot.tasks().stream()
				.sorted(taskComparator(now))
				.limit(5)
				.map(task -> taskItem(task, now))
				.toList();
		List<Item> scheduleItems = snapshot.schedules().stream()
				.sorted(Comparator.comparing(ScheduleFact::startAt))
				.limit(5)
				.map(this::scheduleItem)
				.toList();
		List<Item> informationItems = snapshot.information().stream()
				.sorted(Comparator.comparing(InformationFact::publishedAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.limit(5)
				.map(this::informationItem)
				.toList();
		List<Item> messageItems = snapshot.messages().stream()
				.sorted(Comparator.comparing(MessageFact::occurredAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.limit(5)
				.map(this::messageItem)
				.toList();
		List<Item> emailItems = snapshot.emails().stream()
				.sorted(Comparator.comparing(EmailFact::occurredAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.limit(5)
				.map(this::emailItem)
				.toList();
		List<Item> riskItems = snapshot.tasks().stream()
				.filter(task -> "BLOCKED".equals(task.status())
						|| (task.dueAt() != null && task.dueAt().isBefore(now)))
				.sorted(taskComparator(now))
				.limit(5)
				.map(task -> riskItem(task, now))
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		snapshot.messages().stream()
				.filter(message -> message.actionRequired() || "URGENT".equalsIgnoreCase(message.priority()))
				.limit(Math.max(0, 5 - riskItems.size()))
				.map(this::messageRiskItem)
				.forEach(riskItems::add);

		Map<String, Item> citationIndex = new LinkedHashMap<>();
		concat(focusItems, scheduleItems, informationItems, messageItems, emailItems, riskItems)
				.forEach(item -> citationIndex.putIfAbsent(item.refType() + ":" + item.refId(), item));
		String continuity = snapshot.previousSummary() == null || snapshot.previousSummary().isBlank()
				? "首次生成工作简报，后续版本将对比上次简报形成连续工作记忆。"
				: "上次简报摘要：" + limit(snapshot.previousSummary(), 240);
		return new WorkBriefDraft(
				DAY.format(LocalDate.from(now)) + "工作简报",
				summary,
				continuity,
				metrics,
				focusItems,
				scheduleItems,
				informationItems,
				messageItems,
				emailItems,
				riskItems,
				List.copyOf(citationIndex.values()));
	}

	@SafeVarargs
	private final List<Item> concat(List<Item>... groups) {
		List<Item> result = new ArrayList<>();
		for (List<Item> group : groups) {
			result.addAll(group);
		}
		return result;
	}

	private Comparator<TaskFact> taskComparator(LocalDateTime now) {
		return Comparator.comparing((TaskFact task) -> task.dueAt() == null || !task.dueAt().isBefore(now))
				.thenComparing(task -> priorityRank(task.priority()))
				.thenComparing(TaskFact::dueAt, Comparator.nullsLast(Comparator.naturalOrder()));
	}

	private int priorityRank(String priority) {
		return switch (priority == null ? "" : priority.toUpperCase()) {
			case "URGENT" -> 0;
			case "HIGH" -> 1;
			case "NORMAL" -> 2;
			default -> 3;
		};
	}

	private Item taskItem(TaskFact task, LocalDateTime now) {
		String due = task.dueAt() == null ? "无截止时间" : "截止 " + DATE_TIME.format(task.dueAt());
		String overdue = task.dueAt() != null && task.dueAt().isBefore(now) ? "，已逾期" : "";
		return new Item("TASK", task.id(), task.title(), due + overdue + "；状态 " + task.status(),
				task.priority(), task.dueAt());
	}

	private Item riskItem(TaskFact task, LocalDateTime now) {
		String reason = "BLOCKED".equals(task.status()) ? "任务处于阻塞状态" : "任务已超过截止时间";
		return new Item("TASK", task.id(), task.title(), reason, task.priority(), task.dueAt());
	}

	private Item scheduleItem(ScheduleFact schedule) {
		String detail = DATE_TIME.format(schedule.startAt()) + " - " + DATE_TIME.format(schedule.endAt());
		if (schedule.location() != null && !schedule.location().isBlank()) {
			detail += "；" + schedule.location();
		}
		return new Item("SCHEDULE", schedule.id(), schedule.title(), detail, null, schedule.startAt());
	}

	private Item informationItem(InformationFact information) {
		String detail = limit(information.summary() == null ? "" : information.summary(), 240);
		if (information.sourceName() != null && !information.sourceName().isBlank()) {
			detail = information.sourceName() + "；" + detail;
		}
		return new Item("INFORMATION", information.id(), information.title(), detail, null,
				information.publishedAt());
	}

	private Item messageItem(MessageFact message) {
		return new Item("MESSAGE", message.id(), message.title(), limit(message.content() == null ? "" : message.content(), 240),
				message.priority(), message.occurredAt());
	}

	private Item messageRiskItem(MessageFact message) {
		String reason = message.actionRequired() ? "消息需要业务处理" : "高优先级未读消息";
		return new Item("MESSAGE", message.id(), message.title(), reason, message.priority(), message.occurredAt());
	}

	private Item emailItem(EmailFact email) {
		String sender = email.senderDisplay() == null || email.senderDisplay().isBlank() ? "" : email.senderDisplay() + "；";
		return new Item("EMAIL", email.id(), email.title(), sender + limit(email.excerpt() == null ? "" : email.excerpt(), 240),
				null, email.occurredAt());
	}

	private String limit(String value, int max) {
		return value.length() <= max ? value : value.substring(0, max);
	}
}
