package com.deepdimen.matrix.work.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TenantTable
@TableName("ai_information_policy")
public class InformationPolicy {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	@com.fasterxml.jackson.annotation.JsonProperty(
			access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
	@com.baomidou.mybatisplus.annotation.TableField(
			insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
			updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
	private String skillManagementMode = "LEGACY";

	@com.fasterxml.jackson.annotation.JsonProperty(
			access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
	@com.baomidou.mybatisplus.annotation.TableField(
			insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
			updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
	private Integer skillPolicyVersion = 0;

	private Long orgId;

	private String name;

	private String provider;

	private String queryText;

	private String topic;

	private String timeRange;

	private String includeDomains;

	private String excludeDomains;

	private String modelName;

	private Integer dailyLimit;

	private Integer enabled;

	private String cronExpression;

	private String createBy;

	private LocalDateTime createTime;

	private String updateBy;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
