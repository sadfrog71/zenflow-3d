package com.deepdimen.matrix.common.sse.distribute;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 消息分发器
 *
 * @author caoyuan 2021/1/12
 * @version 1.0
 */
@RequiredArgsConstructor
public class RedisMessageDistributor implements MessageDistributor {

	private final StringRedisTemplate stringRedisTemplate;

	private final KeyedMessageHub keyedHub = new KeyedMessageHub();

	/**
	 * 消息分发
	 * @param messageDO 发送的消息
	 */
	@Override
	public void distribute(MessageDO messageDO) {
		String str = JSONUtil.toJsonStr(messageDO);
		stringRedisTemplate.convertAndSend(RedisSseEmitterMessageListener.CHANNEL, str);
	}

	@Override
	public MessageSubscription subscribe(String key, java.util.function.Consumer<String> consumer,
			Runnable overflowHandler) {
		return keyedHub.subscribe(key, consumer, overflowHandler);
	}

	void deliverLocal(MessageDO messageDO) {
		if (messageDO.getTopicKey() != null) {
			keyedHub.publish(messageDO.getTopicKey(), messageDO.getMessageText());
		}
	}

}
