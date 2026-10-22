package com.deepdimen.matrix.work.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_work_report")
public class WorkBriefReportEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long runId;

	private Long ownerUserId;

	private Long orgId;

	private Long policyId;

	private Long skillId;

	private LocalDate reportDate;

	private String title;

	private String executiveSummary;

	private String continuityNote;

	private String metricsJson;

	private String focusItemsJson;

	private String scheduleItemsJson;

	private String informationItemsJson;

	private String messageItemsJson;

	private String emailItemsJson;

	private String riskItemsJson;

	private String citationsJson;

	private String reviewerStatus;

	private String reviewerReason;

	private String skillCode;

	private String skillVersion;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
	private String delFlag;

	private Long tenantId;
}
