package com.deepdimen.matrix.discover.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户常用智能体VO
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "用户常用智能体")
public class AiUserTopAgentVO {

	@Schema(description = "智能体ID")
	private Long agentId;

	@Schema(description = "智能体名称")
	private String name;

	@Schema(description = "头像URL")
	private String avatar;

	@Schema(description = "使用次数(按会话维度去重)")
	private Integer useCount;

}
