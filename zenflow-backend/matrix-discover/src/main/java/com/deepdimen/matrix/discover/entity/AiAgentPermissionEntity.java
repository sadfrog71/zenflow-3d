package com.deepdimen.matrix.discover.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 智能体权限表
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@TableName("ai_agent_permission")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "智能体权限表")
@TenantTable
public class AiAgentPermissionEntity extends Model<AiAgentPermissionEntity> {

	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	@Schema(description = "租户ID")
	private String tenantId;

	@Schema(description = "智能体ID")
	private Long agentId;

	/**
	 * 权限类型: all-所有人可见, role-指定角色, user-指定用户 (DTO/VO层 restricted 对应 role+user 并集)
	 */
	@Schema(description = "权限类型: all-所有人可见, role-指定角色, user-指定用户")
	private String permissionType;

	/**
	 * 角色ID或用户ID，permission_type=all时为NULL
	 */
	@Schema(description = "角色ID或用户ID，permission_type=all时为NULL")
	private Long targetId;

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

}
