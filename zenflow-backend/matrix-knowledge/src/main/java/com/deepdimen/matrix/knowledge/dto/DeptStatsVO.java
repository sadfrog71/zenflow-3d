package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 按部门统计数据
 *
 * @author matrix
 */
@Data
@Schema(description = "按部门统计")
public class DeptStatsVO {

	/**
	 * 部门统计列表
	 */
	@Schema(description = "部门统计列表")
	private List<DeptStatsItem> items;

	/**
	 * 部门统计项
	 */
	@Data
	@Schema(description = "部门统计项")
	public static class DeptStatsItem {

		/**
		 * 部门ID
		 */
		@Schema(description = "部门ID")
		private Long deptId;

		/**
		 * 部门名称
		 */
		@Schema(description = "部门名称")
		private String deptName;

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

	}

}
