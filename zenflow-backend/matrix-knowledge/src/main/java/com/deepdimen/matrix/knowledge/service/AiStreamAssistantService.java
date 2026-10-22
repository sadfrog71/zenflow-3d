package com.deepdimen.matrix.knowledge.service;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import reactor.core.publisher.Flux;

/**
 * AI流式对话助手服务接口 提供基于LangChain4j的流式AI对话能力，支持上下文记忆和系统提示
 *
 * @author caoyuan
 * @date 2024/09/27
 */
public interface AiStreamAssistantService {

	/**
	 * 带系统提示的流式对话 支持自定义系统提示词，用于设定AI助手的角色和行为规范
	 * @param memoryId 会话记忆ID，用于维护对话上下文
	 * @param systemMessage 系统提示信息，定义AI助手的角色和回复规则
	 * @param userMessage 用户输入的消息内容
	 * @return 返回响应式流对象，支持流式输出AI回复内容
	 */
	@SystemMessage("{{systemMessage}}")
	Flux<String> chat(@MemoryId String memoryId, @V("systemMessage") String systemMessage,
			@UserMessage String userMessage);

	/**
	 * 标准流式对话 使用默认的系统设置进行对话，保持会话上下文连续性
	 * @param memoryId 会话记忆ID，用于维护对话上下文
	 * @param userMessage 用户输入的消息内容
	 * @return 返回响应式流对象，支持流式输出AI回复内容
	 */
	Flux<String> chat(@MemoryId String memoryId, @UserMessage String userMessage);

}
