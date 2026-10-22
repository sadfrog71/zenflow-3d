package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 消息评价请求体
 * <p>
 * 用于 POST /chat/record/evaluate 接口
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "消息评价请求体")
public class ChatEvaluateDTO implements Serializable {

	/**
	 * 消息ID（对应 ai_chat_record.record_id）
	 */
	@NotNull(message = "消息ID不能为空")
	@Schema(description = "消息ID，对应 ai_chat_record.record_id")
	private Long messageId;

	/**
	 * 评价类型: like/dislike/null（null表示取消评价）
	 */
	@Schema(description = "评价类型: like/dislike/null（null表示取消评价）")
	private String type;

	private String queryId;

	private String requestId;

	private com.deepdimen.matrix.knowledge.support.dataquery.DataQueryCorrectionService.Category category;

	private String description;

}
