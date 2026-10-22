package com.deepdimen.matrix.assistant.orchestrator;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AssistantProviderExecutionConfiguration {

	@Bean(name = "assistantProviderExecutor", destroyMethod = "shutdown")
	ExecutorService assistantProviderExecutor(
			@Value("${matrix.assistant.provider-executor.core-size:4}") int coreSize,
			@Value("${matrix.assistant.provider-executor.max-size:8}") int maxSize,
			@Value("${matrix.assistant.provider-executor.queue-capacity:64}") int queueCapacity) {
		int safeCore = Math.max(1, coreSize);
		int safeMax = Math.max(safeCore, maxSize);
		int safeQueue = Math.max(1, queueCapacity);
		ThreadPoolExecutor executor = new ThreadPoolExecutor(safeCore, safeMax, 60, TimeUnit.SECONDS,
				new ArrayBlockingQueue<>(safeQueue), daemonThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
		executor.allowCoreThreadTimeOut(true);
		return executor;
	}

	static ExecutorService sharedTestExecutor() {
		return SharedExecutor.INSTANCE;
	}

	private static ThreadFactory daemonThreadFactory() {
		AtomicInteger sequence = new AtomicInteger();
		return runnable -> {
			Thread thread = new Thread(runnable, "assistant-provider-" + sequence.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}

	private static final class SharedExecutor {

		private static final ExecutorService INSTANCE = new ThreadPoolExecutor(2, 8, 60, TimeUnit.SECONDS,
				new ArrayBlockingQueue<>(64), daemonThreadFactory(), new ThreadPoolExecutor.AbortPolicy());

	}

}
