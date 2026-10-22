package com.deepdimen.matrix.assistant.matrix;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.*;
import com.deepdimen.matrix.assistant.run.AssistantRunRecord;
import com.deepdimen.matrix.assistant.session.AssistantSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

class JdbcAssistantManualSelectionGuardTest {

	@Test
	void recentLookupUsesDescendingOrderBeforeApplyingBoundedLimit() {
		var jdbc = mock(JdbcTemplate.class);
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
		var repository = new JdbcAssistantRunRepository(jdbc, new ObjectMapper(),
				mock(AssistantSessionRepository.class));
		repository.findRecentBySessionId("s", 999);
		var sql = ArgumentCaptor.forClass(String.class);
		var args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).query(sql.capture(), any(RowMapper.class), args.capture());
		assertTrue(sql.getValue().contains("ORDER BY created_at DESC, id DESC LIMIT ?"));
		assertEquals("s", args.getValue()[0]);
		assertEquals(200, args.getValue()[1]);
	}

	@Test
	void locksSourceAndRejectsCompetingClientBeforeAnyWrite() {
		var jdbc = mock(JdbcTemplate.class);
		var source = mock(AssistantRunRecord.class);
		var existing = mock(AssistantRunRecord.class);
		when(existing.clientMessageId()).thenReturn("winner-client");
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(source),
				List.of(existing));
		when(jdbc.queryForList(anyString(), eq(String.class), any(Object[].class))).thenReturn(List.of("winner"));
		var input = mock(AssistantRunRecord.RunInputSnapshot.class);
		when(input.clarificationSelection()).thenReturn(new ClarificationSelection("source", "a".repeat(64),
				ExecutionStepType.MATRIX_PROVIDER, "KNOWLEDGE", null));
		var request = mock(AssistantRunRecord.class);
		when(request.input()).thenReturn(input);
		when(request.sessionId()).thenReturn("s");
		when(request.clientMessageId()).thenReturn("other-client");
		var repository = new JdbcAssistantRunRepository(jdbc, new ObjectMapper(),
				mock(AssistantSessionRepository.class));
		assertThrows(ResponseStatusException.class, () -> repository.startAtomic(request, null, null));
		var order = inOrder(jdbc);
		var sql = ArgumentCaptor.forClass(String.class);
		order.verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
		assertTrue(sql.getValue().contains("FOR UPDATE"));
		order.verify(jdbc).queryForList(sql.capture(), eq(String.class), any(Object[].class));
		assertTrue(sql.getValue().contains("$.clarificationSelection.sourceRunId"));
		assertTrue(sql.getValue().contains("FOR UPDATE"));
		verify(jdbc, never()).update(anyString(), any(Object[].class));
		when(request.clientMessageId()).thenReturn("winner-client");
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(source),
				List.of(existing));
		assertFalse(repository.startAtomic(request, null, null).created());
	}

}
