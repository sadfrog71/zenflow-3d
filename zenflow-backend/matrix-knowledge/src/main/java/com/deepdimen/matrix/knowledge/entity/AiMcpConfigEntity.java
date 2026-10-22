package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import com.deepdimen.matrix.common.sensitive.annotation.Sensitive;
import com.deepdimen.matrix.common.sensitive.core.SensitiveTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * MCP配置表
 *
 * @author matrix
 * @date 2025-03-22 13:36:32
 */
@Data
@TableName("ai_mcp_config")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "MCP配置表")
@TenantTable
public class AiMcpConfigEntity extends Model<AiMcpConfigEntity> {

	/**
	 * 主键ID
	 */
	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long mcpId;

	/**
	 * 服务器名称
	 */
	@Schema(description = "服务器名称")
	private String name;

	/**
	 * 描述
	 */
	@Schema(description = "描述")
	private String description;

	/**
	 * 类型 (SSE: Server-Sent Events, STDIO: Standard Input/Output)
	 */
	@Schema(description = "类型 (SSE: Server-Sent Events, STDIO: Standard Input/Output)")
	private String mcpType;

	/**
	 * 命令
	 */
	@Schema(description = "命令")
	private String command;

	/**
	 * 服务器地址
	 */
	@Sensitive(type = SensitiveTypeEnum.QUERY_PARAM)
	@Schema(description = "SSE服务地址")
	private String sseUrl;

	/**
	 * 参数
	 */
	@Schema(description = "参数")
	private String parameters;

	/**
	 * 环境变量
	 */
	@Schema(description = "环境变量")
	@Sensitive(type = SensitiveTypeEnum.KEY)
	private String environmentVariables;

	/**
	 * 是否启用（1：启用，0：禁用）
	 */
	@Schema(description = "是否启用（1：启用，0：禁用）")
	private String mcpEnabled;

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
	 * 更新人
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "更新人")
	private String updateBy;

	/**
	 * 更新时间
	 */
	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	/**
	 * 删除标记
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标记")
	private String delFlag;

}
