package com.deepdimen.matrix.discover.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 智能体权限设置请求DTO
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "智能体权限设置请求")
public class AiAgentPermissionDTO {

	@NotBlank(message = "权限类型不能为空")
	@Pattern(regexp = "all|restricted", message = "权限类型只能为 all/restricted")
	@Schema(description = "权限类型: all-所有人可见, restricted-指定范围")
	private String permissionType;

	@Schema(description = "授权角色ID列表, permissionType=restricted 时有效")
	private List<Long> roleIds;

	@Schema(description = "授权用户ID列表, permissionType=restricted 时有效")
	private List<Long> userIds;

	@Schema(description = "授权部门ID列表, permissionType=restricted 时有效")
	private List<Long> deptIds;

}
