package com.deepdimen.matrix.discover.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * Dify 对话记录实体，映射 ai_chat_record 表（仅 Dify 透传场景使用）
 */
@Data
@TableName("ai_chat_record")
@EqualsAndHashCode(callSuper = true)
@TenantTable
public class DifyChatRecordEntity extends Model<DifyChatRecordEntity> {

	@TableId(type = IdType.ASSIGN_ID)
	private Long recordId;

	@TableField(fill = FieldFill.INSERT)
	private String tenantId;

	private String modelName;

	private String userAgent;

	private String username;

	private String questionText;

	private String answerText;

	private Long agentId;

	private String conversationId;

	private String messageId;

	private String evaluate;

	private String sessionInputs;

	private String ip;

	private String llmFlag;

	private String innerFlag;

	private Long useTime;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	private String delFlag;

}
