package com.deepdimen.matrix.assistant.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSession;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SessionStatus;

class AssistantExecutionRunAccessTest {

	@Test
	void ordinaryUserCanOnlyRequestOwnExecutionRuns() {
		RecordingRepository repository = new RecordingRepository();
		AssistantSessionService service = new AssistantSessionService(repository, null, null);

		service.executionRuns(identity("tenant-1", "user-1"));

		assertEquals("tenant-1", repository.tenantId);
		assertEquals("user-1", repository.ownerUserId);
		assertEquals(false, repository.tenantWide);
	}

	@Test
	void explicitlyAuthorizedAdminPathCanRequestTenantWideExecutionRuns() {
		RecordingRepository repository = new RecordingRepository();
		AssistantSessionService service = new AssistantSessionService(repository, null, null);

		service.tenantExecutionRuns(identity("tenant-1", "admin-1"));

		assertEquals("tenant-1", repository.tenantId);
		assertEquals("admin-1", repository.ownerUserId);
		assertEquals(true, repository.tenantWide);
	}

	@Test
	void executionRunQueryRejectsIncompleteIdentityBeforeRepositoryAccess() {
		RecordingRepository repository = new RecordingRepository();
		AssistantSessionService service = new AssistantSessionService(repository, null, null);

		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> service.executionRuns(identity("tenant-1", "")));

		assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
		assertEquals(null, repository.tenantId);
	}

	@Test
	void sessionRunRecoveryDoesNotRevealAnotherUsersSession() {
		RecordingRepository repository = new RecordingRepository();
		repository.saveSession(new AssistantSession("session-1", "tenant-1", "owner-1", "Private",
				SessionStatus.ACTIVE, List.of(), null, 1, Instant.now(), Instant.now()));
		AssistantSessionService service = new AssistantSessionService(repository, null, null);

		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> service.runs(identity("tenant-1", "other-1"), "session-1"));

		assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
	}

	private IdentityEnvelope identity(String tenantId, String userId) {
		return new IdentityEnvelope(tenantId, userId, "org-1", List.of("org-1"), List.of(), List.of(), "corr-1",
				"ASSISTANT_CONTEXT");
	}

	private static final class RecordingRepository extends InMemoryAssistantSessionRepository {

		private String tenantId;

		private String ownerUserId;

		private boolean tenantWide;

		@Override
		public List<Map<String, Object>> listExecutionRuns(String tenantId, String ownerUserId, boolean tenantWide,
				int limit) {
			this.tenantId = tenantId;
			this.ownerUserId = ownerUserId;
			this.tenantWide = tenantWide;
			return List.of();
		}

	}

}
