package com.deepdimen.matrix.assistant.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepStatus;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType;
import com.deepdimen.matrix.assistant.run.AssistantExecutionStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class JdbcAssistantExecutionStepRepositoryTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private final JdbcAssistantExecutionStepRepository repository = new JdbcAssistantExecutionStepRepository(
			jdbcTemplate, new ObjectMapper());

	@Test
	void insertsOneImmutableStepAndInvocationKey() {
		when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);

		var result = repository.createStepIfAbsent(step("invocation-1"));

		assertTrue(result.created());
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(sql.capture(), arguments.capture());
		assertTrue(sql.getValue().contains("ai_assistant_execution_step"));
		assertTrue(sql.getValue().contains("invocation_key"));
		assertTrue(java.util.Arrays.asList(arguments.getValue()).contains("invocation-1"));
	}

	@Test
	@SuppressWarnings("unchecked")
	void duplicateStepReturnsThePersistedInvocationKey() {
		var first = step("invocation-1");
		when(jdbcTemplate.update(any(String.class), any(Object[].class)))
			.thenThrow(new DuplicateKeyException("duplicate"));
		when(jdbcTemplate.query(any(String.class), any(RowMapper.class), any(Object[].class)))
			.thenReturn(List.of(first));

		var result = repository.createStepIfAbsent(step("invocation-2"));

		assertFalse(result.created());
		assertEquals("invocation-1", result.step().invocationKey());
	}

	@Test
	void illegalUnknownReplayIsRejectedBeforeJdbcMutation() {
		assertThrows(IllegalArgumentException.class,
				() -> repository.transitionOwnedStep("run-1", "step-1", "owner-1", ExecutionStepStatus.UNKNOWN,
						ExecutionStepStatus.READY, AssistantExecutionStepRepository.StepCompletion.empty()));

		verifyNoInteractions(jdbcTemplate);
	}

	@Test
	void recordsRemoteAcknowledgementOnlyForTheOwnedRunningStep() {
		when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);

		assertTrue(repository.recordExternalIdentifiersOwned("run-1", "step-1", "owner-1", "task-1",
				"workflow-1", Instant.EPOCH));

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(sql.capture(), arguments.capture());
		assertTrue(sql.getValue().contains("status = 'RUNNING'"));
		assertTrue(sql.getValue().contains("owner_instance_id"));
		assertTrue(java.util.Arrays.asList(arguments.getValue()).contains("task-1"));
	}

	private AssistantExecutionStepRepository.AssistantExecutionStepRecord step(String invocationKey) {
		return new AssistantExecutionStepRepository.AssistantExecutionStepRecord("run-1", "step-1",
				ExecutionStepType.DIFY_WORKFLOW, "workflow-1", 9L, true, List.of("provider-1"),
				ExecutionStepStatus.READY, null, invocationKey, "input-digest", null, null, null, null, null, null,
				null, null, null, Instant.EPOCH, Instant.EPOCH);
	}

}
