package com.deepdimen.matrix.knowledge.support.flow.model.nodes;

import lombok.Data;

/**
 * AI 通知节点 用于在工作流中发送通知消息，支持多种通知渠道（如邮件、短信、钉钉等）
 *
 * @author caoyuan
 * @date 2025/03/03
 */
@Data
public class AiNoticeNode {

	/**
	 * 消息模板ID 系统中预定义的消息模板唯一标识
	 */
	private String templateId;

	/**
	 * 消息模板编码 用于标识特定类型的消息模板，便于模板管理和查找
	 */
	private String templateCode;

}
