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

class ManagedTaskParseRunnerTest {

	private final SkillBindingResolver resolver = mock(SkillBindingResolver.class);

	private final SkillExecutionService executor = mock(SkillExecutionService.class);

	private final TaskSkillResolver sources = mock(TaskSkillResolver.class);

	private final TaskSkillResultStore store = mock(TaskSkillResultStore.class);

	private final ObjectMapper json = new ObjectMapper();

	private final WorkTaskSkillInvocationEntity run = new WorkTaskSkillInvocationEntity();

	private final String output = "{\"tasks\":[{\"title\":\"T\",\"content\":\"C\"}]}";

	private ManagedTaskParseRunner runner;

	private ObjectProvider<SkillExecutionService> executors;

	@BeforeEach
	void setup() {
		executors = provider(executor);
		runner = new ManagedTaskParseRunner(provider(resolver), executors, provider(sources), store, json);
		run.setId(8L);
		run.setTenantId(42L);
		run.setOwnerUserId(3L);
		run.setPolicyId(7L);
		run.setSourcePackageId(11L);
		run.setModelId(77L);
		run.setInputSnapshot("{\"text\":\"original\"}");
		when(store.prepareParse(7L, null, "original")).thenReturn(run);
		when(store.authorizeInput(any(), any(), eq("8"), eq(run.getInputSnapshot())))
			.thenReturn(run.getInputSnapshot());
		when(sources.resolve(42L, 11L))
			.thenReturn(new TaskSkillResolver.TaskSkill(11L, "1", "instructions", "digest", "hash"));
		when(resolver.resolve(any())).thenReturn(resolution("instructions", "77"));
		when(executor.execute(any())).thenReturn(result(RunStatus.SUCCEEDED, "22", output));
		when(store.read(any(), any(), eq("8"), eq("10"), eq("ref"))).thenReturn(output);
	}

	@Test
	void usesAuthorizedFrozenInputAndReturnsPersistedSuggestionsWithConfirmationAndProvenance() throws Exception {
		var preview = runner.execute(7L, null, "original");
		assertEquals(json.readTree(output).get("tasks"), preview.drafts());
		assertTrue(preview.warnings().isArray());
		assertTrue(preview.confirmationRequired());
		assertEquals("11", preview.skillPackageId());
		assertEquals("hash", preview.skillHash());
		assertEquals("8", preview.businessRunId());
		assertEquals("12", preview.skillId());
		assertEquals("22", preview.actualReleaseId());
		assertEquals("10", preview.skillRunId());
		var captured = org.mockito.ArgumentCaptor.forClass(ExecuteRequest.class);
		verify(executor).execute(captured.capture());
		var request = captured.getValue();
		assertEquals(new Consumer("TASK", "7", "TASK_PARSE", "default"), request.consumer());
		assertEquals("42", request.identity().tenantId());
		assertEquals("3", request.identity().subjectId());
		assertEquals("work-task-parse:8", request.idempotencyKey());
		assertEquals("token", request.resolvedSnapshotToken());
		assertEquals(run.getInputSnapshot(), request.inputJson());
		verify(store).read(any(), any(), eq("8"), eq("10"), eq("ref"));
		verify(executor, never()).fallbackAllowed(any(), any());
	}

	@Test
    void missingExecutorDoesNotPrepareAnInvocation() {
        when(executors.getIfAvailable()).thenReturn(null);
        assertThrows(IllegalStateException.class,()->runner.execute(7L,null,"original"));
        verifyZeroInteractions(store,resolver,executor,sources);
    }

	@Test
    void unauthorizedPreparedInputStopsBeforeResolutionAndGeneration() {
        when(store.authorizeInput(any(),any(),any(),any())).thenThrow(new IllegalArgumentException("TASK_POLICY_CHANGED"));
        var error=assertThrows(ManagedTaskParseFailure.class,()->runner.execute(7L,null,"original"));
        assertEquals("8",error.businessRunId()); assertNull(error.skillRunId());
        verifyZeroInteractions(resolver,executor,sources);
    }

	@Test
	void instructionModelAndOriginalPackageDriftStopBeforeGeneration() {
		for (Resolution frozen : List.of(resolution("changed", "77"), resolution("instructions", "78"))) {
			when(resolver.resolve(any())).thenReturn(frozen);
			assertThrows(ManagedTaskParseFailure.class, () -> runner.execute(7L, null, "original"));
		}
		when(resolver.resolve(any())).thenReturn(resolution("instructions", "77"));
		when(sources.resolve(42L, 11L))
			.thenReturn(new TaskSkillResolver.TaskSkill(99L, "1", "instructions", "digest", "hash"));
		assertThrows(ManagedTaskParseFailure.class, () -> runner.execute(7L, null, "original"));
		verifyZeroInteractions(executor);
	}

	@Test
	void unknownRecordedFailurePreservesActualRunWithoutRetryOrFallback() {
		var failure = new SkillExecutionFailure(result(RunStatus.UNKNOWN, "22", null),
				new IllegalStateException("timeout"));
		when(executor.execute(any())).thenThrow(failure);
		var error = assertThrows(ManagedTaskParseFailure.class, () -> runner.execute(7L, null, "original"));
		assertSame(failure, error.getCause());
		assertEquals("22", error.actualReleaseId());
		assertEquals("10", error.skillRunId());
		verify(executor, times(1)).execute(any());
		verify(executor, never()).fallbackAllowed(any(), any());
		verify(store, never()).read(any(), any(), any(), any(), any());
	}

	@Test
	void nonSuccessfulOrUnexpectedReleaseCannotBecomeAPreview() {
		for (RunStatus status : List.of(RunStatus.QUEUED, RunStatus.RUNNING, RunStatus.DEGRADED, RunStatus.FAILED,
				RunStatus.UNKNOWN, RunStatus.CANCELLED)) {
			when(executor.execute(any())).thenReturn(result(status, "22", null));
			assertThrows(ManagedTaskParseFailure.class, () -> runner.execute(7L, null, "original"));
		}
		when(executor.execute(any())).thenReturn(result(RunStatus.SUCCEEDED, "23", output));
		assertThrows(ManagedTaskParseFailure.class, () -> runner.execute(7L, null, "original"));
		verify(store, never()).read(any(), any(), any(), any(), any());
	}

	@Test
    void inaccessibleInvalidOrConflictingPersistedResultCannotBeReturned() {
        when(store.read(any(),any(),any(),any(),any())).thenThrow(new IllegalArgumentException("TASK_POLICY_CHANGED"));
        assertThrows(ManagedTaskParseFailure.class,()->runner.execute(7L,null,"original"));
        doReturn("{}").when(store).read(any(),any(),any(),any(),any());
        assertThrows(ManagedTaskParseFailure.class,()->runner.execute(7L,null,"original"));
        doReturn(output.replace("C","different")).when(store).read(any(),any(),any(),any(),any());
        var error=assertThrows(ManagedTaskParseFailure.class,()->runner.execute(7L,null,"original"));
        assertEquals("TASK_SKILL_RESULT_CONFLICT",error.getMessage()); assertEquals("10",error.skillRunId());
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
