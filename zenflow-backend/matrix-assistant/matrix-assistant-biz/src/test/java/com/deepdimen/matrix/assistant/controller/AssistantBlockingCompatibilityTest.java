package com.deepdimen.matrix.assistant.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageExchange;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SendMessageRequest;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunAcceptedResponse;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSession;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.IntentDisposition;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageRole;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SessionStatus;
import com.deepdimen.matrix.assistant.run.AssistantRunEngine;
import com.deepdimen.matrix.assistant.session.AssistantSessionService;
import com.deepdimen.matrix.assistant.session.InMemoryAssistantSessionRepository;

/** Characterizes the public blocking endpoint before it becomes an async-core adapter. */
class AssistantBlockingCompatibilityTest {

	@Test
	void keepsTheExistingPostMessagesRouteAndResponseShape() throws Exception {
		Method send = AssistantSessionController.class.getDeclaredMethod("send", String.class,
				SendMessageRequest.class);

		assertArrayEquals(new String[] { "/sessions/{sessionId}/messages" },
				send.getAnnotation(PostMapping.class).value());
		assertEquals(MessageExchange.class, send.getReturnType());
		assertEquals(
				"session,userMessage,responseMessage,contextPack,providerResults,disposition,proposal,executionTrace",
				String.join(",",
						Arrays.stream(MessageExchange.class.getRecordComponents())
							.map(component -> component.getName())
							.toList()));
	}

	@Test
	void blockingServiceWaitsForTheSamePersistedRunAndReturnsTheLegacyExchange() {
		IdentityEnvelope identity = new IdentityEnvelope("tenant-1", "user-1", "org-1", List.of("org-1"), List.of(),
				List.of(), "corr-1", "assistant");
		InMemoryAssistantSessionRepository repository = new InMemoryAssistantSessionRepository();
		AssistantSession session = repository.saveSession(new AssistantSession("session-1", identity.tenantId(),
				identity.userId(), "Test", SessionStatus.ACTIVE, List.of(), null, 1, Instant.now(), Instant.now()));
		AssistantMessage user = new AssistantMessage("message-1", session.assistantSessionId(), MessageRole.USER,
				"hello", MessageStatus.COMPLETED, 1, null, Instant.now());
		AssistantMessage response = new AssistantMessage("response-1", session.assistantSessionId(),
				MessageRole.ASSISTANT, "answer", MessageStatus.COMPLETED, 2, null, Instant.now());
		MessageExchange expected = new MessageExchange(session, user, response, null, List.of(),
				IntentDisposition.READ_ONLY, null, null);
		RecordingRunEngine engine = new RecordingRunEngine(expected);
		AssistantSessionService service = new AssistantSessionService(repository, null, engine);

		MessageExchange actual = service.send(identity, session.assistantSessionId(),
				new SendMessageRequest("message-1", "hello", List.of(), null, List.of()));

		assertEquals(expected, actual);
		assertEquals("message-1", engine.startedClientMessageId);
		assertEquals("message-1", engine.awaitedClientMessageId);
	}

	private static final class RecordingRunEngine extends AssistantRunEngine {

		private final MessageExchange exchange;

		private String startedClientMessageId;

		private String awaitedClientMessageId;

		private RecordingRunEngine(MessageExchange exchange) {
			super(null, null, null, null, null, null, null, null, null, null);
			this.exchange = exchange;
		}

		@Override
		public RunAcceptedResponse startOrGet(IdentityEnvelope identity, AssistantSession session,
				String clientMessageId, String content, List<ProviderId> providerIds, boolean providerSelectionExplicit,
				com.deepdimen.matrix.assistant.api.AssistantSessionContract.ScopeHint scopeHint,
				List<String> explicitSkillCodes, Double temperature, Double topP, Integer maxOutputTokens) {
			startedClientMessageId = clientMessageId;
			return new RunAcceptedResponse("run-1", "message-1", "response-1", RunStatus.ACCEPTED, "/events");
		}

		@Override
		public MessageExchange awaitExchange(String sessionId, String clientMessageId, Duration timeout) {
			awaitedClientMessageId = clientMessageId;
			return exchange;
		}

	}

}
