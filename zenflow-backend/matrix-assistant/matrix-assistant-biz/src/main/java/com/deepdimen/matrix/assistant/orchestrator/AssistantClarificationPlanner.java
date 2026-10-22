package com.deepdimen.matrix.assistant.orchestrator;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.*;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds non-executing questions from authorized catalog facts, never model-generated
 * prose.
 */
public final class AssistantClarificationPlanner {

	private final AssistantExecutionPlanValidator validator;

	private final AssistantCapabilityCandidateRetriever retriever = new AssistantCapabilityCandidateRetriever();

	public AssistantClarificationPlanner(AssistantExecutionPlanValidator validator) {
		this.validator = validator;
	}

	public Optional<ExecutionPlan> create(String runId, String question, FrozenCapabilityCatalog catalog,
			ExecutionPlan previous, Instant now) {
		if (runId == null || runId.isBlank() || now == null)
			throw new IllegalArgumentException("CLARIFICATION_INPUT_INVALID");
		ClarificationState prior = null;
		if (previous != null) {
			// Identity/session/message adjacency must be checked by the caller before
			// passing previous.
			validator.validateAndDigest(previous, catalog);
			prior = previous.clarification();
			if (prior == null || prior.expiresAtEpochMillis() <= now.toEpochMilli())
				throw new IllegalArgumentException("CLARIFICATION_CONTEXT_EXPIRED");
		}
		List<ClarificationCandidate> candidates = prior != null ? prior.candidates()
				: retriever.retrieve(question, catalog, 8).stream().map(c -> {
					if (c.workflow()) {
						var w = catalog.workflows()
							.stream()
							.filter(item -> item.capabilityId().equals(c.id()))
							.findFirst()
							.orElseThrow();
						return new ClarificationCandidate(ExecutionStepType.DIFY_WORKFLOW, w.capabilityId(),
								w.releaseId(), w.displayName());
					}
					var p = catalog.providers()
						.stream()
						.filter(item -> item.providerCode().equals(c.id()))
						.findFirst()
						.orElseThrow();
					return new ClarificationCandidate(ExecutionStepType.MATRIX_PROVIDER, p.providerCode(), null,
							p.displayName());
				}).toList();
		if (candidates.isEmpty())
			return Optional.empty();
		int count = prior == null ? 1 : 2;
		String choices = candidates.stream()
			.map(c -> display(c.displayName()))
			.distinct()
			.collect(java.util.stream.Collectors.joining("、"));
		boolean duplicateNames = candidates.stream()
			.map(ClarificationCandidate::displayName)
			.distinct()
			.count() < candidates.size();
		String text = count == 2 ? "仍无法确定所需能力，请从候选列表中选择。"
				: duplicateNames ? "存在用途相近的同名能力，请补充业务对象、区域或指标以便区分。" : candidates.size() == 1
						? "是否需要使用「" + choices + "」？请补充要查询的对象与时间范围。" : "需要使用哪项能力：" + choices + "？请补充业务对象与时间范围。";
		var state = new ClarificationState(prior == null ? runId : prior.originRunId(), count, count == 2, text,
				candidates, prior == null ? now.plusSeconds(600).toEpochMilli() : prior.expiresAtEpochMillis());
		var route = new RouteDecision(ConversationIntent.AMBIGUOUS, ExecutionMode.CLARIFICATION, List.of(), List.of(),
				prior == null ? RouteTriggerType.PLANNER : RouteTriggerType.CONTEXT, null, null, true, prior != null,
				"CLARIFICATION_REQUIRED");
		return Optional.of(validator.validateAndDigest(new ExecutionPlan(null, UUID.randomUUID().toString(), runId,
				SourceMode.AUTO, PlanningDisposition.AMBIGUOUS, List.of(), null, List.of("CLARIFICATION_REQUIRED"),
				catalog.digest(), null, now, route, state), catalog));
	}

	private String display(String text) {
		// Display prose only; authoritative full name stays in the candidate snapshot.
		if (text == null)
			return "未命名能力";
		String clean = text.replaceAll("[\\p{Cntrl}]", " ").replaceAll("[<>]", "").strip();
		return clean.substring(0, Math.min(clean.length(), 40));
	}

}
