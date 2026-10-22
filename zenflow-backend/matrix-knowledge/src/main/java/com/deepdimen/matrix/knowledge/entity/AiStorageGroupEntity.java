package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI存储分组表
 *
 * @author MATRIXAI
 * @date 2025-09-30 13:55:30
 */
@Data
@TenantTable
@TableName("ai_storage_group")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI存储分组表")
public class AiStorageGroupEntity extends Model<AiStorageGroupEntity> {

	/**
	 * 主键ID
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	/**
	 * 分组名称
	 */
	@Schema(description = "分组名称")
	private String groupName;

	/**
	 * 父分组ID
	 */
	@Schema(description = "父分组ID")
	private Long parentId;

	/**
	 * 分组图标
	 */
	@Schema(description = "分组图标")
	private String icon;

	/**
	 * 排序
	 */
	@Schema(description = "排序")
	private Integer sortOrder;

	/**
	 * 分组描述
	 */
	@Schema(description = "分组描述")
	private String description;

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
	private String updateBy;

	/**
	 * 更新时间
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	/**
	 * 删除标记(0-正常,1-删除)
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标记(0-正常,1-删除)")
	private String delFlag;

}
