package com.deepdimen.matrix.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.deepdimen.matrix.admin.api.entity.SysDept;
import com.deepdimen.matrix.admin.api.entity.SysTenantUser;
import com.deepdimen.matrix.admin.api.entity.SysUser;
import com.deepdimen.matrix.admin.api.entity.SysUserDept;
import com.deepdimen.matrix.admin.api.entity.SysPost;
import com.deepdimen.matrix.admin.api.entity.SysUserPost;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory.AssignableUser;
import com.deepdimen.matrix.admin.mapper.SysDeptMapper;
import com.deepdimen.matrix.admin.mapper.SysTenantUserMapper;
import com.deepdimen.matrix.admin.mapper.SysUserDeptMapper;
import com.deepdimen.matrix.admin.mapper.SysUserMapper;
import com.deepdimen.matrix.admin.mapper.SysPostMapper;
import com.deepdimen.matrix.admin.mapper.SysUserPostMapper;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;

class TenantUserDirectoryImplTest {

	private static final Long TENANT_ID = 100L;

	private static final Long USER_ID = 200L;

	@AfterEach
	void clearTenantContext() {
		TenantContextHolder.clear();
	}

	@Test
	void validatesCrossTenantMemberWithoutLeakingTheTenantSkipFlag() {
		SysUserMapper userMapper = mock(SysUserMapper.class);
		SysTenantUserMapper tenantUserMapper = mock(SysTenantUserMapper.class);
		SysUserDeptMapper userDeptMapper = mock(SysUserDeptMapper.class);
		SysDeptMapper deptMapper = mock(SysDeptMapper.class);
		TenantUserDirectoryImpl directory = new TenantUserDirectoryImpl(userMapper, tenantUserMapper, userDeptMapper,
				deptMapper, mock(SysPostMapper.class), mock(SysUserPostMapper.class),
				mock(com.deepdimen.matrix.admin.mapper.SysUserRoleMapper.class),
				mock(com.deepdimen.matrix.admin.mapper.SysRoleMapper.class));
		SysUser user = new SysUser();
		user.setUserId(USER_ID);
		when(userMapper.selectOne(any())).thenAnswer(invocation -> {
			assertTrue(TenantContextHolder.getTenantSkip());
			return user;
		});
		when(tenantUserMapper.selectCount(any())).thenReturn(1L);
		when(userDeptMapper.selectList(any())).thenReturn(List.of(membership(10L)));
		SysDept department = new SysDept();
		department.setDeptId(10L);
		when(deptMapper.selectList(any())).thenReturn(List.of(department));
		TenantContextHolder.setTenantId(TENANT_ID);

		assertTrue(directory.isAssignableUser(TENANT_ID, USER_ID));
		assertFalse(TenantContextHolder.getTenantSkip());
		assertEquals(TENANT_ID, TenantContextHolder.getTenantId());
	}

	@Test
	void resolvesActiveOrganizationsWithoutUpmsXmlStatements() {
		SysUserMapper userMapper = mock(SysUserMapper.class);
		SysTenantUserMapper tenantUserMapper = mock(SysTenantUserMapper.class);
		SysUserDeptMapper userDeptMapper = mock(SysUserDeptMapper.class);
		SysDeptMapper deptMapper = mock(SysDeptMapper.class);
		TenantUserDirectoryImpl directory = new TenantUserDirectoryImpl(userMapper, tenantUserMapper, userDeptMapper,
				deptMapper, mock(SysPostMapper.class), mock(SysUserPostMapper.class),
				mock(com.deepdimen.matrix.admin.mapper.SysUserRoleMapper.class),
				mock(com.deepdimen.matrix.admin.mapper.SysRoleMapper.class));
		SysUserDept firstMembership = membership(10L);
		SysUserDept deletedMembership = membership(11L);
		SysDept activeDepartment = new SysDept();
		activeDepartment.setDeptId(10L);
		when(userDeptMapper.selectList(any())).thenReturn(List.of(firstMembership, deletedMembership));
		when(deptMapper.selectList(any())).thenReturn(List.of(activeDepartment));

		assertEquals(List.of(10L), directory.listOrganizationIds(TENANT_ID, USER_ID));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Wrapper<SysDept>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(deptMapper).selectList(wrapperCaptor.capture());
		AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
		wrapper.getSqlSegment();
		assertEquals(true, wrapper.getParamNameValuePairs().containsValue(TENANT_ID));
		assertEquals(true, wrapper.getParamNameValuePairs().containsValue(10L));
		assertEquals(true, wrapper.getParamNameValuePairs().containsValue(11L));
	}

