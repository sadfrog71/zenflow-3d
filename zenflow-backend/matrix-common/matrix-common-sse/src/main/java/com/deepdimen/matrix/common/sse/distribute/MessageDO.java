package com.deepdimen.matrix.common.sse.distribute;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author caoyuan 2021/1/12
 * @version 1.0
 */
@Data
@Accessors(chain = true)
public class MessageDO {

	/**
	 * 是否广播
	 */
	private Boolean needBroadcast;

	/**
	 * sessionKeys
	 */
	private List<Object> sessionKeys;

	/** Logical fan-out key used by recoverable streams such as assistant runs. */
	private String topicKey;

	/**
	 * 需要发送的消息文本
	 */
	private String messageText;

	/**
	 * 构建需要广播的message
	 * @author lingting 2021-03-25 17:28
	 */
	public static MessageDO broadcastMessage(String text) {
		return new MessageDO().setMessageText(text).setNeedBroadcast(true);
	}

	public static MessageDO keyedMessage(String key, String text) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("message key must not be blank");
		}
		return new MessageDO().setTopicKey(key).setMessageText(text).setNeedBroadcast(false);
	}

}
