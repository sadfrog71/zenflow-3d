package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 网络搜索使用情况数据传输对象
 * <p>
 * 统计使用了网络搜索功能的对话数量及使用率
 *
 * @author matrix
 * @date 2025-01-17
 */
@Data
@Schema(description = "网络搜索使用情况")
public class WebSearchUsageVO {

	/**
	 * 使用了网络搜索的对话数
	 */
	@Schema(description = "使用了网络搜索的对话数")
	private Long withSearch;

	/**
	 * 总对话数
	 */
	@Schema(description = "总对话数")
	private Long total;

	/**
	 * 使用率（百分比）
	 */
	@Schema(description = "使用率", example = "29.7")
	private Double usageRate;

}
