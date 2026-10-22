package com.deepdimen.matrix.discover.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 智能体管理后台VO
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "智能体管理后台视图")
public class AiAgentVO {

	@Schema(description = "主键ID")
	private Long id;

	@Schema(description = "智能体名称")
	private String name;

	@Schema(description = "描述")
	private String description;

	@Schema(description = "智能体标签")
	private List<String> tags;

	@Schema(description = "使用场景JSON数组")
	private String useScenes;

	@Schema(description = "操作说明富文本HTML")
	private String operationGuide;

	@Schema(description = "头像URL")
	private String avatar;

	@Schema(description = "类型: knowledge/comprehensive/dify/external")
	private String agentType;

	@Schema(description = "分类: operation/production/equipment/emergency/general")
	private String category;

	@Schema(description = "状态: draft/published/disabled")
	private String status;

	@Schema(description = "是否在市场展示")
	private Integer marketVisible;

	@Schema(description = "使用次数")
	private Integer useCount;

	@Schema(description = "满意度(0.000-1.000)")
	private BigDecimal satisfaction;

	@Schema(description = "收藏数量")
	private Integer collectCount;

	@Schema(description = "智能体配置JSON")
	private String config;

	@Schema(description = "市场展示配置JSON")
	private String marketConfig;

	@Schema(description = "排序权重")
	private Integer sort;

	@Schema(description = "最大并发数")
	private Integer maxConcurrency;

	@Schema(description = "每分钟限流数")
	private Integer rateLimitPerMin;

	@Schema(description = "创建人")
	private String createBy;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@Schema(description = "Dify应用ID")
	private String difyAppId;

	@Schema(description = "Dify应用模式: agent-chat/advanced-chat/chat/workflow/completion")
	private String difyMode;

	@Schema(description = "权限: public/restricted")
	private String permission;

	@Schema(description = "当前用户是否有权限使用")
	private Boolean accessible;

	@Schema(description = "来源: local/external")
	private String source;

	@Schema(description = "外部智能体接入端点URL（仅 source=external，从 config JSON 反序列化）")
	private String endpointUrl;

	@Schema(description = "外部智能体静态应用密钥 token（仅 source=external，选填，输出已脱敏为 ****）")
	private String authValue;

	@Schema(description = "Dify应用信息，同名字段(name/description/icon)已覆盖到VO对应属性，其余字段保留原始key")
	private Map<String, Object> difyInfo;

}
