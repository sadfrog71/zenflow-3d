package com.deepdimen.matrix.assistant.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.ExecutionPlan;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SessionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

class JdbcAssistantSessionRepositoryExecutionRunTest {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

	private final JdbcAssistantSessionRepository repository = new JdbcAssistantSessionRepository(jdbcTemplate,
			new ObjectMapper());

	@Test
	void ordinaryQueryConstrainsBothTenantAndOwner() {
		repository.listExecutionRuns("tenant-1", "user-1", false, 100);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).queryForList(sql.capture(), eq("tenant-1"), eq("ADMIN_USER"), eq("user-1"), eq(100));
		assertTrue(sql.getValue().contains("s.tenant_id = e.tenant_id"));
		assertTrue(sql.getValue().contains("e.tenant_id = ?"));
		assertTrue(sql.getValue().contains("s.owner_user_id = ?"));
	}

	@Test
	void sessionDirectoryConstrainsTenantOwnerStatusAndCursor() {
		Instant cursorTime = Instant.parse("2026-08-28T02:24:00Z");

		repository.findSessions("tenant-1", "user-1", SessionStatus.ACTIVE, "周报", cursorTime, "session-9", 31);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).query(sql.capture(), any(org.springframework.jdbc.core.RowMapper.class),
				arguments.capture());
		assertTrue(sql.getValue().contains("tenant_id = ?"));
		assertTrue(sql.getValue().contains("owner_user_id = ?"));
		assertTrue(sql.getValue().contains("status = ?"));
		assertTrue(sql.getValue().contains("message_sequence > 0"));
		assertTrue(sql.getValue().contains("last_message_at < ?"));
		assertTrue(sql.getValue().contains("ORDER BY last_message_at DESC, id DESC"));
		assertTrue(java.util.Arrays.asList(arguments.getValue()).contains("tenant-1"));
		assertTrue(java.util.Arrays.asList(arguments.getValue()).contains("user-1"));
		assertTrue(java.util.Arrays.asList(arguments.getValue()).contains("ACTIVE"));
	}

	@Test
	void metadataUpdateUsesOwnerAndVersionInTheSqlPredicate() {
		repository.updateTitle("session-1", "tenant-1", "user-1", "renamed", 7);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(sql.capture(), arguments.capture());
		assertTrue(sql.getValue().contains("tenant_id = ?"));
		assertTrue(sql.getValue().contains("owner_user_id = ?"));
		assertTrue(sql.getValue().contains("version = ?"));
		assertTrue(java.util.Arrays.asList(arguments.getValue()).contains(7L));
	}

	@Test
	void runStartUsesTenantOwnerAndActiveSessionInTheSqlPredicate() {
		when(jdbcTemplate.update(any(String.class), any(Object[].class))).thenReturn(1);
		repository.recordRunStarted("session-1", "tenant-1", "user-1", "first question", List.of(), null, null,
				Instant.EPOCH);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(sql.capture(), arguments.capture());
		assertTrue(sql.getValue().contains("tenant_id = ?"));
		assertTrue(sql.getValue().contains("owner_user_id = ?"));
		assertTrue(sql.getValue().contains("status = 'ACTIVE'"));
		assertTrue(java.util.Arrays.asList(arguments.getValue()).contains("tenant-1"));
		assertTrue(java.util.Arrays.asList(arguments.getValue()).contains("user-1"));
	}

	@Test
	void tenantWideQueryStillConstrainsTenant() {
		repository.listExecutionRuns("tenant-1", "admin-1", true, 100);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbcTemplate).queryForList(sql.capture(), eq("tenant-1"), eq("ADMIN_USER"), eq(100));
		assertTrue(sql.getValue().contains("s.tenant_id = e.tenant_id"));
		assertTrue(sql.getValue().contains("e.tenant_id = ?"));
	}

	@Test
	void persistsExecutedSkillReleaseProvenanceSeparatelyFromActivation() {
		when(jdbcTemplate.queryForObject("SELECT tenant_id FROM ai_assistant_session WHERE id = ?", String.class,
				"session-1")).thenReturn("tenant-1");
		AssistantExecutionTrace trace = new AssistantExecutionTrace("run-1", ExecutionPlan.PRIMARY_ONLY,
				"chat", "app-1", "task-1", null, "conversation-1", List.of("water-report"), null, 88L,
				"agent-v1", "DIFY", "model-1", 10L, 5L, 15L, "DIFY_RESPONSE", "request-1", "SUCCEEDED",
				List.of("water-report"), 901L, "v3", "package-sha", "instruction-sha");

		repository.saveExecutionTrace("session-1", "message-1", "client-1", trace, Instant.EPOCH);

		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
		verify(jdbcTemplate).update(sql.capture(), arguments.capture());
		assertTrue(sql.getValue().contains("executed_skills_json"));
		assertTrue(sql.getValue().contains("skill_release_id"));
		assertTrue(sql.getValue().contains("skill_package_sha256"));
		assertTrue(sql.getValue().contains("skill_instruction_digest"));
		assertTrue(java.util.Arrays.asList(arguments.getValue()).contains("[\"water-report\"]"));
		assertTrue(java.util.Arrays.asList(arguments.getValue()).contains("package-sha"));
		assertTrue(java.util.Arrays.asList(arguments.getValue()).contains("instruction-sha"));
	}

	@Test
	void failedTraceCanRetainAnActuallyExecutedSkill() {
		AssistantExecutionTrace trace = new AssistantExecutionTrace("run-1", ExecutionPlan.UNAVAILABLE, "chat", null,
				null, null, null, List.of("water-report"), "DIFY_RUNTIME_CALL_FAILED", null, null, null, null, null,
				null, null, null, null, "FAILED", List.of("water-report"), 901L, "v3", "package-sha",
				"instruction-sha");

		assertEquals(List.of("water-report"), trace.executedSkillCodes());
	}

	@Test
	void legacySuccessfulTraceDoesNotInferExecutionFromActivation() {
		AssistantExecutionTrace trace = new AssistantExecutionTrace("run-legacy", ExecutionPlan.PRIMARY_ONLY, "chat",
				"app-1", "task-1", null, "conversation-1", List.of("water-report"), null);

		assertEquals("SUCCEEDED", trace.executionStatus());
		assertEquals(List.of("water-report"), trace.activatedSkillCodes());
		assertEquals(List.of(), trace.executedSkillCodes());
	}

}
