package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 大模型流程表
 *
 * @author matrix
 * @date 2025-03-03 10:21:32
 */
@Data
@TableName("ai_flow")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "大模型流程表")
@TenantTable
public class AiFlowEntity extends Model<AiFlowEntity> {

	/**
	 * 流程主键
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "流程主键")
	private Long id;

	/**
	 * 租户ID
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "租户ID")
	private String tenantId;

	/**
	 * 流程名称
	 */
	@Schema(description = "流程名称")
	private String name;

	/**
	 * 流程描述
	 */
	@Schema(description = "流程描述")
	private String description;

	/**
	 * 流程类型
	 */
	@Schema(description = "流程类型")
	private String type;

	/**
	 * 流程设计DSL
	 */
	@Schema(description = "流程设计DSL")
	private String dsl;

	/**
	 * 流程图标
	 */
	@Schema(description = "流程图标")
	private String icon;

	/**
	 * 是否启用api
	 */
	@Schema(description = "是否启用api")
	private String apiEnabled;

	/**
	 * 其否启用
	 */
	@Schema(description = "其否启用")
	private String enabled;

	/**
	 * 状态
	 */
	@Schema(description = "状态")
	private String status;

	/**
	 * 最终结果
	 */
	@Schema(description = "最终结果")
	private String result;

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
	 * 修改时间
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	/**
	 * 是否已删除
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "是否已删除")
	private String delFlag;

}
