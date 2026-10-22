package com.deepdimen.matrix.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.entity.SysRole;
import com.deepdimen.matrix.admin.api.entity.SysUserRole;
import com.deepdimen.matrix.admin.api.entity.SysTenantUser;
import com.deepdimen.matrix.admin.api.entity.SysDept;
import com.deepdimen.matrix.admin.api.entity.SysUser;
import com.deepdimen.matrix.admin.api.entity.SysUserDept;
import com.deepdimen.matrix.admin.api.entity.SysPost;
import com.deepdimen.matrix.admin.api.entity.SysUserPost;
import com.deepdimen.matrix.admin.api.constant.PostCodeEnum;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory.AssignableUser;
import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.admin.mapper.SysRoleMapper;
import com.deepdimen.matrix.admin.mapper.SysUserRoleMapper;
import com.deepdimen.matrix.admin.mapper.SysDeptMapper;
import com.deepdimen.matrix.admin.mapper.SysTenantUserMapper;
import com.deepdimen.matrix.admin.mapper.SysUserDeptMapper;
import com.deepdimen.matrix.admin.mapper.SysUserMapper;
import com.deepdimen.matrix.admin.mapper.SysPostMapper;
import com.deepdimen.matrix.admin.mapper.SysUserPostMapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TenantUserDirectoryImpl implements TenantUserDirectory {

	private final SysUserMapper userMapper;

	private final SysTenantUserMapper tenantUserMapper;

	private final SysUserDeptMapper userDeptMapper;

	private final SysDeptMapper deptMapper;

	private final SysPostMapper postMapper;

	private final SysUserPostMapper userPostMapper;

	private final SysUserRoleMapper userRoleMapper;

	private final SysRoleMapper roleMapper;

	@Override
	public TaskAuthority taskAuthority(Long tenantId, Long userId) {
		if (tenantId == null || userId == null || !isActiveTenantUser(tenantId, userId)) {
			return TenantUserDirectory.super.taskAuthority(tenantId, userId);
		}
		List<Long> roleIds = userRoleMapper
			.selectList(Wrappers.<SysUserRole>query().eq("tenant_id", tenantId).eq("user_id", userId))
			.stream()
			.map(SysUserRole::getRoleId)
			.distinct()
			.toList();
		if (roleIds.isEmpty()) {
			return TenantUserDirectory.super.taskAuthority(tenantId, userId);
		}
		var roles = roleMapper
			.selectList(Wrappers.<SysRole>query().eq("tenant_id", tenantId).eq("del_flag", "0").in("role_id", roleIds));
		var departments = deptMapper
			.selectList(Wrappers.<SysDept>query().eq("tenant_id", tenantId).eq("del_flag", "0").orderByAsc("dept_id"));
		return OrganizationTaskAuthorityResolver.resolve(userId, findDefaultOrganizationId(tenantId, userId), roles,
				departments);
	}

	@Override
	public boolean isAssignableUser(Long tenantId, Long userId) {
		if (tenantId == null || userId == null) {
			return false;
		}
		return isActiveTenantUser(tenantId, userId) && !activeOrganizationIds(tenantId, userId).isEmpty();
	}

	@Override
	public boolean isOrganizationMember(Long tenantId, Long userId, Long organizationId) {
		if (tenantId == null || userId == null || organizationId == null) {
			return false;
		}
		if (!isActiveTenantUser(tenantId, userId)) {
			return false;
		}
		return activeOrganizationIds(tenantId, userId).contains(organizationId);
	}

	@Override
	public List<Long> listAssignableUserIds(Long tenantId, List<Long> userIds) {
		if (tenantId == null || userIds == null || userIds.isEmpty()) {
			return List.of();
		}
		List<Long> requested = userIds.stream().filter(id -> id != null).distinct().toList();
		if (requested.isEmpty()) {
			return List.of();
		}
		Set<Long> tenantMemberIds = tenantUserMapper
			.selectList(Wrappers.<SysTenantUser>lambdaQuery()
				.eq(SysTenantUser::getTenantId, tenantId)
				.in(SysTenantUser::getUserId, requested))
			.stream()
			.map(SysTenantUser::getUserId)
			.collect(java.util.stream.Collectors.toSet());
		if (tenantMemberIds.isEmpty()) {
			return List.of();
		}
		Set<Long> activeUserIds = TenantBroker
			.noneAs(() -> userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
				.in(SysUser::getUserId, tenantMemberIds)
				.eq(SysUser::getDelFlag, "0")
				.eq(SysUser::getLockFlag, "0")))
			.stream()
			.map(SysUser::getUserId)
			.collect(java.util.stream.Collectors.toSet());
		if (activeUserIds.isEmpty()) {
			return List.of();
		}
		List<SysUserDept> memberships = userDeptMapper
			.selectList(Wrappers.<SysUserDept>query().eq("tenant_id", tenantId).in("user_id", activeUserIds));
		Set<Long> departmentIds = memberships.stream()
			.map(SysUserDept::getDeptId)
			.collect(java.util.stream.Collectors.toSet());
		if (departmentIds.isEmpty()) {
			return List.of();
		}
		Set<Long> activeDepartmentIds = deptMapper.selectList(
				Wrappers.<SysDept>query().eq("tenant_id", tenantId).in("dept_id", departmentIds).eq("del_flag", "0"))
			.stream()
			.map(SysDept::getDeptId)
			.collect(java.util.stream.Collectors.toSet());
		Set<Long> assignableIds = memberships.stream()
			.filter(membership -> activeDepartmentIds.contains(membership.getDeptId()))
			.map(SysUserDept::getUserId)
			.collect(java.util.stream.Collectors.toSet());
		return requested.stream().filter(assignableIds::contains).toList();
	}

	@Override
	public Long findDefaultOrganizationId(Long tenantId, Long userId) {
		if (tenantId == null || userId == null) {
			return null;
		}
		return activeOrganizationIds(tenantId, userId).stream().findFirst().orElse(null);
	}

	@Override
	public List<Long> listOrganizationIds(Long tenantId, Long userId) {
		if (tenantId == null || userId == null) {
			return List.of();
		}
		return activeOrganizationIds(tenantId, userId);
	}

	@Override
	public List<Long> listOrganizationUserIds(Long tenantId, Long organizationId) {
		return listOrganizationUsers(tenantId, organizationId).stream().map(AssignableUser::userId).toList();
	}

	@Override
	public List<AssignableUser> listOrganizationUsers(Long tenantId, Long organizationId) {
		if (tenantId == null || organizationId == null) {
			return List.of();
		}
		boolean activeOrganization = deptMapper.selectCount(Wrappers.<SysDept>query()
			.eq("tenant_id", tenantId)
			.eq("dept_id", organizationId)
			.eq("del_flag", "0")) > 0;
		if (!activeOrganization) {
			return List.of();
		}
		List<Long> memberIds = userDeptMapper
			.selectList(Wrappers.<SysUserDept>query().eq("tenant_id", tenantId).eq("dept_id", organizationId))
			.stream()
			.map(SysUserDept::getUserId)
			.distinct()
			.toList();
		if (memberIds.isEmpty()) {
			return List.of();
		}
		List<Long> tenantMemberIds = tenantUserMapper
			.selectList(Wrappers.<SysTenantUser>lambdaQuery()
				.eq(SysTenantUser::getTenantId, tenantId)
				.in(SysTenantUser::getUserId, memberIds))
			.stream()
			.map(SysTenantUser::getUserId)
			.distinct()
			.toList();
		if (tenantMemberIds.isEmpty()) {
			return List.of();
		}
		Set<Long> leaderIds = organizationLeaderIds(tenantId, tenantMemberIds);
		return TenantBroker
			.noneAs(() -> userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
				.in(SysUser::getUserId, tenantMemberIds)
				.eq(SysUser::getDelFlag, "0")
				.eq(SysUser::getLockFlag, "0")
				.orderByAsc(SysUser::getUserId)))
			.stream()
			.map(user -> new AssignableUser(user.getUserId(), displayName(user), user.getUsername(),
					leaderIds.contains(user.getUserId())))
			.toList();
	}

	@Override
	public List<AssignableUser> listTenantUsers(Long tenantId) {
		if (tenantId == null)
			return List.of();
		List<Long> members = tenantUserMapper
			.selectList(Wrappers.<SysTenantUser>lambdaQuery().eq(SysTenantUser::getTenantId, tenantId))
			.stream()
			.map(SysTenantUser::getUserId)
			.distinct()
			.toList();
		List<Long> activeIds = listAssignableUserIds(tenantId, members);
		if (activeIds.isEmpty())
			return List.of();
		return TenantBroker
			.noneAs(() -> userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
				.in(SysUser::getUserId, activeIds)
				.eq(SysUser::getDelFlag, "0")
				.eq(SysUser::getLockFlag, "0")
				.orderByAsc(SysUser::getUserId)))
			.stream()
			.map(user -> new AssignableUser(user.getUserId(), displayName(user), user.getUsername(), false))
			.toList();
	}

	private Set<Long> organizationLeaderIds(Long tenantId, List<Long> memberIds) {
		SysPost leaderPost = postMapper.selectOne(Wrappers.<SysPost>lambdaQuery()
			.eq(SysPost::getPostCode, PostCodeEnum.TEAM_LEADER.getCode())
			.eq(SysPost::getDelFlag, "0"));
		if (leaderPost == null) {
			return Set.of();
		}
		Set<Long> leaderIds = userPostMapper
			.selectList(Wrappers.<SysUserPost>lambdaQuery()
				.eq(SysUserPost::getTenantId, tenantId)
				.eq(SysUserPost::getPostId, leaderPost.getPostId())
				.in(SysUserPost::getUserId, memberIds))
			.stream()
			.map(SysUserPost::getUserId)
			.collect(java.util.stream.Collectors.toSet());
		// A default approver must be deterministic. Legacy tenants can have the
		// TEAM_LEADER post assigned broadly; never pick an arbitrary person there.
		return leaderIds.size() == 1 ? leaderIds : Set.of();
	}

	private static String displayName(SysUser user) {
		return StrUtil.firstNonBlank(user.getName(), user.getNickname(), user.getUsername(),
				String.valueOf(user.getUserId()));
	}

	private boolean isActiveTenantUser(Long tenantId, Long userId) {
		// A platform user can belong to several tenants through sys_tenant_user while
		// sys_user.tenant_id keeps its owning tenant. Skip only that automatic line;
		// the explicit tenant membership check below preserves the selected boundary.
		SysUser user = TenantBroker.noneAs(() -> userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
			.eq(SysUser::getUserId, userId)
			.eq(SysUser::getDelFlag, "0")
			.eq(SysUser::getLockFlag, "0")));
		return user != null && tenantUserMapper.selectCount(Wrappers.<SysTenantUser>lambdaQuery()
			.eq(SysTenantUser::getTenantId, tenantId)
			.eq(SysTenantUser::getUserId, userId)) > 0;
	}

	/**
	 * Returns only memberships whose organization still exists and is active. A deleted
	 * department must never become the implicit organization context after a tenant
	 * switch or a stale notification deep link.
	 */
	private List<Long> activeOrganizationIds(Long tenantId, Long userId) {
		if (tenantId == null || userId == null) {
			return List.of();
		}
		List<Long> userOrganizationIds = userDeptMapper
			.selectList(
					Wrappers.<SysUserDept>query().eq("tenant_id", tenantId).eq("user_id", userId).orderByAsc("dept_id"))
			.stream()
			.map(SysUserDept::getDeptId)
			.distinct()
			.toList();
		if (userOrganizationIds.isEmpty()) {
			return List.of();
		}
		return deptMapper
			.selectList(Wrappers.<SysDept>query()
				.eq("tenant_id", tenantId)
				.in("dept_id", userOrganizationIds)
				.eq("del_flag", "0")
				.orderByAsc("dept_id"))
			.stream()
			.map(SysDept::getDeptId)
			.distinct()
			.toList();
	}

}
