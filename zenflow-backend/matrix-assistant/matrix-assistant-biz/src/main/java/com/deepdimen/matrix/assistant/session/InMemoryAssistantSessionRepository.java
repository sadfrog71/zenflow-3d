package com.deepdimen.matrix.assistant.session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantContextPack;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSession;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSessionSummary;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageExchange;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SessionStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.TitleSource;

@Repository
@ConditionalOnMissingBean(AssistantSessionRepository.class)
@ConditionalOnProperty(prefix = "matrix.assistant", name = "repository", havingValue = "memory", matchIfMissing = true)
public class InMemoryAssistantSessionRepository implements AssistantSessionRepository {

	private final Map<String, AssistantSession> sessions = new ConcurrentHashMap<>();

	private final Map<String, List<AssistantMessage>> messages = new ConcurrentHashMap<>();

	private final Map<String, AssistantContextPack> contextPacks = new ConcurrentHashMap<>();

	private final Map<String, MessageExchange> exchanges = new ConcurrentHashMap<>();

	private final Map<String, String> externalConversations = new ConcurrentHashMap<>();

	private final Map<String, AssistantExecutionTrace> executionTraces = new ConcurrentHashMap<>();

	private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();

	@Override
	public AssistantSession saveSession(AssistantSession session) {
		sessions.put(session.assistantSessionId(), session);
		return session;
	}

	@Override
	public Optional<AssistantSession> findSession(String sessionId) {
		return Optional.ofNullable(sessions.get(sessionId));
	}

	@Override
	public List<AssistantSessionSummary> findSessions(String tenantId, String ownerUserId, SessionStatus status,
			String keyword, Instant beforeLastMessageAt, String beforeId, int limit) {
		return findSessions(tenantId, ownerUserId, status, keyword, beforeLastMessageAt, beforeId, limit, "ADMIN_USER");
	}

	@Override
	public List<AssistantSessionSummary> findSessions(String tenantId, String ownerUserId, SessionStatus status,
			String keyword, Instant beforeLastMessageAt, String beforeId, int limit, String subjectType) {
		String normalizedKeyword = keyword == null ? null : keyword.toLowerCase(java.util.Locale.ROOT);
		Comparator<AssistantSession> order = Comparator.comparing(AssistantSession::lastMessageAt)
			.reversed()
			.thenComparing(AssistantSession::assistantSessionId, Comparator.reverseOrder());
		return sessions.values()
			.stream()
			.filter(session -> tenantId.equals(session.tenantId()) && ownerUserId.equals(session.ownerUserId()) && subjectType.equals(session.ownerSubjectType()))
			.filter(session -> session.status() == status && session.lastMessageAt() != null)
			.filter(session -> sequences.getOrDefault(session.assistantSessionId(), new AtomicLong()).get() > 0)
			.filter(session -> normalizedKeyword == null || normalizedKeyword.isBlank()
					|| session.title().toLowerCase(java.util.Locale.ROOT).contains(normalizedKeyword))
			.filter(session -> beforeLastMessageAt == null || session.lastMessageAt().isBefore(beforeLastMessageAt)
					|| session.lastMessageAt().equals(beforeLastMessageAt) && beforeId != null
							&& session.assistantSessionId().compareTo(beforeId) < 0)
			.sorted(order)
			.limit(Math.max(1, limit))
			.map(session -> new AssistantSessionSummary(session.assistantSessionId(), session.title(), session.status(),
					session.activeScopes(), session.activeSkillCode(), session.lastSourceRoute(),
					sequences.get(session.assistantSessionId()).get(), session.lastMessageAt(), session.archivedAt(),
					session.version(), session.createdAt(), session.updatedAt()))
			.toList();
	}

	@Override
	public Optional<AssistantSession> updateTitle(String sessionId, String tenantId, String ownerUserId, String title,
			long expectedVersion) {
		return updateTitle(sessionId, tenantId, ownerUserId, title, expectedVersion, "ADMIN_USER");
	}

