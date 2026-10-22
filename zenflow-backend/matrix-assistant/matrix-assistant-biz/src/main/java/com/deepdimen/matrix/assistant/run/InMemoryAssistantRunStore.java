package com.deepdimen.matrix.assistant.run;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.deepdimen.matrix.assistant.api.AssistantRunContract.DispatchStatus;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepStatus;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisResult;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisArtifact;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionPlan;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RunPhase;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageExchange;
import com.deepdimen.matrix.assistant.session.AssistantSessionRepository;
import com.deepdimen.matrix.assistant.session.AssistantSessionTitles;

@Repository
@ConditionalOnMissingBean(value = { AssistantRunRepository.class, AssistantRunEventRepository.class })
@ConditionalOnProperty(prefix = "matrix.assistant", name = "repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryAssistantRunStore implements AssistantRunRepository, AssistantRunEventRepository,
		AssistantExecutionStepRepository, AssistantAnalysisArtifactRepository {

	private final AssistantSessionRepository sessionRepository;

	private final Map<String, AssistantRunRecord> runs = new ConcurrentHashMap<>();

	private final Map<String, String> idempotency = new ConcurrentHashMap<>();

	private final Map<String, List<RunEventEnvelope>> events = new ConcurrentHashMap<>();

	private final Map<String, AssistantExecutionStepRecord> steps = new ConcurrentHashMap<>();

	private final Map<String, AnalysisArtifact> artifacts = new ConcurrentHashMap<>();

	private final Map<String, String> catalogSnapshots = new ConcurrentHashMap<>();

	public InMemoryAssistantRunStore(AssistantSessionRepository sessionRepository) {
		this.sessionRepository = sessionRepository;
	}

	@Override
	public synchronized StartResult startAtomic(AssistantRunRecord run, AssistantMessage userMessage,
			AssistantMessage responsePlaceholder) {
		String key = key(run.sessionId(), run.clientMessageId());
		String existingRunId = idempotency.get(key);
		if (existingRunId != null) {
			return new StartResult(false, runs.get(existingRunId));
		}
		if (run.input().clarificationSelection() != null) {
			String sourceId = run.input().clarificationSelection().sourceRunId();
			if (runs.values()
				.stream()
				.anyMatch(prior -> run.sessionId().equals(prior.sessionId())
						&& prior.input().clarificationSelection() != null
						&& sourceId.equals(prior.input().clarificationSelection().sourceRunId())))
				throw AssistantManualSelectionGuard.consumed();
			String latest = sessionRepository.findMessages(run.sessionId())
				.stream()
				.max(Comparator.comparingLong(AssistantMessage::sequenceNo))
				.map(AssistantMessage::messageId)
				.orElse(null);
			AssistantManualSelectionGuard.requireAvailable(run, runs.get(sourceId), latest, Instant.now());
		}
		AssistantMessage sequencedUser = withSequence(userMessage, sessionRepository.nextSequence(run.sessionId()));
		AssistantMessage sequencedResponse = withSequence(responsePlaceholder,
				sessionRepository.nextSequence(run.sessionId()));
		sessionRepository.appendMessage(sequencedUser);
		sessionRepository.appendMessage(sequencedResponse);
		sessionRepository.recordRunStarted(run.sessionId(), run.tenantId(), run.input().identity().userId(),
				AssistantSessionTitles.fromFirstMessage(userMessage.content()), run.input().providerIds(),
				run.input().selectedSkillId(),
				run.input().scopeHint() == null ? null : run.input().scopeHint().sourceRoute(), run.createdAt(), run.input().identity().subjectType());
		runs.put(run.runId(), run);
		idempotency.put(key, run.runId());
		events.put(run.runId(),
				new ArrayList<>(List.of(new RunEventEnvelope(1, run.runId(), 1, RunEventType.RUN_ACCEPTED,
						run.createdAt(), run.input().identity().correlationId(),
						Map.of("userMessageId", run.userMessageId(), "responseMessageId", run.responseMessageId())))));
		return new StartResult(true, run);
	}

	@Override
	public AnalysisArtifact saveIfAbsent(AnalysisArtifact artifact) {
		artifacts.putIfAbsent(key(artifact.runId(), artifact.artifactId()), artifact);
		return artifacts.get(key(artifact.runId(), artifact.artifactId()));
	}

	@Override
	public Optional<AnalysisArtifact> findById(String runId, String artifactId) {
		return Optional.ofNullable(artifacts.get(key(runId, artifactId)));
	}

	@Override
	public List<AnalysisArtifact> findByRunId(String runId) {
		return artifacts.values()
			.stream()
			.filter(item -> runId.equals(item.runId()))
			.sorted(Comparator.comparing(AnalysisArtifact::createdAt).thenComparing(AnalysisArtifact::artifactId))
			.toList();
	}

	@Override
	public Optional<AssistantRunRecord> findById(String runId) {
		return Optional.ofNullable(runs.get(runId));
	}

	@Override
	public Optional<AssistantRunRecord> findByClientMessageId(String sessionId, String clientMessageId) {
		return Optional.ofNullable(idempotency.get(key(sessionId, clientMessageId))).map(runs::get);
	}

	@Override
	public List<AssistantRunRecord> findBySessionId(String sessionId, int limit) {
		return runs.values()
			.stream()
			.filter(run -> sessionId.equals(run.sessionId()))
			.sorted(Comparator.comparing(AssistantRunRecord::createdAt))
			.limit(Math.max(1, Math.min(limit, 200)))
			.toList();
	}

	@Override
	public List<AssistantRunRecord> findRecentBySessionId(String sessionId, int limit) {
		return runs.values()
			.stream()
			.filter(run -> sessionId.equals(run.sessionId()))
			.sorted(Comparator.comparing(AssistantRunRecord::createdAt)
				.thenComparing(AssistantRunRecord::runId)
				.reversed())
			.limit(Math.max(1, Math.min(limit, 200)))
			.toList();
	}

	@Override
	public synchronized boolean claimPending(String runId, String ownerInstanceId, Instant leaseExpiresAt) {
		AssistantRunRecord run = runs.get(runId);
		if (run == null || run.dispatchStatus() != DispatchStatus.PENDING || run.status().terminal()) {
			return false;
		}
		runs.put(runId,
				copy(run, RunStatus.CONTEXT_LOADING, DispatchStatus.CLAIMED, run.contextPackId(), run.draftContent(),
						run.lastEventSeq(), ownerInstanceId, leaseExpiresAt, run.modelInvokedAt(), null, null,
						run.startedAt() == null ? Instant.now() : run.startedAt(), null));
		return true;
	}

	@Override
	public synchronized boolean attachContext(String runId, String ownerInstanceId, String contextPackId) {
		AssistantRunRecord run = required(runId);
		if (!owned(run, ownerInstanceId) || run.status() != RunStatus.CONTEXT_LOADING) {
			return false;
		}
		runs.put(runId,
				copy(run, run.status(), run.dispatchStatus(), contextPackId, run.draftContent(), run.lastEventSeq(),
						run.ownerInstanceId(), run.leaseExpiresAt(), run.modelInvokedAt(), run.errorCode(),
						run.errorMessageSafe(), run.startedAt(), run.completedAt()));
		return true;
	}

	@Override
	public synchronized boolean attachPlan(String runId, String ownerInstanceId, String catalogDigest,
			ExecutionPlan executionPlan) {
		AssistantRunRecord run = required(runId);
		if (!owned(run, ownerInstanceId) || run.status() != RunStatus.CONTEXT_LOADING || run.executionPlan() != null) {
			return false;
		}
		runs.put(runId,
				new AssistantRunRecord(run.runId(), run.sessionId(), run.tenantId(), run.ownerUserId(),
						run.clientMessageId(), run.requestHash(), run.userMessageId(), run.responseMessageId(),
						run.status(), run.dispatchStatus(), run.input(), run.contextPackId(), run.draftContent(),
						run.lastEventSeq(), run.ownerInstanceId(), run.leaseExpiresAt(), run.modelInvokedAt(),
						run.errorCode(), run.errorMessageSafe(), run.createdAt(), run.startedAt(), run.completedAt(),
						RunPhase.PLANNING, catalogDigest, executionPlan, run.analysisResult()));
		return true;
	}

	@Override
	public synchronized boolean attachPlan(String runId, String ownerInstanceId, String catalogDigest,
			String catalogSnapshotJson, ExecutionPlan executionPlan) {
		boolean attached = attachPlan(runId, ownerInstanceId, catalogDigest, executionPlan);
		if (attached && catalogSnapshotJson != null) {
			catalogSnapshots.put(runId, catalogSnapshotJson);
		}
		return attached;
	}

	@Override
	public synchronized boolean attachAnalysisResult(String runId, String ownerInstanceId,
			AnalysisResult analysisResult) {
		AssistantRunRecord run = required(runId);
		if (!owned(run, ownerInstanceId) || run.status().terminal() || run.analysisResult() != null) {
			return false;
		}
		runs.put(runId,
				new AssistantRunRecord(run.runId(), run.sessionId(), run.tenantId(), run.ownerUserId(),
						run.clientMessageId(), run.requestHash(), run.userMessageId(), run.responseMessageId(),
						run.status(), run.dispatchStatus(), run.input(), run.contextPackId(), run.draftContent(),
						run.lastEventSeq(), run.ownerInstanceId(), run.leaseExpiresAt(), run.modelInvokedAt(),
						run.errorCode(), run.errorMessageSafe(), run.createdAt(), run.startedAt(), run.completedAt(),
						RunPhase.VALIDATION, run.catalogDigest(), run.executionPlan(), analysisResult));
		return true;
	}

	@Override
	public synchronized boolean markModelInvoked(String runId, String ownerInstanceId, Instant invokedAt) {
		AssistantRunRecord run = required(runId);
		if (!owned(run, ownerInstanceId) || run.status() != RunStatus.CONTEXT_LOADING || run.modelInvokedAt() != null
				|| run.leaseExpiresAt() == null || run.leaseExpiresAt().isBefore(invokedAt)) {
			return false;
		}
		runs.put(runId,
				copy(run, RunStatus.MODEL_STREAMING, run.dispatchStatus(), run.contextPackId(), run.draftContent(),
						run.lastEventSeq(), run.ownerInstanceId(), run.leaseExpiresAt(), invokedAt, run.errorCode(),
						run.errorMessageSafe(), run.startedAt(), run.completedAt()));
		return true;
	}

	@Override
	public synchronized boolean renewLease(String runId, String ownerInstanceId, Instant leaseExpiresAt) {
		AssistantRunRecord run = required(runId);
		if (run.dispatchStatus() != DispatchStatus.CLAIMED
				|| !java.util.Objects.equals(run.ownerInstanceId(), ownerInstanceId) || run.status().terminal()) {
			return false;
		}
		runs.put(runId,
				copy(run, run.status(), run.dispatchStatus(), run.contextPackId(), run.draftContent(),
						run.lastEventSeq(), ownerInstanceId, leaseExpiresAt, run.modelInvokedAt(), run.errorCode(),
						run.errorMessageSafe(), run.startedAt(), run.completedAt()));
		return true;
	}

	@Override
	public synchronized CancelRequestResult requestCancel(String runId, Instant requestedAt) {
		AssistantRunRecord run = required(runId);
		if (run.status().terminal() || run.status() == RunStatus.CANCEL_REQUESTED) {
			return new CancelRequestResult(false, run);
		}
		long seq = run.lastEventSeq() + 1;
		events.computeIfAbsent(runId, ignored -> new ArrayList<>())
			.add(new RunEventEnvelope(1, runId, seq, RunEventType.RUN_CANCEL_REQUESTED, requestedAt,
					run.input().identity().correlationId(), Map.of()));
		AssistantRunRecord cancelled = copy(run, RunStatus.CANCEL_REQUESTED, run.dispatchStatus(), run.contextPackId(),
				run.draftContent(), seq, run.ownerInstanceId(), run.leaseExpiresAt(), run.modelInvokedAt(),
				run.errorCode(), run.errorMessageSafe(), run.startedAt(), run.completedAt());
		runs.put(runId, cancelled);
		return new CancelRequestResult(true, cancelled);
	}

	@Override
	public synchronized boolean cancelAtomic(String runId, String ownerInstanceId, MessageExchange exchange,
			AssistantExecutionTrace trace, Instant completedAt) {
		AssistantRunRecord run = required(runId);
		if (run.status() != RunStatus.CANCEL_REQUESTED || run.dispatchStatus() == DispatchStatus.CLAIMED
				&& !java.util.Objects.equals(run.ownerInstanceId(), ownerInstanceId)) {
			return false;
		}
		sessionRepository.updateMessage(exchange.responseMessage());
		sessionRepository.saveExchange(run.clientMessageId(), exchange);
		long terminalSeq = run.lastEventSeq() + 1;
		events.computeIfAbsent(runId, ignored -> new ArrayList<>())
			.add(new RunEventEnvelope(1, runId, terminalSeq, RunEventType.RUN_CANCELLED, completedAt,
					run.input().identity().correlationId(), Map.of("responseMessageId", run.responseMessageId())));
		runs.put(runId,
				copy(run, RunStatus.CANCELLED, DispatchStatus.FINISHED, run.contextPackId(),
						exchange.responseMessage().content(), terminalSeq, run.ownerInstanceId(), run.leaseExpiresAt(),
						run.modelInvokedAt(), null, null, run.startedAt(), completedAt));
		return true;
	}

	@Override
	public synchronized boolean completeAtomic(String runId, String ownerInstanceId, MessageExchange exchange,
			AssistantExecutionTrace trace, Instant completedAt) {
		return terminal(runId, ownerInstanceId, null, null, exchange, RunStatus.COMPLETED, null, null, completedAt);
	}

	@Override
	public synchronized boolean failAtomic(String runId, String ownerInstanceId, MessageExchange exchange,
			AssistantExecutionTrace trace, String errorCode, String errorMessageSafe, Instant completedAt) {
		return terminal(runId, ownerInstanceId, null, null, exchange, RunStatus.FAILED, errorCode, errorMessageSafe,
				completedAt);
	}

	@Override
	public synchronized boolean failExpiredInvokedAtomic(String runId, Instant observedLeaseExpiresAt,
			Instant recoveryStartedAt, MessageExchange exchange, AssistantExecutionTrace trace, String errorCode,
			String errorMessageSafe, Instant completedAt) {
		return terminal(runId, null, observedLeaseExpiresAt, recoveryStartedAt, exchange, RunStatus.FAILED, errorCode,
				errorMessageSafe, completedAt);
	}

	@Override
	public List<String> findPendingRunIds(int limit) {
		return runs.values()
			.stream()
			.filter(run -> run.dispatchStatus() == DispatchStatus.PENDING)
			.sorted(Comparator.comparing(AssistantRunRecord::createdAt))
			.limit(limit)
			.map(AssistantRunRecord::runId)
			.toList();
	}

	@Override
	public List<AssistantRunRecord> findExpiredClaims(Instant now, int limit) {
		return runs.values()
			.stream()
			.filter(run -> run.dispatchStatus() == DispatchStatus.CLAIMED)
			.filter(run -> run.leaseExpiresAt() != null && run.leaseExpiresAt().isBefore(now))
			.limit(limit)
			.toList();
	}

	@Override
	public synchronized boolean requeueUninvokedClaim(String runId, Instant now) {
		AssistantRunRecord run = required(runId);
		if (run.dispatchStatus() != DispatchStatus.CLAIMED || run.modelInvokedAt() != null
				|| run.leaseExpiresAt() == null || run.leaseExpiresAt().isAfter(now)) {
			return false;
		}
		runs.put(runId, copy(run, RunStatus.ACCEPTED, DispatchStatus.PENDING, run.contextPackId(), run.draftContent(),
				run.lastEventSeq(), null, null, null, null, null, run.startedAt(), null));
		return true;
	}

	@Override
	public synchronized RunEventEnvelope append(String runId, RunEventType type, String correlationId,
			Map<String, Object> data, Instant occurredAt) {
		AssistantRunRecord run = required(runId);
		long seq = run.lastEventSeq() + 1;
		RunEventEnvelope event = new RunEventEnvelope(1, runId, seq, type, occurredAt, correlationId, data);
		events.computeIfAbsent(runId, ignored -> new ArrayList<>()).add(event);
		runs.put(runId,
				copy(run, run.status(), run.dispatchStatus(), run.contextPackId(), run.draftContent(), seq,
						run.ownerInstanceId(), run.leaseExpiresAt(), run.modelInvokedAt(), run.errorCode(),
						run.errorMessageSafe(), run.startedAt(), run.completedAt()));
		return event;
	}

	@Override
	public synchronized Optional<RunEventEnvelope> appendDeltaOwned(String runId, String ownerInstanceId,
			String responseMessageId, String correlationId, String delta, String draftContent, Instant occurredAt) {
		AssistantRunRecord run = required(runId);
		if (!owned(run, ownerInstanceId)
				|| run.status() != RunStatus.MODEL_STREAMING && run.status() != RunStatus.CANCEL_REQUESTED
				|| !java.util.Objects.equals(run.responseMessageId(), responseMessageId)) {
			return Optional.empty();
		}
		AssistantMessage current = sessionRepository.findMessage(run.sessionId(), responseMessageId).orElseThrow();
		sessionRepository.updateMessage(new AssistantMessage(current.messageId(), current.assistantSessionId(),
				current.role(), draftContent, current.status(), current.sequenceNo(), current.contextPackId(),
				current.createdAt(), current.attachments()));
		long seq = run.lastEventSeq() + 1;
		RunEventEnvelope event = new RunEventEnvelope(1, runId, seq, RunEventType.RESPONSE_DELTA, occurredAt,
				correlationId, Map.of("delta", delta));
		events.computeIfAbsent(runId, ignored -> new ArrayList<>()).add(event);
		runs.put(runId,
				copy(run, run.status(), run.dispatchStatus(), run.contextPackId(), draftContent, seq,
						run.ownerInstanceId(), run.leaseExpiresAt(), run.modelInvokedAt(), run.errorCode(),
						run.errorMessageSafe(), run.startedAt(), run.completedAt()));
		return Optional.of(event);
	}

	@Override
	public List<RunEventEnvelope> findAfter(String runId, long afterSeq, int limit) {
		return events.getOrDefault(runId, List.of())
			.stream()
			.filter(event -> event.seq() > afterSeq)
			.limit(limit)
			.toList();
	}

	@Override
	public synchronized int deleteTerminalEventsBefore(Instant cutoff, int limit) {
		int remaining = Math.max(1, limit);
		int deleted = 0;
		for (Map.Entry<String, List<RunEventEnvelope>> entry : events.entrySet()) {
			AssistantRunRecord run = runs.get(entry.getKey());
			if (run == null || !run.status().terminal()) {
				continue;
			}
			List<RunEventEnvelope> retained = new ArrayList<>();
			for (RunEventEnvelope event : entry.getValue()) {
				if (remaining > 0 && event.occurredAt().isBefore(cutoff)) {
					deleted++;
					remaining--;
				}
				else {
					retained.add(event);
				}
			}
			events.put(entry.getKey(), retained);
			if (remaining == 0) {
				break;
			}
		}
		return deleted;
	}

	@Override
	public synchronized StepCreateResult createStepIfAbsent(AssistantExecutionStepRecord step) {
		String key = stepKey(step.runId(), step.stepId());
		AssistantExecutionStepRecord existing = steps.get(key);
		if (existing != null) {
			return new StepCreateResult(false, existing);
		}
		steps.put(key, step);
		return new StepCreateResult(true, step);
	}

	@Override
	public Optional<AssistantExecutionStepRecord> findStep(String runId, String stepId) {
		return Optional.ofNullable(steps.get(stepKey(runId, stepId)));
	}

	@Override
	public List<AssistantExecutionStepRecord> findStepsByRunId(String runId) {
		return steps.values()
			.stream()
			.filter(step -> runId.equals(step.runId()))
			.sorted(Comparator.comparing(AssistantExecutionStepRecord::createdAt)
				.thenComparing(AssistantExecutionStepRecord::stepId))
			.toList();
	}

	@Override
	public synchronized boolean claimReadyStep(String runId, String stepId, String ownerInstanceId, Instant startedAt) {
		AssistantExecutionStepRecord current = steps.get(stepKey(runId, stepId));
		if (current == null || current.status() != ExecutionStepStatus.READY) {
			return false;
		}
		steps.put(stepKey(runId, stepId), current.claimed(ownerInstanceId, startedAt));
		return true;
	}

	@Override
	public synchronized boolean recordExternalIdentifiersOwned(String runId, String stepId, String ownerInstanceId,
			String externalTaskId, String workflowRunId, Instant observedAt) {
		AssistantExecutionStepRecord current = steps.get(stepKey(runId, stepId));
		if (current == null || current.status() != ExecutionStepStatus.RUNNING
				|| !java.util.Objects.equals(current.ownerInstanceId(), ownerInstanceId)) {
			return false;
		}
		steps.put(stepKey(runId, stepId), current.withExternalIdentifiers(externalTaskId, workflowRunId,
				observedAt == null ? Instant.now() : observedAt));
		return true;
	}

	@Override
	public synchronized boolean transitionOwnedStep(String runId, String stepId, String ownerInstanceId,
			ExecutionStepStatus expectedStatus, ExecutionStepStatus targetStatus, StepCompletion completion) {
		AssistantExecutionStepRepository.requireLegalTransition(expectedStatus, targetStatus);
		AssistantExecutionStepRecord current = steps.get(stepKey(runId, stepId));
		if (current == null || current.status() != expectedStatus
				|| !java.util.Objects.equals(current.ownerInstanceId(), ownerInstanceId)) {
			return false;
		}
		Instant updatedAt = completion == null || completion.completedAt() == null ? Instant.now()
				: completion.completedAt();
		steps.put(stepKey(runId, stepId), current.completed(targetStatus, completion, updatedAt));
		return true;
	}

	@Override
	public synchronized boolean recoverOrphanedStep(String runId, String stepId, ExecutionStepStatus targetStatus,
			StepCompletion completion) {
		AssistantExecutionStepRecord current = steps.get(stepKey(runId, stepId));
		if (current == null || current.status() != ExecutionStepStatus.RUNNING
				|| targetStatus != ExecutionStepStatus.READY && !current.status().canTransitionTo(targetStatus)) {
			return false;
		}
		steps.put(stepKey(runId, stepId), targetStatus == ExecutionStepStatus.READY
				? current.recoveredReady(Instant.now()) : current.completed(targetStatus, completion, Instant.now()));
		return true;
	}

	private boolean terminal(String runId, String ownerInstanceId, Instant observedLeaseExpiresAt,
			Instant recoveryStartedAt, MessageExchange exchange, RunStatus status, String errorCode, String safeMessage,
			Instant completedAt) {
		AssistantRunRecord run = required(runId);
		boolean expiredRecovery = observedLeaseExpiresAt != null && recoveryStartedAt != null;
		if (run.status().terminal() || (expiredRecovery ? run.modelInvokedAt() == null
				|| !java.util.Objects.equals(run.leaseExpiresAt(), observedLeaseExpiresAt)
				|| !run.leaseExpiresAt().isBefore(recoveryStartedAt) : !owned(run, ownerInstanceId))) {
			return false;
		}
		sessionRepository.updateMessage(exchange.responseMessage());
		sessionRepository.saveExchange(run.clientMessageId(), exchange);
		long terminalSeq = run.lastEventSeq() + 1;
		RunEventType eventType = status == RunStatus.COMPLETED ? RunEventType.RUN_COMPLETED : RunEventType.RUN_FAILED;
		Map<String, Object> payload = status == RunStatus.COMPLETED
				? Map.of("responseMessageId", run.responseMessageId())
				: Map.of("errorCode", errorCode, "retryable", false);
		events.computeIfAbsent(runId, ignored -> new ArrayList<>())
			.add(new RunEventEnvelope(1, runId, terminalSeq, eventType, completedAt,
					run.input().identity().correlationId(), payload));
		runs.put(runId,
				copy(run, status, DispatchStatus.FINISHED,
						exchange.contextPack() == null ? run.contextPackId() : exchange.contextPack().contextPackId(),
						exchange.responseMessage().content(), terminalSeq, run.ownerInstanceId(), run.leaseExpiresAt(),
						run.modelInvokedAt(), errorCode, safeMessage, run.startedAt(), completedAt));
		return true;
	}

	private AssistantMessage withSequence(AssistantMessage message, long sequence) {
		return new AssistantMessage(message.messageId(), message.assistantSessionId(), message.role(),
				message.content(), message.status(), sequence, message.contextPackId(), message.createdAt(),
				message.attachments());
	}

	private AssistantRunRecord required(String runId) {
		AssistantRunRecord run = runs.get(runId);
		if (run == null) {
			throw new IllegalStateException("Assistant run not found: " + runId);
		}
		return run;
	}

	private AssistantRunRecord copy(AssistantRunRecord run, RunStatus status, DispatchStatus dispatchStatus,
			String contextPackId, String draftContent, long lastEventSeq, String ownerInstanceId,
			Instant leaseExpiresAt, Instant modelInvokedAt, String errorCode, String errorMessageSafe,
			Instant startedAt, Instant completedAt) {
		return new AssistantRunRecord(run.runId(), run.sessionId(), run.tenantId(), run.ownerUserId(),
				run.clientMessageId(), run.requestHash(), run.userMessageId(), run.responseMessageId(), status,
				dispatchStatus, run.input(), contextPackId, draftContent, lastEventSeq, ownerInstanceId, leaseExpiresAt,
				modelInvokedAt, errorCode, errorMessageSafe, run.createdAt(), startedAt, completedAt,
				phaseForStatus(status, run.phase()), run.catalogDigest(), run.executionPlan(), run.analysisResult());
	}

	private RunPhase phaseForStatus(RunStatus status, RunPhase current) {
		if (status == RunStatus.COMPLETED || status == RunStatus.FAILED || status == RunStatus.CANCELLED) {
			return RunPhase.COMPLETED;
		}
		if (status == RunStatus.MODEL_STREAMING) {
			return RunPhase.ANALYSIS;
		}
		if (status == RunStatus.CONTEXT_LOADING && current == null) {
			return RunPhase.PROVIDER_QUERY;
		}
		return current;
	}

	private String key(String sessionId, String clientMessageId) {
		return sessionId + ":" + clientMessageId;
	}

	private String stepKey(String runId, String stepId) {
		return runId + ":" + stepId;
	}

	private boolean owned(AssistantRunRecord run, String ownerInstanceId) {
		return run.dispatchStatus() == DispatchStatus.CLAIMED
				&& java.util.Objects.equals(run.ownerInstanceId(), ownerInstanceId);
	}

}
