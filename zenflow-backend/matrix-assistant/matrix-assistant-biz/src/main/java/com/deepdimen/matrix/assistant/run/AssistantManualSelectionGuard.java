package com.deepdimen.matrix.assistant.run;

import java.time.Instant;
import java.util.Objects;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Must be called while source selection consumption is serialized by the store. */
public final class AssistantManualSelectionGuard {

	private AssistantManualSelectionGuard() {
	}

	public static void requireAvailable(AssistantRunRecord request, AssistantRunRecord source, String latestMessageId,
			Instant now) {
		var selection = request.input().clarificationSelection();
		if (source == null || selection == null || !selection.sourceRunId().equals(source.runId())
				|| !Objects.equals(request.sessionId(), source.sessionId())
				|| !Objects.equals(request.tenantId(), source.tenantId())
				|| !Objects.equals(request.ownerUserId(), source.ownerUserId())
				|| source.status() != RunStatus.COMPLETED || source.executionPlan() == null
				|| source.executionPlan().clarification() == null
				|| !source.executionPlan().clarification().manualSelectionRequired()
				|| !Objects.equals(selection.planDigest(), source.executionPlan().planDigest())
				|| source.executionPlan().clarification().expiresAtEpochMillis() <= now.toEpochMilli()
				|| !Objects.equals(source.responseMessageId(), latestMessageId))
			throw consumed();
	}

	public static ResponseStatusException consumed() {
		return new ResponseStatusException(HttpStatus.CONFLICT, "CLARIFICATION_SELECTION_STALE_OR_CONSUMED");
	}

}