	@Override
	public Optional<AssistantSession> updateTitle(String sessionId, String tenantId, String ownerUserId, String title,
			long expectedVersion, String subjectType) {
		AtomicReference<AssistantSession> updated = new AtomicReference<>();
		sessions.computeIfPresent(sessionId, (ignored, current) -> {
			if (!tenantId.equals(current.tenantId()) || !ownerUserId.equals(current.ownerUserId()) || !subjectType.equals(current.ownerSubjectType())
					|| current.version() != expectedVersion) {
				return current;
			}
			Instant now = Instant.now();
			AssistantSession replacement = new AssistantSession(current.assistantSessionId(), current.tenantId(),
					current.ownerUserId(), title, current.status(), current.activeScopes(), current.activeSkillCode(),
					current.lastSourceRoute(), TitleSource.MANUAL, current.lastMessageAt(), current.archivedAt(),
					current.version() + 1, current.createdAt(), now, current.ownerSubjectType());
			updated.set(replacement);
			return replacement;
		});
		return Optional.ofNullable(updated.get());
	}

	@Override
	public Optional<AssistantSession> updatePreferences(String sessionId, String tenantId, String ownerUserId,
			List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute, long expectedVersion) {
		return updatePreferences(sessionId, tenantId, ownerUserId, activeScopes, activeSkillCode, lastSourceRoute, expectedVersion, "ADMIN_USER");
	}

	@Override
	public Optional<AssistantSession> updatePreferences(String sessionId, String tenantId, String ownerUserId,
			List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute, long expectedVersion, String subjectType) {
		AtomicReference<AssistantSession> updated = new AtomicReference<>();
		sessions.computeIfPresent(sessionId, (ignored, current) -> {
			if (!tenantId.equals(current.tenantId()) || !ownerUserId.equals(current.ownerUserId()) || !subjectType.equals(current.ownerSubjectType())
					|| current.version() != expectedVersion) {
				return current;
			}
			Instant now = Instant.now();
			AssistantSession replacement = new AssistantSession(current.assistantSessionId(), current.tenantId(),
					current.ownerUserId(), current.title(), current.status(), activeScopes, activeSkillCode,
					lastSourceRoute, current.titleSource(), current.lastMessageAt(), current.archivedAt(),
					current.version() + 1, current.createdAt(), now, current.ownerSubjectType());
			updated.set(replacement);
			return replacement;
		});
		return Optional.ofNullable(updated.get());
	}

	@Override
	public Optional<AssistantSession> updateStatus(String sessionId, String tenantId, String ownerUserId,
			SessionStatus status, Instant archivedAt, long expectedVersion) {
		return updateStatus(sessionId, tenantId, ownerUserId, status, archivedAt, expectedVersion, "ADMIN_USER");
	}

	@Override
	public Optional<AssistantSession> updateStatus(String sessionId, String tenantId, String ownerUserId,
			SessionStatus status, Instant archivedAt, long expectedVersion, String subjectType) {
		AtomicReference<AssistantSession> updated = new AtomicReference<>();
		sessions.computeIfPresent(sessionId, (ignored, current) -> {
			if (!tenantId.equals(current.tenantId()) || !ownerUserId.equals(current.ownerUserId()) || !subjectType.equals(current.ownerSubjectType())
					|| current.version() != expectedVersion) {
				return current;
			}
			Instant now = Instant.now();
			AssistantSession replacement = new AssistantSession(current.assistantSessionId(), current.tenantId(),
					current.ownerUserId(), current.title(), status, current.activeScopes(), current.activeSkillCode(),
					current.lastSourceRoute(), current.titleSource(), current.lastMessageAt(), archivedAt,
					current.version() + 1, current.createdAt(), now, current.ownerSubjectType());
			updated.set(replacement);
			return replacement;
		});
		return Optional.ofNullable(updated.get());
	}

	@Override
	public Optional<AssistantMessage> findMessage(String sessionId, String messageId) {
		return findMessages(sessionId).stream().filter(message -> message.messageId().equals(messageId)).findFirst();
	}

	@Override
	public AssistantMessage appendMessage(AssistantMessage message) {
		messages.computeIfAbsent(message.assistantSessionId(), ignored -> new ArrayList<>());
		synchronized (messages.get(message.assistantSessionId())) {
			messages.get(message.assistantSessionId()).add(message);
		}
		return message;
	}

	@Override
	public AssistantMessage updateMessage(AssistantMessage message) {
		List<AssistantMessage> sessionMessages = messages.get(message.assistantSessionId());
		if (sessionMessages == null) {
			throw new IllegalStateException("Assistant message not found: " + message.messageId());
		}
		synchronized (sessionMessages) {
			for (int index = 0; index < sessionMessages.size(); index++) {
				if (message.messageId().equals(sessionMessages.get(index).messageId())) {
					sessionMessages.set(index, message);
					return message;
				}
			}
		}
		throw new IllegalStateException("Assistant message not found: " + message.messageId());
	}

