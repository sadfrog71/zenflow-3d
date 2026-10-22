package com.deepdimen.matrix.discover.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.discover.dto.AiAgentPermissionDTO;
import com.deepdimen.matrix.discover.entity.AiAgentPermissionEntity;
import com.deepdimen.matrix.discover.vo.AiAgentPermissionVO;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 智能体权限服务接口
 *
 * @author matrix
 * @date 2025-03-11
 */
public interface AiAgentPermissionService extends IService<AiAgentPermissionEntity> {

	/**
	 * 查询智能体权限配置
	 * @param agentId 智能体ID
	 * @return 权限配置VO
	 */
	AiAgentPermissionVO getPermission(Long agentId);

	/**
	 * 设置智能体权限（先删后插），支持 all 和 restricted(role+user并集) 两种模式
	 * @param agentId 智能体ID
	 * @param dto 权限设置请求
	 */
	void setPermission(Long agentId, AiAgentPermissionDTO dto);

	/**
	 * 创建默认权限（permission_type=all），在智能体创建时调用
	 * @param agentId 智能体ID
	 */
	void createDefaultPermission(Long agentId);

	/** Reuses the Agent ACL for non-UI runtime callers such as unified Assistant. */
	default boolean isAccessible(Long agentId, IdentityEnvelope identity) {
		if (agentId == null || identity == null || identity.userId() == null) {
			return false;
		}
		AiAgentPermissionVO permission = getPermission(agentId);
		if (permission == null || "all".equals(permission.getPermissionType())) {
			return true;
		}
		if (!"restricted".equals(permission.getPermissionType())) {
			return false;
		}
		if (contains(permission.getUserIds(), identity.userId())) {
			return true;
		}
		return intersects(permission.getRoleIds(), identity.roleIds())
				|| intersects(permission.getDeptIds(), identity.deptIds());
	}

	private static boolean contains(List<Long> ids, String value) {
		if (ids == null || value == null) {
			return false;
		}
		return ids.stream().map(String::valueOf).anyMatch(value::equals);
	}

	private static boolean intersects(List<Long> ids, List<String> values) {
		if (ids == null || ids.isEmpty() || values == null || values.isEmpty()) {
			return false;
		}
		Set<String> expected = new HashSet<>(ids.stream().map(String::valueOf).toList());
		return values.stream().anyMatch(expected::contains);
	}

}
