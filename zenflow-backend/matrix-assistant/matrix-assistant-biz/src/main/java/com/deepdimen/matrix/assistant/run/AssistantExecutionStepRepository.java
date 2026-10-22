package com.deepdimen.matrix.assistant.run;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepStatus;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType;

/** Durable step ledger. State changes are compare-and-set and owner-scoped. */
public interface AssistantExecutionStepRepository {

	StepCreateResult createStepIfAbsent(AssistantExecutionStepRecord step);

	Optional<AssistantExecutionStepRecord> findStep(String runId, String stepId);

	List<AssistantExecutionStepRecord> findStepsByRunId(String runId);

	boolean claimReadyStep(String runId, String stepId, String ownerInstanceId, Instant startedAt);

	/** Persist remote acknowledgement while the step is still RUNNING. */
	boolean recordExternalIdentifiersOwned(String runId, String stepId, String ownerInstanceId,
			String externalTaskId, String workflowRunId, Instant observedAt);

	boolean transitionOwnedStep(String runId, String stepId, String ownerInstanceId,
			ExecutionStepStatus expectedStatus, ExecutionStepStatus targetStatus, StepCompletion completion);

	/** Called only after the parent Run lease expired and was atomically requeued. */
	boolean recoverOrphanedStep(String runId, String stepId, ExecutionStepStatus targetStatus,
			StepCompletion completion);

	record StepCreateResult(boolean created, AssistantExecutionStepRecord step) {
	}

	record StepCompletion(String outputDigest, String artifactId, String externalTaskId, String workflowRunId,
			String usageSource, String errorCode, String errorMessageSafe, Instant completedAt) {

		public static StepCompletion empty() {
			return new StepCompletion(null, null, null, null, null, null, null, null);
		}

	}

	record AssistantExecutionStepRecord(String runId, String stepId, ExecutionStepType stepType, String targetId,
			Long targetReleaseId, boolean required, List<String> dependencies, ExecutionStepStatus status,
			String ownerInstanceId, String invocationKey, String inputDigest, String outputDigest, String artifactId,
			String externalTaskId, String workflowRunId, String usageSource, String errorCode, String errorMessageSafe,
			Instant startedAt, Instant completedAt, Instant createdAt, Instant updatedAt) {

		public AssistantExecutionStepRecord {
			dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
			status = status == null ? ExecutionStepStatus.READY : status;
			createdAt = createdAt == null ? Instant.now() : createdAt;
			updatedAt = updatedAt == null ? createdAt : updatedAt;
		}

		public AssistantExecutionStepRecord claimed(String owner, Instant at) {
			return new AssistantExecutionStepRecord(runId, stepId, stepType, targetId, targetReleaseId, required,
					dependencies, ExecutionStepStatus.RUNNING, owner, invocationKey, inputDigest, outputDigest, artifactId,
					externalTaskId, workflowRunId, usageSource, errorCode, errorMessageSafe, at, completedAt, createdAt,
					at);
		}

		public AssistantExecutionStepRecord withExternalIdentifiers(String externalTaskId, String workflowRunId,
				Instant updated) {
			return new AssistantExecutionStepRecord(runId, stepId, stepType, targetId, targetReleaseId, required,
					dependencies, status, ownerInstanceId, invocationKey, inputDigest, outputDigest, artifactId,
					externalTaskId == null || externalTaskId.isBlank() ? this.externalTaskId : externalTaskId,
					workflowRunId == null || workflowRunId.isBlank() ? this.workflowRunId : workflowRunId,
					usageSource, errorCode, errorMessageSafe, startedAt, completedAt, createdAt, updated);
		}

		public AssistantExecutionStepRecord completed(ExecutionStepStatus target, StepCompletion completion,
				Instant updated) {
			StepCompletion value = completion == null ? StepCompletion.empty() : completion;
			return new AssistantExecutionStepRecord(runId, stepId, stepType, targetId, targetReleaseId, required,
					dependencies, target, ownerInstanceId, invocationKey, inputDigest, value.outputDigest(),
					value.artifactId(), value.externalTaskId() == null ? externalTaskId : value.externalTaskId(),
					value.workflowRunId() == null ? workflowRunId : value.workflowRunId(), value.usageSource(), value.errorCode(),
					value.errorMessageSafe(), startedAt, value.completedAt(), createdAt, updated);
		}

		public AssistantExecutionStepRecord recoveredReady(Instant updated) {
			return new AssistantExecutionStepRecord(runId, stepId, stepType, targetId, targetReleaseId, required,
					dependencies, ExecutionStepStatus.READY, null, invocationKey, inputDigest, null, null, null, null,
					null, null, null, null, null, createdAt, updated);
		}

	}

	static void requireLegalTransition(ExecutionStepStatus expectedStatus, ExecutionStepStatus targetStatus) {
		if (expectedStatus == null || !expectedStatus.canTransitionTo(targetStatus)) {
			throw new IllegalArgumentException("ILLEGAL_STEP_TRANSITION");
		}
	}

}
