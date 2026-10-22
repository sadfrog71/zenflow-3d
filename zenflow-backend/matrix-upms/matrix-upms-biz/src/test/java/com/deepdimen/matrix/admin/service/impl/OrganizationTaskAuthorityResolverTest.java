package com.deepdimen.matrix.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.deepdimen.matrix.admin.api.entity.SysRole;
import com.deepdimen.matrix.admin.api.entity.SysDept;

class OrganizationTaskAuthorityResolverTest {

	@Test
	void technicalAdministratorDoesNotGainTaskVisibility() {
		var authority = OrganizationTaskAuthorityResolver.resolve(7L, 10L, List.of(role("NONE", null, 0, "")),
				departments());
		assertFalse(authority.generalManager());
		assertNull(authority.level());
		assertTrue(authority.managedDepartmentIds().isEmpty());
	}

	@Test
	void explicitManagerUsesExistingScopeAndLowestConfiguredLevel() {
		var authority = OrganizationTaskAuthorityResolver.resolve(7L, 10L,
				List.of(role("DEPARTMENT_MANAGER", 3, 2, ""), role("EMPLOYEE", 5, 0, "")), departments());
		assertEquals(3, authority.level());
		assertEquals(List.of(10L, 11L), authority.managedDepartmentIds());
	}

	@Test
	void customScopeCannotIncludeUnknownDepartmentsAndAllFailsClosed() {
		var authority = OrganizationTaskAuthorityResolver.resolve(7L, 10L,
				List.of(role("DEPARTMENT_MANAGER", 3, 1, "11,999"), role("DEPARTMENT_MANAGER", 3, 0, "")),
				departments());
		assertEquals(List.of(11L), authority.managedDepartmentIds());
		assertFalse(authority.generalManager());
	}

	@Test
	void onlyExplicitGeneralManagerGrantsEnterpriseVisibility() {
		assertTrue(OrganizationTaskAuthorityResolver
			.resolve(7L, null, List.of(role("GENERAL_MANAGER", 1, 0, "")), departments())
			.generalManager());
	}

	@Test
	void invalidCustomScopeFailsClosed() {
		assertTrue(OrganizationTaskAuthorityResolver
			.resolve(7L, 10L, List.of(role("DEPARTMENT_MANAGER", 2, 1, "10,bad")), departments())
			.managedDepartmentIds()
			.isEmpty());
	}

	private SysRole role(String identity, Integer level, int scope, String departments) {
		SysRole role = new SysRole();
		role.setTaskIdentity(identity);
		role.setTaskLevel(level);
		role.setDsType(scope);
		role.setDsScope(departments);
		return role;
	}

	private List<SysDept> departments() {
		SysDept parent = new SysDept();
		parent.setDeptId(10L);
		parent.setParentId(0L);
		SysDept child = new SysDept();
		child.setDeptId(11L);
		child.setParentId(10L);
		return List.of(parent, child);
	}

}
