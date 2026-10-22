package com.deepdimen.matrix.work.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_work_task_policy")
public class WorkTaskPolicyEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long tenantId;

	private Integer version;

	private Boolean enabled;

	@com.fasterxml.jackson.annotation.JsonProperty(
			access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
	@com.baomidou.mybatisplus.annotation.TableField(
			insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
			updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
	private String parseSkillManagementMode = "LEGACY";

	@com.fasterxml.jackson.annotation.JsonProperty(
			access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
	@com.baomidou.mybatisplus.annotation.TableField(
			insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
			updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
	private String dispatchSkillManagementMode = "LEGACY";

	@com.baomidou.mybatisplus.annotation.TableField(
			updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
	private Long dispatchSkillPackageId;

	@com.baomidou.mybatisplus.annotation.TableField(
			updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
	private Long parseSkillPackageId;

	@com.baomidou.mybatisplus.annotation.TableField(
			updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
	private Long chatModelId;

	private Boolean emailEnabled;

	@com.baomidou.mybatisplus.annotation.TableField(
			updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
	private String emailBizCode;

	@com.baomidou.mybatisplus.annotation.TableField(
			updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.ALWAYS)
	private String webBaseUrl;

	private Integer dueSoonHours;

	private Integer acceptanceStaleHours;

	private Integer nudgeIntervalMinutes;

	private Integer nudgeDailyLimit;

}
