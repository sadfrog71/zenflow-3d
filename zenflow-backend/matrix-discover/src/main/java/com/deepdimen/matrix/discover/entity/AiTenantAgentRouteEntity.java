package com.deepdimen.matrix.discover.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.deepdimen.matrix.common.core.util.TenantTable;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TenantTable
@TableName("ai_tenant_agent_route")
public class AiTenantAgentRouteEntity {

	@TableId(type = IdType.ASSIGN_ID)
	private Long id;
	private Long orgId;
	private String channelCode;
	private Long agentId;
	private String releaseVersion;
	private Integer enabled;
	private String createBy;
	private LocalDateTime createTime;
	private String updateBy;
	private LocalDateTime updateTime;
	@TableLogic
	private String delFlag;
	private Long tenantId;
}
