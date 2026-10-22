package com.deepdimen.matrix.assistant.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.DispatchStatus;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSession;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageRole;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SessionStatus;
import com.deepdimen.matrix.assistant.run.AssistantRunRecord.RunInputSnapshot;
import com.deepdimen.matrix.assistant.session.InMemoryAssistantSessionRepository;

class AssistantRunEventStreamServiceTest {

	@Test
	void subscribeBeforeReplayDeliversStrictSequenceWithoutDuplicates() throws Exception {
		Fixture fixture = new Fixture();
		fixture.events.append(fixture.run, RunEventType.CONTEXT_STARTED, Map.of());
		CapturingEmitter emitter = new CapturingEmitter(3);

		fixture.streams.stream(fixture.run.runId(), 0, emitter);
		fixture.events.append(fixture.run, RunEventType.MODEL_STARTED, Map.of());

		assertTrue(emitter.finished.await(2, TimeUnit.SECONDS));
		assertEquals(List.of(1L, 2L, 3L), emitter.events.stream().map(RunEventEnvelope::seq).toList());
	}

	@Test
	void realtimeFailureDoesNotInvalidateDurableAppend() {
		Fixture fixture = new Fixture(event -> {
			throw new IllegalStateException("redis unavailable");
		});

		RunEventEnvelope persisted = fixture.events.append(fixture.run, RunEventType.CONTEXT_STARTED, Map.of());

		assertEquals(2, persisted.seq());
		assertEquals(List.of(1L, 2L),
				fixture.store.findAfter(fixture.run.runId(), 0, 10).stream().map(RunEventEnvelope::seq).toList());
	}

	@Test
	void rolledBackTransactionDoesNotPublishRealtimeEvent() {
		Fixture fixture = new Fixture();
		TransactionSynchronizationManager.setActualTransactionActive(true);
		TransactionSynchronizationManager.initSynchronization();
		try {
			fixture.events.append(fixture.run, RunEventType.CONTEXT_STARTED, Map.of());
			assertTrue(fixture.bus.published.isEmpty());
			TransactionSynchronizationManager.getSynchronizations()
				.forEach(synchronization -> synchronization
					.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
			assertTrue(fixture.bus.published.isEmpty());
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
			TransactionSynchronizationManager.setActualTransactionActive(false);
		}
	}

	private static final class Fixture {

		private final InMemoryAssistantSessionRepository sessions = new InMemoryAssistantSessionRepository();

		private final InMemoryAssistantRunStore store = new InMemoryAssistantRunStore(sessions);

		private final TestBus bus;

		private final AssistantRunEventService events;

		private final AssistantRunEventStreamService streams;

		private final AssistantRunRecord run;

		private Fixture() {
			this(null);
		}

		private Fixture(Consumer<RunEventEnvelope> publishOverride) {
			bus = new TestBus(publishOverride);
			events = new AssistantRunEventService(store, bus);
			streams = new AssistantRunEventStreamService(events, bus, store);
			IdentityEnvelope identity = new IdentityEnvelope("tenant-1", "user-1", "org-1", List.of("org-1"), List.of(),
					List.of(), "corr-1", "assistant");
			AssistantSession session = sessions.saveSession(new AssistantSession("session-1", "tenant-1", "user-1",
					"Test", SessionStatus.ACTIVE, List.of(), null, 1, Instant.now(), Instant.now()));
			RunInputSnapshot input = new RunInputSnapshot(identity, "hello", List.of(), true, null, null, null,
					List.of(), null, null, null);
			run = new AssistantRunRecord("run-1", session.assistantSessionId(), "tenant-1", "user-1", "client-1",
					"hash", "user-1", "response-1", RunStatus.ACCEPTED, DispatchStatus.PENDING, input, null, "", 1,
					null, null, null, null, null, Instant.now(), null, null);
			store.startAtomic(run,
					new AssistantMessage("user-1", session.assistantSessionId(), MessageRole.USER, "hello",
							MessageStatus.COMPLETED, 0, null, Instant.now()),
					new AssistantMessage("response-1", session.assistantSessionId(), MessageRole.ASSISTANT, "",
							MessageStatus.ACCEPTED, 0, null, Instant.now()));
		}

	}

	private static final class TestBus implements AssistantRunEventBus {

		private final List<Consumer<RunEventEnvelope>> subscribers = new CopyOnWriteArrayList<>();

		private final List<RunEventEnvelope> published = new CopyOnWriteArrayList<>();

		private final Consumer<RunEventEnvelope> publishOverride;

		private TestBus(Consumer<RunEventEnvelope> publishOverride) {
			this.publishOverride = publishOverride;
		}

		@Override
		public void publish(RunEventEnvelope event) {
			if (publishOverride != null) {
				publishOverride.accept(event);
				return;
			}
			published.add(event);
			subscribers.forEach(subscriber -> subscriber.accept(event));
		}

		@Override
		public Subscription subscribe(String runId, Consumer<RunEventEnvelope> consumer, Runnable overflowHandler) {
			subscribers.add(consumer);
			return () -> subscribers.remove(consumer);
		}

	}

	private static final class CapturingEmitter extends SseEmitter {

		private final List<RunEventEnvelope> events = new CopyOnWriteArrayList<>();

		private final CountDownLatch finished = new CountDownLatch(1);

		private final int stopAfter;

		private CapturingEmitter(int stopAfter) {
			this.stopAfter = stopAfter;
		}

		@Override
		public void send(SseEventBuilder builder) throws IOException {
			builder.build()
				.stream()
				.map(org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType::getData)
				.filter(RunEventEnvelope.class::isInstance)
				.map(RunEventEnvelope.class::cast)
				.forEach(events::add);
			if (events.size() >= stopAfter) {
				throw new IOException("test complete");
			}
		}

		@Override
		public void complete() {
			finished.countDown();
		}

		@Override
		public void completeWithError(Throwable failure) {
			finished.countDown();
		}

	}

}
