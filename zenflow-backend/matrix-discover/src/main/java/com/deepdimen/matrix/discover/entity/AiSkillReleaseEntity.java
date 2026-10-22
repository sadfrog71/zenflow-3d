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
@TableName("ai_skill_release")
public class AiSkillReleaseEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long skillId;

	private Long packageId;

	private String tenantId;

	private String version;

	private String runtimeKind;

	private String contentSnapshot;

	private String inputSchema;

	private String outputSchema;

	private String executorRef;

	private String resourceRefs;

	private String limitsJson;

	private String allowedParameters;

	private String packageSha256;

	private String releaseDigest;

	private String status;

	private String createdBy;

	private String revokedBy;

	private String revocationReason;

	private LocalDateTime createdAt;

	private LocalDateTime revokedAt;

}
