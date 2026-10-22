package com.deepdimen.matrix.work.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.entity.*;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.admin.mapper.*;
import com.deepdimen.matrix.admin.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Current database-backed permission check shared by legacy and managed dispatch. */
@Component
@RequiredArgsConstructor
public class TaskDispatchCreatorAuthorization {

	private final TenantUserDirectory directory;

	private final SysUserRoleMapper userRoles;

	private final SysMenuService menus;

	private final SysRoleMapper roles;

	public boolean authorized(Long tenant, Long creator) {
		return authorized(tenant, creator, directory, userRoles, menus, roles);
	}

	static boolean authorized(Long tenant, Long creator, TenantUserDirectory directory, SysUserRoleMapper userRoles,
			SysMenuService menus, SysRoleMapper roles) {
		if (tenant == null || tenant <= 0 || creator == null || creator <= 0
				|| !directory.isAssignableUser(tenant, creator))
			return false;
		return userRoles
			.selectList(Wrappers.<SysUserRole>lambdaQuery()
				.eq(SysUserRole::getTenantId, tenant)
				.eq(SysUserRole::getUserId, creator))
			.stream()
			.filter(role -> roles
				.selectCount(Wrappers.<SysRole>query().eq("tenant_id", tenant).eq("role_id", role.getRoleId())) > 0)
			.flatMap(role -> menus.findMenuByRoleId(role.getRoleId()).stream())
			.anyMatch(menu -> "work_task_create".equals(menu.getPermission()));
	}

}
