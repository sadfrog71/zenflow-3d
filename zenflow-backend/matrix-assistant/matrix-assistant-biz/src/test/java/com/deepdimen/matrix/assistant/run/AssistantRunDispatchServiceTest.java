package com.deepdimen.matrix.assistant.run;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AssistantRunDispatchServiceTest {

	@Test
	void shutdownRejectionsAreSeparateAndMetricsContainNoQueuedRunIds() {
		var service = new AssistantRunDispatchService(1, 1, 1, 1);
		service.destroy();
		assertThrows(RejectedExecutionException.class, () -> service.dispatch("private-run", id -> fail("shutdown")));
		assertThrows(RejectedExecutionException.class, () -> service.dispatchWorkflow(() -> fail("shutdown")));
		var metrics = service.metrics();
		assertTrue(metrics.runs().shutdown());
		assertTrue(metrics.workflows().shutdown());
		assertEquals(1, metrics.runs().shutdownRejections());
		assertEquals(1, metrics.workflows().shutdownRejections());
		assertEquals(0, metrics.runs().saturatedRejections());
		assertFalse(metrics.toString().contains("private-run"));
	}

	@Test
	void boundedRunQueueRejectsOverflowAndDeduplicatesQueuedIdsWithoutUsingCaller() throws Exception {
		var service = new AssistantRunDispatchService(1, 1, 1, 1);
		var started = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var done = new CountDownLatch(2);
		var rejected = new AtomicInteger();
		var name = new AtomicReference<String>();
		try {
			service.dispatch("running", id -> {
				name.set(Thread.currentThread().getName());
				started.countDown();
				await(release);
				done.countDown();
			});
			assertTrue(started.await(2, TimeUnit.SECONDS));
			service.dispatch("queued", id -> done.countDown());
			service.dispatchAfterCommit("queued", id -> fail("duplicate"), id -> fail("duplicate rejection"));
			service.dispatchAfterCommit("overflow", id -> fail("caller must not execute"),
					id -> rejected.incrementAndGet());
			assertEquals(1, rejected.get());
			var metrics = service.metrics();
			assertEquals("INSTANCE", metrics.scope());
			assertTrue(metrics.approximate());
			assertEquals(1, metrics.runs().activeThreads());
			assertEquals(1, metrics.runs().queuedTasks());
			assertEquals(1, metrics.runs().queueCapacity());
			assertEquals(1, metrics.runs().saturatedRejections());
			assertEquals(0, metrics.runs().shutdownRejections());
			assertEquals(0, metrics.workflows().saturatedRejections());
			assertEquals(metrics.instanceId(), service.metrics().instanceId());

			assertTrue(name.get().startsWith("assistant-run-worker-"));
			release.countDown();
			assertTrue(done.await(2, TimeUnit.SECONDS));
		}
		finally {
			release.countDown();
			service.destroy();
		}
	}

	@Test
	void workflowQueueIsSeparateAndOverflowDoesNotDisruptAlreadyAcceptedWork() throws Exception {
		var service = new AssistantRunDispatchService(1, 1, 1, 1);
		var started = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		var runDone = new CountDownLatch(1);
		try {
			var first = service.dispatchWorkflow(() -> {
				started.countDown();
				await(release);
			});
			assertTrue(started.await(2, TimeUnit.SECONDS));
			var second = service.dispatchWorkflow(() -> {
			});
			assertThrows(RejectedExecutionException.class, () -> service.dispatchWorkflow(() -> fail("overflow")));
			assertEquals(1, service.metrics().workflows().saturatedRejections());
			assertEquals(1, service.metrics().workflows().queuedTasks());
			assertEquals(0, service.metrics().runs().saturatedRejections());

			service.dispatch("independent-run", id -> runDone.countDown());
			assertTrue(runDone.await(2, TimeUnit.SECONDS));
			release.countDown();
			first.get(2, TimeUnit.SECONDS);
			second.get(2, TimeUnit.SECONDS);
		}
		finally {
			release.countDown();
			service.destroy();
		}
	}

	@Test
	void afterCommitRejectionUsesFreshTransactionAndRollbackNeverDispatches() throws Exception {
		var service = new AssistantRunDispatchService(1, 1) {
			@Override
			public void dispatch(String id, java.util.function.Consumer<String> worker) {
				throw new RejectedExecutionException("full");
			}
		};
		var definition = new AtomicReference<org.springframework.transaction.TransactionDefinition>();
		var commits = new AtomicInteger();
		var manager = new org.springframework.transaction.PlatformTransactionManager() {
			@Override
			public org.springframework.transaction.TransactionStatus getTransaction(
					org.springframework.transaction.TransactionDefinition value) {
				definition.set(value);
				return new org.springframework.transaction.support.SimpleTransactionStatus();
			}

			@Override
			public void commit(org.springframework.transaction.TransactionStatus status) {
				commits.incrementAndGet();
			}

			@Override
			public void rollback(org.springframework.transaction.TransactionStatus status) {
				fail("unexpected rollback");
			}
		};
		var field = AssistantRunDispatchService.class.getDeclaredField("transactionManager");
		field.setAccessible(true);
		field.set(service, manager);
		var rejected = new AtomicInteger();
		org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
		org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
		try {
			service.dispatchAfterCommit("commit", id -> fail("must not execute"), id -> rejected.incrementAndGet());
			assertEquals(0, rejected.get());
			var callbacks = org.springframework.transaction.support.TransactionSynchronizationManager
				.getSynchronizations();
			callbacks.get(0).afterCommit();
			assertEquals(1, rejected.get());
			assertEquals(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW,
					definition.get().getPropagationBehavior());
			assertEquals(1, commits.get());
			service.dispatchAfterCommit("rollback", id -> fail("rolled back"), id -> rejected.incrementAndGet());
			var rollback = org.springframework.transaction.support.TransactionSynchronizationManager
				.getSynchronizations()
				.get(1);
			rollback
				.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
			assertEquals(1, rejected.get());
		}
		finally {
			org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
			org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
			service.destroy();
		}
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(2, TimeUnit.SECONDS))
				throw new AssertionError("worker did not release");
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new AssertionError(ex);
		}
	}

}
