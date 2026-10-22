package com.deepdimen.matrix.discover.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体主表
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@TableName(value = "ai_agent", autoResultMap = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "智能体主表")
@TenantTable
public class AiAgentEntity extends Model<AiAgentEntity> {

	/**
	 * 主键ID
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	/**
	 * 租户ID
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "租户ID")
	private String tenantId;

	/**
	 * 智能体名称
	 */
	@Schema(description = "智能体名称")
	private String name;

	/**
	 * 描述
	 */
	@Schema(description = "描述")
	private String description;

	/**
	 * 智能体标签
	 */
	@TableField(typeHandler = JacksonTypeHandler.class)
	@Schema(description = "智能体标签")
	private List<String> tags;

	/**
	 * 使用场景JSON数组
	 */
	@Schema(description = "使用场景JSON数组")
	private String useScenes;

	/**
	 * 操作说明富文本HTML
	 */
	@Schema(description = "操作说明富文本HTML")
	private String operationGuide;

	/**
	 * 头像URL
	 */
	@Schema(description = "头像URL")
	private String avatar;

	/**
	 * 类型: knowledge/comprehensive/dify
	 */
	@Schema(description = "类型: knowledge/comprehensive/dify")
	private String agentType;

	/**
	 * 分类: operation/production/equipment/emergency/general
	 */
	@Schema(description = "分类: operation/production/equipment/emergency/general")
	private String category;

	/**
	 * 状态: draft/published/disabled
	 */
	@Schema(description = "状态: draft/published/disabled")
	private String status;

	/**
	 * 智能体配置JSON
	 */
	@Schema(description = "智能体配置JSON")
	private String config;

	/**
	 * 是否在市场展示
	 */
	@Schema(description = "是否在市场展示")
	private Integer marketVisible;

	/**
	 * 市场展示配置JSON
	 */
	@Schema(description = "市场展示配置JSON")
	private String marketConfig;

	/**
	 * 使用次数
	 */
	@Schema(description = "使用次数")
	private Integer useCount;

	/**
	 * 满意度(0.000-1.000)
	 */
	@Schema(description = "满意度(0.000-1.000)")
	private BigDecimal satisfaction;

	/**
	 * 排序权重
	 */
	@Schema(description = "排序权重")
	private Integer sort;

	/**
	 * 最大并发数
	 */
	@Schema(description = "最大并发数")
	private Integer maxConcurrency;

	/**
	 * 每分钟限流数
	 */
	@Schema(description = "每分钟限流数")
	private Integer rateLimitPerMin;

	/**
	 * Dify Service API Key
	 */
	@Schema(description = "Dify Service API Key")
	private String difyApiKey;

	/**
	 * Dify 应用 ID
	 */
	@Schema(description = "Dify应用ID")
	private String difyAppId;

	/**
	 * Dify应用模式: agent-chat/advanced-chat/chat/workflow/completion
	 */
	@Schema(description = "Dify应用模式: agent-chat/advanced-chat/chat/workflow/completion")
	private String difyMode;

	/**
	 * 权限: public/restricted
	 */
	@Schema(description = "权限: public/restricted")
	private String permission;

	/**
	 * 来源: local/external
	 */
	@Schema(description = "来源: local/external")
	private String source;

	/**
	 * 创建人
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	/**
	 * 创建时间
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	/**
	 * 修改人
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	/**
	 * 修改时间
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	/**
	 * 删除标志
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标志")
	private String delFlag;

}
