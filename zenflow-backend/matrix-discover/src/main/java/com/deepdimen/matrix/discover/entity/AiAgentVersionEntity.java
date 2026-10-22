package com.deepdimen.matrix.discover.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 智能体版本表
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@TableName("ai_agent_version")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "智能体版本表")
@TenantTable
public class AiAgentVersionEntity extends Model<AiAgentVersionEntity> {

	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	@Schema(description = "租户ID")
	private String tenantId;

	@Schema(description = "智能体ID")
	private Long agentId;

	/**
	 * 版本号，格式: v1/v2/v3...
	 */
	@Schema(description = "版本号，格式: v1/v2/v3...")
	private String version;

	@Schema(description = "版本配置快照JSON")
	private String config;

	/**
	 * 状态: active/inactive，每个智能体只有一个active版本
	 */
	@Schema(description = "状态: active/inactive")
	private String status;

	/**
	 * 灰度配置JSON，格式: {"percent":10,"userIds":[]}
	 */
	@Schema(description = "灰度配置JSON，格式: {\"percent\":10,\"userIds\":[]}")
	private String grayConfig;

	@Schema(description = "版本说明")
	private String remark;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "更新人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标志")
	private String delFlag;

}
