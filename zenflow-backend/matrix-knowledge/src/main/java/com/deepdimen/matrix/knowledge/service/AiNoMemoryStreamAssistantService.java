package com.deepdimen.matrix.knowledge.service;

import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * AI 无记忆流式对话服务接口
 * <p>
 * 提供无状态的流式AI对话服务，每次对话独立处理， 不保留历史上下文，适用于简单问答、实时生成等场景。
 *
 * @author caoyuan
 * @date 2024/09/27
 */
public interface AiNoMemoryStreamAssistantService {

	/**
	 * 无状态流式对话
	 * <p>
	 * 接收用户消息并生成响应，不依赖历史对话记录， 每次调用都是独立的会话，响应以流式方式返回。 适合用于文本摘要、翻译、简单问答等场景。
	 * @param userMessage 用户输入的消息内容
	 * @return {@link Flux}<{@link String}> 流式响应内容，支持逐字推送
	 */
	Flux<String> chat(@UserMessage String userMessage);

	/**
	 * 根据用户消息生成Token流
	 * @param userMessage 用户输入的消息
	 * @return 生成的Token流
	 */
	TokenStream chatTokenStream(@UserMessage String userMessage);

}
