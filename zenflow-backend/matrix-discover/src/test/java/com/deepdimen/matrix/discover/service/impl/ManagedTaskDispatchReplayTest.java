package com.deepdimen.matrix.discover.service.impl;

import com.deepdimen.matrix.discover.entity.AiSkillRunEntity;
import com.deepdimen.matrix.discover.mapper.AiSkillRunMapper;
import com.deepdimen.matrix.discover.skill.*;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.BeanUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * Real execution service and run index with an in-memory mapper fixture, not a database
 * test.
 */
class ManagedTaskDispatchReplayTest {

	final ObjectMapper json = new ObjectMapper();

	final AiSkillRunMapper mapper = mock(AiSkillRunMapper.class);

	final SkillResolutionTokenStore tokens = mock(SkillResolutionTokenStore.class);

	final ManagedSkillBindingResolver resolver = mock(ManagedSkillBindingResolver.class);

	final ManagedPromptSkillExecutor prompt = mock(ManagedPromptSkillExecutor.class);

	final SkillResultStore results = mock(SkillResultStore.class);

	final Map<String, AiSkillRunEntity> rows = new HashMap<>();

	final Identity identity = new Identity("42", "3", "3", "work-task-dispatch:8");

	final Consumer consumer = new Consumer("TASK", "7", "TASK_DISPATCH", "default");

	final String input = "{\"title\":\"Task\",\"candidates\":[{\"userId\":\"4\"}]}";

	final String output = "{\"assigneeId\":\"4\",\"reason\":\"Suitable\"}";

	final ExecuteRequest request = new ExecuteRequest(identity, consumer, "token", input, null, "work-task-dispatch:8",
			null);

	ManagedSkillExecutionService service;

