package com.deepdimen.matrix.knowledge.service;

import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * AI 函数调用助手服务接口
 * <p>
 * 提供基于函数调用的AI对话服务，支持流式响应。 该服务能够解析用户意图并调用相应的函数工具来完成任务。
 *
 * @author caoyuan
 * @date 2024/09/27
 */
public interface AiFunctionAssistantService {

	/**
	 * 流式对话接口
	 * <p>
	 * 接收用户消息，通过AI模型理解意图并调用相应函数工具， 以流式方式返回处理结果，提供更好的用户体验。
	 * @param userMessage 用户输入的消息内容
	 * @return {@link Flux}<{@link String}> 响应消息流，支持实时推送
	 */
	Flux<String> chatFlux(@UserMessage String userMessage);

}
