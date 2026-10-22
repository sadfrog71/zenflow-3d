package com.deepdimen.matrix.assistant.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope;

/**
 * Bridges durable events and ephemeral fan-out. Subscription is established before
 * replay, and sequence ordering removes duplicates caused by that overlap.
 */
@Service
public class AssistantRunEventStreamService {

	private static final int CONNECTION_QUEUE_CAPACITY = 512;

	private static final int REPLAY_PAGE_SIZE = 1000;

	private static final int MAX_REPLAY_EVENTS = 5000;

	private static final long MAX_CONNECTION_BYTES = 8L * 1024 * 1024;

	private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

	private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(15).toMillis();

	private static final ThreadPoolExecutor STREAM_EXECUTOR = new ThreadPoolExecutor(4, 32, 60, TimeUnit.SECONDS,
			new java.util.concurrent.SynchronousQueue<>(), runnable -> {
				Thread thread = new Thread(runnable, "assistant-sse-stream");
				thread.setDaemon(true);
				return thread;
			}, new ThreadPoolExecutor.AbortPolicy());

	private final AssistantRunEventService eventService;

	private final AssistantRunEventBus eventBus;

	private final AssistantRunRepository runRepository;

	public AssistantRunEventStreamService(AssistantRunEventService eventService, AssistantRunEventBus eventBus,
			AssistantRunRepository runRepository) {
		this.eventService = eventService;
		this.eventBus = eventBus;
		this.runRepository = runRepository;
	}

	public SseEmitter stream(String runId, long afterSeq) {
		return stream(runId, afterSeq, new SseEmitter(EMITTER_TIMEOUT_MS));
	}

    /** Product streams recheck their retained identity and access before every body/heartbeat write. */
    public SseEmitter stream(String runId,long afterSeq,Runnable authorization) {
        authorization.run();
        SseEmitter guarded=new SseEmitter(EMITTER_TIMEOUT_MS) {
            @Override public void send(SseEventBuilder event) throws IOException {
                authorization.run();super.send(event);
            }
            @Override public void send(Object object,org.springframework.http.MediaType mediaType) throws IOException {
                authorization.run();super.send(object,mediaType);
            }
        };
        return stream(runId,afterSeq,guarded);
    }

	SseEmitter stream(String runId, long afterSeq, SseEmitter emitter) {
		long normalizedAfterSeq = Math.max(0, afterSeq);
		BlockingQueue<RunEventEnvelope> incoming = new ArrayBlockingQueue<>(CONNECTION_QUEUE_CAPACITY);
		AtomicBoolean closed = new AtomicBoolean();
		AtomicLong deliveredBytes = new AtomicLong();
		AtomicReference<AssistantRunEventBus.Subscription> subscription = new AtomicReference<>();
		Runnable close = () -> {
			if (closed.compareAndSet(false, true)) {
				AssistantRunEventBus.Subscription current = subscription.getAndSet(null);
				if (current != null) {
					current.close();
				}
			}
		};
		emitter.onCompletion(close);
		emitter.onTimeout(() -> {
			close.run();
			emitter.complete();
		});
		emitter.onError(error -> close.run());
		AssistantRunEventBus.Subscription live = eventBus.subscribe(runId, event -> {
			if (!incoming.offer(event)) {
				close.run();
				emitter.completeWithError(new IllegalStateException("Assistant SSE connection buffer overflow"));
			}
		}, () -> {
			close.run();
			emitter.completeWithError(new IllegalStateException("Assistant SSE distributor buffer overflow"));
		});
		subscription.set(live);
		try {
			STREAM_EXECUTOR
				.execute(() -> deliver(runId, normalizedAfterSeq, incoming, emitter, closed, deliveredBytes, close));
		}
		catch (RuntimeException ex) {
			close.run();
			emitter.completeWithError(new IllegalStateException("Assistant SSE connection limit reached"));
		}
		return emitter;
	}

