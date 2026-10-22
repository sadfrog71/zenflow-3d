package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 按模型维度Token用量统计数据
 *
 * @author matrix
 */
@Data
@Schema(description = "按模型维度Token用量统计")
public class ModelStatsVO {

	/**
	 * 模型统计列表
	 */
	@Schema(description = "模型统计列表")
	private List<ModelStatsItem> items;

	/**
	 * 模型统计项
	 */
	@Data
	@Schema(description = "模型统计项")
	public static class ModelStatsItem {

		/**
		 * 模型名称
		 */
		@Schema(description = "模型名称")
		private String modelName;

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
		 * 提示词Token消耗量
		 */
		@Schema(description = "提示词Token消耗量")
		private Long promptTokens;

		/**
		 * 补全Token消耗量
		 */
		@Schema(description = "补全Token消耗量")
		private Long completionTokens;

	}

}
