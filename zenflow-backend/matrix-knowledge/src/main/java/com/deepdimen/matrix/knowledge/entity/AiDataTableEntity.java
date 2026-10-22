package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;

/**
 * AI数据表管理表 用于管理数据库表的元数据信息，支持表注释的虚拟化管理
 *
 * @author matrix
 * @date 2025-03-26 21:48:16
 */
@Data
@FieldNameConstants
@TableName("ai_data_table")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI数据表管理表")
@TenantTable
public class AiDataTableEntity extends Model<AiDataTableEntity> {

	/**
	 * 主键ID
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long tableId;

	/**
	 * 关联数据源名称 数据库连接的标识名称
	 */
	@Schema(description = "关联数据源名称")
	private String dsName;

	/** 数据库类型 */
	@Schema(description = "数据库类型")
	private String dsType;

	/**
	 * 表名称 数据库中的表名
	 */
	@Schema(description = "表名称")
	private String tableName;

	/**
	 * 物理表注释 数据库中的原始表注释
	 */
	@Schema(description = "物理表注释")
	private String tableComment;

	/**
	 * 虚拟表注释 AI学习时使用的优化后的表描述
	 */
	@Schema(description = "虚拟表注释")
	private String virtualComment;

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
