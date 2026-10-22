package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.knowledge.support.handler.rag.QdrantEmbeddingStoreFactory.FixedResource;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded product resources; a previously returned handle reacquires an evicted
 * connection.
 */
public final class ZenflowVectorStorePool implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(ZenflowVectorStorePool.class);

	private final int capacity;

	private final Semaphore concurrency;

	private final Semaphore initializing;

	private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(16, .75f, true);

	private final LinkedHashMap<String, Long> retryAfter = new LinkedHashMap<>();

	private boolean closed;

	@FunctionalInterface
	public interface Factory {

		FixedResource create() throws Exception;

	}

	@FunctionalInterface
	private interface Operation {

		Object apply(EmbeddingStore<TextSegment> store) throws Throwable;

	}

	private static final class Entry {

		final CompletableFuture<FixedResource> ready = new CompletableFuture<>();

		FixedResource resource;

		int users;

		boolean retired;

	}

	public ZenflowVectorStorePool(int capacity, int concurrentOperations) {
		if (capacity < 1 || concurrentOperations < 1)
			throw new IllegalArgumentException("VECTOR_POOL_LIMIT_INVALID");
		this.capacity = capacity;
		this.concurrency = new Semaphore(concurrentOperations);
		this.initializing = new Semaphore(concurrentOperations);
	}

	@SuppressWarnings("unchecked")
	public EmbeddingStore<TextSegment> handle(String key, Factory factory) {
		try {
			use(key, factory, store -> null);
		}
		catch (RuntimeException | Error failure) {
			throw failure;
		}
		catch (Throwable failure) {
			throw new IllegalStateException("VECTOR_STORE_UNAVAILABLE", failure);
		}
		return (EmbeddingStore<TextSegment>) Proxy.newProxyInstance(EmbeddingStore.class.getClassLoader(),
				new Class<?>[] { EmbeddingStore.class }, (proxy, method, args) -> {
					if (method.getDeclaringClass() == Object.class)
						return switch (method.getName()) {
							case "toString" -> "Invelle fixed vector store";
							case "hashCode" -> System.identityHashCode(proxy);
							case "equals" -> proxy == args[0];
							default -> throw new UnsupportedOperationException();
						};
					return use(key, factory, store -> {
						try {
							return method.invoke(store, args);
						}
						catch (InvocationTargetException failure) {
							throw failure.getCause();
						}
					});
				});
	}

	private Object use(String key, Factory factory, Operation operation) throws Throwable {
		Entry entry = null;
		boolean waiting = false, operating = false, leased = false;
		try {
			Entry evicted = null;
			boolean create = false;
			synchronized (this) {
				if (closed)
					throw new IllegalStateException("VECTOR_STORE_CLOSED");
				Long retry = retryAfter.get(key);
				if (retry != null && retry > System.currentTimeMillis())
					throw new IllegalStateException("VECTOR_STORE_RETRY_LATER");
				retryAfter.remove(key);
				entry = entries.get(key);
				if (entry == null || !entry.ready.isDone()) {
					if (!initializing.tryAcquire())
						throw new IllegalStateException("VECTOR_STORE_BUSY");
					waiting = true;
				}
				if (entry == null) {
					if (entries.size() >= capacity) {
						var iterator = entries.entrySet().iterator();
						while (iterator.hasNext()) {
							var candidate = iterator.next();
							if (candidate.getValue().users == 0) {
								evicted = candidate.getValue();
								evicted.retired = true;
								iterator.remove();
								break;
							}
						}
						if (evicted == null)
							throw new IllegalStateException("VECTOR_STORE_BUSY");
					}
					entry = new Entry();
					entries.put(key, entry);
					create = true;
				}
				entry.users++;
				leased = true;
			}
			closeResource(evicted);
			if (create) {
				try {
					var resource = Objects.requireNonNull(factory.create());
					synchronized (this) {
						entry.resource = resource;
					}
					entry.ready.complete(resource);
				}
				catch (Throwable failure) {
					synchronized (this) {
						boolean current = entries.remove(key, entry);
						entry.retired = true;
						if (current && !closed) {
							retryAfter.put(key, System.currentTimeMillis() + 5000);
							while (retryAfter.size() > 64)
								retryAfter.remove(retryAfter.keySet().iterator().next());
						}
					}
					entry.ready.completeExceptionally(failure);
				}
			}
			FixedResource resource;
			try {
				resource = entry.ready.join();
			}
			catch (CompletionException failure) {
				throw failure.getCause();
			}
			if (waiting) {
				initializing.release();
				waiting = false;
			}
			if (!concurrency.tryAcquire())
				throw new IllegalStateException("VECTOR_STORE_BUSY");
			operating = true;
			return operation.apply(resource.store());
		}
		finally {
			if (leased) {
				boolean close;
				synchronized (this) {
					entry.users--;
					close = entry.retired && entry.users == 0;
				}
				if (close)
					closeResource(entry);
			}
			if (waiting)
				initializing.release();
			if (operating)
				concurrency.release();
		}
	}

	public void invalidate(String prefix) {
		var close = new ArrayList<Entry>();
		synchronized (this) {
			var iterator = entries.entrySet().iterator();
			while (iterator.hasNext()) {
				var item = iterator.next();
				if (item.getKey().startsWith(prefix)) {
					iterator.remove();
					item.getValue().retired = true;
					if (item.getValue().users == 0)
						close.add(item.getValue());
				}
			}
			retryAfter.keySet().removeIf(key -> key.startsWith(prefix));
		}
		close.forEach(this::closeResource);
	}

	@Override
	public void close() {
		var close = new ArrayList<Entry>();
		synchronized (this) {
			if (closed)
				return;
			closed = true;
			for (var entry : entries.values()) {
				entry.retired = true;
				if (entry.users == 0)
					close.add(entry);
			}
			entries.clear();
			retryAfter.clear();
		}
		close.forEach(this::closeResource);
	}

	private void closeResource(Entry entry) {
		if (entry == null)
			return;
		FixedResource resource;
		synchronized (this) {
			resource = entry.resource;
			entry.resource = null;
		}
		if (resource != null)
			try {
				resource.close();
			}
			catch (Exception failure) {
				LOG.warn("Fixed vector resource close failed: {}", failure.getClass().getSimpleName());
			}
	}

}
