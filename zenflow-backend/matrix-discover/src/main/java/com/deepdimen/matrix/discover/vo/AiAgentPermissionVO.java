package com.deepdimen.matrix.discover.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 智能体权限配置VO
 *
 * @author matrix
 * @date 2025-03-11
 */
@Data
@Schema(description = "智能体权限配置视图")
public class AiAgentPermissionVO {

	@Schema(description = "权限类型: all-所有人可见, restricted-指定范围")
	private String permissionType;

	@Schema(description = "授权角色ID列表")
	private List<Long> roleIds;

	@Schema(description = "授权用户ID列表")
	private List<Long> userIds;

	@Schema(description = "授权部门ID列表")
	private List<Long> deptIds;

}
