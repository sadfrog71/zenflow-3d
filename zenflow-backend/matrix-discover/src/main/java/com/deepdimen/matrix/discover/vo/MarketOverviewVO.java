package com.deepdimen.matrix.discover.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 市场统计概览VO
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "市场统计概览")
public class MarketOverviewVO {

	@Schema(description = "智能体总数(本地已发布+第三方active)")
	private Long totalCount;

	@Schema(description = "本地智能体数(已发布)")
	private Long localCount;

	@Schema(description = "第三方智能体数(active)")
	private Long thirdPartyCount;

	@Schema(description = "当前用户可用智能体数")
	private Long availableCount;

	@Schema(description = "今日调用次数(智能体相关对话)")
	private Long todayCallCount;

	@Schema(description = "较昨日增长率(百分比，如12.5表示12.5%)")
	private BigDecimal callGrowthRate;

	@Schema(description = "当前用户收藏数")
	private Long favoriteCount;

}
