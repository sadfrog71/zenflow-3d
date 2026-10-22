package com.deepdimen.matrix.common.sse.distribute;

/**
 * 本地消息分发，直接进行发送
 *
 * @author caoyuan 2021/1/12
 * @version 1.0
 */
public class LocalMessageDistributor implements MessageDistributor, MessageSender {

	private final KeyedMessageHub keyedHub = new KeyedMessageHub();

	/**
	 * 消息分发
	 * @param messageDO 发送的消息
	 */
	@Override
	public void distribute(MessageDO messageDO) {
		if (messageDO.getTopicKey() != null) {
			keyedHub.publish(messageDO.getTopicKey(), messageDO.getMessageText());
		}
		else {
			doSend(messageDO);
		}
	}

	@Override
	public MessageSubscription subscribe(String key, java.util.function.Consumer<String> consumer,
			Runnable overflowHandler) {
		return keyedHub.subscribe(key, consumer, overflowHandler);
	}

	public int subscriberCount(String key) {
		return keyedHub.subscriberCount(key);
	}

}
