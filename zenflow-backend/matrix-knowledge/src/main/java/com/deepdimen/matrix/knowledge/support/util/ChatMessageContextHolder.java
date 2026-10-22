package com.deepdimen.matrix.knowledge.support.util;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import lombok.experimental.UtilityClass;

/**
 * 聊天消息上下文持有者
 * <p>
 * 使用TransmittableThreadLocal实现跨线程传递聊天消息上下文， 支持在异步任务和线程池场景下保持消息上下文的正确传递
 * </p>
 *
 * @author caoyuan
 * @date 2024/09/26
 */
@UtilityClass
public class ChatMessageContextHolder {

	/**
	 * 使用TransmittableThreadLocal存储聊天消息，支持跨线程传递
	 */
	private final ThreadLocal<ChatMessageDTO> THREAD_LOCAL_TENANT = new TransmittableThreadLocal<>();

	/**
	 * 设置当前线程的聊天消息上下文
	 * @param chatMessageDTO 聊天消息数据传输对象
	 */
	public void set(ChatMessageDTO chatMessageDTO) {
		THREAD_LOCAL_TENANT.set(chatMessageDTO);
	}

	/**
	 * 获取当前线程的聊天消息上下文
	 * @return 当前线程的聊天消息数据传输对象，如果未设置则返回null
	 */
	public ChatMessageDTO get() {
		return THREAD_LOCAL_TENANT.get();
	}

	/**
	 * 清除当前线程的聊天消息上下文
	 * <p>
	 * 在请求处理完成后应调用此方法，避免内存泄漏
	 * </p>
	 */
	public void clear() {
		THREAD_LOCAL_TENANT.remove();
	}

}
