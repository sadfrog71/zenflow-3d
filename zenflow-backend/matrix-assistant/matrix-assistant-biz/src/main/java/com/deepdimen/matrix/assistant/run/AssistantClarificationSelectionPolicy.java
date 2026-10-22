package com.deepdimen.matrix.assistant.run;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.*;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.*;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;
import com.deepdimen.matrix.assistant.orchestrator.AssistantExecutionPlanValidator;
import java.time.Instant;
import java.util.*;

/**
 * Validates a choice against its original frozen plan and the current authorized catalog.
 */
public final class AssistantClarificationSelectionPolicy {

	private final AssistantExecutionPlanValidator validator;

	public AssistantClarificationSelectionPolicy(AssistantExecutionPlanValidator validator) {
		this.validator = validator;
	}

	public ClarificationCandidate validate(IdentityEnvelope identity, String sessionId, List<AssistantMessage> history,
			AssistantRunRecord source, ClarificationSelection selection, FrozenCapabilityCatalog catalog, Instant now) {
		if (source == null || selection == null || identity == null || !Objects.equals(sessionId, source.sessionId())
				|| !Objects.equals(identity.tenantId(), source.tenantId())
				|| !Objects.equals(identity.userId(), source.ownerUserId()) || !Objects.equals(identity.subjectType(), source.input().identity().subjectType())
				|| !source.runId().equals(selection.sourceRunId()) || source.status() != RunStatus.COMPLETED)
			throw new IllegalArgumentException("CLARIFICATION_SELECTION_SOURCE_INVALID");
		var input = source.input();
		if (input == null || !Objects.equals(identity.currentOrgId(), input.identity().currentOrgId())
				|| !Objects.equals(identity.accessibleOrgIds(), input.identity().accessibleOrgIds())
				|| !Objects.equals(identity.roleIds(), input.identity().roleIds())
				|| !Objects.equals(identity.deptIds(), input.identity().deptIds()))
			throw new IllegalArgumentException("CLARIFICATION_SELECTION_IDENTITY_CHANGED");
		var plan = source.executionPlan();
		if (plan == null || plan.clarification() == null || !plan.clarification().manualSelectionRequired()
				|| !selection.planDigest().equals(plan.planDigest())
				|| plan.clarification().expiresAtEpochMillis() <= now.toEpochMilli())
			throw new IllegalArgumentException("CLARIFICATION_SELECTION_STALE");
		var last = history.stream().max(Comparator.comparingLong(AssistantMessage::sequenceNo)).orElse(null);
		if (last == null || !Objects.equals(sessionId, last.assistantSessionId())
				|| last.role() != MessageRole.ASSISTANT || last.status() != MessageStatus.COMPLETED
				|| !source.responseMessageId().equals(last.messageId()))
			throw new IllegalArgumentException("CLARIFICATION_SELECTION_NOT_LATEST");
		var checked = validator.validateAndDigest(plan, catalog);
		if (!Objects.equals(checked.planDigest(), plan.planDigest()))
			throw new IllegalArgumentException("CLARIFICATION_SELECTION_PLAN_CHANGED");
		return plan.clarification()
			.candidates()
			.stream()
			.filter(c -> c.type() == selection.type() && c.targetId().equals(selection.targetId())
					&& Objects.equals(c.releaseId(), selection.releaseId()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("CLARIFICATION_SELECTION_NOT_OFFERED"));
	}

}
