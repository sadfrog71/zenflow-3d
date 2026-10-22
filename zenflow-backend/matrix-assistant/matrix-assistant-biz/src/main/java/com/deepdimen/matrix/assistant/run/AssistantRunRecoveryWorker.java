package com.deepdimen.matrix.assistant.run;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * DB-backed compensation loop; every instance may scan because claim/recovery transitions
 * are CAS guarded.
 */
@Component
public class AssistantRunRecoveryWorker implements InitializingBean, DisposableBean {

	private static final Logger LOGGER = Logger.getLogger(AssistantRunRecoveryWorker.class.getName());

	private final AssistantRunEngine engine;

	private final long scanDelayMs;

	private final int batchSize;

	private final ScheduledExecutorService scheduler;

	public AssistantRunRecoveryWorker(AssistantRunEngine engine,
			@Value("${matrix.assistant.run.scan-delay-ms:5000}") long scanDelayMs,
			@Value("${matrix.assistant.run.scan-batch-size:100}") int batchSize) {
		this.engine = engine;
		this.scanDelayMs = Math.max(1000, scanDelayMs);
		this.batchSize = Math.max(1, Math.min(batchSize, 200));
		ThreadFactory threadFactory = runnable -> {
			Thread thread = new Thread(runnable, "assistant-run-recovery");
			thread.setDaemon(true);
			return thread;
		};
		this.scheduler = Executors.newSingleThreadScheduledExecutor(threadFactory);
	}

	@Override
	public void afterPropertiesSet() {
		scheduler.scheduleWithFixedDelay(this::scanSafely, scanDelayMs, scanDelayMs, TimeUnit.MILLISECONDS);
	}

	@Override
	public void destroy() {
		scheduler.shutdownNow();
	}

	private void scanSafely() {
		try {
			engine.recoverExpired(Instant.now(), batchSize);
		}
		catch (RuntimeException ex) {
			LOGGER.log(Level.WARNING, "Assistant Run recovery scan failed; the next scan will retry", ex);
		}
	}

}
