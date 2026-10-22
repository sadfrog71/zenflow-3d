package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.api.service.TaskSkillResolver;
import com.deepdimen.matrix.work.entity.WorkTaskSkillInvocationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ManagedTaskDispatchRunnerTest {

	private final SkillBindingResolver resolver = mock(SkillBindingResolver.class);

	private final SkillExecutionService executor = mock(SkillExecutionService.class);

	private final TaskSkillResolver sources = mock(TaskSkillResolver.class);

	private final TaskSkillResultStore store = mock(TaskSkillResultStore.class);

	private final TaskDispatchSkillInputs inputs = mock(TaskDispatchSkillInputs.class);

	private final ObjectMapper json = new ObjectMapper();

	private final WorkTaskSkillInvocationEntity run = new WorkTaskSkillInvocationEntity();

	private final String output = "{\"assigneeId\":\"4\",\"reason\":\"C\"}";

	private ManagedTaskDispatchRunner runner;

	private ObjectProvider<SkillExecutionService> executors;

	@BeforeEach
	void setup() {
		executors = provider(executor);
		runner = new ManagedTaskDispatchRunner(provider(resolver), executors, provider(sources), store, inputs, json);
		run.setId(8L);
		run.setTenantId(42L);
		run.setOwnerUserId(3L);
		run.setPolicyId(7L);
		run.setSourcePackageId(11L);
		run.setModelId(77L);
		run.setDispatchRunId(6L);
		run.setInputSnapshot("{\"title\":\"T\",\"content\":\"C\",\"candidates\":[{\"userId\":\"4\"}]}");
		when(inputs.prepare()).thenReturn(run);
		when(store.authorizeInput(any(), any(), eq("8"), eq(run.getInputSnapshot())))
			.thenReturn(run.getInputSnapshot());
		when(sources.resolve(42L, 11L))
			.thenReturn(new TaskSkillResolver.TaskSkill(11L, "1", "instructions", "digest", "hash"));
		when(resolver.resolve(any())).thenReturn(resolution("instructions", "77"));
		when(executor.execute(any())).thenReturn(result(RunStatus.SUCCEEDED, "22", output));
		when(store.read(any(), any(), eq("8"), eq("10"), eq("ref"))).thenReturn(output);
	}

	@Test
	void usesAuthorizedFrozenInputAndReturnsPersistedSuggestionAndProvenance() throws Exception {
		var preview = runner.execute();
		assertEquals(4L, preview.assigneeId());
		assertEquals(json.readTree(output), preview.output());
		assertEquals("hash", preview.sourcePackageHash());
		assertEquals("8", preview.businessRunId());
		assertEquals("12", preview.skillId());
		assertEquals("22", preview.actualReleaseId());
		assertEquals("10", preview.skillRunId());
		var captured = org.mockito.ArgumentCaptor.forClass(ExecuteRequest.class);
		verify(executor).execute(captured.capture());
		var request = captured.getValue();
		assertEquals(new Consumer("TASK", "7", "TASK_DISPATCH", "default"), request.consumer());
		assertEquals("42", request.identity().tenantId());
		assertEquals("3", request.identity().subjectId());
		assertEquals("work-task-dispatch:8", request.idempotencyKey());
		assertEquals("token", request.resolvedSnapshotToken());
		assertEquals(run.getInputSnapshot(), request.inputJson());
		verify(store).read(any(), any(), eq("8"), eq("10"), eq("ref"));
		verify(executor, never()).fallbackAllowed(any(), any());
	}

	@Test
    void missingExecutorDoesNotPrepareAnInvocation() {
        when(executors.getIfAvailable()).thenReturn(null);
        assertThrows(IllegalStateException.class,()->runner.execute());
        verifyZeroInteractions(inputs,store,resolver,executor,sources);
    }

	@Test
    void unauthorizedPreparedInputStopsBeforeResolutionAndGeneration() {
        when(store.authorizeInput(any(),any(),any(),any())).thenThrow(new IllegalArgumentException("TASK_POLICY_CHANGED"));
        var error=assertThrows(ManagedTaskDispatchFailure.class,()->runner.execute());
        assertEquals("8",error.businessRunId()); assertNull(error.result());
        verifyZeroInteractions(resolver,executor,sources);
    }

	@Test
	void instructionModelAndOriginalPackageDriftStopBeforeGeneration() {
		for (Resolution frozen : List.of(resolution("changed", "77"), resolution("instructions", "78"))) {
			when(resolver.resolve(any())).thenReturn(frozen);
			assertThrows(ManagedTaskDispatchFailure.class, () -> runner.execute());
		}
		when(resolver.resolve(any())).thenReturn(resolution("instructions", "77"));
		when(sources.resolve(42L, 11L))
			.thenReturn(new TaskSkillResolver.TaskSkill(99L, "1", "instructions", "digest", "hash"));
		assertThrows(ManagedTaskDispatchFailure.class, () -> runner.execute());
		verifyZeroInteractions(executor);
	}

	@Test
	void unknownRecordedFailurePreservesActualRunWithoutRetryOrFallback() {
		var failure = new SkillExecutionFailure(result(RunStatus.UNKNOWN, "22", null),
				new IllegalStateException("timeout"));
		when(executor.execute(any())).thenThrow(failure);
		var error = assertThrows(ManagedTaskDispatchFailure.class, () -> runner.execute());
		assertSame(failure, error.getCause());
		assertEquals("22", error.result().actualReleaseId());
		assertEquals("10", error.result().skillRunId());
		verify(executor, times(1)).execute(any());
		verify(executor, never()).fallbackAllowed(any(), any());
		verify(store, never()).read(any(), any(), any(), any(), any());
	}

	@Test
	void nonSuccessfulOrUnexpectedReleaseCannotBecomeASuggestion() {
		for (RunStatus status : List.of(RunStatus.QUEUED, RunStatus.RUNNING, RunStatus.DEGRADED, RunStatus.FAILED,
				RunStatus.UNKNOWN, RunStatus.CANCELLED)) {
			when(executor.execute(any())).thenReturn(result(status, "22", null));
			assertThrows(ManagedTaskDispatchFailure.class, () -> runner.execute());
		}
		when(executor.execute(any())).thenReturn(result(RunStatus.SUCCEEDED, "23", output));
		assertThrows(ManagedTaskDispatchFailure.class, () -> runner.execute());
		verify(store, never()).read(any(), any(), any(), any(), any());
	}

	@Test
    void inaccessibleInvalidOrConflictingPersistedResultCannotBeReturned() {
        when(store.read(any(),any(),any(),any(),any())).thenThrow(new IllegalArgumentException("TASK_POLICY_CHANGED"));
        assertThrows(ManagedTaskDispatchFailure.class,()->runner.execute());
        doReturn("{}").when(store).read(any(),any(),any(),any(),any());
        assertThrows(ManagedTaskDispatchFailure.class,()->runner.execute());
        doReturn(output.replace("C","different")).when(store).read(any(),any(),any(),any(),any());
        var error=assertThrows(ManagedTaskDispatchFailure.class,()->runner.execute());
        assertEquals("TASK_SKILL_RESULT_CONFLICT",error.getMessage()); assertEquals("10",error.result().skillRunId());
    }

	@Test
	void repeatedWorkerAttemptsReuseBusinessIdentityAndIdempotencyKey() {
		runner.execute();
		runner.execute();
		var captured = org.mockito.ArgumentCaptor.forClass(ExecuteRequest.class);
		verify(executor, times(2)).execute(captured.capture());
		assertEquals(captured.getAllValues().get(0).idempotencyKey(), captured.getAllValues().get(1).idempotencyKey());
		assertEquals("work-task-dispatch:8", captured.getValue().idempotencyKey());
		// Actual model deduplication belongs to the execution index, not this runner.
		verify(inputs, times(2)).prepare();
	}

	@Test
	void explicitNullSuggestionRemainsNull() {
		String unknown = "{\"assigneeId\":null,\"reason\":\"No suitable candidate\"}";
		when(executor.execute(any())).thenReturn(result(RunStatus.SUCCEEDED, "22", unknown));
		when(store.read(any(), any(), any(), any(), any())).thenReturn(unknown);
		assertNull(runner.execute().assigneeId());
	}

	private Resolution resolution(String instructions, String model) {
		return new Resolution(new ResolvedSkillSnapshot("12", "22", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, model, null), "{}", "{}", instructions, List.of(), "{}", List.of()),
				"token", Instant.now().plusSeconds(60));
	}

	private ExecuteResult result(RunStatus status, String release, String value) {
		return new ExecuteResult("10", status, value, "ref", List.of(), release, null, null,
				status == RunStatus.UNKNOWN ? ErrorCode.RESULT_UNKNOWN : null);
	}

	@SuppressWarnings("unchecked")
	private <T> ObjectProvider<T> provider(T value) {
		ObjectProvider<T> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(value);
		return provider;
	}

}
