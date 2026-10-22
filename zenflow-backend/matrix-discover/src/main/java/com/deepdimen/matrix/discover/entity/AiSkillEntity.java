package com.deepdimen.matrix.discover.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 技能目录表（不加 @TenantTable，内置技能 tenant_id=0 跨租户共享）
 *
 * @author matrix
 * @date 2026-04-25
 */
@Data
@TableName("ai_skill")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "技能目录表")
public class AiSkillEntity extends Model<AiSkillEntity> {

	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	@Schema(description = "租户ID，0=系统内置技能")
	private String tenantId;

	/** LEGACY keeps the old projection; MANAGED is controlled by release bindings. */
	private String managementMode;

	@Schema(description = "技能标识")
	private String skillCode;

	@Schema(description = "技能名称")
	private String skillName;

	@Schema(description = "技能描述")
	private String description;

	@Schema(description = "图标URL或emoji")
	private String icon;

	@Schema(description = "技能类型: builtin/custom")
	private String skillType;

	@Schema(description = "实现类型: prompt_injection/dify_plugin/matrix_plugin")
	private String implType;

	@Schema(description = "技能配置JSON")
	private String config;

	@Schema(description = "是否可用")
	private Integer isEnabled;

	@Schema(description = "排序权重")
	private Integer sort;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标志")
	private String delFlag;

}
