package com.deepdimen.matrix.knowledge.service;

import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.dto.ChatSendDTO;
import reactor.core.publisher.Flux;

/**
 * AI对话交互服务接口 提供用户与AI助手的对话管理功能，包括会话保持、上下文记忆等
 *
 * @author caoyuan
 * @date 2024/3/20
 */
public interface ChatService {

	/**
	 * 获取聊天消息列表 以流式方式返回指定会话的历史消息记录
	 * @param key 会话标识符
	 * @return 返回响应式流对象，包含历史消息列表
	 */
	Flux<AiMessageResultDTO> chatList(Long key);

	/**
	 * 保存会话连接参数 初始化或更新会话配置，包括模型选择、参数设置等
	 * @param chatMessageDTO 聊天消息配置对象，包含会话参数和初始消息
	 * @return 返回会话UUID，用于后续的对话交互
	 */
	R<String> saveConnectionParams(ChatMessageDTO chatMessageDTO);

	/**
	 * 清除会话记忆 删除指定会话的上下文记忆，重置对话状态
	 * @param id 会话标识符
	 * @return 清除操作是否成功
	 */
	boolean clearMemory(String id);

	/**
	 * 前台对话发送（合并消息发送与流式响应）
	 * @param chatSendDTO 对话发送请求体
	 * @return SSE流式响应
	 */
	Flux<AiMessageResultDTO> chatSend(ChatSendDTO chatSendDTO);

}
