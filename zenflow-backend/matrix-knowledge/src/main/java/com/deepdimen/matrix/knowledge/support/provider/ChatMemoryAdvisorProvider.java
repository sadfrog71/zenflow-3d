package com.deepdimen.matrix.knowledge.support.provider;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import com.deepdimen.matrix.knowledge.config.properties.AiKnowledgeProperties;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 聊天记忆管理提供者
 * <p>
 * 实现ChatMemoryProvider接口，负责管理聊天会话的历史记忆 使用LRU缓存策略存储每个会话的聊天记忆，避免内存溢出 支持根据配置调整会话历史窗口大小
 *
 * @author caoyuan
 * @date 2024/09/26
 */
@Service
@RequiredArgsConstructor
public class ChatMemoryAdvisorProvider implements ChatMemoryProvider {

	/**
	 * 聊天记忆LRU缓存 使用缓存策略管理会话记忆，最大支持1024000个会话
	 */
	private final static Cache<String, ChatMemory> chatMemoryMap = CacheUtil.newLRUCache(1024 * 1000);

	/**
	 * AI知识库配置属性
	 */
	private final AiKnowledgeProperties aiKnowledgeProperties;

	/**
	 * 获取指定会话的聊天记忆 如果记忆不存在，则创建新的聊天记忆实例
	 * @param memoryId 会话ID
	 * @return 聊天记忆实例
	 */
	@Override
	public ChatMemory get(Object memoryId) {
		ChatMemory chatMemory = chatMemoryMap.get(memoryId.toString());

		if (Objects.isNull(chatMemory)) {
			chatMemory = MatrixMessageWindowChatMemoryImpl.builder()
				.id(memoryId.toString())
				.maxMessages(aiKnowledgeProperties.getChatHistoryWindowSize())
				.build();
			chatMemoryMap.put(memoryId.toString(), chatMemory);
		}

		return chatMemory;
	}

}
