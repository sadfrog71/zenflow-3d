package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI问答明细
 */
@Data
@Schema(description = "AI问答明细")
public class AiChatDetailVO {

	private LocalDateTime questionTime;

	private String username;

	private String name;

	private String deptName;

	private String agentName;

	private String questionText;

	private String responseStatus;

	private Long responseTime;

	private Long promptTokens;

	private Long completionTokens;

	private String evaluate;

	private String conversationId;

	private String messageId;

}
