package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI公文模板
 *
 * @author aurorax
 * @date 2025-08-22 15:30:38
 */
@Data
@TableName("ai_document_template")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI公文模板")
public class AiDocumentTemplateEntity extends Model<AiDocumentTemplateEntity> {

	/**
	 * id
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "id")
	private Long id;

	/**
	 * 模板名称
	 */
	@Schema(description = "模板名称")
	private String name;

	/**
	 * 模板内容
	 */
	@Schema(description = "模板内容")
	private String content;

	/**
	 * 模板json
	 */
	@Schema(description = "模板json")
	private String json;

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
	 * 更新人
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "更新人")
	private String updateBy;

	/**
	 * 更新时间
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	/**
	 * 删除标记 (0-正常, 1-删除)
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标记 (0-正常, 1-删除)")
	private String delFlag;

}
