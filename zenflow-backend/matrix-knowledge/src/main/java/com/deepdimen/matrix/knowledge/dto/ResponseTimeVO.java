package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 响应时间分布数据传输对象
 * <p>
 * 按模型展示响应时间的统计分布，包含箱线图所需的最小值、四分位数、最大值、异常值
 *
 * @author matrix
 * @date 2025-01-17
 */
@Data
@Schema(description = "响应时间分布")
public class ResponseTimeVO {

	/**
	 * 模型响应时间统计列表
	 */
	@Schema(description = "模型响应时间统计列表")
	private List<ModelResponseTime> models;

	/**
	 * 模型响应时间统计
	 */
	@Data
	@Schema(description = "模型响应时间统计")
	public static class ModelResponseTime {

		/**
		 * 模型名称
		 */
		@Schema(description = "模型名称", example = "GPT-4")
		private String model;

		/**
		 * 最小值（毫秒）
		 */
		@Schema(description = "最小值", example = "800")
		private Long min;

		/**
		 * 第一四分位数（Q1）（毫秒）
		 */
		@Schema(description = "第一四分位数", example = "1200")
		private Long q1;

		/**
		 * 中位数（Q2）（毫秒）
		 */
		@Schema(description = "中位数", example = "1500")
		private Long median;

		/**
		 * 第三四分位数（Q3）（毫秒）
		 */
		@Schema(description = "第三四分位数", example = "1800")
		private Long q3;

		/**
		 * 最大值（毫秒）
		 */
		@Schema(description = "最大值", example = "2500")
		private Long max;

		/**
		 * 异常值列表（毫秒）
		 */
		@Schema(description = "异常值列表")
		private List<Long> outliers;

	}

}
