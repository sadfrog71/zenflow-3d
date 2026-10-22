package com.deepdimen.matrix.zenflow.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.knowledge.support.handler.rag.QdrantEmbeddingStoreFactory.FixedResource;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ZenflowVectorStorePoolTest {

	@SuppressWarnings("unchecked")
	private EmbeddingStore<TextSegment> store() {
		return mock(EmbeddingStore.class);
	}

	@Test
	void evictedHandleReacquiresAndShutdownClosesEachOwnedResourceOnce() {
		var created = new AtomicInteger();
		var closed = new AtomicInteger();
		var delegate = store();
		when(delegate.add(any(Embedding.class))).thenReturn("result");
		var pool = new ZenflowVectorStorePool(1, 4);
		ZenflowVectorStorePool.Factory factory = () -> {
			created.incrementAndGet();
			return new FixedResource(delegate, () -> closed.incrementAndGet());
		};
		var old = pool.handle("a", factory);
		pool.handle("b", factory);
		assertEquals(1, closed.get());
		assertEquals("result", old.add(Embedding.from(new float[] { 1 })));
		assertEquals(3, created.get());
		assertEquals(2, closed.get());
		pool.close();
		pool.close();
		assertEquals(3, closed.get());
		assertThrows(IllegalStateException.class, () -> old.add(Embedding.from(new float[] { 1 })));
		assertEquals(3, created.get());
	}

	@Test
	void activeCallIsNotClosedByInvalidationOrShutdown() throws Exception {
		var entered = new CountDownLatch(1);
		var finish = new CountDownLatch(1);
		var closed = new AtomicInteger();
		var delegate = store();
		when(delegate.add(any(Embedding.class))).thenAnswer(invocation -> {
			entered.countDown();
			assertTrue(finish.await(3, TimeUnit.SECONDS));
			return "answer";
		});
		var pool = new ZenflowVectorStorePool(1, 4);
		var handle = pool.handle("a", () -> new FixedResource(delegate, () -> closed.incrementAndGet()));
		var executor = Executors.newSingleThreadExecutor();
		try {
			var call = executor.submit(() -> handle.add(Embedding.from(new float[] { 1 })));
			assertTrue(entered.await(3, TimeUnit.SECONDS));
			pool.invalidate("a");
			pool.close();
			assertEquals(0, closed.get());
			finish.countDown();
			assertEquals("answer", call.get(3, TimeUnit.SECONDS));
			assertEquals(1, closed.get());
		}
		finally {
			finish.countDown();
			executor.shutdownNow();
			pool.close();
		}
	}

	@Test
	void busyPoolDoesNotEvictAnActiveCall() throws Exception {
		var entered = new CountDownLatch(1);
		var finish = new CountDownLatch(1);
		var delegate = store();
		when(delegate.add(any(Embedding.class))).thenAnswer(invocation -> {
			entered.countDown();
			assertTrue(finish.await(3, TimeUnit.SECONDS));
			return "answer";
		});
		try (var pool = new ZenflowVectorStorePool(1, 4)) {
			var handle = pool.handle("a", () -> new FixedResource(delegate, () -> {
			}));
			var executor = Executors.newSingleThreadExecutor();
			try {
				var call = executor.submit(() -> handle.add(Embedding.from(new float[] { 1 })));
				assertTrue(entered.await(3, TimeUnit.SECONDS));
				assertEquals("VECTOR_STORE_BUSY", assertThrows(IllegalStateException.class,
						() -> pool.handle("b", () -> new FixedResource(delegate, () -> {
						})))
					.getMessage());
				finish.countDown();
				call.get(3, TimeUnit.SECONDS);
			}
			finally {
				finish.countDown();
				executor.shutdownNow();
			}
		}
	}

	@Test
	void factoryFailureHasBoundedCooldownAndDoesNotLeaveAnUnresolvedEntry() {
		try (var pool = new ZenflowVectorStorePool(1, 4)) {
			var calls = new AtomicInteger();
			ZenflowVectorStorePool.Factory fail = () -> {
				calls.incrementAndGet();
				throw new java.io.IOException("fixture");
			};
			assertThrows(IllegalStateException.class, () -> pool.handle("a", fail));
			assertThrows(IllegalStateException.class, () -> pool.handle("a", fail));
			assertEquals(1, calls.get());
			pool.invalidate("a");
			assertThrows(IllegalStateException.class, () -> pool.handle("a", fail));
			assertEquals(2, calls.get());
			pool.invalidate("a");
			assertThrows(AssertionError.class, () -> pool.handle("a", () -> {
				throw new AssertionError("fixture");
			}));
			assertThrows(IllegalStateException.class, () -> pool.handle("a", fail));
		}
	}

	@Test
	void slowInitializationDoesNotOccupyReadyStoreOperationPermits() throws Exception {
		var entered = new CountDownLatch(1);
		var finish = new CountDownLatch(1);
		var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
		var delegate = store();
		when(delegate.add(any(Embedding.class))).thenReturn("ready answer");
		try (var pool = new ZenflowVectorStorePool(2, 2)) {
			var ready = pool.handle("ready", () -> new FixedResource(delegate, () -> {
			}));
			ZenflowVectorStorePool.Factory slow = () -> {
				entered.countDown();
				assertTrue(finish.await(3, TimeUnit.SECONDS));
				return new FixedResource(delegate, () -> {
				});
			};
			var creator = new Thread(() -> {
				try {
					pool.handle("slow", slow);
				}
				catch (Throwable error) {
					failure.set(error);
				}
			});
			var waiter = new Thread(() -> {
				try {
					pool.handle("slow", () -> {
						throw new AssertionError("duplicate initialization");
					});
				}
				catch (Throwable error) {
					failure.set(error);
				}
			});
			try {
				creator.start();
				assertTrue(entered.await(3, TimeUnit.SECONDS));
				waiter.start();
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
				while (waiter.getState() != Thread.State.WAITING && waiter.isAlive() && System.nanoTime() < deadline)
					Thread.sleep(1);
				assertEquals(Thread.State.WAITING, waiter.getState());
				assertEquals("ready answer", ready.add(Embedding.from(new float[] { 1 })));
			}
			finally {
				finish.countDown();
				creator.join(3000);
				waiter.join(3000);
			}
			assertFalse(creator.isAlive());
			assertFalse(waiter.isAlive());
			assertNull(failure.get());
		}
	}

}
