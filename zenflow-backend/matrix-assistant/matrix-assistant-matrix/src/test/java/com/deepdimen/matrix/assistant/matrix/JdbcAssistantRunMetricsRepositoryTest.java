package com.deepdimen.matrix.assistant.matrix;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.deepdimen.matrix.assistant.run.AssistantRunMetricsService.Input;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;

class JdbcAssistantRunMetricsRepositoryTest {

	@Test
	void legacySuccessfulStatusMapsToCompletedWithoutChangingTimes() {
		assertEquals(RunStatus.COMPLETED, JdbcAssistantRunMetricsRepository.metricStatus("SUCCEEDED"));
		assertEquals(RunStatus.COMPLETED, JdbcAssistantRunMetricsRepository.metricStatus("COMPLETED"));
		assertEquals(RunStatus.FAILED, JdbcAssistantRunMetricsRepository.metricStatus("FAILED"));
		assertThrows(IllegalArgumentException.class,
				() -> JdbcAssistantRunMetricsRepository.metricStatus("unsupported"));
	}

	@Test
	void enabledProjectionReadsDurableTimesAndKeepsLegacyRouteFallback() throws Exception {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
		var repository = new JdbcAssistantRunMetricsRepository(jdbc);
		var flag = JdbcAssistantRunMetricsRepository.class.getDeclaredField("projectionEnabled");
		flag.setAccessible(true);
		flag.setBoolean(repository, true);
		repository.read("t", Instant.EPOCH, Instant.EPOCH.plusSeconds(1), 1);
		var sql = ArgumentCaptor.forClass(String.class);
		verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
		assertTrue(sql.getValue().contains("COALESCE(route_intent,"));
		assertTrue(sql.getValue().contains("route_decided_at, model_first_chunk_at, first_visible_answer_at"));
		assertFalse(sql.getValue().contains("NULL AS model_first_chunk_at"));
	}

	@Test
	void sqlScopesMetadataAndEventsAndDoesNotReadTextPayloads() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		Instant from = Instant.parse("2026-09-26T00:00:00Z");
		when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
			.thenReturn(List.of(new Input("r", RunStatus.COMPLETED, null, null, from, from, from, 8)), List.of());
		var results = new JdbcAssistantRunMetricsRepository(jdbc).read("tenant-a", from, from.plusSeconds(10), 2);
		assertEquals(1, results.size());
		assertEquals("tenant-a", results.get(0).tenantId());
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Object[]> params = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc, times(3)).query(sql.capture(), any(RowMapper.class), params.capture());
		assertTrue(sql.getAllValues().get(0).contains("WHERE tenant_id = ?"));
		assertTrue(sql.getAllValues().get(0).contains("$.routeDecision.conversationIntent"));
		assertTrue(sql.getAllValues().get(0).contains("NULL AS model_first_chunk_at"));
		assertTrue(sql.getAllValues().get(0).contains("created_at >= ? AND created_at < ?"));
		assertTrue(sql.getAllValues().get(1).contains("r.tenant_id = ? AND e.run_id = ? AND e.seq <= ?"));
		assertTrue(sql.getAllValues().get(1).contains("LIMIT 2048"));
		assertEquals("tenant-a", params.getAllValues().get(0)[0]);
		assertEquals("tenant-a", params.getAllValues().get(1)[0]);
		assertEquals(8L, params.getAllValues().get(1)[2]);
		assertTrue(sql.getAllValues().get(2).contains("r.tenant_id = ? AND s.run_id = ?"));
		assertTrue(sql.getAllValues().get(2).contains("LIMIT 257"));
		assertEquals("tenant-a", params.getAllValues().get(2)[0]);
		assertEquals("r", params.getAllValues().get(2)[1]);
		for (String statement : sql.getAllValues()) {
			assertFalse(statement.contains("SELECT *"));
			assertFalse(statement.contains("input_snapshot_json"));
			assertFalse(statement.contains("draft_content"));
			assertFalse(statement.contains("payload_json"));
			assertFalse(statement.contains("error_message"));
			assertFalse(statement.contains("output_json"));
		}
	}

	@Test
	void invalidLimitsNeverTouchDatabase() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		assertThrows(IllegalArgumentException.class, () -> new JdbcAssistantRunMetricsRepository(jdbc).read("t",
				Instant.EPOCH, Instant.EPOCH.plusSeconds(1), 202));
		verifyNoInteractions(jdbc);
	}

}
