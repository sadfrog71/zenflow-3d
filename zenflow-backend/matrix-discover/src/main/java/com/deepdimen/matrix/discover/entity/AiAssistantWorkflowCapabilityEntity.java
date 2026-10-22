package com.deepdimen.matrix.discover.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Administrator-maintained draft for one Agent-backed Workflow Capability. */
@Data
@TableName("ai_assistant_workflow_capability")
@TenantTable
@EqualsAndHashCode(callSuper = true)
public class AiAssistantWorkflowCapabilityEntity extends Model<AiAssistantWorkflowCapabilityEntity> {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	@TableField(fill = FieldFill.INSERT)
	private String tenantId;

	private Long agentId;

	private String capabilityCode;

	private String status;

	private String intentExamplesJson;

	private String inputSchemaJson;

	private String outputSchemaJson;

	private String requiredProvidersJson;

	private String dataClassification;

	private String sideEffectClass;

	private String egressAllowlistJson;

	private Integer allowCodeNode;

	private String toolAllowlistJson;

	private Integer timeoutMs;

	private Long activeReleaseId;

	private String activeSourceDigest;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	private String delFlag;

}
