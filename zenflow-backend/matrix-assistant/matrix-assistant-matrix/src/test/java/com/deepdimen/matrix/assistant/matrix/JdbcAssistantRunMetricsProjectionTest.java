package com.deepdimen.matrix.assistant.matrix;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType;

class JdbcAssistantRunMetricsProjectionTest {

	@Test
	void projectsOnlyEnumsConfidenceAndFlagAndKeepsFirstRoute() {
		var jdbc = mock(JdbcTemplate.class);
		var projection = new JdbcAssistantRunMetricsProjection(jdbc);
		projection.append("r", RunEventType.ROUTE_DECIDED,
				Map.of("conversationIntent", "GENERAL_QA", "executionMode", "DIRECT_CHAT", "triggerType", "PLANNER",
						"confidence", 0.92, "plannerUsed", true, "prompt", "PRIVATE QUESTION", "reasonCode",
						"PRIVATE REASON"),
				Instant.EPOCH);
		var sql = ArgumentCaptor.forClass(String.class);
		var args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc).update(sql.capture(), args.capture());
		assertTrue(sql.getValue().contains("route_decided_at IS NULL"));
		assertEquals("GENERAL_QA", args.getValue()[0]);
		assertEquals(0.92, args.getValue()[3]);
		assertFalse(java.util.Arrays.toString(args.getValue()).contains("PRIVATE"));
	}

	@Test
	void firstChunkAndVisibleProjectionNeverReplaceEarlierValues() {
		var jdbc = mock(JdbcTemplate.class);
		var projection = new JdbcAssistantRunMetricsProjection(jdbc);
		projection.append("r", RunEventType.MODEL_FIRST_CHUNK, Map.of(), Instant.EPOCH);
		projection.append("r", RunEventType.RESPONSE_DELTA, Map.of("delta", "PRIVATE ANSWER"), Instant.EPOCH);
		var sql = ArgumentCaptor.forClass(String.class);
		var args = ArgumentCaptor.forClass(Object[].class);
		verify(jdbc, times(2)).update(sql.capture(), args.capture());
		assertTrue(sql.getAllValues().get(0).contains("COALESCE(model_first_chunk_at, ?)"));
		assertTrue(sql.getAllValues().get(1).contains("COALESCE(first_visible_answer_at, ?)"));
		assertTrue(args.getAllValues()
			.stream()
			.noneMatch(values -> java.util.Arrays.toString(values).contains("PRIVATE")));
	}

	@Test
	void rejectsUnrecognizedRouteAndNonIntegralClarificationWithoutWriting() {
		var jdbc = mock(JdbcTemplate.class);
		var projection = new JdbcAssistantRunMetricsProjection(jdbc);
		projection.append("r", RunEventType.ROUTE_DECIDED, Map.of("conversationIntent", "user text"), Instant.EPOCH);
		projection.append("r", RunEventType.CLARIFICATION_REQUESTED, Map.of("clarificationCount", 1.5), Instant.EPOCH);
		projection.append("r", RunEventType.MODEL_FIRST_CHUNK, Map.of(), null);
		verifyNoInteractions(jdbc);
	}

}