	@BeforeEach
	void setup() {
		var snapshot = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, "77", null), "{\"type\":\"object\"}", "{}", "frozen", List.of(), "{}",
				List.of());
		when(tokens.require("token", identity, consumer)).thenReturn(new SkillResolutionTokenStore.StoredResolution(
				identity, consumer, "8", "{}", snapshot, Long.MAX_VALUE));
		when(results.consumerType()).thenReturn("TASK");
		when(results.supportsConsumer(consumer)).thenReturn(true);
		when(results.authorizeInput(any(), any(), any(), any())).thenAnswer(call -> call.getArgument(3));
		when(prompt.available(any(), any())).thenReturn(true);
		when(results.save(any(), any(), any(), any(), eq(output))).thenReturn("ref");
		when(results.read(any(), any(), any(), any(), eq("ref"))).thenReturn(output);
		when(mapper.reserve(any())).thenAnswer(call -> {
			AiSkillRunEntity row = call.getArgument(0);
			row.setStatus("QUEUED");
			row.setRevision(0L);
			rows.putIfAbsent(row.getIdempotencyScopeDigest(), copy(row));
			return 1;
		});
		when(mapper.findByScope(any(), any())).thenAnswer(call -> copy(rows.get(call.getArgument(1))));
		when(mapper.claim(any(), any(), any())).thenAnswer(call -> {
			var row = rows.values()
				.stream()
				.filter(r -> r.getId().equals(call.getArgument(1)))
				.findFirst()
				.orElseThrow();
			if (!row.getStatus().equals("QUEUED") || !row.getRevision().equals(call.getArgument(2)))
				return 0;
			row.setStatus("RUNNING");
			row.setRevision(row.getRevision() + 1);
			return 1;
		});
		when(mapper.finish(any(), any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
			var row = rows.values()
				.stream()
				.filter(r -> r.getId().equals(call.getArgument(1)))
				.findFirst()
				.orElseThrow();
			if (!row.getStatus().equals("RUNNING") || !row.getRevision().equals(call.getArgument(2)))
				return 0;
			row.setStatus(call.getArgument(3));
			row.setResultRef(call.getArgument(4));
			row.setErrorCode(call.getArgument(5));
			row.setRevision(row.getRevision() + 1);
			return 1;
		});
		doAnswer(call -> {
			call.getArgument(5, Runnable.class).run();
			return new ManagedPromptSkillExecutor.Result(output, "77");
		}).when(prompt).execute(any(), any(), any(), any(), any(), any());
		service = new ManagedSkillExecutionService(tokens, resolver, new ManagedSkillRunIndex(mapper, json), prompt,
				List.of(results), json);
	}

	@Test
	void successfulReplayUsesSameRunAndStoredResultWithoutAnotherModelCall() {
		var first = service.execute(request);
		var second = service.execute(request);
		assertEquals(RunStatus.SUCCEEDED, second.status());
		assertEquals(first.skillRunId(), second.skillRunId());
		assertEquals(output, second.resultJson());
		assertEquals(1, rows.size());
		verify(prompt, times(1)).execute(any(), any(), any(), any(), any(), any());
		verify(results, times(1)).save(any(), any(), any(), any(), any());
		verify(results).read(identity, consumer, "8", first.skillRunId(), "ref");
	}

	@Test
	void unknownOutcomeRemainsUnknownOnRepeatedExecution() {
		doAnswer(call -> {
			call.getArgument(5, Runnable.class).run();
			throw new IllegalStateException("lost connection");
		}).when(prompt).execute(any(), any(), any(), any(), any(), any());
		var first = assertThrows(SkillExecutionFailure.class, () -> service.execute(request));
		var second = service.execute(request);
		assertEquals(RunStatus.UNKNOWN, second.status());
		assertEquals(first.result().skillRunId(), second.skillRunId());
		verify(prompt, times(1)).execute(any(), any(), any(), any(), any(), any());
		verify(results, never()).save(any(), any(), any(), any(), any());
	}

	@Test
	void overlappingAttemptObservesRunningWithoutReenteringModel() {
		doAnswer(call -> {
			call.getArgument(5, Runnable.class).run();
			assertEquals(RunStatus.RUNNING, service.execute(request).status());
			return new ManagedPromptSkillExecutor.Result(output, "77");
		}).when(prompt).execute(any(), any(), any(), any(), any(), any());
		assertEquals(RunStatus.SUCCEEDED, service.execute(request).status());
		verify(prompt, times(1)).execute(any(), any(), any(), any(), any(), any());
	}

	@Test
	void sameKeyWithChangedInputConflictsBeforeCallingModelAgain() {
		service.execute(request);
		var changed = new ExecuteRequest(identity, consumer, "token", input.replace("Task", "Changed"), null,
				request.idempotencyKey(), null);
		assertEquals("IDEMPOTENCY_CONFLICT",
				assertThrows(IllegalArgumentException.class, () -> service.execute(changed)).getMessage());
		verify(prompt, times(1)).execute(any(), any(), any(), any(), any(), any());
	}

	@Test
	void replayStillRequiresCurrentAuthorization() {
		service.execute(request);
		doThrow(new IllegalArgumentException("LEASE_LOST")).when(resolver).revalidate(any());
		assertThrows(IllegalArgumentException.class, () -> service.execute(request));
		verify(results, never()).read(any(), any(), any(), any(), any());
		verify(prompt, times(1)).execute(any(), any(), any(), any(), any(), any());
	}

	@Test
	void terminalPersistenceFailureLeavesRunningAndCannotReplayExternalAction() {
		doReturn(0).when(mapper).finish(any(), any(), any(), any(), any(), any(), any(), any());
		var failure = assertThrows(IllegalStateException.class, () -> service.execute(request));
		assertFalse(failure instanceof SkillExecutionFailure);
		assertEquals(RunStatus.RUNNING, service.execute(request).status());
		verify(prompt, times(1)).execute(any(), any(), any(), any(), any(), any());
		verify(results, times(1)).save(any(), any(), any(), any(), any());
	}

	AiSkillRunEntity copy(AiSkillRunEntity row) {
		var copy = new AiSkillRunEntity();
		BeanUtils.copyProperties(row, copy);
		return copy;
	}

}
