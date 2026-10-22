package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户使用统计数据
 *
 * @author matrix
 */
@Data
@Schema(description = "用户使用统计")
public class UserStatsVO {

	/**
	 * 对话次数
	 */
	@Schema(description = "对话次数")
	private Long chatCount;

	/**
	 * 总Token消耗量
	 */
	@Schema(description = "总Token消耗量")
	private Long totalTokens;

	/**
	 * 输入Token消耗量（prompt_tokens）
	 */
	@Schema(description = "输入Token消耗量")
	private Long promptTokens;

	/**
	 * 输出Token消耗量（completion_tokens）
	 */
	@Schema(description = "输出Token消耗量")
	private Long completionTokens;

	/**
	 * 月度Token配额（-1表示不限制）
	 */
	@Schema(description = "月度Token配额")
	private Long tokenQuota;

	/**
	 * 活跃天数（日期范围内有对话记录的天数）
	 */
	@Schema(description = "活跃天数")
	private Long activeDays;

	/**
	 * 对话次数较上月变化率（百分比）
	 */
	@Schema(description = "对话次数较上月变化率")
	private Double chatCountGrowth;

	/**
	 * Token使用量较上月变化率（百分比）
	 */
	@Schema(description = "Token使用量较上月变化率")
	private Double tokenGrowth;

	/**
	 * 当月活跃率（百分比，活跃天数/当月已过天数*100）
	 */
	@Schema(description = "当月活跃率")
	private Double activeRate;

}
