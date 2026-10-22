package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;

/**
 * AI 报告
 *
 * @author matrix
 * @date 2025-05-11 22:14:42
 */
@Data
@TableName("ai_report_conf")
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI 报告")

public class AiReportConfEntity extends Model<AiReportConfEntity> {

	/**
	 * 主键
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键")
	private Long id;

	/**
	 * 标题
	 */
	@Schema(description = "标题")
	private String reportTitle;

	/**
	 * 描述提示词
	 */
	@Schema(description = "描述提示词")
	private String reportPrompt;

	/**
	 * 资源base64
	 */
	@Schema(description = "资源base64")
	private String reportResource;

	/**
	 * 标注结果
	 */
	@Schema(description = "标注结果")
	private String reportMarked;

	/**
	 * 创建人
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	/**
	 * 创建时间
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	/**
	 * 修改人
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改人")
	private LocalDateTime updateBy;

	/**
	 * 修改时间
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	/**
	 * 删除标记
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标记")
	private String delFlag;

}