package com.deepdimen.matrix.discover.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;
import java.time.LocalDateTime;

/** Managed Skill storage; legacy projections are maintained separately. */
@Data
@TenantTable
@TableName("ai_skill_run")
public class AiSkillRunEntity {
	private String subjectType="ADMIN_USER";
	private String actorType="ADMIN_USER";

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long bindingRevisionId;

	private Long releaseId;

	private Long revision;

	private String tenantId;

	private String actorId;

	private String subjectId;

	private String consumerType;

	private String consumerId;

	private String slotCode;

	private String bindingKey;

	private String parentRunId;

	private String businessRunId;

	private String correlationId;

	private String idempotencyKey;

	private String idempotencyScopeDigest;

	private String inputDigest;

	private String requestDigest;

	private String status;

	private String resultRef;

	private String artifactRefs;

	private String executorVersion;

	private String errorCode;

	private String timing;

	private String usageJson;

	private LocalDateTime createdAt;

	private LocalDateTime startedAt;

	private LocalDateTime finishedAt;

}
