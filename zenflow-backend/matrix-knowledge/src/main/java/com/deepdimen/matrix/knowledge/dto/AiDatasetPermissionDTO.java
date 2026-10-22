package com.deepdimen.matrix.knowledge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 知识库权限设置请求DTO
 *
 * @author matrix
 * @date 2025-03-12
 */
@Data
@Schema(description = "知识库权限设置请求")
public class AiDatasetPermissionDTO {

	@NotBlank(message = "权限类型不能为空")
	@Pattern(regexp = "all|role|user", message = "权限类型只能为 all/role/user")
	@Schema(description = "权限类型: all/role/user")
	private String permissionType;

	/**
	 * permission_type=role 时填角色ID列表，permission_type=user 时填用户ID列表，permission_type=all 时为空
	 */
	@Schema(description = "目标ID列表（角色ID或用户ID），permission_type=all时为空")
	private List<Long> targetIds;

}
