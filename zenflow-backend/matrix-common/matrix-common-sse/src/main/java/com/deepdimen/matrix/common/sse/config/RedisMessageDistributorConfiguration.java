package com.deepdimen.matrix.common.sse.config;

import com.deepdimen.matrix.common.sse.distribute.MessageDistributor;
import com.deepdimen.matrix.common.sse.distribute.RedisMessageDistributor;
import com.deepdimen.matrix.common.sse.distribute.RedisSseEmitterMessageListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 基于 Redis Pub/Sub 的消息分发器
 *
 * @author caoyuan
 */
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = SseEmitterProperties.PREFIX, name = "message-distributor",
		havingValue = MessageDistributorTypeConstants.REDIS, matchIfMissing = true)
@Configuration(proxyBeanMethods = false)
public class RedisMessageDistributorConfiguration {

	/**
	 * 创建Redis消息分发器Bean
	 * @param stringRedisTemplate Redis字符串模板
	 * @return Redis消息分发器实例
	 * @ConditionalOnMissingBean 当容器中不存在MessageDistributor类型的Bean时创建
	 */
	@Bean
	@ConditionalOnMissingBean(MessageDistributor.class)
	public RedisMessageDistributor sseMessageDistributor(StringRedisTemplate stringRedisTemplate) {
		return new RedisMessageDistributor(stringRedisTemplate);
	}

	/**
	 * 创建Redis消息监听容器
	 * @param connectionFactory Redis连接工厂
	 * @return Redis消息监听容器实例
	 * @see RedisMessageListenerContainer
	 */
	@Bean
	@ConditionalOnMissingBean
	public RedisMessageListenerContainer sseRedisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		return container;
	}

	/**
	 * 在单例初始化完成后注册 SSE Redis 消息监听器。
	 * <p>
	 * 使用 ObjectProvider 避免配置类解析阶段的条件竞态；当应用显式提供自定义 MessageDistributor 时安全跳过。
	 */
	@Bean
	public SmartInitializingSingleton sseRedisMessageListenerRegistrar(StringRedisTemplate stringRedisTemplate,
			ObjectProvider<RedisMessageDistributor> distributorProvider,
			ObjectProvider<RedisMessageListenerContainer> containerProvider) {
		return () -> {
			RedisMessageDistributor distributor = distributorProvider.getIfAvailable();
			RedisMessageListenerContainer container = containerProvider.getIfAvailable();
			if (distributor != null && container != null) {
				container.addMessageListener(new RedisSseEmitterMessageListener(stringRedisTemplate, distributor),
						new PatternTopic(RedisSseEmitterMessageListener.CHANNEL));
			}
		};
	}

}
