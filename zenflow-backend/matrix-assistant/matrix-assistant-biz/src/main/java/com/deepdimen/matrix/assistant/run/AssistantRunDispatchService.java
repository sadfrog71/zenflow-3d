package com.deepdimen.matrix.assistant.run;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.function.Consumer;

import com.deepdimen.matrix.assistant.api.AssistantRunContract;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Durable dispatch uses DB PENDING as truth; this signal is only a latency optimization.
 */
@Service
public class AssistantRunDispatchService implements org.springframework.beans.factory.DisposableBean {

	private final ThreadPoolExecutor executor;

	private final ThreadPoolExecutor workflows;

	private final Set<String> scheduled = ConcurrentHashMap.newKeySet();

	private final String metricsInstanceId = java.util.UUID.randomUUID().toString();

	private final Instant metricsStartedAt = Instant.now();

	private final java.util.concurrent.atomic.LongAdder runSaturated = new java.util.concurrent.atomic.LongAdder();

	private final java.util.concurrent.atomic.LongAdder runShutdown = new java.util.concurrent.atomic.LongAdder();

	private final java.util.concurrent.atomic.LongAdder workflowSaturated = new java.util.concurrent.atomic.LongAdder();

	private final java.util.concurrent.atomic.LongAdder workflowShutdown = new java.util.concurrent.atomic.LongAdder();

	private final int runQueueCapacity;

	private final int workflowQueueCapacity;

	@org.springframework.beans.factory.annotation.Autowired(required = false)
	private org.springframework.transaction.PlatformTransactionManager transactionManager;

	public AssistantRunDispatchService() {
		this(8, 64, 3, 32);
	}

	public AssistantRunDispatchService(int threads, int capacity) {
		this(threads, capacity, 3, 32);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public AssistantRunDispatchService(
			@org.springframework.beans.factory.annotation.Value("${matrix.assistant.run.worker-threads:8}") int threads,
			@org.springframework.beans.factory.annotation.Value("${matrix.assistant.run.queue-capacity:64}") int capacity,
			@org.springframework.beans.factory.annotation.Value("${matrix.assistant.run.workflow-threads:3}") int workflowThreads,
			@org.springframework.beans.factory.annotation.Value("${matrix.assistant.run.workflow-queue-capacity:32}") int workflowCapacity) {
		if (threads < 1 || threads > 64 || capacity < 1 || capacity > 4096 || workflowThreads < 1
				|| workflowThreads > 32 || workflowCapacity < 1 || workflowCapacity > 4096)
			throw new IllegalArgumentException("Assistant executor capacity is outside supported bounds");
		runQueueCapacity = capacity;
		workflowQueueCapacity = workflowCapacity;
		var sequence = new java.util.concurrent.atomic.AtomicInteger();
		executor = new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<>(capacity), task -> {
					var thread = new Thread(task, "assistant-run-worker-" + sequence.incrementAndGet());
					thread.setDaemon(true);
					return thread;
				}, countRejections(runSaturated, runShutdown));
		workflows = new ThreadPoolExecutor(workflowThreads, workflowThreads, 0, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<>(workflowCapacity), task -> {
					var thread = new Thread(task, "assistant-workflow-step-" + sequence.incrementAndGet());
					thread.setDaemon(true);
					return thread;
				}, countRejections(workflowSaturated, workflowShutdown));
	}

	public record PoolSnapshot(int threadLimit, int activeThreads, int queuedTasks, int queueCapacity,
			long completedTasks, long saturatedRejections, long shutdownRejections, boolean shutdown) {
	}

	public record ExecutorSnapshot(String instanceId, Instant countersSince, Instant sampledAt, String scope,
			boolean approximate, PoolSnapshot runs, PoolSnapshot workflows) {
	}