	private void deliver(String runId, long afterSeq, BlockingQueue<RunEventEnvelope> incoming, SseEmitter emitter,
			AtomicBoolean closed, AtomicLong deliveredBytes, Runnable close) {
		long deliveredSeq = afterSeq;
		TreeMap<Long, RunEventEnvelope> pending = new TreeMap<>();
		try {
			deliveredSeq = replay(runId, deliveredSeq, pending, emitter, closed, deliveredBytes);
			long lastHeartbeat = System.nanoTime();
			while (!closed.get()) {
				RunEventEnvelope live = incoming.poll(250, TimeUnit.MILLISECONDS);
				if (live != null && live.seq() > deliveredSeq) {
					pending.putIfAbsent(live.seq(), live);
				}
				deliveredSeq = drainContinuous(deliveredSeq, pending, emitter, closed, deliveredBytes);
				if (!pending.isEmpty() && pending.firstKey() > deliveredSeq + 1) {
					deliveredSeq = replay(runId, deliveredSeq, pending, emitter, closed, deliveredBytes);
				}
				AssistantRunRecord current = runRepository.findById(runId).orElse(null);
				if (current == null || current.status().terminal() && deliveredSeq >= current.lastEventSeq()) {
					break;
				}
				if (System.nanoTime() - lastHeartbeat >= HEARTBEAT_INTERVAL.toNanos()) {
					emitter.send(SseEmitter.event().comment("heartbeat"));
					lastHeartbeat = System.nanoTime();
				}
			}
			emitter.complete();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			emitter.completeWithError(ex);
		}
		catch (IOException | RuntimeException ex) {
			emitter.completeWithError(ex);
		}
		finally {
			close.run();
		}
	}

	private long replay(String runId, long deliveredSeq, TreeMap<Long, RunEventEnvelope> pending, SseEmitter emitter,
			AtomicBoolean closed, AtomicLong deliveredBytes) throws IOException {
		int replayed = 0;
		while (!closed.get() && replayed < MAX_REPLAY_EVENTS) {
			List<RunEventEnvelope> page = eventService.replay(runId, deliveredSeq, REPLAY_PAGE_SIZE);
			if (page.isEmpty()) {
				break;
			}
			for (RunEventEnvelope event : page) {
				if (event.seq() > deliveredSeq) {
					pending.putIfAbsent(event.seq(), event);
				}
			}
			replayed += page.size();
			deliveredSeq = drainContinuous(deliveredSeq, pending, emitter, closed, deliveredBytes);
			if (page.size() < REPLAY_PAGE_SIZE) {
				break;
			}
		}
		if (replayed >= MAX_REPLAY_EVENTS) {
			throw new IllegalStateException("Assistant SSE replay limit reached; snapshot alignment is required");
		}
		return deliveredSeq;
	}

	private long drainContinuous(long deliveredSeq, TreeMap<Long, RunEventEnvelope> pending, SseEmitter emitter,
			AtomicBoolean closed, AtomicLong deliveredBytes) throws IOException {
		while (!closed.get()) {
			RunEventEnvelope next = pending.remove(deliveredSeq + 1);
			if (next == null) {
				break;
			}
			long totalBytes = deliveredBytes.addAndGet(serializedSize(next));
			if (totalBytes > MAX_CONNECTION_BYTES) {
				throw new IllegalStateException("Assistant SSE byte limit reached; snapshot alignment is required");
			}
			emitter.send(SseEmitter.event().id(Long.toString(next.seq())).name(next.type().name()).data(next));
			deliveredSeq = next.seq();
		}
		pending.headMap(deliveredSeq, true).clear();
		return deliveredSeq;
	}

	private int serializedSize(RunEventEnvelope event) {
		// Conservative transport estimate without coupling the stream guard to a particular
		// JSON mapper configuration. The multiplier covers JSON field names and escaping.
		return 128 + event.toString().getBytes(StandardCharsets.UTF_8).length * 2;
	}

}
