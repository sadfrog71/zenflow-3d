package com.deepdimen.matrix.work.brief;

import java.time.LocalDateTime;
import java.util.List;

public record WorkBriefSnapshot(
		Long tenantId,
		Long ownerUserId,
		LocalDateTime generatedAt,
		List<TaskFact> tasks,
		List<ScheduleFact> schedules,
		List<InformationFact> information,
		List<MessageFact> messages,
		List<EmailFact> emails,
		String previousSummary) {

	public record TaskFact(
			Long id,
			String title,
			String description,
			String priority,
			String status,
			LocalDateTime dueAt,
			String sourceType) {}

	public record ScheduleFact(
			Long id,
			String title,
			LocalDateTime startAt,
			LocalDateTime endAt,
			String location,
			String status) {}

	public record InformationFact(
			Long id,
			String contentType,
			String title,
			String summary,
			String sourceName,
			LocalDateTime publishedAt) {}

	public record MessageFact(
			Long id,
			String title,
			String content,
			String sourceType,
			String eventType,
			String priority,
			String targetPath,
			boolean actionRequired,
			LocalDateTime occurredAt) {}

	public record EmailFact(
			Long id,
			Long sourceId,
			String title,
			String excerpt,
			String senderDisplay,
			LocalDateTime occurredAt) {}
}
