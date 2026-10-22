package com.deepdimen.matrix.work.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_work_analysis_run")
public class WorkBriefRunEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long ownerUserId;

	private Long orgId;

	private Long policyId;

	private Long skillId;

	private Long skillRunId;

	@com.baomidou.mybatisplus.annotation.TableField(select = false)
	private String skillOutputJson;

	private String triggerType;

	private String status;

	private String skillCode;

	private String skillVersion;

	private String inputHash;

	private String inputSnapshot;

	private Integer taskCount;

	private Integer scheduleCount;

	private Integer informationCount;

	private Integer messageCount;

	private Integer emailCount;

	private Integer externalTaskCount;

	private String reviewerStatus;

	private String errorMessage;

	private String provider;

	private String model;

	private Long promptTokens;

	private Long completionTokens;

	private Long totalTokens;

	private String usageSource;

	private String requestId;

	private LocalDateTime startedAt;

	private LocalDateTime finishedAt;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	private Long tenantId;

}
