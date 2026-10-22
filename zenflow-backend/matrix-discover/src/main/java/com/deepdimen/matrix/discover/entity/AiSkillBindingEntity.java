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
@TableName("ai_skill_binding")
public class AiSkillBindingEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long activeRevisionId;

	private Long revision;

	private String tenantId;

	private String consumerType;

	private String consumerId;

	private String slotCode;

	private String bindingKey;

	private String createdBy;

	private String updatedBy;

	private LocalDateTime createdAt;

	private LocalDateTime updatedAt;

}
