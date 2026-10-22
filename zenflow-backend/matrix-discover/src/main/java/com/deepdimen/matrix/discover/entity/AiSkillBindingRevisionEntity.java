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
@TableName("ai_skill_binding_revision")
public class AiSkillBindingRevisionEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long bindingId;

	private Long releaseId;

	private Long revision;

	private Integer enabled;

	private String tenantId;

	private String audienceJson;

	private String activationMode;

	private String parameterJson;

	private String fallbackPolicy;

	private String createdBy;

	private LocalDateTime createdAt;

}
