package com.deepdimen.matrix.admin.api.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体分享校验结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentShareValidationVO {

	private Long agentId;

	private String tenantId;

	private String status;

	private boolean available;

}
