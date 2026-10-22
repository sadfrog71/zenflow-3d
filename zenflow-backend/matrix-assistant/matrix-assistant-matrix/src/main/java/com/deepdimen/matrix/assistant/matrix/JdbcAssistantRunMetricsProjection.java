package com.deepdimen.matrix.assistant.matrix;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RouteTriggerType;

/**
 * Called in the event append transaction; only whitelisted, non-text metric values are
 * projected.
 */
@Component
@ConditionalOnProperty(prefix = "matrix.assistant.metrics", name = "projection-enabled", havingValue = "true")
public class JdbcAssistantRunMetricsProjection {

	private final JdbcTemplate jdbc;

	public JdbcAssistantRunMetricsProjection(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void append(String runId, RunEventType type, Map<String, Object> data, Instant at) {
		if (at == null)
			return;
		if (type == RunEventType.ROUTE_DECIDED) {
			String intent = enumName(ConversationIntent.class, data.get("conversationIntent"));
			String mode = enumName(ExecutionMode.class, data.get("executionMode"));
			String trigger = enumName(RouteTriggerType.class, data.get("triggerType"));
			if (intent == null || mode == null || trigger == null)
				return;
			Object rawConfidence = data.get("confidence");
			Double confidence = rawConfidence instanceof Number number ? number.doubleValue() : null;
			if (confidence != null && (!Double.isFinite(confidence) || confidence < 0 || confidence > 1))
				confidence = null;
			Boolean planner = data.get("plannerUsed") instanceof Boolean value ? value : null;
			jdbc.update(
					"""
							UPDATE ai_assistant_execution_run
							SET route_intent = ?, route_mode = ?, route_trigger = ?, route_confidence = ?, route_planner_used = ?, route_decided_at = ?
							WHERE id = ? AND route_decided_at IS NULL
							""",
					intent, mode, trigger, confidence, planner, Timestamp.from(at), runId);
		}
		else if (type == RunEventType.MODEL_FIRST_CHUNK) {
			jdbc.update(
					"UPDATE ai_assistant_execution_run SET model_first_chunk_at = COALESCE(model_first_chunk_at, ?) WHERE id = ?",
					Timestamp.from(at), runId);
		}
		else if (type == RunEventType.RESPONSE_DELTA) {
			jdbc.update(
					"UPDATE ai_assistant_execution_run SET first_visible_answer_at = COALESCE(first_visible_answer_at, ?) WHERE id = ?",
					Timestamp.from(at), runId);
		}
		else if (type == RunEventType.CLARIFICATION_REQUESTED) {
			Object value = data.get("clarificationCount");
			if (value instanceof Number count && Double.isFinite(count.doubleValue())
					&& count.doubleValue() == count.intValue() && count.intValue() >= 0 && count.intValue() <= 2)
				jdbc.update(
						"UPDATE ai_assistant_execution_run SET route_clarification_count = GREATEST(COALESCE(route_clarification_count, 0), ?) WHERE id = ?",
						count.intValue(), runId);
		}
	}

	private <E extends Enum<E>> String enumName(Class<E> type, Object value) {
		if (!(value instanceof String text))
			return null;
		try {
			return Enum.valueOf(type, text).name();
		}
		catch (IllegalArgumentException ignored) {
			return null;
		}
	}

}
