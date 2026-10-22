package com.deepdimen.matrix.discover.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Immutable, approved routing and Workflow identity used by Assistant Runs. */
@Data
@TableName("ai_assistant_workflow_capability_release")
@TenantTable
@EqualsAndHashCode(callSuper = true)
public class AiAssistantWorkflowCapabilityReleaseEntity extends Model<AiAssistantWorkflowCapabilityReleaseEntity> {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	@TableField(fill = FieldFill.INSERT)
	private String tenantId;

	private Long capabilityId;

	private Long agentId;

	private String capabilityCode;

	private String releaseVersion;

	private String releaseStatus;

	private String displayName;

	private String description;

	private String operationGuideHtml;

	private String operationGuideText;

	private String operationRulesJson;

	private String operationGuideHtmlDigest;

	private String operationGuideTextDigest;

	private String routingProfileDigest;

	private String sourceDigest;

	private String intentExamplesJson;

	private String inputSchemaJson;

	private String inputSchemaId;

	private String outputSchemaJson;

	private String outputSchemaId;

	private String requiredProvidersJson;

	private String dataClassification;

	private String sideEffectClass;

	private String egressAllowlistJson;

	private Integer allowCodeNode;

	private String toolAllowlistJson;

	private Integer timeoutMs;

	private String difyAppId;

	private String publishedWorkflowId;

	private String workflowDefinitionDigest;

	private Integer policyAuditPassed;

	private String policyAuditJson;

	private String releaseDigest;

	private LocalDateTime releasedAt;

	private LocalDateTime revokedAt;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

}
