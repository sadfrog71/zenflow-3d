package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 按公司统计数据
 *
 * @author matrix
 */
@Data
@Schema(description = "按公司统计")
public class CompanyStatsVO {

	/**
	 * 公司统计列表
	 */
	@Schema(description = "公司统计列表")
	private List<CompanyStatsItem> items;

	/**
	 * 公司统计项
	 */
	@Data
	@Schema(description = "公司统计项")
	public static class CompanyStatsItem {

		/**
		 * 租户ID
		 */
		@Schema(description = "租户ID")
		private Long tenantId;

		/**
		 * 公司名称
		 */
		@Schema(description = "公司名称")
		private String companyName;

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