	@Test
	void returnsOnlyActiveUsersFromTheSelectedOrganizationWithDisplayNames() {
		SysUserMapper userMapper = mock(SysUserMapper.class);
		SysTenantUserMapper tenantUserMapper = mock(SysTenantUserMapper.class);
		SysUserDeptMapper userDeptMapper = mock(SysUserDeptMapper.class);
		SysDeptMapper deptMapper = mock(SysDeptMapper.class);
		SysPostMapper postMapper = mock(SysPostMapper.class);
		SysUserPostMapper userPostMapper = mock(SysUserPostMapper.class);
		TenantUserDirectoryImpl directory = new TenantUserDirectoryImpl(userMapper, tenantUserMapper, userDeptMapper,
				deptMapper, postMapper, userPostMapper, mock(com.deepdimen.matrix.admin.mapper.SysUserRoleMapper.class),
				mock(com.deepdimen.matrix.admin.mapper.SysRoleMapper.class));
		SysUserDept firstMembership = membership(10L);
		SysUser user = new SysUser();
		user.setUserId(USER_ID);
		user.setName("马跃");
		user.setUsername("mark");
		when(deptMapper.selectCount(any())).thenReturn(1L);
		when(userDeptMapper.selectList(any())).thenReturn(List.of(firstMembership));
		SysTenantUser tenantMember = new SysTenantUser();
		tenantMember.setTenantId(TENANT_ID);
		tenantMember.setUserId(USER_ID);
		when(tenantUserMapper.selectList(any())).thenReturn(List.of(tenantMember));
		SysPost leaderPost = new SysPost();
		leaderPost.setPostId(99L);
		when(postMapper.selectOne(any())).thenReturn(leaderPost);
		SysUserPost leaderAssignment = new SysUserPost();
		leaderAssignment.setTenantId(TENANT_ID);
		leaderAssignment.setUserId(USER_ID);
		leaderAssignment.setPostId(99L);
		when(userPostMapper.selectList(any())).thenReturn(List.of(leaderAssignment));
		when(userMapper.selectList(any())).thenAnswer(invocation -> {
			assertTrue(TenantContextHolder.getTenantSkip());
			return List.of(user);
		});
		TenantContextHolder.setTenantId(TENANT_ID);

		assertEquals(List.of(new AssignableUser(USER_ID, "马跃", "mark", true)),
				directory.listOrganizationUsers(TENANT_ID, 10L));
		assertFalse(TenantContextHolder.getTenantSkip());
		assertEquals(TENANT_ID, TenantContextHolder.getTenantId());
		assertEquals(List.of(USER_ID), directory.listOrganizationUserIds(TENANT_ID, 10L));
	}

	@Test
	void validatesSeveralAssignableUsersWithAConstantNumberOfQueries() {
		SysUserMapper userMapper = mock(SysUserMapper.class);
		SysTenantUserMapper tenantUserMapper = mock(SysTenantUserMapper.class);
		SysUserDeptMapper userDeptMapper = mock(SysUserDeptMapper.class);
		SysDeptMapper deptMapper = mock(SysDeptMapper.class);
		TenantUserDirectoryImpl directory = new TenantUserDirectoryImpl(userMapper, tenantUserMapper, userDeptMapper,
				deptMapper, mock(SysPostMapper.class), mock(SysUserPostMapper.class),
				mock(com.deepdimen.matrix.admin.mapper.SysUserRoleMapper.class),
				mock(com.deepdimen.matrix.admin.mapper.SysRoleMapper.class));
		SysTenantUser tenantMember = new SysTenantUser();
		tenantMember.setTenantId(TENANT_ID);
		tenantMember.setUserId(USER_ID);
		SysUser user = new SysUser();
		user.setUserId(USER_ID);
		SysUserDept membership = membership(10L);
		SysDept department = new SysDept();
		department.setDeptId(10L);
		when(tenantUserMapper.selectList(any())).thenReturn(List.of(tenantMember));
		when(userMapper.selectList(any())).thenAnswer(invocation -> {
			assertTrue(TenantContextHolder.getTenantSkip());
			return List.of(user);
		});
		when(userDeptMapper.selectList(any())).thenReturn(List.of(membership));
		when(deptMapper.selectList(any())).thenReturn(List.of(department));
		TenantContextHolder.setTenantId(TENANT_ID);

		assertEquals(List.of(USER_ID), directory.listAssignableUserIds(TENANT_ID, List.of(USER_ID, 201L)));
		verify(tenantUserMapper).selectList(any());
		verify(userMapper).selectList(any());
		verify(userDeptMapper).selectList(any());
		verify(deptMapper).selectList(any());
		assertFalse(TenantContextHolder.getTenantSkip());
	}

