package com.deepdimen.matrix.assistant.run;

import java.util.function.Consumer;

import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope;

/** Ephemeral fan-out port. The database event log remains the replay authority. */
public interface AssistantRunEventBus {

	AssistantRunEventBus NOOP = new AssistantRunEventBus() {
		@Override
		public void publish(RunEventEnvelope event) {
		}

		@Override
		public Subscription subscribe(String runId, Consumer<RunEventEnvelope> consumer, Runnable overflowHandler) {
			return () -> {
			};
		}
	};

	void publish(RunEventEnvelope event);

	Subscription subscribe(String runId, Consumer<RunEventEnvelope> consumer, Runnable overflowHandler);

	interface Subscription extends AutoCloseable {

		@Override
		void close();

	}

}
