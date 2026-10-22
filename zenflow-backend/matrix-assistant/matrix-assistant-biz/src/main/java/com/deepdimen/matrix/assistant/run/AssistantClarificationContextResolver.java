package com.deepdimen.matrix.assistant.run;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionPlan;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageRole;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageStatus;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Selects only the immediately preceding frozen message, never a random recent ambiguous
 * run.
 */
public final class AssistantClarificationContextResolver {

	public Optional<ExecutionPlan> resolve(AssistantRunRecord current, List<AssistantRunRecord> recent,
			FrozenCapabilityCatalog catalog, Instant now) {
		if (current.input() == null)
			return Optional.empty();
		var last = current.input().history().stream().max(Comparator.comparingLong(m -> m.sequenceNo())).orElse(null);
		if (last == null || last.role() != MessageRole.ASSISTANT || last.status() != MessageStatus.COMPLETED
				|| !current.sessionId().equals(last.assistantSessionId()))
			return Optional.empty();
		var matches = recent.stream().filter(r -> last.messageId().equals(r.responseMessageId())).toList();
		if (matches.size() != 1)
			return Optional.empty();
		var prior = matches.get(0);
		var plan = prior.executionPlan();
		if (prior.runId().equals(current.runId()) || prior.status() != RunStatus.COMPLETED
				|| !current.sessionId().equals(prior.sessionId()) || !current.tenantId().equals(prior.tenantId())
				|| !current.ownerUserId().equals(prior.ownerUserId()) || prior.input() == null
				|| !Objects.equals(current.input().identity().currentOrgId(), prior.input().identity().currentOrgId())
				|| !Objects.equals(current.input().identity().accessibleOrgIds(),
						prior.input().identity().accessibleOrgIds())
				|| !Objects.equals(current.input().identity().roleIds(), prior.input().identity().roleIds())
				|| !Objects.equals(current.input().identity().deptIds(), prior.input().identity().deptIds())
				|| !Objects.equals(current.input().scopeHint(), prior.input().scopeHint())
				|| !Objects.equals(current.input().selectedSkillId(), prior.input().selectedSkillId())
				|| current.input().effectiveSourceMode() != prior.input().effectiveSourceMode() || plan == null
				|| plan.clarification() == null || !catalog.digest().equals(plan.catalogDigest())
				|| plan.clarification().expiresAtEpochMillis() <= now.toEpochMilli() || prior.completedAt() == null
				|| prior.completedAt().isAfter(current.createdAt()))
			return Optional.empty();
		return Optional.of(plan);
	}

	/**
	 * Conservative follow-up test; fresh disjoint candidates never inherit an old
	 * question.
	 */
	public boolean continues(String question, ExecutionPlan previous, FrozenCapabilityCatalog catalog) {
		if (previous == null || previous.clarification() == null || question == null)
			return false;
		String text = question.strip();
		if (text.matches("^(换个话题|换一个问题|另外|不说这个|重新开始|取消).*"))
			return false;
		if (text.matches("^(都看看|都要|都可以|不确定|不知道|继续|还是都看看|两个都要)[。！？!?\\s]*$"))
			return true;
		if (text.matches("^(那|改为|换成|只查)?(今天|昨天|前天|明天|本周|上周|本月|上月)(呢)?[。！？!?\\s]*$"))
			return true;
		var fresh = new com.deepdimen.matrix.assistant.orchestrator.AssistantCapabilityCandidateRetriever()
			.retrieve(text, catalog, 8);
		return !fresh.isEmpty() && fresh.stream()
			.allMatch(item -> previous.clarification()
				.candidates()
				.stream()
				.anyMatch(candidate -> candidate.targetId().equals(item.id()) && (candidate
					.type() == com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType.DIFY_WORKFLOW) == item
						.workflow()));
	}

}
