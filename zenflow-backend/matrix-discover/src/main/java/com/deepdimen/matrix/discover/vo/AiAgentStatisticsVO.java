package com.deepdimen.matrix.discover.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 智能体概况统计VO
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "智能体概况统计")
public class AiAgentStatisticsVO {

	@Schema(description = "智能体总数(已发布+已停用)")
	private Long totalCount;

	@Schema(description = "已发布数量")
	private Long publishedCount;

	@Schema(description = "已停用数量")
	private Long disabledCount;

	@Schema(description = "平均满意度(like数/总评价数, 0.000-1.000)")
	private BigDecimal avgSatisfaction;

	@Schema(description = "总收藏数")
	private Long totalFavorites;

	@Schema(description = "总使用次数(所有智能体use_count之和)")
	private Long totalUseCount;

}
