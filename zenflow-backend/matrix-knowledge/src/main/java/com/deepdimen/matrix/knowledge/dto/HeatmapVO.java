package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 时段活跃度热力图数据传输对象
 * <p>
 * 展示24小时x7天的对话分布热力图数据
 *
 * @author matrix
 * @date 2025-01-17
 */
@Data
@Schema(description = "时段活跃度热力图")
public class HeatmapVO {

	/**
	 * 热力图数据列表
	 */
	@Schema(description = "热力图数据列表")
	private List<HeatmapItem> data;

	/**
	 * 热力图数据项
	 */
	@Data
	@Schema(description = "热力图数据项")
	public static class HeatmapItem {

		/**
		 * 星期几（1-周一，7-周日）
		 */
		@Schema(description = "星期几", example = "1")
		private Integer dayOfWeek;

		/**
		 * 小时（0-23）
		 */
		@Schema(description = "小时", example = "14")
		private Integer hour;

		/**
		 * 对话数量
		 */
		@Schema(description = "对话数量")
		private Long count;

	}

}
