package com.deepdimen.matrix.work.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.deepdimen.matrix.common.core.util.TenantTable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_work_task_skill_invocation")
public class WorkTaskSkillInvocationEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long tenantId;

	private Long ownerUserId;

	private Long orgId;

	private Long policyId;

	private Integer policyVersion;

	private String slotCode;

	private Long parentTaskId;

	private Long dispatchRunId;

	private Long modelId;

	private Long sourcePackageId;

	private String inputSnapshot;

	private String status;

	private Long skillRunId;

	private String skillOutputJson;

	private LocalDateTime createdAt;

}
