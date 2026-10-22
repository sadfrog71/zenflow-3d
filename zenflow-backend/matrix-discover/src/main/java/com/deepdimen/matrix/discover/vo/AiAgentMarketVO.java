package com.deepdimen.matrix.discover.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体市场展示VO（含收藏状态和使用次数，favorited 由 Service 层聚合填充）
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "智能体市场展示视图")
public class AiAgentMarketVO {

	@Schema(description = "主键ID")
	private Long id;

	@Schema(description = "智能体名称")
	private String name;

	@Schema(description = "描述")
	private String description;

	@Schema(description = "智能体标签")
	private List<String> tags;

	@Schema(description = "头像URL")
	private String avatar;

	@Schema(description = "类型: knowledge/comprehensive/dify/external")
	private String agentType;

	@Schema(description = "Dify应用模式: agent-chat/advanced-chat/chat/workflow/completion")
	private String difyMode;

	@Schema(description = "分类: operation/production/equipment/emergency/general")
	private String category;

	@Schema(description = "状态: draft/published/disabled")
	private String status;

	@Schema(description = "市场展示配置JSON")
	private String marketConfig;

	@Schema(description = "使用次数")
	private Integer useCount;

	@Schema(description = "满意度(0.000-1.000)")
	private BigDecimal satisfaction;

	@Schema(description = "排序权重")
	private Integer sort;

	@Schema(description = "是否已收藏（由Service层聚合填充）")
	private Boolean favorited;

	@Schema(description = "收藏数量")
	private Integer collectCount;

	@Schema(description = "来源: local/external")
	private String source;

	@Schema(description = "权限: public/restricted")
	private String permission;

	@Schema(description = "当前用户是否有权限使用")
	private Boolean accessible;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
