package com.deepdimen.matrix.discover.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 智能体技能关联表（物理删除，无 del_flag）
 *
 * @author matrix
 * @date 2026-04-25
 */
@Data
@TableName("ai_agent_skill")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "智能体技能关联表")
@TenantTable
public class AiAgentSkillEntity extends Model<AiAgentSkillEntity> {

	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "租户ID")
	private String tenantId;

	@Schema(description = "智能体ID")
	private Long agentId;

	@Schema(description = "技能ID")
	private Long skillId;

	@Schema(description = "管理员配置的默认开关: 1开启 0关闭")
	private Integer isDefaultEnabled;

	@Schema(description = "技能实例配置JSON，如chat-db的{dataId,dataName}")
	private String skillConfig;

	@Schema(description = "执行策略: PRIMARY_APP_INSTRUCTION/DEDICATED_DIFY_WORKFLOW")
	private String executionStrategy;

	@Schema(description = "专用Dify Workflow对应的平台智能体ID")
	private Long workflowAgentId;

	@Schema(description = "MATRIX规范输入到Dify变量的映射JSON")
	private String inputMapping;

	@Schema(description = "Dify输出到MATRIX规范输出的映射JSON")
	private String outputMapping;

	@Schema(description = "结果模式: FINAL_RESPONSE/INTERMEDIATE_RESULT")
	private String resultMode;

	@Schema(description = "超时毫秒数")
	private Integer timeoutMs;

	@Schema(description = "重试策略: NONE/SAFE_ONCE")
	private String retryPolicy;

	@Schema(description = "副作用分类: READ_ONLY/PROPOSAL_ONLY")
	private String sideEffectClass;

	@Schema(description = "排序")
	private Integer sort;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

}
