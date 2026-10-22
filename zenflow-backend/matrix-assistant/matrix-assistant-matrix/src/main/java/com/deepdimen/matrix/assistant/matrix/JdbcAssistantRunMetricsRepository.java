package com.deepdimen.matrix.assistant.matrix;

import java.time.Instant;
import java.time.Duration;
import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.deepdimen.matrix.assistant.run.AssistantRunMetricsRepository;
import com.deepdimen.matrix.assistant.run.AssistantRunMetricsService.Input;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope;

@Repository
@ConditionalOnProperty(prefix = "matrix.assistant", name = "repository", havingValue = "jdbc")
public class JdbcAssistantRunMetricsRepository implements AssistantRunMetricsRepository {

	private final JdbcTemplate jdbc;

	@org.springframework.beans.factory.annotation.Value("${matrix.assistant.metrics.projection-enabled:false}")
	private boolean projectionEnabled;

	public JdbcAssistantRunMetricsRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Sample> read(String tenantId, Instant from, Instant to, int limit) {
		if (tenantId == null || tenantId.isBlank() || from == null || to == null || !from.isBefore(to)
				|| Duration.between(from, to).compareTo(Duration.ofDays(7)) > 0 || limit < 1 || limit > 201)
			throw new IllegalArgumentException("METRICS_QUERY_INVALID");

		String routeIntent = "JSON_UNQUOTE(JSON_EXTRACT(plan_snapshot_json, '$.routeDecision.conversationIntent'))";
		String routeMode = "JSON_UNQUOTE(JSON_EXTRACT(plan_snapshot_json, '$.routeDecision.executionMode'))";
		if (projectionEnabled) {
			routeIntent = "COALESCE(route_intent, " + routeIntent + ")";
			routeMode = "COALESCE(route_mode, " + routeMode + ")";
		}
		String times = projectionEnabled ? "route_decided_at, model_first_chunk_at, first_visible_answer_at"
				: "NULL AS route_decided_at, NULL AS model_first_chunk_at, NULL AS first_visible_answer_at";
		List<Input> runs = jdbc.query("""
				SELECT id, status, created_at, started_at, completed_at, last_event_seq, provider, model,
				  %s AS route_intent,
				  %s AS route_mode, %s
				FROM ai_assistant_execution_run
				WHERE tenant_id = ? AND created_at >= ? AND created_at < ?
				ORDER BY created_at DESC, id DESC LIMIT ?
				""".formatted(routeIntent, routeMode, times),
				(rs, row) -> new Input(rs.getString("id"), metricStatus(rs.getString("status")),
						enumOrNull(ConversationIntent.class, rs.getString("route_intent")),
						enumOrNull(ExecutionMode.class, rs.getString("route_mode")), instant(rs, "created_at"),
						instant(rs, "started_at"), instant(rs, "completed_at"), rs.getLong("last_event_seq"),
						instant(rs, "route_decided_at"), instant(rs, "model_first_chunk_at"),
						instant(rs, "first_visible_answer_at"), rs.getString("provider"), rs.getString("model")),
				tenantId, Timestamp.from(from), Timestamp.from(to), limit);
		return runs.stream().map(run -> {
			List<RunEventEnvelope> events = jdbc.query("""
					SELECT e.run_id, e.seq, e.event_type, e.created_at
					FROM ai_assistant_run_event e
					JOIN ai_assistant_execution_run r ON r.id = e.run_id
					WHERE r.tenant_id = ? AND e.run_id = ? AND e.seq <= ?
					ORDER BY e.seq LIMIT 2048
					""",
					(rs, row) -> new RunEventEnvelope(1, rs.getString("run_id"), rs.getLong("seq"),
							enumOrNull(RunEventType.class, rs.getString("event_type")), instant(rs, "created_at"), null,
							Map.of()),
					tenantId, run.runId(), run.lastEventSeq());
			List<com.deepdimen.matrix.assistant.run.AssistantStepMetrics.Step> steps = jdbc.query("""
					SELECT s.run_id, s.step_id, s.step_type, s.target_id, s.status, s.started_at, s.completed_at
					FROM ai_assistant_execution_step s
					JOIN ai_assistant_execution_run r ON r.id = s.run_id
					WHERE r.tenant_id = ? AND s.run_id = ? AND s.step_type IN ('MATRIX_PROVIDER', 'DIFY_WORKFLOW')
					ORDER BY s.step_id LIMIT 257
					""", (rs, row) -> new com.deepdimen.matrix.assistant.run.AssistantStepMetrics.Step(
					rs.getString("run_id"), rs.getString("step_id"),
					enumOrNull(com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType.class,
							rs.getString("step_type")),
					rs.getString("target_id"),
					java.util.Optional
						.ofNullable(enumOrNull(
								com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepStatus.class,
								rs.getString("status")))
						.orElse(com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepStatus.UNKNOWN),
					instant(rs, "started_at"), instant(rs, "completed_at")), tenantId, run.runId());
			return new Sample(tenantId, run, events, steps.stream().limit(256).toList(), steps.size() > 256);
		}).toList();
	}

	static RunStatus metricStatus(String status) {
		return "SUCCEEDED".equals(status) ? RunStatus.COMPLETED : RunStatus.valueOf(status);
	}

	private Instant instant(ResultSet rs, String field) throws SQLException {
		Timestamp value = rs.getTimestamp(field);
		return value == null ? null : value.toInstant();
	}

	private <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
		if (value == null)
			return null;
		try {
			return Enum.valueOf(type, value);
		}
		catch (IllegalArgumentException ignored) {
			return null;
		}
	}

}
