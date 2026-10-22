package com.deepdimen.matrix.common.sse.distribute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;

class LocalMessageDistributorTest {

	@Test
	void oneKeySupportsIndependentMultipleSubscribers() throws Exception {
		LocalMessageDistributor distributor = new LocalMessageDistributor();
		List<String> left = new CopyOnWriteArrayList<>();
		List<String> right = new CopyOnWriteArrayList<>();
		CountDownLatch delivered = new CountDownLatch(4);
		try (var first = distributor.subscribe("run-1", message -> {
			left.add(message);
			delivered.countDown();
		}, null); var second = distributor.subscribe("run-1", message -> {
			right.add(message);
			delivered.countDown();
		}, null)) {
			distributor.distributeToKey("run-1", "one");
			distributor.distributeToKey("run-1", "two");
			assertTrue(await(delivered, Duration.ofSeconds(2)));
			assertEquals(List.of("one", "two"), left);
			assertEquals(List.of("one", "two"), right);
			assertEquals(2, distributor.subscriberCount("run-1"));
		}
		assertEquals(0, distributor.subscriberCount("run-1"));
	}

	private boolean await(CountDownLatch latch, Duration timeout) throws InterruptedException {
		Instant deadline = Instant.now().plus(timeout);
		while (latch.getCount() > 0 && Instant.now().isBefore(deadline)) {
			Thread.sleep(5);
		}
		return latch.getCount() == 0;
	}

}
