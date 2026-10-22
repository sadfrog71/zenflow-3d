package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * Token配额类型分布数据传输对象
 * <p>
 * 展示系统配额Token和用户配额Token的时间分布趋势
 *
 * @author matrix
 * @date 2025-01-17
 */
@Data
@Schema(description = "Token配额类型分布")
public class TokenTypeVO {

	/**
	 * Token类型分布数据列表
	 */
	@Schema(description = "Token类型分布数据列表")
	private List<TokenTypeItem> data;

	/**
	 * Token类型分布项
	 */
	@Data
	@Schema(description = "Token类型分布项")
	public static class TokenTypeItem {

		/**
		 * 日期
		 */
		@Schema(description = "日期", example = "2025-01-01")
		private String date;

		/**
		 * 系统配额Token（tokenType=0）
		 */
		@Schema(description = "系统配额Token")
		private Long systemTokens;

		/**
		 * 用户配额Token（tokenType=1）
		 */
		@Schema(description = "用户配额Token")
		private Long userTokens;

	}

}
