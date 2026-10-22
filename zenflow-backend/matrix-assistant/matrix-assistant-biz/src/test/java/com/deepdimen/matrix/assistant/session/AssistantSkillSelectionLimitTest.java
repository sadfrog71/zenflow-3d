package com.deepdimen.matrix.assistant.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SendMessageRequest;

class AssistantSkillSelectionLimitTest {

	@Test
	void rejectsMoreThanOneExplicitSkillBeforeCreatingMessagesOrContext() {
		AssistantSessionService service = new AssistantSessionService(null, null, null);
		IdentityEnvelope identity = new IdentityEnvelope("tenant-1", "user-1", "org-1", List.of("org-1"), List.of(),
				List.of(), "corr-1", "ASSISTANT_CONTEXT");
		SendMessageRequest request = new SendMessageRequest("message-1", "hello", List.of(), null,
				List.of("skill-a", "skill-b"));

		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> service.send(identity, "session-1", request));

		assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
		assertEquals("SKILL_SELECTION_LIMIT_EXCEEDED", error.getReason());
	}

}
