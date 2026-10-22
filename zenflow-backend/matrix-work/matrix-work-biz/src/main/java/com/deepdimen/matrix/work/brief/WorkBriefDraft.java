package com.deepdimen.matrix.work.brief;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record WorkBriefDraft(
		String title,
		String executiveSummary,
		String continuityNote,
		Map<String, Integer> metrics,
		List<Item> focusItems,
		List<Item> scheduleItems,
		List<Item> informationItems,
		List<Item> messageItems,
		List<Item> emailItems,
		List<Item> riskItems,
		List<Item> citations) {

	public WorkBriefDraft {
		metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
		focusItems = immutable(focusItems);
		scheduleItems = immutable(scheduleItems);
		informationItems = immutable(informationItems);
		messageItems = immutable(messageItems);
		emailItems = immutable(emailItems);
		riskItems = immutable(riskItems);
		citations = immutable(citations);
	}

	private static List<Item> immutable(List<Item> items) {
		return items == null ? List.of() : List.copyOf(items);
	}

	public record Item(
			String refType,
			Long refId,
			String title,
			String detail,
			String priority,
			LocalDateTime occurredAt) {}
}
