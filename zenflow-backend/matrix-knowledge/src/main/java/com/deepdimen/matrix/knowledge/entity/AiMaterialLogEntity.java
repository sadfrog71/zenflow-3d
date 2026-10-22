package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI资源记录
 *
 * @author matrix ai
 * @date 2025-05-08 11:06:03
 */
@Data
@TableName("ai_material_log")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI资源记录")
@TenantTable
public class AiMaterialLogEntity extends Model<AiMaterialLogEntity> {

	/**
	 * 主键ID
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	/**
	 * 租户ID
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "租户ID")
	private String tenantId;

	/**
	 * 资源类型
	 */
	@Schema(description = "资源类型")
	private String materialType;

	/**
	 * 物料状态
	 */
	@Schema(description = "资源状态")
	private String materialStatus;

	/**
	 * 提示词
	 */
	@Schema(description = "提示词")
	private String prompt;

	/**
	 * 模型
	 */
	@Schema(description = "模型")
	private String modelName;

	/**
	 * 原生链接
	 */
	@Schema(description = "原生链接")
	private String originalUrl;

	/**
	 * 本地链接
	 */
	@Schema(description = "本地链接")
	private String localUrl;

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
	 * 删除标记
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标记")
	private String delFlag;

}