	/** Local process counters, never tenant totals or a cluster-wide snapshot. */
	public ExecutorSnapshot metrics() {
		return new ExecutorSnapshot(metricsInstanceId, metricsStartedAt, Instant.now(), "INSTANCE", true,
				snapshot(executor, runQueueCapacity, runSaturated, runShutdown),
				snapshot(workflows, workflowQueueCapacity, workflowSaturated, workflowShutdown));
	}

	private PoolSnapshot snapshot(ThreadPoolExecutor pool, int capacity,
			java.util.concurrent.atomic.LongAdder saturated, java.util.concurrent.atomic.LongAdder shutdown) {
		return new PoolSnapshot(pool.getMaximumPoolSize(), pool.getActiveCount(), pool.getQueue().size(), capacity,
				pool.getCompletedTaskCount(), saturated.sum(), shutdown.sum(), pool.isShutdown());
	}

	private java.util.concurrent.RejectedExecutionHandler countRejections(
			java.util.concurrent.atomic.LongAdder saturated, java.util.concurrent.atomic.LongAdder shutdown) {
		return (task, pool) -> {
			if (pool.isShutdown())
				shutdown.increment();
			else
				saturated.increment();
			throw new RejectedExecutionException("Assistant executor cannot accept more work");
		};
	}

	@Override
	public void destroy() {
		executor.shutdown();
		workflows.shutdown();
	}

	public java.util.concurrent.CompletableFuture<Void> dispatchWorkflow(Runnable task) {
		return java.util.concurrent.CompletableFuture.runAsync(task, workflows);
	}

	public void dispatch(String runId, Consumer<String> worker) {
		if (!scheduled.add(runId))
			return;
		try {
			executor.execute(() -> {
				try {
					worker.accept(runId);
				}
				finally {
					scheduled.remove(runId);
				}
			});
		}
		catch (RejectedExecutionException rejected) {
			scheduled.remove(runId);
			throw rejected;
		}
	}

	public void dispatchAfterCommit(String runId, Consumer<String> worker) {
		dispatchAfterCommit(runId, worker, ignored -> {
		});
	}

	public void dispatchAfterCommit(String runId, Consumer<String> worker, Consumer<String> rejected) {
		if (TransactionSynchronizationManager.isActualTransactionActive()
				&& TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					dispatchOrReject(runId, worker, rejected);
				}
			});
			return;
		}
		dispatchOrReject(runId, worker, rejected);
	}

	private void dispatchOrReject(String runId, Consumer<String> worker, Consumer<String> rejected) {
		try {
			dispatch(runId, worker);
		}
		catch (RejectedExecutionException saturated) {
			// afterCommit still has the committing transaction bound to the caller.
			// Persist rejection in a fresh transaction rather than joining a completed
			// one.
			if (transactionManager != null && TransactionSynchronizationManager.isActualTransactionActive()) {
				var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
				transaction.setPropagationBehavior(
						org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
				transaction.executeWithoutResult(status -> rejected.accept(runId));
			}
			else
				rejected.accept(runId);
		}
	}

	public void dispatchPending(AssistantRunRepository repository, Consumer<String> worker, int limit) {
		// Recovery keeps durable PENDING rows when local capacity is exhausted.
		repository.findPendingRunIds(limit).forEach(runId -> dispatchOrReject(runId, worker, ignored -> {
		}));
	}

	public List<AssistantRunRecord> recoverExpiredClaims(AssistantRunRepository repository, Instant now, int limit,
			Consumer<AssistantRunRecord> executorLostHandler, Consumer<AssistantRunRecord> cancellationHandler) {
		List<AssistantRunRecord> expired = repository.findExpiredClaims(now, limit);
		for (AssistantRunRecord run : expired) {
			if (run.status() == AssistantRunContract.RunStatus.CANCEL_REQUESTED) {
				cancellationHandler.accept(run);
			}
			else if (run.modelInvokedAt() == null) {
				repository.requeueUninvokedClaim(run.runId(), now);
			}
			else {
				executorLostHandler.accept(run);
			}
		}
		return expired;
	}

}
