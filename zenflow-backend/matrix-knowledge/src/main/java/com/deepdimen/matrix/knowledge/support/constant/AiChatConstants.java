package com.deepdimen.matrix.knowledge.support.constant;

/**
 * AI聊天常量接口
 * <p>
 * 定义了AI聊天功能中使用的常量，主要用于流式响应的控制标识
 *
 * @author caoyuan
 * @date 2025/03/22
 */
public interface AiChatConstants {

	/**
	 * 流式响应结束标识
	 * <p>
	 * 当AI模型完成响应时，会发送此标识表示消息流结束
	 */
	String END_MSG = "[DONE]";

}
