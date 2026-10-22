package com.deepdimen.matrix.discover.entity;

import com.deepdimen.matrix.common.core.util.TenantTable;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_market_skill_request")
public class MarketSkillRequestEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long tenantId;

	private Long actorId;

	private Long agentId;

	private String bindingKey;

	private String clientRequestId;

	private String inputDigest;

	private String inputSnapshot;

	private String observedConversationId;

	private Long skillRunId;

	private String skillOutputJson;

}
