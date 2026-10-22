package com.deepdimen.matrix.discover.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户技能偏好表（物理删除，无 del_flag）
 *
 * @author matrix
 * @date 2026-04-27
 */
@Data
@TableName("ai_user_skill_pref")
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户技能偏好表")
@TenantTable
public class AiUserSkillPrefEntity extends Model<AiUserSkillPrefEntity> {

	@TableId(type = IdType.ASSIGN_ID)
	@Schema(description = "主键ID")
	private Long id;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "租户ID")
	private String tenantId;

	@Schema(description = "用户ID")
	private Long userId;

	@Schema(description = "智能体ID")
	private Long agentId;

	@Schema(description = "技能ID")
	private Long skillId;

	@Schema(description = "用户偏好开关: 1开启 0关闭")
	private Integer isEnabled;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

}
