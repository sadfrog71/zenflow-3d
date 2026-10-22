package com.deepdimen.matrix.discover.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 智能体排行VO
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "智能体排行")
public class AiAgentRankVO {

	@Schema(description = "智能体ID")
	private Long id;

	@Schema(description = "智能体名称")
	private String name;

	@Schema(description = "头像URL")
	private String avatar;

	@Schema(description = "分类")
	private String category;

	@Schema(description = "使用次数")
	private Integer useCount;

	@Schema(description = "满意度")
	private BigDecimal satisfaction;

}
