package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 知识库使用统计数据传输对象
 * <p>
 * 统计各知识库的调用次数和平均匹配度
 *
 * @author matrix
 * @date 2025-01-17
 */
@Data
@Schema(description = "知识库使用统计")
public class DatasetUsageVO {

	/**
	 * 知识库统计列表
	 */
	@Schema(description = "知识库统计列表")
	private List<DatasetUsageItem> datasets;

	/**
	 * 知识库统计项
	 */
	@Data
	@Schema(description = "知识库统计项")
	public static class DatasetUsageItem {

		/**
		 * 知识库ID
		 */
		@Schema(description = "知识库ID")
		private Long datasetId;

		/**
		 * 知识库名称
		 */
		@Schema(description = "知识库名称", example = "产品文档库")
		private String datasetName;

		/**
		 * 使用次数
		 */
		@Schema(description = "使用次数")
		private Long usageCount;

		/**
		 * 平均匹配度（0-1之间）
		 */
		@Schema(description = "平均匹配度", example = "0.87")
		private Double avgSuitability;

	}

}
