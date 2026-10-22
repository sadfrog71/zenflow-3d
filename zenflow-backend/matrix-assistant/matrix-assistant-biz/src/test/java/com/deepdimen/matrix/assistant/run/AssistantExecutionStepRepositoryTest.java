package com.deepdimen.matrix.assistant.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepStatus;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType;
import com.deepdimen.matrix.assistant.session.InMemoryAssistantSessionRepository;

class AssistantExecutionStepRepositoryTest {

	private final InMemoryAssistantRunStore store = new InMemoryAssistantRunStore(
			new InMemoryAssistantSessionRepository());

	@Test
	void duplicateStepKeepsTheOriginalInvocationKey() {
		var first = step("invocation-1");
		var duplicate = step("invocation-2");

		var created = store.createStepIfAbsent(first);
		var existing = store.createStepIfAbsent(duplicate);

		assertTrue(created.created());
		assertFalse(existing.created());
		assertEquals("invocation-1", existing.step().invocationKey());
		assertEquals(1, store.findStepsByRunId("run-1").size());
	}

	@Test
	void ownedTransitionsRejectStaleOwnerAndUnknownReplay() {
		store.createStepIfAbsent(step("invocation-1"));
		assertTrue(store.claimReadyStep("run-1", "step-1", "owner-1", Instant.EPOCH));
		assertFalse(store.transitionOwnedStep("run-1", "step-1", "owner-2", ExecutionStepStatus.RUNNING,
				ExecutionStepStatus.UNKNOWN, new AssistantExecutionStepRepository.StepCompletion(null, null, null, null,
						null, "EXECUTOR_LOST", "执行状态未知", Instant.EPOCH.plusSeconds(1))));
		assertTrue(store.transitionOwnedStep("run-1", "step-1", "owner-1", ExecutionStepStatus.RUNNING,
				ExecutionStepStatus.UNKNOWN, new AssistantExecutionStepRepository.StepCompletion(null, null, null, null,
						null, "EXECUTOR_LOST", "执行状态未知", Instant.EPOCH.plusSeconds(1))));

		assertThrows(IllegalArgumentException.class,
				() -> store.transitionOwnedStep("run-1", "step-1", "owner-1", ExecutionStepStatus.UNKNOWN,
						ExecutionStepStatus.READY, AssistantExecutionStepRepository.StepCompletion.empty()));
	}

	@Test
	void externalIdentifiersArePersistedBeforeCompletionAndNeverCleared() {
		store.createStepIfAbsent(step("invocation-1"));
		assertTrue(store.claimReadyStep("run-1", "step-1", "owner-1", Instant.EPOCH));
		assertTrue(store.recordExternalIdentifiersOwned("run-1", "step-1", "owner-1", "task-1", "workflow-1",
				Instant.EPOCH.plusMillis(100)));
		assertTrue(store.transitionOwnedStep("run-1", "step-1", "owner-1", ExecutionStepStatus.RUNNING,
				ExecutionStepStatus.SUCCEEDED, new AssistantExecutionStepRepository.StepCompletion("output", "artifact",
						null, null, "DIFY_RESPONSE", null, null, Instant.EPOCH.plusSeconds(1))));

		var completed = store.findStep("run-1", "step-1").orElseThrow();
		assertEquals("task-1", completed.externalTaskId());
		assertEquals("workflow-1", completed.workflowRunId());
	}

	private AssistantExecutionStepRepository.AssistantExecutionStepRecord step(String invocationKey) {
		return new AssistantExecutionStepRepository.AssistantExecutionStepRecord("run-1", "step-1",
				ExecutionStepType.DIFY_WORKFLOW, "workflow-1", 9L, true, List.of(), ExecutionStepStatus.READY, null,
				invocationKey, "input-digest", null, null, null, null, null, null, null, null, null, Instant.EPOCH,
				Instant.EPOCH);
	}

}
