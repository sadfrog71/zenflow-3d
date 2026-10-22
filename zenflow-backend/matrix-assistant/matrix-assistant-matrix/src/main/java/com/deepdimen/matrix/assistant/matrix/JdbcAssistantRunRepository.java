package com.deepdimen.matrix.assistant.matrix;

import com.deepdimen.matrix.assistant.run.AssistantManualSelectionGuard;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.deepdimen.matrix.assistant.api.AssistantRunContract.DispatchStatus;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisResult;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionPlan;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RunPhase;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageExchange;
import com.deepdimen.matrix.assistant.run.AssistantRunRecord;
import com.deepdimen.matrix.assistant.run.AssistantRunRecord.RunInputSnapshot;
import com.deepdimen.matrix.assistant.run.AssistantRunRepository;
import com.deepdimen.matrix.assistant.session.AssistantSessionRepository;
import com.deepdimen.matrix.assistant.session.AssistantSessionTitles;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Primary
@Repository
@ConditionalOnProperty(prefix = "matrix.assistant", name = "repository", havingValue = "jdbc")
public class JdbcAssistantRunRepository implements AssistantRunRepository {

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	private final AssistantSessionRepository sessionRepository;

	public JdbcAssistantRunRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
			AssistantSessionRepository sessionRepository) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.sessionRepository = sessionRepository;
	}

	@Override
	@Transactional
	public StartResult startAtomic(AssistantRunRecord run, AssistantMessage userMessage,
			AssistantMessage responsePlaceholder) {
		if (run.input().clarificationSelection() != null) {
			String sourceId = run.input().clarificationSelection().sourceRunId();
			// All consumers of one prompt serialize on its durable run row.
			var source = findByIdForUpdate(sourceId).orElse(null);
			var consumed = jdbcTemplate.queryForList(
					"""
							SELECT id FROM ai_assistant_execution_run
							WHERE session_id = ? AND JSON_UNQUOTE(JSON_EXTRACT(input_snapshot_json, '$.clarificationSelection.sourceRunId')) = ?
							ORDER BY created_at, id LIMIT 1 FOR UPDATE
							""",
					String.class, run.sessionId(), sourceId);
			if (!consumed.isEmpty()) {
				var existing = findByIdForUpdate(consumed.get(0)).orElseThrow();
				if (existing.clientMessageId().equals(run.clientMessageId()))
					return new StartResult(false, existing);
				throw AssistantManualSelectionGuard.consumed();
			}
			var latest = jdbcTemplate.queryForList("""
					SELECT id FROM ai_assistant_message WHERE session_id = ?
					ORDER BY sequence_no DESC LIMIT 1 FOR UPDATE
					""", String.class, run.sessionId());
			AssistantManualSelectionGuard.requireAvailable(run, source, latest.isEmpty() ? null : latest.get(0),
					Instant.now());
		}
		try {
			jdbcTemplate.update(
					"""
									INSERT INTO ai_assistant_execution_run
									(id, session_id, message_id, user_message_id, response_message_id, client_message_id, execution_plan,
								 runtime_mode, activated_skills_json, executed_skills_json, status, execution_status,
								 tenant_id, request_hash, dispatch_status, draft_content, last_event_seq, input_snapshot_json,
								 source_mode, phase, created_at)
							VALUES (?, ?, ?, ?, ?, ?, 'PRIMARY_ONLY', 'NATIVE', ?, '[]', ?, ?, ?, ?, ?, '', 1, ?, ?, ?, ?)
							""",
					run.runId(), run.sessionId(), run.responseMessageId(), run.userMessageId(), run.responseMessageId(),
					run.clientMessageId(),
					toJson(run.input().promptSkill() == null ? List.of()
							: List.of(run.input().promptSkill().skillId())),
					run.status().name(), run.status().name(), run.tenantId(), run.requestHash(),
					run.dispatchStatus().name(), toJson(run.input()),
					run.input().sourceMode() == null ? null : run.input().sourceMode().name(), RunPhase.ACCEPTED.name(),
					Timestamp.from(run.createdAt()));
		}
		catch (DuplicateKeyException ex) {
			AssistantRunRecord existing = findByClientMessageIdForUpdate(run.sessionId(), run.clientMessageId())
				.orElse(null);
			if (existing == null) {
				throw ex;
			}
			return new StartResult(false, existing);
		}
		AssistantMessage sequencedUser = withSequence(userMessage, sessionRepository.nextSequence(run.sessionId()));
		AssistantMessage sequencedResponse = withSequence(responsePlaceholder,
				sessionRepository.nextSequence(run.sessionId()));
		sessionRepository.appendMessage(sequencedUser);
		sessionRepository.appendMessage(sequencedResponse);
		sessionRepository.recordRunStarted(run.sessionId(), run.tenantId(), run.input().identity().userId(),
				AssistantSessionTitles.fromFirstMessage(userMessage.content()), run.input().providerIds(),
				run.input().selectedSkillId(),
				run.input().scopeHint() == null ? null : run.input().scopeHint().sourceRoute(), run.createdAt(), run.input().identity().subjectType());
		jdbcTemplate.update("""
				INSERT INTO ai_assistant_run_event
				(run_id, seq, event_type, event_version, payload_json, correlation_id, created_at)
				VALUES (?, 1, 'RUN_ACCEPTED', 1, ?, ?, ?)
				""", run.runId(),
				toJson(java.util.Map.of("userMessageId", run.userMessageId(), "responseMessageId",
						run.responseMessageId())),
				run.input().identity().correlationId(), Timestamp.from(run.createdAt()));
		return new StartResult(true, run);
	}

	@Override
	public Optional<AssistantRunRecord> findById(String runId) {
		return jdbcTemplate
			.query("SELECT * FROM ai_assistant_execution_run WHERE id = ? AND input_snapshot_json IS NOT NULL",
					this::mapRun, runId)
			.stream()
			.findFirst();
	}

	@Override
	public Optional<AssistantRunRecord> findByClientMessageId(String sessionId, String clientMessageId) {
		return jdbcTemplate.query("""
				SELECT * FROM ai_assistant_execution_run
				WHERE session_id = ? AND client_message_id = ? AND input_snapshot_json IS NOT NULL
				""", this::mapRun, sessionId, clientMessageId).stream().findFirst();
	}

	@Override
	public List<AssistantRunRecord> findBySessionId(String sessionId, int limit) {
		return jdbcTemplate.query("""
				SELECT * FROM ai_assistant_execution_run
				WHERE session_id = ? AND input_snapshot_json IS NOT NULL
				ORDER BY created_at LIMIT ?
				""", this::mapRun, sessionId, Math.max(1, Math.min(limit, 200)));
	}

	@Override
	public List<AssistantRunRecord> findRecentBySessionId(String sessionId, int limit) {
		return jdbcTemplate.query("""
				SELECT * FROM ai_assistant_execution_run
				WHERE session_id = ? AND input_snapshot_json IS NOT NULL
				ORDER BY created_at DESC, id DESC LIMIT ?
				""", this::mapRun, sessionId, Math.max(1, Math.min(limit, 200)));
	}

	private Optional<AssistantRunRecord> findByClientMessageIdForUpdate(String sessionId, String clientMessageId) {
		return jdbcTemplate.query("""
				SELECT * FROM ai_assistant_execution_run
				WHERE session_id = ? AND client_message_id = ? AND input_snapshot_json IS NOT NULL
				FOR UPDATE
				""", this::mapRun, sessionId, clientMessageId).stream().findFirst();
	}

	private Optional<AssistantRunRecord> findByIdForUpdate(String runId) {
		return jdbcTemplate.query("""
				SELECT * FROM ai_assistant_execution_run
				WHERE id = ? AND input_snapshot_json IS NOT NULL
				FOR UPDATE
				""", this::mapRun, runId).stream().findFirst();
	}

	@Override
	public boolean claimPending(String runId, String ownerInstanceId, Instant leaseExpiresAt) {
		return jdbcTemplate.update("""
				UPDATE ai_assistant_execution_run
				SET dispatch_status = 'CLAIMED', status = 'CONTEXT_LOADING', execution_status = 'CONTEXT_LOADING',
				    phase = 'PROVIDER_QUERY',
				    owner_instance_id = ?, lease_expires_at = ?, started_at = COALESCE(started_at, CURRENT_TIMESTAMP(6))
				WHERE id = ? AND dispatch_status = 'PENDING' AND status = 'ACCEPTED'
				""", ownerInstanceId, Timestamp.from(leaseExpiresAt), runId) == 1;
	}

	@Override
	public boolean attachContext(String runId, String ownerInstanceId, String contextPackId) {
		return jdbcTemplate.update("""
				UPDATE ai_assistant_execution_run SET context_pack_id = ?
				WHERE id = ? AND dispatch_status = 'CLAIMED' AND owner_instance_id = ?
				  AND status = 'CONTEXT_LOADING'
				""", contextPackId, runId, ownerInstanceId) == 1;
	}

	@Override
	public boolean attachPlan(String runId, String ownerInstanceId, String catalogDigest, ExecutionPlan executionPlan) {
		return attachPlan(runId, ownerInstanceId, catalogDigest, null, executionPlan);
	}

	@Override
	public boolean attachPlan(String runId, String ownerInstanceId, String catalogDigest, String catalogSnapshotJson,
			ExecutionPlan executionPlan) {
		if (executionPlan == null || executionPlan.planDigest() == null || executionPlan.planDigest().isBlank()) {
			throw new IllegalArgumentException("PLAN_DIGEST_REQUIRED");
		}
		return jdbcTemplate.update(
				"""
						UPDATE ai_assistant_execution_run
						SET phase = 'PLANNING', catalog_digest = ?, catalog_snapshot_json = ?, plan_digest = ?, plan_snapshot_json = ?
						WHERE id = ? AND dispatch_status = 'CLAIMED' AND owner_instance_id = ?
						  AND status = 'CONTEXT_LOADING' AND plan_snapshot_json IS NULL
						""",
				catalogDigest, catalogSnapshotJson, executionPlan.planDigest(), toJson(executionPlan), runId,
				ownerInstanceId) == 1;
	}

	@Override
	public boolean attachAnalysisResult(String runId, String ownerInstanceId, AnalysisResult analysisResult) {
		if (analysisResult == null || analysisResult.resultDigest() == null
				|| analysisResult.resultDigest().isBlank()) {
			throw new IllegalArgumentException("ANALYSIS_RESULT_DIGEST_REQUIRED");
		}
		return jdbcTemplate.update("""
				UPDATE ai_assistant_execution_run
				SET phase = 'VALIDATION', analysis_result_digest = ?, analysis_result_json = ?
				WHERE id = ? AND dispatch_status = 'CLAIMED' AND owner_instance_id = ?
				  AND status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED') AND analysis_result_json IS NULL
				""", analysisResult.resultDigest(), toJson(analysisResult), runId, ownerInstanceId) == 1;
	}

	@Override
	public boolean markModelInvoked(String runId, String ownerInstanceId, Instant invokedAt) {
		return jdbcTemplate.update("""
				UPDATE ai_assistant_execution_run
				SET status = 'MODEL_STREAMING', execution_status = 'MODEL_STREAMING', phase = 'ANALYSIS',
				    model_invoked_at = ?
				WHERE id = ? AND dispatch_status = 'CLAIMED' AND owner_instance_id = ?
				  AND model_invoked_at IS NULL AND status = 'CONTEXT_LOADING' AND lease_expires_at >= ?
				""", Timestamp.from(invokedAt), runId, ownerInstanceId, Timestamp.from(invokedAt)) == 1;
	}

	@Override
	public boolean renewLease(String runId, String ownerInstanceId, Instant leaseExpiresAt) {
		return jdbcTemplate.update("""
				UPDATE ai_assistant_execution_run SET lease_expires_at = ?
				WHERE id = ? AND dispatch_status = 'CLAIMED' AND owner_instance_id = ?
				  AND status IN ('CONTEXT_LOADING', 'MODEL_STREAMING', 'CANCEL_REQUESTED')
				""", Timestamp.from(leaseExpiresAt), runId, ownerInstanceId) == 1;
	}

	@Override
	@Transactional
	public CancelRequestResult requestCancel(String runId, Instant requestedAt) {
		AssistantRunRecord current = findByIdForUpdate(runId).orElseThrow();
		if (current.status().terminal() || current.status() == RunStatus.CANCEL_REQUESTED) {
			return new CancelRequestResult(false, current);
		}
		int updated = jdbcTemplate.update("""
				UPDATE ai_assistant_execution_run
				SET status = 'CANCEL_REQUESTED', execution_status = 'CANCEL_REQUESTED', cancel_requested = 1
				WHERE id = ? AND status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED', 'CANCEL_REQUESTED')
				""", runId);
		if (updated != 1) {
			return new CancelRequestResult(false, findById(runId).orElseThrow());
		}
		long seq = allocateEventSequence(runId);
		jdbcTemplate.update("""
				INSERT INTO ai_assistant_run_event
				(run_id, seq, event_type, event_version, payload_json, correlation_id, created_at)
				VALUES (?, ?, 'RUN_CANCEL_REQUESTED', 1, '{}', ?, ?)
				""", runId, seq, current.input().identity().correlationId(), Timestamp.from(requestedAt));
		return new CancelRequestResult(true, findById(runId).orElseThrow());
	}

	@Override
	@Transactional
	public boolean cancelAtomic(String runId, String ownerInstanceId, MessageExchange exchange,
			AssistantExecutionTrace trace, Instant completedAt) {
		AssistantRunRecord current = findById(runId).orElseThrow();
		String condition = ownerInstanceId == null ? "AND status = 'CANCEL_REQUESTED' AND dispatch_status = 'PENDING'"
				: "AND status = 'CANCEL_REQUESTED' AND dispatch_status = 'CLAIMED' AND owner_instance_id = ?";
		int updated = ownerInstanceId == null
				? updateTerminal(runId, exchange, trace, RunStatus.CANCELLED, null, null, completedAt, condition)
				: updateTerminal(runId, exchange, trace, RunStatus.CANCELLED, null, null, completedAt, condition,
						ownerInstanceId);
		return finishSideEffects(updated, current, exchange, RunStatus.CANCELLED, null, completedAt);
	}

	@Override
	@Transactional
	public boolean completeAtomic(String runId, String ownerInstanceId, MessageExchange exchange,
			AssistantExecutionTrace trace, Instant completedAt) {
		return finishOwned(runId, ownerInstanceId, exchange, trace, RunStatus.COMPLETED, null, null, completedAt);
	}

	@Override
	@Transactional
	public boolean failAtomic(String runId, String ownerInstanceId, MessageExchange exchange,
			AssistantExecutionTrace trace, String errorCode, String errorMessageSafe, Instant completedAt) {
		return finishOwned(runId, ownerInstanceId, exchange, trace, RunStatus.FAILED, errorCode, errorMessageSafe,
				completedAt);
	}

	@Override
	@Transactional
	public boolean failExpiredInvokedAtomic(String runId, Instant observedLeaseExpiresAt, Instant recoveryStartedAt,
			MessageExchange exchange, AssistantExecutionTrace trace, String errorCode, String errorMessageSafe,
			Instant completedAt) {
		AssistantRunRecord current = findById(runId).orElseThrow();
		int updated = updateTerminal(runId, exchange, trace, RunStatus.FAILED, errorCode, errorMessageSafe, completedAt,
				"AND dispatch_status = 'CLAIMED' AND status = 'MODEL_STREAMING' AND model_invoked_at IS NOT NULL "
						+ "AND lease_expires_at = ? AND lease_expires_at < ?",
				Timestamp.from(observedLeaseExpiresAt), Timestamp.from(recoveryStartedAt));
		return finishSideEffects(updated, current, exchange, RunStatus.FAILED, errorCode, completedAt);
	}

	@Override
	public List<String> findPendingRunIds(int limit) {
		return jdbcTemplate.query("""
				SELECT id FROM ai_assistant_execution_run
				WHERE dispatch_status = 'PENDING' AND status = 'ACCEPTED'
				ORDER BY created_at LIMIT ?
				""", (resultSet, rowNum) -> resultSet.getString("id"), Math.max(1, Math.min(limit, 200)));
	}

	@Override
	public List<AssistantRunRecord> findExpiredClaims(Instant now, int limit) {
		return jdbcTemplate.query("""
				SELECT * FROM ai_assistant_execution_run
				WHERE dispatch_status = 'CLAIMED' AND lease_expires_at < ?
				  AND status IN ('CONTEXT_LOADING', 'MODEL_STREAMING')
				ORDER BY lease_expires_at LIMIT ?
				""", this::mapRun, Timestamp.from(now), Math.max(1, Math.min(limit, 200)));
	}

	@Override
	public boolean requeueUninvokedClaim(String runId, Instant now) {
		return jdbcTemplate.update("""
				UPDATE ai_assistant_execution_run
				SET dispatch_status = 'PENDING', status = 'ACCEPTED', execution_status = 'ACCEPTED', phase = 'ACCEPTED',
				    owner_instance_id = NULL, lease_expires_at = NULL
				WHERE id = ? AND dispatch_status = 'CLAIMED' AND lease_expires_at < ?
				  AND model_invoked_at IS NULL AND status = 'CONTEXT_LOADING'
				""", runId, Timestamp.from(now)) == 1;
	}

	private boolean finishOwned(String runId, String ownerInstanceId, MessageExchange exchange,
			AssistantExecutionTrace trace, RunStatus status, String errorCode, String safeMessage,
			Instant completedAt) {
		AssistantRunRecord current = findById(runId).orElseThrow();
		int updated = updateTerminal(runId, exchange, trace, status, errorCode, safeMessage, completedAt,
				"AND dispatch_status = 'CLAIMED' AND owner_instance_id = ?", ownerInstanceId);
		return finishSideEffects(updated, current, exchange, status, errorCode, completedAt);
	}

	private int updateTerminal(String runId, MessageExchange exchange, AssistantExecutionTrace trace, RunStatus status,
			String errorCode, String safeMessage, Instant completedAt, String extraCondition, Object... conditionArgs) {
		String sql = """
				UPDATE ai_assistant_execution_run
				SET dispatch_status = 'FINISHED', status = ?, execution_status = ?, phase = 'COMPLETED', draft_content = ?,
				    context_pack_id = COALESCE(?, context_pack_id), completed_at = ?, error_code = ?,
				    error_message_safe = ?, runtime_id = ?, provider = ?, model = ?, request_id = ?,
				    skill_release_id = ?, skill_release_version = ?, skill_package_sha256 = ?,
				    skill_instruction_digest = ?, citations_json = ?
				WHERE id = ? AND status NOT IN ('COMPLETED', 'FAILED', 'CANCELLED')
				"""
				+ " " + extraCondition;
		List<Object> args = new java.util.ArrayList<>();
		java.util.Collections.addAll(args, status.name(), status.name(), exchange.responseMessage().content(),
				exchange.contextPack() == null ? null : exchange.contextPack().contextPackId(),
				Timestamp.from(completedAt), errorCode, safeMessage, trace == null ? null : trace.runtimeId(),
				trace == null ? null : trace.provider(), trace == null ? null : trace.model(),
				trace == null ? null : trace.requestId(), trace == null ? null : trace.skillReleaseId(),
				trace == null ? null : trace.skillReleaseVersion(), trace == null ? null : trace.skillPackageSha256(),
				trace == null ? null : trace.skillInstructionDigest(),
				toJson(exchange.contextPack() == null ? List.of() : exchange.contextPack().fragments()), runId);
		java.util.Collections.addAll(args, conditionArgs);
		return jdbcTemplate.update(sql, args.toArray());
	}

	private boolean finishSideEffects(int updated, AssistantRunRecord current, MessageExchange exchange,
			RunStatus status, String errorCode, Instant completedAt) {
		if (updated != 1) {
			return false;
		}
		appendTerminalEvent(current, status, errorCode, completedAt);
		sessionRepository.updateMessage(exchange.responseMessage());
		sessionRepository.saveExchange(current.clientMessageId(), exchange);
		return true;
	}

	private void appendTerminalEvent(AssistantRunRecord run, RunStatus status, String errorCode, Instant occurredAt) {
		long seq = allocateEventSequence(run.runId());
		String eventType = switch (status) {
			case COMPLETED -> "RUN_COMPLETED";
			case CANCELLED -> "RUN_CANCELLED";
			default -> "RUN_FAILED";
		};
		Object payload = status == RunStatus.FAILED ? java.util.Map.of("errorCode", errorCode, "retryable", false)
				: java.util.Map.of("responseMessageId", run.responseMessageId());
		jdbcTemplate.update("""
				INSERT INTO ai_assistant_run_event
				(run_id, seq, event_type, event_version, payload_json, correlation_id, created_at)
				VALUES (?, ?, ?, 1, ?, ?, ?)
				""", run.runId(), seq, eventType, toJson(payload), run.input().identity().correlationId(),
				Timestamp.from(occurredAt));
	}

	private long allocateEventSequence(String runId) {
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

	private AssistantRunRecord mapRun(ResultSet resultSet, int rowNum) throws SQLException {
		RunInputSnapshot input = fromJson(resultSet.getString("input_snapshot_json"), RunInputSnapshot.class);
		String phase = resultSet.getString("phase");
		return new AssistantRunRecord(resultSet.getString("id"), resultSet.getString("session_id"),
				resultSet.getString("tenant_id"), input.identity().userId(), resultSet.getString("client_message_id"),
				resultSet.getString("request_hash"), resultSet.getString("user_message_id"),
				resultSet.getString("response_message_id"), RunStatus.valueOf(resultSet.getString("status")),
				DispatchStatus.valueOf(resultSet.getString("dispatch_status")), input,
				resultSet.getString("context_pack_id"), resultSet.getString("draft_content"),
				resultSet.getLong("last_event_seq"), resultSet.getString("owner_instance_id"),
				instant(resultSet, "lease_expires_at"), instant(resultSet, "model_invoked_at"),
				resultSet.getString("error_code"), resultSet.getString("error_message_safe"),
				instant(resultSet, "created_at"), instant(resultSet, "started_at"), instant(resultSet, "completed_at"),
				phase == null || phase.isBlank() ? null : RunPhase.valueOf(phase),
				resultSet.getString("catalog_digest"),
				fromJsonNullable(resultSet.getString("plan_snapshot_json"), ExecutionPlan.class),
				fromJsonNullable(resultSet.getString("analysis_result_json"), AnalysisResult.class));
	}

	private AssistantMessage withSequence(AssistantMessage message, long sequence) {
		return new AssistantMessage(message.messageId(), message.assistantSessionId(), message.role(),
				message.content(), message.status(), sequence, message.contextPackId(), message.createdAt(),
				message.attachments());
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Assistant run JSON serialization failed", ex);
		}
	}

	private <T> T fromJson(String value, Class<T> type) {
		try {
			return objectMapper.readValue(value, type);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Assistant run JSON deserialization failed", ex);
		}
	}

	private <T> T fromJsonNullable(String value, Class<T> type) {
		return value == null || value.isBlank() ? null : fromJson(value, type);
	}

	private Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp timestamp = resultSet.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

}
