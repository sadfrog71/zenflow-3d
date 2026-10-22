package com.deepdimen.matrix.knowledge.dto;

import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.vo.AiDatasetPermissionVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * AI知识库实体数据传输对象
 * <p>
 * 继承自AiDatasetEntity，扩展了可见用户列表功能
 *
 * @author caoyuan
 * @date 2025/06/06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiDatasetEntityDTO extends AiDatasetEntity {

	/**
	 * 可见用户列表 包含有权限访问该知识库的用户信息（旧字段，兼容保留）
	 */
	private List<VisibleUser> visibleUsers = new ArrayList<>();

	/**
	 * 权限配置（新字段，对应 ai_dataset_permission 表）
	 */
	private AiDatasetPermissionVO permissionConfig;

	/**
	 * 可见用户信息类
	 * <p>
	 * 定义了有权限访问知识库的用户或组织的基本信息
	 *
	 * @author caoyuan
	 * @date 2025/06/06
	 */
	@Data
	public static class VisibleUser {

		/**
		 * 用户类型 如：user（个人用户）、dept（部门）、role（角色）
		 */
		private String type;

		/**
		 * 用户/部门/角色的唯一标识
		 */
		private Long id;

		/**
		 * 用户/部门/角色的名称
		 */
		private String name;

		/**
		 * 用户/部门/角色的头像URL
		 */
		private String avatar;

	}

}
