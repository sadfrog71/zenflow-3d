package com.deepdimen.matrix.assistant.run;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisResult;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionPlan;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageExchange;

/**
 * Persistence port whose start and terminal methods are atomic transaction boundaries.
 */
public interface AssistantRunRepository {

	StartResult startAtomic(AssistantRunRecord run, AssistantMessage userMessage, AssistantMessage responsePlaceholder);

	Optional<AssistantRunRecord> findById(String runId);

	Optional<AssistantRunRecord> findByClientMessageId(String sessionId, String clientMessageId);

	List<AssistantRunRecord> findBySessionId(String sessionId, int limit);

	List<AssistantRunRecord> findRecentBySessionId(String sessionId, int limit);

	boolean claimPending(String runId, String ownerInstanceId, Instant leaseExpiresAt);

	boolean attachContext(String runId, String ownerInstanceId, String contextPackId);

	boolean attachPlan(String runId, String ownerInstanceId, String catalogDigest, ExecutionPlan executionPlan);

	default boolean attachPlan(String runId, String ownerInstanceId, String catalogDigest, String catalogSnapshotJson,
			ExecutionPlan executionPlan) {
		return attachPlan(runId, ownerInstanceId, catalogDigest, executionPlan);
	}

	boolean attachAnalysisResult(String runId, String ownerInstanceId, AnalysisResult analysisResult);

	boolean markModelInvoked(String runId, String ownerInstanceId, Instant invokedAt);

	boolean renewLease(String runId, String ownerInstanceId, Instant leaseExpiresAt);

	CancelRequestResult requestCancel(String runId, Instant requestedAt);

	boolean cancelAtomic(String runId, String ownerInstanceId, MessageExchange exchange, AssistantExecutionTrace trace,
			Instant completedAt);

	boolean completeAtomic(String runId, String ownerInstanceId, MessageExchange exchange,
			AssistantExecutionTrace trace, Instant completedAt);

	boolean failAtomic(String runId, String ownerInstanceId, MessageExchange exchange, AssistantExecutionTrace trace,
			String errorCode, String errorMessageSafe, Instant completedAt);

	boolean failExpiredInvokedAtomic(String runId, Instant observedLeaseExpiresAt, Instant recoveryStartedAt,
			MessageExchange exchange, AssistantExecutionTrace trace, String errorCode, String errorMessageSafe,
			Instant completedAt);

	List<String> findPendingRunIds(int limit);

	List<AssistantRunRecord> findExpiredClaims(Instant now, int limit);

	boolean requeueUninvokedClaim(String runId, Instant now);

	record StartResult(boolean created, AssistantRunRecord run) {
	}

	record CancelRequestResult(boolean newlyRequested, AssistantRunRecord run) {
	}

}
