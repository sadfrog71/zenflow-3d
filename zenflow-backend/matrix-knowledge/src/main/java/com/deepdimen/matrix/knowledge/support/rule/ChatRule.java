package com.deepdimen.matrix.knowledge.support.rule;

import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import reactor.core.publisher.Flux;

/**
 * 聊天规则接口
 * <p>
 * 定义了聊天处理的统一抽象接口，所有聊天规则实现类都需要实现此接口。 通过不同的规则实现，可以支持多种聊天场景，如数据库查询、函数调用、MCP集成等。
 *
 * @author caoyuan
 * @date 2024/6/3
 */
public interface ChatRule {

	/**
	 * 处理聊天信息
	 * <p>
	 * 接收用户的聊天消息，根据具体的规则实现进行处理， 并返回AI的响应结果流。默认返回空流。
	 * @param chatMessageDTO 聊天上下文信息，包含用户消息、会话ID、模型名称等
	 * @return AI响应消息的Flux流，支持流式输出
	 */
	default Flux<AiMessageResultDTO> process(ChatMessageDTO chatMessageDTO) {
		return Flux.empty();
	}

	default Flux<AiMessageResultDTO> processStreaming(ChatMessageDTO message) {
		return process(message);
	}

}
