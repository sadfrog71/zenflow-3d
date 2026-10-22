package com.deepdimen.matrix.work.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_work_task_dispatch_run")
public class WorkTaskDispatchRunEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long tenantId;

	private Long taskId;

	private Integer attemptNo;

	private String status;

	private Long creatorId;

	private Integer inputTaskVersion;

	private Integer policyVersion;

	private Long skillPackageId;

	private String skillHash;

	private Long modelId;

	private String candidateSnapshot;

	private String inputHash;

	private String resultJson;

	private String reasonCode;

	private String leaseOwner;

	private LocalDateTime leaseUntil;

	private LocalDateTime createdAt;

	private LocalDateTime finishedAt;

}
