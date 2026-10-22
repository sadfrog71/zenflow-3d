package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户统计查询参数
 *
 * @author matrix
 */
@Data
@Schema(description = "用户统计查询参数")
public class UserStatsQueryDTO {

	/**
	 * 开始日期，格式：yyyy-MM-dd
	 */
	@Schema(description = "开始日期", example = "2025-01-01")
	private String startDate;

	/**
	 * 结束日期，格式：yyyy-MM-dd
	 */
	@Schema(description = "结束日期", example = "2025-01-31")
	private String endDate;

	/**
	 * 统计天数，用于趋势接口，支持7/30/365，默认30
	 */
	@Schema(description = "统计天数，支持7/30/365", example = "30")
	private Integer days = 30;

}
