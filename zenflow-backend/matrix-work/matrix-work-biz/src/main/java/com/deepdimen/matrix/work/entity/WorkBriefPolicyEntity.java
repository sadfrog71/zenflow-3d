package com.deepdimen.matrix.work.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;

import lombok.Data;

@Data
@TenantTable
@TableName("ai_work_brief_policy")
public class WorkBriefPolicyEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long orgId;

	private Long skillId;

	private String skillVersion;

	private String skillManagementMode;

	private String enabledSources;

	private Boolean allowDeterministicFallback;

	private Boolean enabled;

	private String createBy;

	private LocalDateTime createTime;

	private String updateBy;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

	private Long tenantId;

}
