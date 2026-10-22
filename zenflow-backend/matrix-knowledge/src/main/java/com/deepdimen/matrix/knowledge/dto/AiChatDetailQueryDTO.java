package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * AI问答明细查询参数
 */
@Data
@Schema(description = "AI问答明细查询参数")
public class AiChatDetailQueryDTO {

	@Schema(hidden = true)
	private String tenantId;

	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Schema(description = "开始时间")
	private LocalDateTime startTime;

	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@Schema(description = "结束时间")
	private LocalDateTime endTime;

	@Schema(description = "用户名或姓名关键词")
	private String userKeyword;

	@Schema(description = "部门ID")
	private Long deptId;

	@Schema(description = "智能体ID")
	private Long agentId;

	@Schema(description = "响应状态")
	private String responseStatus;

	@Schema(description = "用户反馈")
	private String evaluate;

	@Schema(description = "原始问题关键词")
	private String questionKeyword;

	@Schema(description = "会话ID")
	private String conversationId;

	@Schema(description = "消息ID")
	private String messageId;

}
