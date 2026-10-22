package com.deepdimen.matrix.assistant.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.CreateSessionRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageRole;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SessionStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.TitleSource;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.UpdateSessionPreferencesRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.UpdateSessionStatusRequest;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.UpdateSessionTitleRequest;

class AssistantSessionHistoryServiceTest {

	private final IdentityEnvelope identity = identity("tenant-1", "user-1");

	private final InMemoryAssistantSessionRepository repository = new InMemoryAssistantSessionRepository();

	private final AssistantSessionService service = new AssistantSessionService(repository, null, null);

	@Test
	void directoryIsOwnerScopedSearchableAndExcludesEmptySessions() {
		var first = service.create(identity, new CreateSessionRequest("新对话", List.of(), "/todo"));
		append(first.assistantSessionId(), "工作任务周报整理", Instant.parse("2026-08-28T02:24:00Z"));
		var second = service.create(identity, new CreateSessionRequest("新对话", List.of(), "/knowledge"));
		append(second.assistantSessionId(), "Knowledge 检索异常排查", Instant.parse("2026-08-27T02:24:00Z"));
		service.create(identity, new CreateSessionRequest("空会话", List.of(), "/home"));
		IdentityEnvelope foreignIdentity = identity("tenant-1", "user-2");
		var foreign = service.create(foreignIdentity,
				new CreateSessionRequest("新对话", List.of(), "/todo"));
		append(foreignIdentity, foreign.assistantSessionId(), "工作任务周报整理",
				Instant.parse("2026-08-28T03:24:00Z"));

		var searched = service.listSessions(identity, SessionStatus.ACTIVE, "周报", null, 30);

		assertEquals(1, searched.items().size());
		assertEquals(first.assistantSessionId(), searched.items().get(0).assistantSessionId());
		assertEquals(1, searched.items().get(0).messageCount());
		assertFalse(searched.hasMore());

		var firstPage = service.listSessions(identity, SessionStatus.ACTIVE, null, null, 1);
		assertEquals(1, firstPage.items().size());
		assertTrue(firstPage.hasMore());
		assertNotNull(firstPage.nextCursor());
		var nextPage = service.listSessions(identity, SessionStatus.ACTIVE, null, firstPage.nextCursor(), 1);
		assertEquals(second.assistantSessionId(), nextPage.items().get(0).assistantSessionId());
	}

	@Test
	void renameUsesOptimisticLockAndMarksManualTitle() {
		var session = service.create(identity, new CreateSessionRequest("新对话", List.of(), "/todo"));

		var renamed = service.updateTitle(identity, session.assistantSessionId(),
				new UpdateSessionTitleRequest("  Q2   发布计划\n复盘  ", session.version()));

		assertEquals("Q2 发布计划 复盘", renamed.title());
		assertEquals(TitleSource.MANUAL, renamed.titleSource());
		assertEquals(2, renamed.version());
		ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
				() -> service.updateTitle(identity, session.assistantSessionId(),
						new UpdateSessionTitleRequest("旧版本覆盖", session.version())));
		assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
	}

	@Test
	void preferencesArchiveAndRestorePreserveConversationData() {
		var session = service.create(identity, new CreateSessionRequest("新对话", List.of(), "/todo"));
		append(session.assistantSessionId(), "测试归档", Instant.parse("2026-08-28T02:24:00Z"));
		var current = service.get(identity, session.assistantSessionId());

		var preferred = service.updatePreferences(identity, session.assistantSessionId(),
				new UpdateSessionPreferencesRequest(List.of(ProviderId.WORK), "work-summary", "/todo",
						current.version()));
		assertEquals(List.of(ProviderId.WORK), preferred.activeScopes());
		assertEquals("work-summary", preferred.activeSkillCode());

		var archived = service.archive(identity, session.assistantSessionId(),
				new UpdateSessionStatusRequest(preferred.version()));
		assertEquals(SessionStatus.ARCHIVED, archived.status());
		assertNotNull(archived.archivedAt());
		assertTrue(service.listSessions(identity, SessionStatus.ACTIVE, null, null, 30).items().isEmpty());
		assertEquals(1, service.listSessions(identity, SessionStatus.ARCHIVED, null, null, 30).items().size());
		assertEquals(1, service.messages(identity, session.assistantSessionId()).size());

		var restored = service.restore(identity, session.assistantSessionId(),
				new UpdateSessionStatusRequest(archived.version()));
		assertEquals(SessionStatus.ACTIVE, restored.status());
		assertEquals(null, restored.archivedAt());
	}

	@Test
	void messagePagingUsesStableSequenceCursorAndChecksOwnership() {
		var session = service.create(identity, new CreateSessionRequest("分页", List.of(), "/chat"));
		for (int index = 1; index <= 5; index++) {
			appendMessage(session.assistantSessionId(), "message-" + index, Instant.parse("2026-08-28T02:24:00Z"));
		}

		var first = service.messagePage(identity, session.assistantSessionId(), null, 2);
		assertEquals(List.of(4L, 5L), first.items().stream().map(AssistantMessage::sequenceNo).toList());
		assertEquals(4L, first.nextBeforeSequence());
		assertTrue(first.hasMore());
		var second = service.messagePage(identity, session.assistantSessionId(), first.nextBeforeSequence(), 2);
		assertEquals(List.of(2L, 3L), second.items().stream().map(AssistantMessage::sequenceNo).toList());

		ResponseStatusException hidden = assertThrows(ResponseStatusException.class,
				() -> service.messagePage(identity("tenant-1", "user-2"), session.assistantSessionId(), null, 2));
		assertEquals(HttpStatus.NOT_FOUND, hidden.getStatusCode());
	}

	private void append(String sessionId, String content, Instant createdAt) {
		append(identity, sessionId, content, createdAt);
	}

	private void append(IdentityEnvelope owner, String sessionId, String content, Instant createdAt) {
		appendMessage(sessionId, content, createdAt);
		repository.recordRunStarted(sessionId, owner.tenantId(), owner.userId(),
				AssistantSessionTitles.fromFirstMessage(content), List.of(), null, null, createdAt);
	}

	private void appendMessage(String sessionId, String content, Instant createdAt) {
		long sequence = repository.nextSequence(sessionId);
		repository.appendMessage(new AssistantMessage("message-" + sessionId + "-" + sequence, sessionId,
				MessageRole.USER, content, MessageStatus.COMPLETED, sequence, null, createdAt));
	}

	private static IdentityEnvelope identity(String tenantId, String userId) {
		return new IdentityEnvelope(tenantId, userId, "org-1", List.of("org-1"), List.of(), List.of(), "correlation-1",
				"assistant");
	}

}
