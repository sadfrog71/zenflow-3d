package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 智能体费用明细数据
 *
 * @author matrix
 */
@Data
@Schema(description = "智能体费用明细")
public class AgentCostVO {

	/**
	 * 费用明细列表
	 */
	@Schema(description = "费用明细列表")
	private List<AgentCostItem> items;

	/**
	 * 费用明细项
	 */
	@Data
	@Schema(description = "费用明细项")
	public static class AgentCostItem {

		/**
		 * 智能体ID
		 */
		@Schema(description = "智能体ID")
		private Long agentId;

		/**
		 * 智能体名称
		 */
		@Schema(description = "智能体名称")
		private String agentName;

		/**
		 * 模型名称
		 */
		@Schema(description = "模型名称")
		private String modelName;

		/**
		 * 总Token消耗量
		 */
		@Schema(description = "总Token消耗量")
		private Long totalTokens;

		/**
		 * 对话次数
		 */
		@Schema(description = "对话次数")
		private Long chatCount;

	}

}
