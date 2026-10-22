package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * LLM调用状态分布数据传输对象
 * <p>
 * 基于LLMUseStatusEnums展示成功、失败、敏感词、受限等各种状态的统计信息
 *
 * @author matrix
 * @date 2025-01-17
 */
@Data
@Schema(description = "LLM调用状态分布")
public class LLMStatusVO {

	/**
	 * 状态分布列表
	 */
	@Schema(description = "状态分布列表")
	private List<LLMStatusItem> statuses;

	/**
	 * 成功率（百分比）
	 */
	@Schema(description = "成功率", example = "95.4")
	private Double successRate;

	/**
	 * LLM状态项
	 */
	@Data
	@Schema(description = "LLM状态项")
	public static class LLMStatusItem {

		/**
		 * 状态码（0-未使用、1-已使用、2-敏感词、3-用量受限、9-失败）
		 */
		@Schema(description = "状态码", example = "1")
		private String status;

		/**
		 * 状态名称
		 */
		@Schema(description = "状态名称", example = "已使用")
		private String statusName;

		/**
		 * 数量
		 */
		@Schema(description = "数量")
		private Long count;

		/**
		 * 占比（百分比）
		 */
		@Schema(description = "占比", example = "95.4")
		private Double percentage;

	}

}
