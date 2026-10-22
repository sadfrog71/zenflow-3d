package com.deepdimen.matrix.assistant.session;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantContextPack;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSession;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSessionSummary;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageExchange;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SessionStatus;

public interface AssistantSessionRepository {

	AssistantSession saveSession(AssistantSession session);

	Optional<AssistantSession> findSession(String sessionId);

	List<AssistantSessionSummary> findSessions(String tenantId, String ownerUserId, SessionStatus status,
			String keyword, Instant beforeLastMessageAt, String beforeId, int limit);

	Optional<AssistantSession> updateTitle(String sessionId, String tenantId, String ownerUserId, String title,
			long expectedVersion);

	Optional<AssistantSession> updatePreferences(String sessionId, String tenantId, String ownerUserId,
			List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute, long expectedVersion);

	Optional<AssistantSession> updateStatus(String sessionId, String tenantId, String ownerUserId, SessionStatus status,
			Instant archivedAt, long expectedVersion);

	Optional<AssistantMessage> findMessage(String sessionId, String messageId);

	AssistantMessage appendMessage(AssistantMessage message);

	AssistantMessage updateMessage(AssistantMessage message);

	List<AssistantMessage> findMessages(String sessionId);

	List<AssistantMessage> findMessagesBefore(String sessionId, Long beforeSequence, int limit);

	void recordRunStarted(String sessionId, String tenantId, String ownerUserId, String autoTitle,
			List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute, Instant startedAt);

	AssistantContextPack saveContextPack(AssistantContextPack contextPack);

	Optional<AssistantContextPack> findContextPack(String contextPackId);

	Optional<MessageExchange> findExchange(String sessionId, String clientMessageId);

	MessageExchange saveExchange(String clientMessageId, MessageExchange exchange);

	Optional<String> findExternalConversation(String sessionId, String runtimeKey);

	void saveExternalConversation(String sessionId, String runtimeKey, String conversationId);

	void saveExecutionTrace(String sessionId, String messageId, String clientMessageId, AssistantExecutionTrace trace,
			Instant createdAt);

	/** 查询执行记录；普通路径必须按 owner 限制，只有管理端点可开启 tenantWide。 */
	default List<Map<String, Object>> listExecutionRuns(String tenantId, String ownerUserId, boolean tenantWide,
			int limit) {
		return List.of();
	}

	long nextSequence(String sessionId);


	default Optional<AssistantSession> updateStatus(String sessionId, String tenantId, String ownerUserId, SessionStatus status,
			Instant archivedAt, long expectedVersion, String subjectType) {
		if (!"ADMIN_USER".equals(subjectType)) throw new IllegalStateException("Subject-aware repository required");
		return updateStatus(sessionId, tenantId, ownerUserId, status, archivedAt, expectedVersion);
	}

	default void recordRunStarted(String sessionId, String tenantId, String ownerUserId, String autoTitle,
			List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute, Instant startedAt, String subjectType) {
		if (!"ADMIN_USER".equals(subjectType)) throw new IllegalStateException("Subject-aware repository required");
		recordRunStarted(sessionId, tenantId, ownerUserId, autoTitle, activeScopes, activeSkillCode, lastSourceRoute, startedAt);
	}

	default Optional<AssistantSession> updateTitle(String sessionId, String tenantId, String ownerUserId, String title,
			long expectedVersion, String subjectType) {
		if (!"ADMIN_USER".equals(subjectType)) throw new IllegalStateException("Subject-aware repository required");
		return updateTitle(sessionId, tenantId, ownerUserId, title, expectedVersion);
	}

	default List<Map<String, Object>> listExecutionRuns(String tenantId, String ownerUserId, boolean tenantWide,
			int limit, String subjectType) {
		if (!"ADMIN_USER".equals(subjectType)) throw new IllegalStateException("Subject-aware repository required");
		return listExecutionRuns(tenantId, ownerUserId, tenantWide, limit);
	}

	default List<AssistantSessionSummary> findSessions(String tenantId, String ownerUserId, SessionStatus status,
			String keyword, Instant beforeLastMessageAt, String beforeId, int limit, String subjectType) {
		if (!"ADMIN_USER".equals(subjectType)) throw new IllegalStateException("Subject-aware repository required");
		return findSessions(tenantId, ownerUserId, status, keyword, beforeLastMessageAt, beforeId, limit);
	}

	default Optional<AssistantSession> updatePreferences(String sessionId, String tenantId, String ownerUserId,
			List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute, long expectedVersion, String subjectType) {
		if (!"ADMIN_USER".equals(subjectType)) throw new IllegalStateException("Subject-aware repository required");
		return updatePreferences(sessionId, tenantId, ownerUserId, activeScopes, activeSkillCode, lastSourceRoute, expectedVersion);
	}

}
