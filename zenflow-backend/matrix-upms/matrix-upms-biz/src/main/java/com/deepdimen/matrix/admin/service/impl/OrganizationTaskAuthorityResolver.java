package com.deepdimen.matrix.admin.service.impl;

import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.stream.Collectors;
import com.deepdimen.matrix.admin.api.entity.SysRole;
import com.deepdimen.matrix.admin.api.entity.SysDept;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory.TaskAuthority;

/**
 * Resolves only explicitly configured task identities from tenant-validated resources.
 */
final class OrganizationTaskAuthorityResolver {

	private OrganizationTaskAuthorityResolver() {
	}

	static TaskAuthority resolve(Long userId, Long defaultDepartmentId, List<SysRole> roles,
			List<SysDept> departments) {
		Integer level = roles.stream()
			.map(SysRole::getTaskLevel)
			.filter(Objects::nonNull)
			.filter(value -> value > 0)
			.min(Integer::compareTo)
			.orElse(null);
		boolean generalManager = roles.stream().anyMatch(role -> "GENERAL_MANAGER".equals(role.getTaskIdentity()));
		Set<Long> active = departments.stream().map(SysDept::getDeptId).collect(Collectors.toSet());
		Set<Long> managed = new LinkedHashSet<>();
		for (SysRole role : roles) {
			if (!"DEPARTMENT_MANAGER".equals(role.getTaskIdentity()) || role.getDsType() == null) {
				continue;
			}
			Set<Long> scope = new LinkedHashSet<>();
			if (role.getDsType() == 1 && role.getDsScope() != null) {
				try {
					for (String id : role.getDsScope().split(",")) {
						scope.add(Long.valueOf(id.trim()));
					}
				}
				catch (NumberFormatException ex) {
					scope.clear();
				}
			}
			else if ((role.getDsType() == 2 || role.getDsType() == 3) && active.contains(defaultDepartmentId)) {
				scope.add(defaultDepartmentId);
				if (role.getDsType() == 2) {
					boolean changed;
					do {
						changed = false;
						for (SysDept department : departments) {
							if (scope.contains(department.getParentId())) {
								changed |= scope.add(department.getDeptId());
							}
						}
					}
					while (changed);
				}
			}
			scope.retainAll(active);
			managed.addAll(scope);
		}
		return new TaskAuthority(userId, level, generalManager, managed.stream().sorted().toList());
	}

}
