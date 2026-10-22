package com.deepdimen.matrix.work.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.deepdimen.matrix.common.core.util.TenantTable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_information_skill_invocation")
public class InformationSkillInvocationEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long tenantId;

	private Long policyId;

	private Long collectionRunId;

	private Long orgId;

	private Long parentInvocationId;

	private Long skillRunId;

	private String actorId;

	private String slotCode;

	private String documentDigest;

	private String instructionDigest;

	private String policySnapshot;

	private String evidenceSnapshot;

	private String inputSnapshot;

	private String status;

	private String skillOutputJson;

	private LocalDateTime createdAt;

}
