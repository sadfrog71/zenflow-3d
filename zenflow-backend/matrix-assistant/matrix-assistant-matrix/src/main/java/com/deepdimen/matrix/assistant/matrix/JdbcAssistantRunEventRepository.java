package com.deepdimen.matrix.assistant.matrix;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType;
import com.deepdimen.matrix.assistant.run.AssistantRunEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Primary
@Repository
@ConditionalOnProperty(prefix = "matrix.assistant", name = "repository", havingValue = "jdbc")
public class JdbcAssistantRunEventRepository implements AssistantRunEventRepository {

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	@org.springframework.beans.factory.annotation.Autowired(required = false)
	private JdbcAssistantRunMetricsProjection metricsProjection;

	public JdbcAssistantRunEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	@Transactional
	public RunEventEnvelope append(String runId, RunEventType type, String correlationId, Map<String, Object> data,
			Instant occurredAt) {
		long seq = allocateSequence(runId);
		RunEventEnvelope event = new RunEventEnvelope(1, runId, seq, type, occurredAt, correlationId, data);
		jdbcTemplate.update("""
				INSERT INTO ai_assistant_run_event
				(run_id, seq, event_type, event_version, payload_json, correlation_id, created_at)
				VALUES (?, ?, ?, 1, ?, ?, ?)
				""", runId, seq, type.name(), toJson(event.data()), correlationId, Timestamp.from(occurredAt));
		if (metricsProjection != null)
			metricsProjection.append(runId, type, event.data(), occurredAt);
		return event;
	}

	@Override
	@Transactional
	public Optional<RunEventEnvelope> appendDeltaOwned(String runId, String ownerInstanceId, String responseMessageId,
			String correlationId, String delta, String draftContent, Instant occurredAt) {
		int updated = jdbcTemplate.update("""
				UPDATE ai_assistant_execution_run SET draft_content = ?
				WHERE id = ? AND response_message_id = ? AND dispatch_status = 'CLAIMED'
				  AND owner_instance_id = ? AND status IN ('MODEL_STREAMING', 'CANCEL_REQUESTED')
				""", draftContent, runId, responseMessageId, ownerInstanceId);
		if (updated != 1) {
			return Optional.empty();
		}
		int messageUpdated = jdbcTemplate.update("""
				UPDATE ai_assistant_message SET content = ?
				WHERE id = ? AND status = 'ACCEPTED'
				""", draftContent, responseMessageId);
		if (messageUpdated != 1) {
			throw new IllegalStateException("Assistant response placeholder not found: " + responseMessageId);
		}
		long seq = allocateSequence(runId);
		RunEventEnvelope event = new RunEventEnvelope(1, runId, seq, RunEventType.RESPONSE_DELTA, occurredAt,
				correlationId, Map.of("delta", delta));
		jdbcTemplate.update("""
				INSERT INTO ai_assistant_run_event
				(run_id, seq, event_type, event_version, payload_json, correlation_id, created_at)
				VALUES (?, ?, 'RESPONSE_DELTA', 1, ?, ?, ?)
				""", runId, seq, toJson(event.data()), correlationId, Timestamp.from(occurredAt));
		if (metricsProjection != null)
			metricsProjection.append(runId, RunEventType.RESPONSE_DELTA, event.data(), occurredAt);
		return Optional.of(event);
	}

	@Override
	public List<RunEventEnvelope> findAfter(String runId, long afterSeq, int limit) {
		return jdbcTemplate.query("""
				SELECT * FROM ai_assistant_run_event
				WHERE run_id = ? AND seq > ? ORDER BY seq LIMIT ?
				""", this::mapEvent, runId, afterSeq, Math.max(1, Math.min(limit, 1000)));
	}

	@Override
	@Transactional
	public int deleteTerminalEventsBefore(Instant cutoff, int limit) {
		return jdbcTemplate.update("""
				DELETE FROM ai_assistant_run_event
				WHERE id IN (
				  SELECT id FROM (
				    SELECT event.id
				    FROM ai_assistant_run_event event
				    JOIN ai_assistant_execution_run run ON run.id = event.run_id
				    WHERE event.created_at < ? AND run.status IN ('COMPLETED', 'FAILED', 'CANCELLED')
				    ORDER BY event.created_at, event.id
				    LIMIT ?
				  ) expired
				)
				""", Timestamp.from(cutoff), Math.max(1, Math.min(limit, 5000)));
	}

	private long allocateSequence(String runId) {
		Long value = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
			try (PreparedStatement update = connection.prepareStatement("""
					UPDATE ai_assistant_execution_run
					SET last_event_seq = LAST_INSERT_ID(last_event_seq + 1)
					WHERE id = ?
					""")) {
				update.setString(1, runId);
				if (update.executeUpdate() != 1) {
					throw new SQLException("Assistant run not found: " + runId);
				}
			}
			try (PreparedStatement query = connection.prepareStatement("SELECT LAST_INSERT_ID()");
					ResultSet resultSet = query.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("Assistant event sequence allocation failed");
				}
				return resultSet.getLong(1);
			}
		});
		return value == null ? 0 : value;
	}

	private RunEventEnvelope mapEvent(ResultSet resultSet, int rowNum) throws SQLException {
		return new RunEventEnvelope(resultSet.getInt("event_version"), resultSet.getString("run_id"),
				resultSet.getLong("seq"), RunEventType.valueOf(resultSet.getString("event_type")),
				resultSet.getTimestamp("created_at").toInstant(), resultSet.getString("correlation_id"),
				fromJson(resultSet.getString("payload_json")));
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Assistant event JSON serialization failed", ex);
		}
	}

	private Map<String, Object> fromJson(String value) {
		try {
			return objectMapper.readValue(value, new TypeReference<>() {
			});
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Assistant event JSON deserialization failed", ex);
		}
	}

}
