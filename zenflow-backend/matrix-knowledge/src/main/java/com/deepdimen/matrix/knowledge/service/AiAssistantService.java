package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.knowledge.dto.AiSuggestionFieldResultDTO;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * AI助手服务接口 提供AI对话和数据库字段智能分析功能
 *
 * @author caoyuan
 * @date 2024/09/27
 */
public interface AiAssistantService {

	/**
	 * 带会话记忆的AI对话 支持上下文连续对话，适用于需要保持对话历史的场景
	 * @param memoryId 会话内存ID，用于标识和保持对话上下文
	 * @param userMessage 用户输入的消息内容
	 * @return AI生成的回复内容
	 */
	String chat(@MemoryId String memoryId, @UserMessage String userMessage);

	/**
	 * 带会话记忆的AI对话，支持上下文连续对话
	 * @param memoryId 会话内存ID，用于标识和保持对话上下文
	 * @param systemMessage 系统消息内容
	 * @param userMessage 用户输入的消息内容
	 * @return AI生成的回复内容
	 */
	@SystemMessage("{{systemMessage}}")
	String chat(@MemoryId String memoryId, @V("systemMessage") String systemMessage, @UserMessage String userMessage);

	/**
	 * 无状态AI对话 单次对话，不保留历史记录，适用于独立问答场景
	 * @param userMessage 用户输入的消息内容
	 * @return AI生成的回复内容
	 */
	String chat(@UserMessage String userMessage);

	/**
	 * 智能评估数据库表字段 根据字段名称、数据类型等信息，自动生成规范的中文注释
	 * @param text 包含数据库字段信息的文本内容
	 * @return 字段评估建议结果，包含生成的注释等信息
	 */
	@SystemMessage("""
			你是一位MySQL字段注释生成专家，能够根据数据库的字段名称、数据类型和已有注释，生成简洁明了且具有描述性的字段注释
			""")
	@UserMessage("提取关于数据库字段的信息 {{text}}")
	AiSuggestionFieldResultDTO assessTableField(@V("text") String text);

}