	@Override
	public List<AssistantMessage> findMessages(String sessionId) {
		List<AssistantMessage> sessionMessages = messages.get(sessionId);
		if (sessionMessages == null) {
			return List.of();
		}
		synchronized (sessionMessages) {
			return sessionMessages.stream().sorted(Comparator.comparingLong(AssistantMessage::sequenceNo)).toList();
		}
	}

	@Override
	public List<AssistantMessage> findMessagesBefore(String sessionId, Long beforeSequence, int limit) {
		return findMessages(sessionId).stream()
			.filter(message -> beforeSequence == null || message.sequenceNo() < beforeSequence)
			.sorted(Comparator.comparingLong(AssistantMessage::sequenceNo).reversed())
			.limit(Math.max(1, limit))
			.sorted(Comparator.comparingLong(AssistantMessage::sequenceNo))
			.toList();
	}

	@Override
	public void recordRunStarted(String sessionId, String tenantId, String ownerUserId, String autoTitle,
			List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute, Instant startedAt) {
		recordRunStarted(sessionId, tenantId, ownerUserId, autoTitle, activeScopes, activeSkillCode, lastSourceRoute, startedAt, "ADMIN_USER");
	}

	@Override
	public void recordRunStarted(String sessionId, String tenantId, String ownerUserId, String autoTitle,
			List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute, Instant startedAt, String subjectType) {
		sessions.compute(sessionId, (ignored, current) -> {
			if (current == null || !current.tenantId().equals(tenantId) || !current.ownerUserId().equals(ownerUserId) || !subjectType.equals(current.ownerSubjectType())
					|| current.status() != SessionStatus.ACTIVE) {
				throw new IllegalStateException("Active assistant session not found: " + sessionId);
			}
			boolean useAutoTitle = current.titleSource() == TitleSource.DEFAULT;
			return new AssistantSession(current.assistantSessionId(), current.tenantId(), current.ownerUserId(),
					useAutoTitle ? autoTitle : current.title(), current.status(), activeScopes, activeSkillCode,
					lastSourceRoute == null ? current.lastSourceRoute() : lastSourceRoute,
					useAutoTitle ? TitleSource.AUTO : current.titleSource(), startedAt, current.archivedAt(),
					current.version() + 1, current.createdAt(), startedAt, current.ownerSubjectType());
		});
	}

	@Override
	public AssistantContextPack saveContextPack(AssistantContextPack contextPack) {
		contextPacks.put(contextPack.contextPackId(), contextPack);
		return contextPack;
	}

	@Override
	public Optional<AssistantContextPack> findContextPack(String contextPackId) {
		return Optional.ofNullable(contextPacks.get(contextPackId));
	}

	@Override
	public Optional<MessageExchange> findExchange(String sessionId, String clientMessageId) {
		return Optional.ofNullable(exchanges.get(exchangeKey(sessionId, clientMessageId)));
	}

	@Override
	public MessageExchange saveExchange(String clientMessageId, MessageExchange exchange) {
		exchanges.put(exchangeKey(exchange.session().assistantSessionId(), clientMessageId), exchange);
		return exchange;
	}

	@Override
	public Optional<String> findExternalConversation(String sessionId, String runtimeKey) {
		return Optional.ofNullable(externalConversations.get(externalConversationKey(sessionId, runtimeKey)));
	}

	@Override
	public void saveExternalConversation(String sessionId, String runtimeKey, String conversationId) {
		if (conversationId != null && !conversationId.isBlank()) {
			externalConversations.put(externalConversationKey(sessionId, runtimeKey), conversationId);
		}
	}

	@Override
	public void saveExecutionTrace(String sessionId, String messageId, String clientMessageId,
			AssistantExecutionTrace trace, Instant createdAt) {
		if (trace != null && trace.executionRunId() != null) {
			executionTraces.put(trace.executionRunId(), trace);
		}
	}

	@Override
	public long nextSequence(String sessionId) {
		return sequences.computeIfAbsent(sessionId, ignored -> new AtomicLong()).incrementAndGet();
	}

	private String exchangeKey(String sessionId, String clientMessageId) {
		return sessionId + ":" + clientMessageId;
	}

	private String externalConversationKey(String sessionId, String runtimeKey) {
		return sessionId + ":" + runtimeKey;
	}

}
