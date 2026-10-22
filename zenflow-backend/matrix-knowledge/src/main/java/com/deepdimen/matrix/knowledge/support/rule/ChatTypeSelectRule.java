package com.deepdimen.matrix.knowledge.support.rule;

import com.deepdimen.matrix.knowledge.dto.ChatMessageContext;
import com.deepdimen.matrix.knowledge.dto.ChatMessageDTO;
import com.deepdimen.matrix.knowledge.support.constant.ChatTypeEnums;
import com.yomahub.liteflow.core.NodeSwitchComponent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 聊天类型选择规则
 * <p>
 * 基于LiteFlow的选择节点组件，用于根据数据集ID动态选择聊天类型。 支持向量聊天、数据库聊天、函数调用、MCP等多种聊天模式的路由选择。
 *
 * @author caoyuan
 * @date 2024/3/26
 */
@Slf4j
@Component("selectChatType")
public class ChatTypeSelectRule extends NodeSwitchComponent {

	/**
	 * 执行聊天类型选择逻辑
	 * <p>
	 * 从上下文中获取聊天消息，根据数据集ID确定聊天类型。 如果无法识别类型，则默认使用向量聊天模式。
	 * @return 聊天类型字符串，用于LiteFlow流程路由
	 * @throws Exception 处理异常
	 */
	@Override
	public String processSwitch() throws Exception {
		// 从LiteFlow上下文中获取聊天消息上下文
		ChatMessageContext messageContext = this.getContextBean(ChatMessageContext.class);
		ChatMessageDTO lastUserMessage = messageContext.getChatMessageDTO();

		// 根据数据集ID选择对应的聊天类型
		ChatTypeEnums chatTypeEnums = ChatTypeEnums.fromCode(lastUserMessage.getDatasetId());
		// 如果无法识别类型，默认使用向量聊天
		return Objects.requireNonNullElse(chatTypeEnums, ChatTypeEnums.VECTOR_CHAT).getType();

	}

}
