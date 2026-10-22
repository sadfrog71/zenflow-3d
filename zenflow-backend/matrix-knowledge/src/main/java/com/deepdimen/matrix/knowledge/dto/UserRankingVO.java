package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 用户活跃度排行榜数据传输对象
 * <p>
 * 展示Top N用户按对话次数或Token消耗排序的统计数据
 *
 * @author matrix
 * @date 2025-01-17
 */
@Data
@Schema(description = "用户活跃度排行榜")
public class UserRankingVO {

	/**
	 * 用户排名列表
	 */
	@Schema(description = "用户排名列表")
	private List<UserRankingItem> users;

	/**
	 * 用户排名项
	 */
	@Data
	@Schema(description = "用户排名项")
	public static class UserRankingItem {

		/**
		 * 用户名
		 */
		@Schema(description = "用户名")
		private String username;

		/**
		 * 对话次数
		 */
		@Schema(description = "对话次数")
		private Long chatCount;

		/**
		 * 总Token消耗
		 */
		@Schema(description = "总Token消耗")
		private Long totalTokens;

		/**
		 * 排名
		 */
		@Schema(description = "排名")
		private Integer rank;

	}

}
