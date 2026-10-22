package com.deepdimen.matrix.knowledge.support.provider;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * 消息窗口聊天记忆实现
 * <p>
 * 实现了基于滑动窗口的聊天记忆管理 支持限制历史消息数量，自动淘汰旧消息 特殊处理系统消息、工具调用消息等复杂场景
 *
 * @author caoyuan
 * @date 2024/12/17
 */
public class MatrixMessageWindowChatMemoryImpl implements ChatMemory {

	private static final Logger log = LoggerFactory.getLogger(MatrixMessageWindowChatMemoryImpl.class);

	/**
	 * 会话ID
	 */
	private final Object id;

	/**
	 * 最大消息数量限制
	 */
	private final Integer maxMessages;

	/**
	 * 聊天记忆存储
	 */
	private final ChatMemoryStore store;

	private MatrixMessageWindowChatMemoryImpl(MatrixMessageWindowChatMemoryImpl.Builder builder) {
		this.id = ensureNotNull(builder.id, "id");
		this.maxMessages = ensureGreaterThanZero(builder.maxMessages, "maxMessages");
		this.store = ensureNotNull(builder.store, "store");
	}

	/**
	 * 查找系统消息
	 * @param messages 消息列表
	 * @return 系统消息（如果存在）
	 */
	private static Optional<SystemMessage> findSystemMessage(List<ChatMessage> messages) {
		return messages.stream()
			.filter(message -> message instanceof SystemMessage)
			.map(message -> (SystemMessage) message)
			.findAny();
	}

	/**
	 * 确保消息列表符合容量限制
	 * <p>
	 * 当消息数量超过限制时，自动淘汰最旧的消息 特殊处理系统消息和工具调用相关消息
	 * @param messages 消息列表
	 * @param maxMessages 最大消息数
	 */
	private static void ensureCapacity(List<ChatMessage> messages, int maxMessages) {
		while (messages.size() > maxMessages) {

			int messageToEvictIndex = 0;
			// 保留系统消息，从第二条消息开始淘汰
			if (messages.get(0) instanceof SystemMessage) {
				messageToEvictIndex = 1;
			}

			ChatMessage evictedMessage = messages.remove(messageToEvictIndex);
			log.trace("淘汰消息以满足容量要求: {}", evictedMessage);

			// 如果淘汰的是包含工具调用的AI消息，需要同时淘汰相关的工具执行结果消息
			if (evictedMessage instanceof AiMessage && ((AiMessage) evictedMessage).hasToolExecutionRequests()) {
				while (messages.size() > messageToEvictIndex
						&& messages.get(messageToEvictIndex) instanceof ToolExecutionResultMessage) {
					// 某些LLM（如OpenAI）不允许孤立的ToolExecutionResultMessage
					// 必须自动淘汰失去对应AiMessage的工具执行结果消息
					ChatMessage orphanToolExecutionResultMessage = messages.remove(messageToEvictIndex);
					log.trace("淘汰孤立的工具执行结果消息: {}", orphanToolExecutionResultMessage);
				}
			}
		}
	}

	public static MatrixMessageWindowChatMemoryImpl.Builder builder() {
		return new MatrixMessageWindowChatMemoryImpl.Builder();
	}

	public static MatrixMessageWindowChatMemoryImpl withMaxMessages(int maxMessages) {
		return builder().maxMessages(maxMessages).build();
	}

	@Override
	public Object id() {
		return id;
	}

	/**
	 * 添加消息到聊天记忆
	 * <p>
	 * 特殊处理系统消息的更新和default会话的清空逻辑
	 * @param message 要添加的消息
	 */
	@Override
	public void add(ChatMessage message) {
		List<ChatMessage> messages = messages();
		// 处理系统消息的更新
		if (message instanceof SystemMessage) {
			Optional<SystemMessage> systemMessage = findSystemMessage(messages);
			if (systemMessage.isPresent()) {
				if (systemMessage.get().equals(message)) {
					return; // 相同的系统消息不重复添加
				}
				else {
					messages.remove(systemMessage.get()); // 替换现有的系统消息
				}
			}
		}

		// 特殊处理default会话：每次添加新消息前清空历史
		if (id.toString().equals("default")) {
			store.deleteMessages(id);
			messages.add(message);
			store.updateMessages(id, messages);
			return;
		}

		messages.add(message);
		ensureCapacity(messages, maxMessages);
		store.updateMessages(id, messages);
	}

	/**
	 * 获取消息列表
	 * <p>
	 * 自动清理空白消息，确保消息格式正确
	 * @return 处理后的消息列表
	 */
	@Override
	public List<ChatMessage> messages() {
		List<ChatMessage> messages = new ArrayList<>(store.getMessages(id));
		ensureCapacity(messages, maxMessages);

		// 确保第一条消息是UserMessage（如果需要）
		if (CollUtil.isNotEmpty(messages) && !(CollUtil.getFirst(messages) instanceof UserMessage)) {
			messages.add(0, UserMessage.from(StrUtil.NULL));
		}

		// 安全地清理空白消息
		Iterator<ChatMessage> iterator = messages.iterator();
		while (iterator.hasNext()) {
			ChatMessage message = iterator.next();

			// 清理空白的用户消息
			if (message instanceof UserMessage userMessage) {
				if (StrUtil.isBlank(userMessage.singleText()) || StrUtil.isNullOrUndefined(userMessage.singleText())) {
					iterator.remove();
				}
			}

			// 清理空白的AI消息
			if (message instanceof AiMessage aiMessage) {
				if (StrUtil.isBlank(aiMessage.text()) || StrUtil.isNullOrUndefined(aiMessage.text())) {
					iterator.remove();
				}
			}
		}

		return messages;
	}

	@Override
	public void clear() {
		store.deleteMessages(id);
	}

	/**
	 * 构建器类
	 */
	public static class Builder {

		private Object id = "default";

		private Integer maxMessages;

		private ChatMemoryStore store = new InMemoryChatMemoryStore();

		/**
		 * 设置会话ID
		 * @param id 会话ID，如果不提供则使用"default"
		 * @return 构建器
		 */
		public MatrixMessageWindowChatMemoryImpl.Builder id(Object id) {
			this.id = id;
			return this;
		}

		/**
		 * 设置最大消息数量
		 * @param maxMessages 要保留的最大消息数，超出时会淘汰最旧的消息
		 * @return 构建器
		 */
		public MatrixMessageWindowChatMemoryImpl.Builder maxMessages(Integer maxMessages) {
			this.maxMessages = maxMessages;
			return this;
		}

		/**
		 * 设置聊天记忆存储
		 * @param store 负责存储聊天记忆状态的存储器，如果不提供则使用内存存储
		 * @return 构建器
		 */
		public MatrixMessageWindowChatMemoryImpl.Builder chatMemoryStore(ChatMemoryStore store) {
			this.store = store;
			return this;
		}

		public MatrixMessageWindowChatMemoryImpl build() {
			return new MatrixMessageWindowChatMemoryImpl(this);
		}

	}

}
