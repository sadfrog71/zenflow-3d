package com.deepdimen.matrix.knowledge.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 知识库权限表
 *
 * @author matrix
 * @date 2025-03-12
 */
@Data
@TableName("ai_dataset_permission")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识库权限表")
@TenantTable
public class AiDatasetPermissionEntity extends Model<AiDatasetPermissionEntity> {

	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	@Schema(description = "租户ID")
	private String tenantId;

	@Schema(description = "知识库ID")
	private Long datasetId;

	/**
	 * 权限类型: all/role/user
	 */
	@Schema(description = "权限类型: all/role/user")
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
