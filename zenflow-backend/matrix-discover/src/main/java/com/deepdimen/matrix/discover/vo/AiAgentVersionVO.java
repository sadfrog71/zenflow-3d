package com.deepdimen.matrix.discover.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 智能体版本VO
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "智能体版本视图")
public class AiAgentVersionVO {

	@Schema(description = "主键ID")
	private Long id;

	@Schema(description = "智能体ID")
	private Long agentId;

	@Schema(description = "版本号，格式: v1/v2/v3...")
	private String version;

	@Schema(description = "版本配置快照JSON")
	private String config;

	@Schema(description = "状态: active/inactive")
	private String status;

	@Schema(description = "灰度配置JSON")
	private String grayConfig;

	@Schema(description = "版本说明")
	private String remark;

	@Schema(description = "创建人")
	private String createBy;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
