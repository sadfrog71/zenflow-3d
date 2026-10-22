package com.deepdimen.matrix.common.sse.distribute;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * In-process keyed fan-out. Every subscriber owns a bounded queue, so one slow consumer
 * cannot block a publisher or another subscriber.
 */
final class KeyedMessageHub {

	private static final int DEFAULT_QUEUE_CAPACITY = 256;

	private static final Executor DELIVERY_EXECUTOR = Executors
		.newFixedThreadPool(Math.max(2, Math.min(16, Runtime.getRuntime().availableProcessors())), runnable -> {
			Thread thread = new Thread(runnable, "sse-keyed-delivery");
			thread.setDaemon(true);
			return thread;
		});

	private final Map<String, Map<String, Subscriber>> subscribers = new ConcurrentHashMap<>();

	MessageDistributor.MessageSubscription subscribe(String key, Consumer<String> consumer, Runnable overflowHandler) {
		if (key == null || key.isBlank() || consumer == null) {
			throw new IllegalArgumentException("key and consumer are required");
		}
		String id = UUID.randomUUID().toString();
		Subscriber subscriber = new Subscriber(key, id, consumer, overflowHandler);
		subscribers.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(id, subscriber);
		return subscriber;
	}

	void publish(String key, String message) {
		Map<String, Subscriber> current = subscribers.get(key);
		if (current != null) {
			current.values().forEach(subscriber -> subscriber.offer(message));
		}
	}

	int subscriberCount(String key) {
		Map<String, Subscriber> current = subscribers.get(key);
		return current == null ? 0 : current.size();
	}

	private final class Subscriber implements MessageDistributor.MessageSubscription {

		private final String key;

		private final String id;

		private final Consumer<String> consumer;

		private final Runnable overflowHandler;

		private final ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(DEFAULT_QUEUE_CAPACITY);

		private final AtomicBoolean draining = new AtomicBoolean();

		private final AtomicBoolean closed = new AtomicBoolean();

		private Subscriber(String key, String id, Consumer<String> consumer, Runnable overflowHandler) {
			this.key = key;
			this.id = id;
			this.consumer = consumer;
			this.overflowHandler = overflowHandler == null ? () -> {
			} : overflowHandler;
		}

		private void offer(String message) {
			if (closed.get()) {
				return;
			}
			if (!queue.offer(message)) {
				close();
				overflowHandler.run();
				return;
			}
			drainAsync();
		}

		private void drainAsync() {
			if (draining.compareAndSet(false, true)) {
				DELIVERY_EXECUTOR.execute(this::drain);
			}
		}

		private void drain() {
			try {
				String message;
				while (!closed.get() && (message = queue.poll()) != null) {
					consumer.accept(message);
				}
			}
			catch (RuntimeException ex) {
				close();
			}
			finally {
				draining.set(false);
				if (!queue.isEmpty()) {
					drainAsync();
				}
			}
		}

		@Override
		public void close() {
			if (closed.compareAndSet(false, true)) {
				queue.clear();
				Map<String, Subscriber> current = subscribers.get(key);
				if (current != null) {
					current.remove(id);
					if (current.isEmpty()) {
						subscribers.remove(key, current);
					}
				}
			}
		}

	}

}
