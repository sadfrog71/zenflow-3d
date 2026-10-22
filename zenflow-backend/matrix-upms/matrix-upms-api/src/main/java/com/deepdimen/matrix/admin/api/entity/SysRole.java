/*
 *
 *      Copyright (c) 2018-2025, deepdimen All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in the
 *  documentation and/or other materials provided with the distribution.
 *  Neither the name of the deepdimen.com developer nor the names of its
 *  contributors may be used to endorse or promote products derived from
 *  this software without specific prior written permission.
 *  Author: caoyuan (caoyuanchn@gmail.com)
 *
 */

package com.deepdimen.matrix.admin.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.deepdimen.matrix.common.core.util.TenantTable;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * <p>
 * 角色表
 * </p>
 *
 * @author caoyuan
 * @since 2017-10-29
 */
@Data
@TenantTable
@Schema(description = "角色")
@EqualsAndHashCode(callSuper = true)
public class SysRole extends Model<SysRole> {

	private static final long serialVersionUID = 1L;

	@TableId(value = "role_id", type = IdType.ASSIGN_ID)
	@Schema(description = "角色编号")
	private Long roleId;

	@NotBlank(message = "角色名称不能为空")
	@Schema(description = "角色名称")
	private String roleName;

	@NotBlank(message = "角色标识不能为空")
	@Schema(description = "角色标识")
	private String roleCode;

	@Schema(description = "角色描述")
	private String roleDesc;

	@NotNull(message = "数据权限类型不能为空")
	@Schema(description = "数据权限类型")
	private Integer dsType;

	/**
	 * 数据权限作用范围
	 */
	@Schema(description = "数据权限作用范围")
	private String dsScope;

	@Positive(message = "任务职级必须为正整数")
	@TableField(updateStrategy = FieldStrategy.ALWAYS)
	@Schema(description = "任务职级，数值越小职级越高；未配置不能按职级派发")
	private Integer taskLevel;

	@Pattern(regexp = "NONE|EMPLOYEE|DEPARTMENT_MANAGER|GENERAL_MANAGER", message = "任务身份不合法")
	@Schema(description = "任务身份；只有明确的管理身份授予任务管理可见范围")
	private String taskIdentity;

	/**
	 * 创建人
	 */
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	/**
	 * 修改人
	 */
	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	/**
	 * 创建时间
	 */
	@Schema(description = "创建时间")
	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	/**
	 * 修改时间
	 */
	@Schema(description = "修改时间")
	@TableField(fill = FieldFill.UPDATE)
	private LocalDateTime updateTime;

	/**
	 * 删除标识（0-正常,1-删除）
	 */
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "删除标记,1:已删除,0:正常")
	private String delFlag;

}
