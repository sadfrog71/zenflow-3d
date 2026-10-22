package com.deepdimen.matrix.assistant.matrix;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantAttachment;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantContextPack;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSession;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSessionSummary;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageRole;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageExchange;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SessionStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.TitleSource;
import com.deepdimen.matrix.assistant.session.AssistantSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Primary
@Repository
@ConditionalOnProperty(prefix = "matrix.assistant", name = "repository", havingValue = "jdbc")
public class JdbcAssistantSessionRepository implements AssistantSessionRepository {

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	private final RowMapper<AssistantMessage> messageRowMapper;

	public JdbcAssistantSessionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
		this.messageRowMapper = (resultSet, rowNum) -> mapMessage(resultSet);
	}

	@Override
	public AssistantSession saveSession(AssistantSession session) {
		jdbcTemplate.update("""
				INSERT INTO ai_assistant_session
				(id, tenant_id, owner_user_id, owner_subject_type, title, title_source, status, active_scopes, active_scopes_json,
				 active_skill_code, last_source_route, last_message_at, archived_at, version, message_sequence,
				 created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
				ON DUPLICATE KEY UPDATE title = VALUES(title), status = VALUES(status),
				     title_source = VALUES(title_source),
					 active_scopes = VALUES(active_scopes), active_scopes_json = VALUES(active_scopes_json),
					 active_skill_code = VALUES(active_skill_code), last_source_route = VALUES(last_source_route),
					 last_message_at = VALUES(last_message_at), archived_at = VALUES(archived_at),
					 version = VALUES(version), updated_at = VALUES(updated_at)
				""", session.assistantSessionId(), session.tenantId(), session.ownerUserId(), session.ownerSubjectType(), session.title(),
				session.titleSource().name(), session.status().name(), encodeScopes(session.activeScopes()),
				toJson(session.activeScopes()), session.activeSkillCode(), session.lastSourceRoute(),
				timestamp(session.lastMessageAt()), timestamp(session.archivedAt()), session.version(),
				Timestamp.from(session.createdAt()), Timestamp.from(session.updatedAt()));
		return session;
	}

	@Override
	public Optional<AssistantSession> findSession(String sessionId) {
		return jdbcTemplate.query("SELECT * FROM ai_assistant_session WHERE id = ?", this::mapSession, sessionId)
			.stream()
			.findFirst();
	}

	@Override
	public List<AssistantSessionSummary> findSessions(String tenantId, String ownerUserId, SessionStatus status,
			String keyword, Instant beforeLastMessageAt, String beforeId, int limit) {
		return findSessions(tenantId, ownerUserId, status, keyword, beforeLastMessageAt, beforeId, limit, "ADMIN_USER");
	}

	@Override
	public List<AssistantSessionSummary> findSessions(String tenantId, String ownerUserId, SessionStatus status,
			String keyword, Instant beforeLastMessageAt, String beforeId, int limit, String subjectType) {
		StringBuilder sql = new StringBuilder("""
				SELECT id, title, status, active_scopes, active_scopes_json, active_skill_code, last_source_route,
				       message_sequence, last_message_at, archived_at, version, created_at, updated_at
				FROM ai_assistant_session
				WHERE tenant_id = ? AND owner_user_id = ? AND owner_subject_type = ? AND status = ?
				  AND message_sequence > 0 AND last_message_at IS NOT NULL
				""");
		List<Object> arguments = new ArrayList<>(List.of(tenantId, ownerUserId, subjectType, status.name()));
		if (keyword != null && !keyword.isBlank()) {
			sql.append(" AND LOWER(title) LIKE ?");
			arguments.add("%" + keyword.toLowerCase(java.util.Locale.ROOT) + "%");
		}
		if (beforeLastMessageAt != null && beforeId != null) {
			sql.append(" AND (last_message_at < ? OR (last_message_at = ? AND id < ?))");
			Timestamp timestamp = Timestamp.from(beforeLastMessageAt);
			arguments.add(timestamp);
			arguments.add(timestamp);
			arguments.add(beforeId);
		}
		sql.append(" ORDER BY last_message_at DESC, id DESC LIMIT ?");
		arguments.add(Math.max(1, limit));
		return jdbcTemplate.query(sql.toString(), this::mapSummary, arguments.toArray());
	}

	@Override
	public Optional<AssistantSession> updateTitle(String sessionId, String tenantId, String ownerUserId, String title,
			long expectedVersion) {
		return updateTitle(sessionId, tenantId, ownerUserId, title, expectedVersion, "ADMIN_USER");
	}

	@Override
	public Optional<AssistantSession> updateTitle(String sessionId, String tenantId, String ownerUserId, String title,
			long expectedVersion, String subjectType) {
		int updated = jdbcTemplate.update("""
				UPDATE ai_assistant_session
				SET title = ?, title_source = 'MANUAL', version = version + 1,
				    updated_at = CURRENT_TIMESTAMP(6)
				WHERE id = ? AND tenant_id = ? AND owner_user_id = ? AND owner_subject_type = ? AND version = ?
				""", title, sessionId, tenantId, ownerUserId, subjectType, expectedVersion);
		return updated == 1 ? findSession(sessionId) : Optional.empty();
	}

	@Override
	public Optional<AssistantSession> updatePreferences(String sessionId, String tenantId, String ownerUserId,
			List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute, long expectedVersion) {
		return updatePreferences(sessionId, tenantId, ownerUserId, activeScopes, activeSkillCode, lastSourceRoute, expectedVersion, "ADMIN_USER");
	}

	@Override
	public Optional<AssistantSession> updatePreferences(String sessionId, String tenantId, String ownerUserId,
			List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute, long expectedVersion, String subjectType) {
		int updated = jdbcTemplate.update("""
				UPDATE ai_assistant_session
				SET active_scopes = ?, active_scopes_json = ?, active_skill_code = ?, last_source_route = ?,
				    version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
				WHERE id = ? AND tenant_id = ? AND owner_user_id = ? AND owner_subject_type = ? AND version = ?
				""", encodeScopes(activeScopes), toJson(activeScopes), activeSkillCode, lastSourceRoute, sessionId,
				tenantId, ownerUserId, subjectType, expectedVersion);
		return updated == 1 ? findSession(sessionId) : Optional.empty();
	}

	@Override
	public Optional<AssistantSession> updateStatus(String sessionId, String tenantId, String ownerUserId,
			SessionStatus status, Instant archivedAt, long expectedVersion) {
		return updateStatus(sessionId, tenantId, ownerUserId, status, archivedAt, expectedVersion, "ADMIN_USER");
	}

	@Override
	public Optional<AssistantSession> updateStatus(String sessionId, String tenantId, String ownerUserId,
			SessionStatus status, Instant archivedAt, long expectedVersion, String subjectType) {
		int updated = jdbcTemplate.update("""
				UPDATE ai_assistant_session
				SET status = ?, archived_at = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP(6)
				WHERE id = ? AND tenant_id = ? AND owner_user_id = ? AND owner_subject_type = ? AND version = ?
				""", status.name(), timestamp(archivedAt), sessionId, tenantId, ownerUserId, subjectType, expectedVersion);
		return updated == 1 ? findSession(sessionId) : Optional.empty();
	}

	@Override
	public Optional<AssistantMessage> findMessage(String sessionId, String messageId) {
		return jdbcTemplate
			.query("SELECT * FROM ai_assistant_message WHERE session_id = ? AND id = ?", messageRowMapper, sessionId,
					messageId)
			.stream()
			.findFirst();
	}

	@Override
	public AssistantMessage appendMessage(AssistantMessage message) {
		jdbcTemplate.update("""
				INSERT INTO ai_assistant_message
				(id, session_id, role, content, status, sequence_no, context_pack_id, attachments_json, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", message.messageId(), message.assistantSessionId(), message.role().name(), message.content(),
				message.status().name(), message.sequenceNo(), message.contextPackId(), toJson(message.attachments()),
				Timestamp.from(message.createdAt()));
		return message;
	}

	@Override
	public AssistantMessage updateMessage(AssistantMessage message) {
		int updated = jdbcTemplate.update("""
				UPDATE ai_assistant_message
				SET role = ?, content = ?, status = ?, context_pack_id = ?, attachments_json = ?
				WHERE id = ? AND session_id = ?
				""", message.role().name(), message.content(), message.status().name(), message.contextPackId(),
				toJson(message.attachments()), message.messageId(), message.assistantSessionId());
		if (updated != 1) {
			throw new IllegalStateException("Assistant message not found: " + message.messageId());
		}
		return message;
	}

	@Override
	public List<AssistantMessage> findMessages(String sessionId) {
		return jdbcTemplate.query("SELECT * FROM ai_assistant_message WHERE session_id = ? ORDER BY sequence_no",
				messageRowMapper, sessionId);
	}

	@Override
	public List<AssistantMessage> findMessagesBefore(String sessionId, Long beforeSequence, int limit) {
		int safeLimit = Math.max(1, limit);
		if (beforeSequence == null) {
			return jdbcTemplate.query("""
					SELECT * FROM (
					  SELECT * FROM ai_assistant_message
					  WHERE session_id = ? ORDER BY sequence_no DESC LIMIT ?
					) recent ORDER BY sequence_no
					""", messageRowMapper, sessionId, safeLimit);
		}
		return jdbcTemplate.query("""
				SELECT * FROM (
				  SELECT * FROM ai_assistant_message
				  WHERE session_id = ? AND sequence_no < ? ORDER BY sequence_no DESC LIMIT ?
				) recent ORDER BY sequence_no
				""", messageRowMapper, sessionId, beforeSequence, safeLimit);
	}

	@Override
	public void recordRunStarted(String sessionId, String tenantId, String ownerUserId, String autoTitle,
			List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute, Instant startedAt) {
		recordRunStarted(sessionId, tenantId, ownerUserId, autoTitle, activeScopes, activeSkillCode, lastSourceRoute, startedAt, "ADMIN_USER");
	}

	@Override
	public void recordRunStarted(String sessionId, String tenantId, String ownerUserId, String autoTitle,
			List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute, Instant startedAt, String subjectType) {
		int updated = jdbcTemplate.update("""
				UPDATE ai_assistant_session
				SET title = CASE WHEN title_source = 'DEFAULT' THEN ? ELSE title END,
				    title_source = CASE WHEN title_source = 'DEFAULT' THEN 'AUTO' ELSE title_source END,
				    active_scopes = ?, active_scopes_json = ?, active_skill_code = ?,
				    last_source_route = COALESCE(?, last_source_route), last_message_at = ?,
				    version = version + 1, updated_at = ?
				WHERE id = ? AND tenant_id = ? AND owner_user_id = ? AND owner_subject_type = ? AND status = 'ACTIVE'
				""", autoTitle, encodeScopes(activeScopes), toJson(activeScopes), activeSkillCode, lastSourceRoute,
				Timestamp.from(startedAt), Timestamp.from(startedAt), sessionId, tenantId, ownerUserId, subjectType);
		if (updated != 1) {
			throw new IllegalStateException("Assistant session not found: " + sessionId);
		}
	}

	@Override
	public AssistantContextPack saveContextPack(AssistantContextPack contextPack) {
		jdbcTemplate.update("""
				INSERT INTO ai_assistant_context_pack
				(id, session_id, message_id, identity_fingerprint, payload_json, created_at, expires_at)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", contextPack.contextPackId(), contextPack.assistantSessionId(), contextPack.messageId(),
				contextPack.identityFingerprint(), toJson(contextPack), Timestamp.from(contextPack.createdAt()),
				Timestamp.from(contextPack.expiresAt()));
		Set<String> savedCitationIds = new HashSet<>();
		for (var fragment : contextPack.fragments()) {
			if (fragment.citation() == null || !savedCitationIds.add(fragment.citation().citationId())) {
				continue;
			}
			jdbcTemplate.update("""
					INSERT INTO ai_assistant_citation
					(id, context_pack_id, provider_code, source_id, source_version, route_type, route_name,
					 route_params_json, created_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", fragment.citation().citationId(), contextPack.contextPackId(),
					fragment.citation().provider().value(), fragment.citation().sourceId(),
					fragment.citation().sourceVersion(), fragment.citation().routeType(),
					fragment.citation().routeName(), toJson(fragment.citation().routeParams()),
					Timestamp.from(contextPack.createdAt()));
		}
		return contextPack;
	}

	@Override
	public Optional<AssistantContextPack> findContextPack(String contextPackId) {
		return jdbcTemplate
			.query("SELECT payload_json FROM ai_assistant_context_pack WHERE id = ?",
					(resultSet, rowNum) -> fromJson(resultSet.getString("payload_json"), AssistantContextPack.class),
					contextPackId)
			.stream()
			.findFirst();
	}

	@Override
	public Optional<MessageExchange> findExchange(String sessionId, String clientMessageId) {
		return jdbcTemplate
			.query("""
					SELECT payload_json FROM ai_assistant_exchange
					WHERE session_id = ? AND client_message_id = ?
					""", (resultSet, rowNum) -> fromJson(resultSet.getString("payload_json"), MessageExchange.class),
					sessionId, clientMessageId)
			.stream()
			.findFirst();
	}

	@Override
	public MessageExchange saveExchange(String clientMessageId, MessageExchange exchange) {
		jdbcTemplate.update("""
				INSERT INTO ai_assistant_exchange
				(session_id, client_message_id, payload_json, created_at)
				VALUES (?, ?, ?, ?)
				""", exchange.session().assistantSessionId(), clientMessageId, toJson(exchange),
				Timestamp.from(Instant.now()));
		return exchange;
	}

	@Override
	public Optional<String> findExternalConversation(String sessionId, String runtimeKey) {
		return jdbcTemplate.query("""
				SELECT conversation_id FROM ai_assistant_external_conversation
				WHERE session_id = ? AND runtime_key = ?
				""", (resultSet, rowNum) -> resultSet.getString("conversation_id"), sessionId, runtimeKey)
			.stream()
			.findFirst();
	}

	@Override
	public void saveExternalConversation(String sessionId, String runtimeKey, String conversationId) {
		if (conversationId == null || conversationId.isBlank()) {
			return;
		}
		jdbcTemplate.update("""
				INSERT INTO ai_assistant_external_conversation
				(session_id, runtime_key, conversation_id, updated_at)
				VALUES (?, ?, ?, ?)
				ON DUPLICATE KEY UPDATE conversation_id = VALUES(conversation_id), updated_at = VALUES(updated_at)
				""", sessionId, runtimeKey, conversationId, Timestamp.from(Instant.now()));
	}

	@Override
	public void saveExecutionTrace(String sessionId, String messageId, String clientMessageId,
			AssistantExecutionTrace trace, Instant createdAt) {
		if (trace == null || trace.executionRunId() == null) {
			return;
		}
		String tenantId = jdbcTemplate.queryForObject("SELECT tenant_id FROM ai_assistant_session WHERE id = ?",
				String.class, sessionId);
		jdbcTemplate.update("""
				INSERT INTO ai_assistant_execution_run
				(id, session_id, message_id, client_message_id, execution_plan, runtime_mode, runtime_id,
				 task_id, workflow_run_id, conversation_id, activated_skills_json, executed_skills_json,
				 status, execution_status, error_code, tenant_id,
				 agent_id, agent_version, provider, model, prompt_tokens, completion_tokens, total_tokens,
				 usage_source, request_id, skill_versions_json, skill_release_id, skill_release_version,
				 skill_package_sha256, skill_instruction_digest, citations_json, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", trace.executionRunId(), sessionId, messageId, clientMessageId, trace.executionPlan().name(),
				trace.runtimeMode(), trace.runtimeId(), trace.taskId(), trace.workflowRunId(), trace.conversationId(),
				toJson(trace.activatedSkillCodes()), toJson(trace.executedSkillCodes()), trace.executionStatus(),
				trace.executionStatus(), trace.errorCode(), tenantId, trace.agentId(), trace.agentVersion(),
				trace.provider(), trace.model(), trace.promptTokens(), trace.completionTokens(), trace.totalTokens(),
				trace.usageSource(), trace.requestId(), toJson(trace.activatedSkillCodes()), trace.skillReleaseId(),
				trace.skillReleaseVersion(), trace.skillPackageSha256(), trace.skillInstructionDigest(), null,
				Timestamp.from(createdAt));
	}

	@Override
	public List<java.util.Map<String, Object>> listExecutionRuns(String tenantId, String ownerUserId,
			boolean tenantWide, int limit) {
		return listExecutionRuns(tenantId, ownerUserId, tenantWide, limit, "ADMIN_USER");
	}

	@Override
	public List<java.util.Map<String, Object>> listExecutionRuns(String tenantId, String ownerUserId,
			boolean tenantWide, int limit, String subjectType) {
		int safeLimit = Math.max(1, Math.min(limit, 200));
		String select = """
				SELECT e.id, e.session_id, e.message_id, e.client_message_id, e.execution_plan, e.runtime_mode,
				       e.runtime_id, e.task_id, e.workflow_run_id, e.conversation_id, e.activated_skills_json,
				       e.executed_skills_json, e.status, e.execution_status, e.error_code, e.agent_id,
				       e.agent_version, e.skill_versions_json, e.skill_release_id, e.skill_release_version,
				       e.skill_package_sha256, e.skill_instruction_digest,
				       e.provider, e.model, e.prompt_tokens, e.completion_tokens, e.total_tokens,
				       e.usage_source, e.request_id, e.duration_ms, e.citations_json, e.created_at
				FROM ai_assistant_execution_run e
				JOIN ai_assistant_session s ON s.id = e.session_id AND s.tenant_id = e.tenant_id
				WHERE e.tenant_id = ? AND s.owner_subject_type = ?
				""";
		String orderAndLimit = """
				ORDER BY e.created_at DESC
				LIMIT ?
				""";
		if (tenantWide && !"ADMIN_USER".equals(subjectType)) throw new IllegalArgumentException("Consumer tenant-wide access denied");
		if (tenantWide) {
			return jdbcTemplate.queryForList(select + orderAndLimit, tenantId, subjectType, safeLimit);
		}
		return jdbcTemplate.queryForList(select + "AND s.owner_user_id = ?\n" + orderAndLimit, tenantId, subjectType, ownerUserId,
				safeLimit);
	}

	@Override
	public long nextSequence(String sessionId) {
		Long sequence = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
			try (PreparedStatement update = connection.prepareStatement("""
					UPDATE ai_assistant_session
					SET message_sequence = LAST_INSERT_ID(message_sequence + 1), updated_at = CURRENT_TIMESTAMP(6)
					WHERE id = ?
					""")) {
				update.setString(1, sessionId);
				if (update.executeUpdate() != 1) {
					throw new SQLException("Assistant session not found: " + sessionId);
				}
			}
			try (PreparedStatement query = connection.prepareStatement("SELECT LAST_INSERT_ID()");
					ResultSet resultSet = query.executeQuery()) {
				if (!resultSet.next()) {
					throw new SQLException("Assistant sequence allocation failed");
				}
				return resultSet.getLong(1);
			}
		});
		return sequence == null ? 0 : sequence;
	}

	private String toJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Assistant persistence JSON serialization failed", ex);
		}
	}

	private <T> T fromJson(String value, Class<T> type) {
		try {
			return objectMapper.readValue(value, type);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Assistant persistence JSON deserialization failed", ex);
		}
	}

	private AssistantSession mapSession(ResultSet resultSet, int rowNumber) throws SQLException {
		String scopesJson = resultSet.getString("active_scopes_json");
		return new AssistantSession(resultSet.getString("id"), resultSet.getString("tenant_id"),
				resultSet.getString("owner_user_id"), resultSet.getString("title"),
				SessionStatus.valueOf(resultSet.getString("status")),
				decodeScopes(scopesJson, resultSet.getString("active_scopes")),
				resultSet.getString("active_skill_code"), resultSet.getString("last_source_route"),
				TitleSource.valueOf(resultSet.getString("title_source")), instant(resultSet, "last_message_at"),
				instant(resultSet, "archived_at"), resultSet.getLong("version"), instant(resultSet, "created_at"),
				instant(resultSet, "updated_at"), resultSet.getString("owner_subject_type"));
	}

	private AssistantSessionSummary mapSummary(ResultSet resultSet, int rowNumber) throws SQLException {
		String scopesJson = resultSet.getString("active_scopes_json");
		return new AssistantSessionSummary(resultSet.getString("id"), resultSet.getString("title"),
				SessionStatus.valueOf(resultSet.getString("status")),
				decodeScopes(scopesJson, resultSet.getString("active_scopes")),
				resultSet.getString("active_skill_code"), resultSet.getString("last_source_route"),
				resultSet.getLong("message_sequence"), instant(resultSet, "last_message_at"),
				instant(resultSet, "archived_at"), resultSet.getLong("version"), instant(resultSet, "created_at"),
				instant(resultSet, "updated_at"));
	}

	private AssistantMessage mapMessage(ResultSet resultSet) throws SQLException {
		String attachmentsJson = resultSet.getString("attachments_json");
		return new AssistantMessage(resultSet.getString("id"), resultSet.getString("session_id"),
				MessageRole.valueOf(resultSet.getString("role")), resultSet.getString("content"),
				MessageStatus.valueOf(resultSet.getString("status")), resultSet.getLong("sequence_no"),
				resultSet.getString("context_pack_id"), instant(resultSet, "created_at"),
				decodeAttachments(attachmentsJson));
	}

	private List<AssistantAttachment> decodeAttachments(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readerForListOf(AssistantAttachment.class).readValue(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Assistant attachments_json is invalid", ex);
		}
	}

	private static Instant instant(ResultSet resultSet, String column) throws SQLException {
		Timestamp timestamp = resultSet.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private static Timestamp timestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	private static String encodeScopes(List<ProviderId> scopes) {
		return scopes.stream().map(ProviderId::value).reduce((left, right) -> left + "," + right).orElse("");
	}

	private List<ProviderId> decodeScopes(String scopesJson, String legacyScopes) {
		if (scopesJson != null && !scopesJson.isBlank()) {
			try {
				return objectMapper.readerForListOf(ProviderId.class).readValue(scopesJson);
			}
			catch (JsonProcessingException ex) {
				throw new IllegalStateException("Assistant active_scopes_json is invalid", ex);
			}
		}
		if (legacyScopes == null || legacyScopes.isBlank()) {
			return List.of();
		}
		return Arrays.stream(legacyScopes.split(",")).map(ProviderId::of).toList();
	}

}