	@Test
	void taskAuthorityRejectsMissingTenantAndInactiveMembersBeforeReadingRoles() {
		SysUserMapper users = mock(SysUserMapper.class);
		SysTenantUserMapper members = mock(SysTenantUserMapper.class);
		var roles = mock(com.deepdimen.matrix.admin.mapper.SysRoleMapper.class);
		var userRoles = mock(com.deepdimen.matrix.admin.mapper.SysUserRoleMapper.class);
		var directory = new TenantUserDirectoryImpl(users, members, mock(SysUserDeptMapper.class),
				mock(SysDeptMapper.class), mock(SysPostMapper.class), mock(SysUserPostMapper.class), userRoles, roles);
		assertFalse(directory.taskAuthority(null, USER_ID).generalManager());
		assertNull(directory.taskAuthority(TENANT_ID, USER_ID).level());
		verify(userRoles, org.mockito.Mockito.never()).selectList(any());
		verify(roles, org.mockito.Mockito.never()).selectList(any());
	}

	@Test
	void taskAuthorityQueriesMembershipRolesAndDepartmentsWithinExplicitTenant() {
		SysUserMapper users = mock(SysUserMapper.class);
		SysTenantUserMapper members = mock(SysTenantUserMapper.class);
		SysDeptMapper departments = mock(SysDeptMapper.class);
		var roles = mock(com.deepdimen.matrix.admin.mapper.SysRoleMapper.class);
		var userRoles = mock(com.deepdimen.matrix.admin.mapper.SysUserRoleMapper.class);
		var directory = new TenantUserDirectoryImpl(users, members, mock(SysUserDeptMapper.class), departments,
				mock(SysPostMapper.class), mock(SysUserPostMapper.class), userRoles, roles);
		when(users.selectOne(any())).thenReturn(new SysUser());
		when(members.selectCount(any())).thenReturn(1L);
		var membership = new com.deepdimen.matrix.admin.api.entity.SysUserRole();
		membership.setRoleId(9L);
		when(userRoles.selectList(any())).thenAnswer(invocation -> {
			assertTenantQuery(invocation.getArgument(0));
			return List.of(membership);
		});
		var role = new com.deepdimen.matrix.admin.api.entity.SysRole();
		role.setTaskIdentity("GENERAL_MANAGER");
		role.setTaskLevel(1);
		when(roles.selectList(any())).thenAnswer(invocation -> {
			assertTenantQuery(invocation.getArgument(0));
			return List.of(role);
		});
		when(departments.selectList(any())).thenAnswer(invocation -> {
			assertTenantQuery(invocation.getArgument(0));
			return List.of();
		});
		assertTrue(directory.taskAuthority(TENANT_ID, USER_ID).generalManager());
	}

	private void assertTenantQuery(AbstractWrapper<?, ?, ?> wrapper) {
		assertTrue(wrapper.getSqlSegment().contains("tenant_id"));
		assertTrue(wrapper.getParamNameValuePairs().containsValue(TENANT_ID));
	}

	private SysUserDept membership(Long departmentId) {
		SysUserDept membership = new SysUserDept();
		membership.setUserId(USER_ID);
		membership.setDeptId(departmentId);
		return membership;
	}

}
