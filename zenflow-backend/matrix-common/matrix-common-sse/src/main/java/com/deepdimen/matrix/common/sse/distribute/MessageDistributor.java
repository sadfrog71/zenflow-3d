package com.deepdimen.matrix.common.sse.distribute;

import java.util.function.Consumer;

/**
 * 消息分发器
 *
 * @author caoyuan 2021/1/12
 * @version 1.0
 */
public interface MessageDistributor {

	/**
	 * 消息分发
	 * @param messageDO 发送的消息
	 */
	void distribute(MessageDO messageDO);

	default void distributeToKey(String key, String messageText) {
		distribute(MessageDO.keyedMessage(key, messageText));
	}

	default MessageSubscription subscribe(String key, Consumer<String> consumer, Runnable overflowHandler) {
		throw new UnsupportedOperationException("Keyed subscriptions are not supported");
	}

	interface MessageSubscription extends AutoCloseable {

		@Override
		void close();

	}

}
