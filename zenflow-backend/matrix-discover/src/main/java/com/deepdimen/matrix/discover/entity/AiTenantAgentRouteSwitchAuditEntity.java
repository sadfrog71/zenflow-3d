package com.deepdimen.matrix.discover.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_tenant_agent_route_switch_audit")
public class AiTenantAgentRouteSwitchAuditEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;

	private Long tenantId;

	private String routeType;

	private Long previousAgentId;

	private Long newAgentId;

	private String operatorId;

	private String reason;

	private String correlationId;

	private LocalDateTime switchedAt;

	private LocalDateTime rolledBackAt;

}
