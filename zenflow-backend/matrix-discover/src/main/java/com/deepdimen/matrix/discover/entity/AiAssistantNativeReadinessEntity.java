package com.deepdimen.matrix.discover.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_assistant_native_readiness")
public class AiAssistantNativeReadinessEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long tenantId;

	private Long nativeProfileId;

	private String decisionStatus;

	private String reportSha256;

	private String profileConfigSha256;

	private String metricsJson;

	private String businessOwner;

	private String decidedBy;

	private String reason;

	private String correlationId;

	private LocalDateTime decidedAt;

	private LocalDateTime expiresAt;

}
