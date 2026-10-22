package com.deepdimen.matrix.common.sse.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.deepdimen.matrix.common.sse.distribute.RedisMessageDistributor;
import com.deepdimen.matrix.common.sse.distribute.RedisSseEmitterMessageListener;

class RedisMessageDistributorConfigurationTest {

	@Test
	void registrarConnectsRedisFanOutListenerAfterSingletonInitialization() {
		StringRedisTemplate template = new StringRedisTemplate();
		RedisMessageDistributor distributor = new RedisMessageDistributor(template);
		RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<RedisMessageDistributor> distributorProvider = mock(ObjectProvider.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<RedisMessageListenerContainer> containerProvider = mock(ObjectProvider.class);
		when(distributorProvider.getIfAvailable()).thenReturn(distributor);
		when(containerProvider.getIfAvailable()).thenReturn(container);

		new RedisMessageDistributorConfiguration()
			.sseRedisMessageListenerRegistrar(template, distributorProvider, containerProvider)
			.afterSingletonsInstantiated();

		verify(container).addMessageListener(any(RedisSseEmitterMessageListener.class), any(PatternTopic.class));
	}

}
