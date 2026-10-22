package com.deepdimen.matrix.work.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TenantTable
@TableName("ai_information_run")
public class InformationRun {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;
	private Long policyId;
	private Long orgId;
	private String status;
	private Integer foundCount;
	private Integer extractedCount;
	private Integer approvedCount;
	private Integer publishedCount;
	private Integer creditCost;
	private String errorMessage;
	private LocalDateTime startedAt;
	private LocalDateTime finishedAt;
	private LocalDateTime createTime;
}
