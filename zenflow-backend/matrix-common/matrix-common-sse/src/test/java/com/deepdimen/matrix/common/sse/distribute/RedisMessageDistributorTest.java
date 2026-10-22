package com.deepdimen.matrix.common.sse.distribute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

class RedisMessageDistributorTest {

	@Test
	void listenerDeliversRemoteKeyedMessageToEveryLocalSubscriber() throws Exception {
		StringRedisTemplate template = new StringRedisTemplate();
		RedisMessageDistributor remoteInstance = new RedisMessageDistributor(template);
		RedisSseEmitterMessageListener listener = new RedisSseEmitterMessageListener(template, remoteInstance);
		List<String> left = new CopyOnWriteArrayList<>();
		List<String> right = new CopyOnWriteArrayList<>();
		CountDownLatch delivered = new CountDownLatch(2);
		try (var first = remoteInstance.subscribe("run-1", message -> {
			left.add(message);
			delivered.countDown();
		}, null); var second = remoteInstance.subscribe("run-1", message -> {
			right.add(message);
			delivered.countDown();
		}, null)) {
			listener.onMessage(message(template, MessageDO.keyedMessage("run-1", "delta")), null);
			assertTrue(await(delivered, Duration.ofSeconds(2)));
			assertEquals(List.of("delta"), left);
			assertEquals(List.of("delta"), right);
		}
	}

	private Message message(StringRedisTemplate template, MessageDO payload) {
		RedisSerializer<String> serializer = template.getStringSerializer();
		byte[] body = serializer.serialize(JSONUtil.toJsonStr(payload));
		byte[] channel = serializer.serialize(RedisSseEmitterMessageListener.CHANNEL);
		return new Message() {
			@Override
			public byte[] getBody() {
				return body;
			}

			@Override
			public byte[] getChannel() {
				return channel;
			}
		};
	}

	private boolean await(CountDownLatch latch, Duration timeout) throws InterruptedException {
		Instant deadline = Instant.now().plus(timeout);
		while (latch.getCount() > 0 && Instant.now().isBefore(deadline)) {
			Thread.sleep(5);
		}
		return latch.getCount() == 0;
	}

}
