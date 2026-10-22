package com.deepdimen.matrix.assistant.run;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType;

/**
 * Allocates sequence numbers in the database; U6 only adds after-commit real-time
 * delivery.
 */
@Service
public class AssistantRunEventService {

	private final AssistantRunEventRepository repository;

	private final AssistantRunEventBus eventBus;

	public AssistantRunEventService(AssistantRunEventRepository repository) {
		this(repository, AssistantRunEventBus.NOOP);
	}

	@Autowired
	public AssistantRunEventService(AssistantRunEventRepository repository,
			ObjectProvider<AssistantRunEventBus> eventBusProvider) {
		this(repository, eventBusProvider.getIfAvailable(() -> AssistantRunEventBus.NOOP));
	}

	AssistantRunEventService(AssistantRunEventRepository repository, AssistantRunEventBus eventBus) {
		this.repository = repository;
		this.eventBus = eventBus;
	}

	public RunEventEnvelope append(AssistantRunRecord run, RunEventType type, Map<String, Object> data) {
		RunEventEnvelope event = repository.append(run.runId(), type, run.input().identity().correlationId(), data,
				Instant.now());
		publishAfterCommit(event);
		return event;
	}

	public List<RunEventEnvelope> replay(String runId, long afterSeq, int limit) {
		return repository.findAfter(runId, afterSeq, Math.max(1, Math.min(limit, 1000)));
	}

	public Optional<RunEventEnvelope> appendDelta(AssistantRunRecord run, String ownerInstanceId, String delta,
			String draftContent) {
		Optional<RunEventEnvelope> event = repository.appendDeltaOwned(run.runId(), ownerInstanceId,
				run.responseMessageId(), run.input().identity().correlationId(), delta, draftContent, Instant.now());
		event.ifPresent(this::publishAfterCommit);
		return event;
	}

	public void publishPersistedAfter(String runId, long afterSeq) {
		repository.findAfter(runId, afterSeq, 1000).forEach(this::publishAfterCommit);
	}

	public AssistantRunEventBus.Subscription subscribe(String runId,
			java.util.function.Consumer<RunEventEnvelope> consumer) {
		return eventBus.subscribe(runId, consumer, () -> {
		});
	}

	private void publishAfterCommit(RunEventEnvelope event) {
		if (TransactionSynchronizationManager.isActualTransactionActive()
				&& TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					publishBestEffort(event);
				}
			});
		}
		else {
			publishBestEffort(event);
		}
	}

	private void publishBestEffort(RunEventEnvelope event) {
		try {
			eventBus.publish(event);
		}
		catch (RuntimeException ex) {
			// Real-time fan-out is best effort. The durable event log is replayed by SSE.
		}
	}

}
