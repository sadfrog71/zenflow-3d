package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Dashboard核心指标概览数据传输对象
 * <p>
 * 包含总对话数、总Token消耗量、活跃用户数、平均响应时间等核心指标及其增长率
 *
 * @author matrix
 * @date 2025-01-17
 */
@Data
@Schema(description = "Dashboard核心指标概览")
public class DashboardOverviewVO {

	/**
	 * 总对话次数
	 */
	@Schema(description = "总对话次数")
	private Long totalChats;

	/**
	 * 总Token消耗量
	 */
	@Schema(description = "总Token消耗量")
	private Long totalTokens;

	/**
	 * 活跃用户数
	 */
	@Schema(description = "活跃用户数")
	private Long activeUsers;

	/**
	 * 平均响应时间（毫秒）
	 */
	@Schema(description = "平均响应时间，单位毫秒")
	private Long avgResponseTime;

	/**
	 * 增长率
	 */
	@Schema(description = "各项指标增长率")
	private GrowthRate growthRate;

	/**
	 * 增长率数据
	 */
	@Data
	@Schema(description = "增长率")
	public static class GrowthRate {

		/**
		 * 对话数增长率（百分比）
		 */
		@Schema(description = "对话数增长率", example = "12.5")
		private Double chats;

		/**
		 * Token增长率（百分比）
		 */
		@Schema(description = "Token增长率", example = "8.3")
		private Double tokens;

		/**
		 * 用户数增长率（百分比）
		 */
		@Schema(description = "用户数增长率", example = "15.7")
		private Double users;

	}

}
