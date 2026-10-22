package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * AI模型使用分布数据传输对象
 * <p>
 * 统计各AI模型的调用次数、Token消耗和使用占比
 *
 * @author matrix
 * @date 2025-01-17
 */
@Data
@Schema(description = "AI模型使用分布")
public class ModelDistributionVO {

	/**
	 * 模型分布列表
	 */
	@Schema(description = "模型分布列表")
	private List<ModelDistributionItem> models;

	/**
	 * 模型分布项
	 */
	@Data
	@Schema(description = "模型分布项")
	public static class ModelDistributionItem {

		/**
		 * 模型名称
		 */
		@Schema(description = "模型名称", example = "GPT-4")
		private String model;

		/**
		 * 调用次数
		 */
		@Schema(description = "调用次数")
		private Long count;

		/**
		 * Token消耗量
		 */
		@Schema(description = "Token消耗量")
		private Long tokens;

		/**
		 * 使用占比（百分比）
		 */
		@Schema(description = "使用占比", example = "35.6")
		private Double percentage;

	}

}
