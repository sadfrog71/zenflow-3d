package com.deepdimen.matrix.knowledge.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 知识库权限配置VO
 *
 * @author matrix
 * @date 2025-03-12
 */
@Data
@Schema(description = "知识库权限配置视图")
public class AiDatasetPermissionVO {

	@Schema(description = "权限类型: all/role/user")
	private String permissionType;

	@Schema(description = "目标ID列表（角色ID或用户ID），permission_type=all时为空")
	private List<Long> targetIds;

}
