package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 最近对话记录视图对象
 *
 * @author matrix
 */
@Data
@Schema(description = "最近对话记录")
public class RecentConversationVO {

	/**
	 * 会话ID
	 */
	@Schema(description = "会话ID")
	private String conversationId;

	/**
	 * 关联知识库ID
	 */
	@Schema(description = "知识库ID")
	private Long datasetId;

	/**
	 * 关联智能体ID
	 */
	@Schema(description = "关联智能体ID")
	private Long agentId;

	/**
	 * 智能体名称
	 */
	@Schema(description = "智能体名称")
	private String agentName;

	/**
	 * 用户问题文本
	 */
	@Schema(description = "用户问题文本")
	private String questionText;

	/**
	 * 创建时间
	 */
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
