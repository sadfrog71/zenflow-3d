package com.deepdimen.matrix.assistant.matrix;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepStatus;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType;
import com.deepdimen.matrix.assistant.run.AssistantExecutionStepRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
@ConditionalOnProperty(prefix = "matrix.assistant", name = "repository", havingValue = "jdbc")
public class JdbcAssistantExecutionStepRepository implements AssistantExecutionStepRepository {

	private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
	};

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	public JdbcAssistantExecutionStepRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public StepCreateResult createStepIfAbsent(AssistantExecutionStepRecord step) {
		try {
			jdbcTemplate.update("""
					INSERT INTO ai_assistant_execution_step
					(run_id, step_id, step_type, target_id, target_release_id, required_step, dependencies_json,
					 status, owner_instance_id, invocation_key, input_digest, output_digest, artifact_id,
					 external_task_id, workflow_run_id, usage_source, error_code, error_message_safe,
					 started_at, completed_at, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", step.runId(), step.stepId(), step.stepType().name(), step.targetId(), step.targetReleaseId(),
					step.required(), toJson(step.dependencies()), step.status().name(), step.ownerInstanceId(),
					step.invocationKey(), step.inputDigest(), step.outputDigest(), step.artifactId(),
					step.externalTaskId(), step.workflowRunId(), step.usageSource(), step.errorCode(),
					step.errorMessageSafe(), timestamp(step.startedAt()), timestamp(step.completedAt()),
					Timestamp.from(step.createdAt()), Timestamp.from(step.updatedAt()));
			return new StepCreateResult(true, step);
		}
		catch (DuplicateKeyException ex) {
			AssistantExecutionStepRecord existing = findStep(step.runId(), step.stepId()).orElseThrow(() -> ex);
			return new StepCreateResult(false, existing);
		}
	}

	@Override
	public Optional<AssistantExecutionStepRecord> findStep(String runId, String stepId) {
		return jdbcTemplate.query("""
				SELECT * FROM ai_assistant_execution_step WHERE run_id = ? AND step_id = ?
				""", this::mapStep, runId, stepId).stream().findFirst();
	}

	@Override
	public List<AssistantExecutionStepRecord> findStepsByRunId(String runId) {
		return jdbcTemplate.query("""
				SELECT * FROM ai_assistant_execution_step WHERE run_id = ? ORDER BY created_at, step_id
				""", this::mapStep, runId);
	}

	@Override
	public boolean claimReadyStep(String runId, String stepId, String ownerInstanceId, Instant startedAt) {
		return jdbcTemplate.update("""
				UPDATE ai_assistant_execution_step
				SET status = 'RUNNING', owner_instance_id = ?, started_at = ?, updated_at = ?
				WHERE run_id = ? AND step_id = ? AND status = 'READY'
				""", ownerInstanceId, Timestamp.from(startedAt), Timestamp.from(startedAt), runId, stepId) == 1;
	}

	@Override
	public boolean recordExternalIdentifiersOwned(String runId, String stepId, String ownerInstanceId,
			String externalTaskId, String workflowRunId, Instant observedAt) {
		Instant at = observedAt == null ? Instant.now() : observedAt;
		return jdbcTemplate.update("""
				UPDATE ai_assistant_execution_step
				SET external_task_id = COALESCE(external_task_id, ?),
				    workflow_run_id = COALESCE(workflow_run_id, ?), updated_at = ?
				WHERE run_id = ? AND step_id = ? AND owner_instance_id = ? AND status = 'RUNNING'
				""", blankToNull(externalTaskId), blankToNull(workflowRunId), Timestamp.from(at), runId, stepId,
				ownerInstanceId) == 1;
	}

	@Override
	public boolean transitionOwnedStep(String runId, String stepId, String ownerInstanceId,
			ExecutionStepStatus expectedStatus, ExecutionStepStatus targetStatus, StepCompletion completion) {
		AssistantExecutionStepRepository.requireLegalTransition(expectedStatus, targetStatus);
		StepCompletion value = completion == null ? StepCompletion.empty() : completion;
		Instant updatedAt = value.completedAt() == null ? Instant.now() : value.completedAt();
		return jdbcTemplate.update("""
				UPDATE ai_assistant_execution_step
				SET status = ?, output_digest = ?, artifact_id = ?,
				    external_task_id = COALESCE(?, external_task_id),
				    workflow_run_id = COALESCE(?, workflow_run_id),
				    usage_source = ?, error_code = ?, error_message_safe = ?, completed_at = ?, updated_at = ?
				WHERE run_id = ? AND step_id = ? AND owner_instance_id = ? AND status = ?
				""", targetStatus.name(), value.outputDigest(), value.artifactId(), value.externalTaskId(),
				value.workflowRunId(), value.usageSource(), value.errorCode(), value.errorMessageSafe(),
				timestamp(value.completedAt()), Timestamp.from(updatedAt), runId, stepId, ownerInstanceId,
				expectedStatus.name()) == 1;
	}

	@Override
	public boolean recoverOrphanedStep(String runId, String stepId, ExecutionStepStatus targetStatus,
			StepCompletion completion) {
		if (targetStatus != ExecutionStepStatus.READY && !ExecutionStepStatus.RUNNING.canTransitionTo(targetStatus)) {
			throw new IllegalArgumentException("ILLEGAL_STEP_TRANSITION");
		}
		StepCompletion value = completion == null ? StepCompletion.empty() : completion;
		Instant updatedAt = value.completedAt() == null ? Instant.now() : value.completedAt();
		if (targetStatus == ExecutionStepStatus.READY) {
			return jdbcTemplate.update("""
					UPDATE ai_assistant_execution_step
					SET status = 'READY', owner_instance_id = NULL, started_at = NULL, updated_at = ?
					WHERE run_id = ? AND step_id = ? AND status = 'RUNNING'
					""", Timestamp.from(updatedAt), runId, stepId) == 1;
		}
		return jdbcTemplate.update("""
				UPDATE ai_assistant_execution_step
				SET status = ?, output_digest = ?, artifact_id = ?,
				    external_task_id = COALESCE(?, external_task_id),
				    workflow_run_id = COALESCE(?, workflow_run_id),
				    usage_source = ?, error_code = ?, error_message_safe = ?, completed_at = ?, updated_at = ?
				WHERE run_id = ? AND step_id = ? AND status = 'RUNNING'
				""", targetStatus.name(), value.outputDigest(), value.artifactId(), value.externalTaskId(),
				value.workflowRunId(), value.usageSource(), value.errorCode(), value.errorMessageSafe(),
				timestamp(value.completedAt()), Timestamp.from(updatedAt), runId, stepId) == 1;
	}

	private AssistantExecutionStepRecord mapStep(ResultSet resultSet, int rowNum) throws SQLException {
		return new AssistantExecutionStepRecord(resultSet.getString("run_id"), resultSet.getString("step_id"),
				ExecutionStepType.valueOf(resultSet.getString("step_type")), resultSet.getString("target_id"),
				getNullableLong(resultSet, "target_release_id"), resultSet.getBoolean("required_step"),
				fromJson(resultSet.getString("dependencies_json")),
				ExecutionStepStatus.valueOf(resultSet.getString("status")), resultSet.getString("owner_instance_id"),
				resultSet.getString("invocation_key"), resultSet.getString("input_digest"),
				resultSet.getString("output_digest"), resultSet.getString("artifact_id"),
				resultSet.getString("external_task_id"), resultSet.getString("workflow_run_id"),
				resultSet.getString("usage_source"), resultSet.getString("error_code"),
				resultSet.getString("error_message_safe"), instant(resultSet, "started_at"),
				instant(resultSet, "completed_at"), instant(resultSet, "created_at"),
				instant(resultSet, "updated_at"));
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Assistant step JSON serialization failed", ex);
		}
	}

	private List<String> fromJson(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(value, STRING_LIST);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Assistant step JSON deserialization failed", ex);
		}
	}

	private Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp value = resultSet.getTimestamp(column);
		return value == null ? null : value.toInstant();
	}

	private Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private Long getNullableLong(ResultSet resultSet, String column) throws SQLException {
		long value = resultSet.getLong(column);
		return resultSet.wasNull() ? null : value;
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

}
