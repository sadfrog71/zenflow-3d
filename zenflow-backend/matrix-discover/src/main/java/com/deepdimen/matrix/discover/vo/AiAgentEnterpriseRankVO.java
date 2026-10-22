package com.deepdimen.matrix.discover.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 企业排行榜VO
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "企业排行榜")
public class AiAgentEnterpriseRankVO {

	@Schema(description = "租户ID")
	private String tenantId;

	@Schema(description = "企业名称")
	private String tenantName;

	@Schema(description = "智能体数量")
	private Long agentCount;

	@Schema(description = "总使用次数")
	private Long totalUseCount;

	@Schema(description = "平均满意度")
	private BigDecimal avgSatisfaction;

	@Schema(description = "平均正确率")
	private BigDecimal avgAccuracy;

	@Schema(description = "总收藏数")
	private Long totalFavorites;

	@Schema(description = "是否推荐（推荐租户置顶）")
	private Boolean recommended;

}
