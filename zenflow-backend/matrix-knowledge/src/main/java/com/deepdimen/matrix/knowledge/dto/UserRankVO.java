package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 人员使用排行数据
 *
 * @author matrix
 * @date 2026-03-12
 */
@Data
@Schema(description = "人员使用排行")
public class UserRankVO {

	/**
	 * 排行列表
	 */
	@Schema(description = "排行列表")
	private List<UserRankItem> items;

	@Schema(description = "总记录数")
	private Long total;

	@Schema(description = "当前页码")
	private Integer pageNum;

	@Schema(description = "每页数量")
	private Integer pageSize;

	/**
	 * 人员排行项
	 */
	@Data
	@Schema(description = "人员排行项")
	public static class UserRankItem {

		/**
		 * 用户名
		 */
		@Schema(description = "用户名")
		private String username;

		/**
		 * 真实姓名
		 */
		@Schema(description = "真实姓名")
		private String realName;

		/**
		 * 部门名称
		 */
		@Schema(description = "部门名称")
		private String deptName;

		/**
		 * 调用次数
		 */
		@Schema(description = "调用次数")
		private Long chatCount;

		/**
		 * Token消耗量
		 */
		@Schema(description = "Token消耗量")
		private Long totalTokens;

		/**
		 * 排名
		 */
		@Schema(description = "排名")
		private Integer rank;

	}

}
