package com.deepdimen.matrix.knowledge.support.rule;

import com.deepdimen.matrix.knowledge.dto.ChatMessageContext;
import com.yomahub.liteflow.core.NodeBooleanComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 内部消息判断规则
 * <p>
 * 基于LiteFlow的布尔节点组件，用于判断消息是否为内部消息。 内部消息可能具有特殊权限或跳过某些验证流程。
 *
 * @author caoyuan
 * @date 2024/4/14
 */
@Slf4j
@Component("isInner")
@RequiredArgsConstructor
public class IsInnerChatRule extends NodeBooleanComponent {

	/**
	 * 判断是否为内部消息
	 * <p>
	 * 从上下文中获取聊天消息，检查其是否标记为内部消息。
	 * @return true表示是内部消息，false表示非内部消息
	 * @throws Exception 处理异常
	 */
	@Override
	public boolean processBoolean() throws Exception {
		// 从LiteFlow上下文中获取聊天消息上下文
		ChatMessageContext messageContext = this.getContextBean(ChatMessageContext.class);
		// 返回消息的内部标识
		return messageContext.getChatMessageDTO().isInner();
	}

}
