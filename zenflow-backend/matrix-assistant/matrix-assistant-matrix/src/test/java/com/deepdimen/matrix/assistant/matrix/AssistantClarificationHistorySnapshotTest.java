package com.deepdimen.matrix.assistant.matrix;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.*;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.*;
import com.deepdimen.matrix.assistant.run.AssistantRunEngine;
import com.deepdimen.matrix.assistant.run.AssistantRunRecord;
import com.deepdimen.matrix.assistant.session.AssistantSessionService;
import com.deepdimen.matrix.assistant.session.InMemoryAssistantSessionRepository;
import org.springframework.web.server.ResponseStatusException;

class AssistantClarificationHistorySnapshotTest {

	@Test
	void ownedSingleAndHistorySnapshotsExposeFrozenInputAndOtherOwnersCannotReadIt() {
		var identity = new IdentityEnvelope("tenant", "owner", "org", List.of("org"), List.of(), List.of(), "trace",
				"assistant");
		var engine = mock(AssistantRunEngine.class);
		var service = new AssistantSessionService(new InMemoryAssistantSessionRepository(), null, engine);
		var session = service.create(identity, new CreateSessionRequest("test", List.of(), "/station"));
		var scope = new ScopeHint("/station", "STATION", List.of("station-1"), Map.of("period", "today"));
		var selection = new ClarificationSelection("source", "a".repeat(64), ExecutionStepType.DIFY_WORKFLOW, "quality",
				7L);
		var input = new AssistantRunRecord.RunInputSnapshot(identity, "today", List.of(), false, scope, "skill", null,
				List.of(), null, null, null, List.of(), SourceMode.AUTO, selection);
		var run = mock(AssistantRunRecord.class);
		when(run.runId()).thenReturn("run");
		when(run.sessionId()).thenReturn(session.assistantSessionId());
		when(run.status()).thenReturn(RunStatus.COMPLETED);
		when(run.input()).thenReturn(input);
		when(engine.requireOwnedRun(identity, session.assistantSessionId(), "run")).thenReturn(run);
		when(engine.runs(session.assistantSessionId(), 200)).thenReturn(List.of(run));
		when(engine.recentRuns(session.assistantSessionId(), 20)).thenReturn(List.of(run));
		when(engine.stepTraces("run")).thenReturn(List.of());
		var single = service.runSnapshot(identity, session.assistantSessionId(), "run");
		var listed = service.runs(identity, session.assistantSessionId()).get(0);
		assertEquals(scope, single.scopeHint());
		assertEquals(selection, single.clarificationSelection());
		assertEquals(single, listed);
		assertEquals(single, service.recentRuns(identity, session.assistantSessionId()).get(0));
		verify(engine).recentRuns(session.assistantSessionId(), 20);
		var other = new IdentityEnvelope("tenant", "other", "org", List.of("org"), List.of(), List.of(), "trace",
				"assistant");
		var error = assertThrows(ResponseStatusException.class,
				() -> service.runSnapshot(other, session.assistantSessionId(), "run"));
		assertEquals(404, error.getStatusCode().value());
		verify(engine, never()).requireOwnedRun(other, session.assistantSessionId(), "run");
		assertThrows(ResponseStatusException.class, () -> service.recentRuns(other, session.assistantSessionId()));
		verify(engine, times(1)).recentRuns(session.assistantSessionId(), 20);
	}

}
