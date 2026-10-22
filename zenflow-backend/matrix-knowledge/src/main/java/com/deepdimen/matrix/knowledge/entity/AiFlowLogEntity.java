package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 大模型流程日志表
 *
 * @author matrix
 * @date 2025-03-03 10:21:15
 */
@Data
@TableName("ai_flow_log")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "大模型流程日志表")
@TenantTable
public class AiFlowLogEntity extends Model<AiFlowLogEntity> {

	/**
	 * 流程日志主键
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "流程日志主键")
	private Long id;

	/**
	 * 流程主键
	 */
	@Schema(description = "流程主键")
	private Long flowId;

	/**
	 * 流程类型
	 */
	@Schema(description = "流程类型")
	private String type;

	/**
	 * 输入参数
	 */
	@Schema(description = "输入参数")
	private String inputParams;

	/**
	 * 输出参数
	 */
	@Schema(description = "输出参数")
	private String outputParams;

	/**
	 * 最终结果
	 */
	@Schema(description = "最终结果")
	private String result;

	/**
	 * 执行状态
	 */
	@Schema(description = "执行状态")
	private String executed;

	/**
	 * 错误信息
	 */
	@Schema(description = "错误信息")
	private String error;

	/**
	 * 运行时间
	 */
	@Schema(description = "运行时间")
	private String runTime;

	/**
	 * api key
	 */
	@Schema(description = "api key")
	private String apiKey;

	/**
	 * api tokens
	 */
	@Schema(description = "api tokens")
	private Long totalTokens;

	/**
	 * 创建人
	 */
	@Schema(description = "创建人")
	private Long createUser;

	/**
	 * 创建部门
	 */
	@Schema(description = "创建部门")
	private Long createDept;

	/**
	 * 修改人
	 */
	@Schema(description = "修改人")
	private Long updateUser;

	/**
	 * 状态
	 */
	@Schema(description = "状态")
	private Integer status;

	/**
	 * 是否已删除
	 */
	@Schema(description = "是否已删除")
	private Integer isDeleted;

	/**
	 * 创建时间
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	/**
	 * 修改时间
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

}
