package com.deepdimen.matrix.admin.api.service;

import java.util.List;

/**
 * Canonical tenant/user membership boundary shared by business domains.
 *
 * <p>
 * Business modules must not trust a user id supplied by a browser. They validate it
 * through this directory so assignment follows the same tenant, active-user and
 * organization membership rules as the platform selector.
 * </p>
 */
public interface TenantUserDirectory {

	boolean isAssignableUser(Long tenantId, Long userId);

	boolean isOrganizationMember(Long tenantId, Long userId, Long organizationId);

	List<Long> listAssignableUserIds(Long tenantId, List<Long> userIds);

	Long findDefaultOrganizationId(Long tenantId, Long userId);

	List<Long> listOrganizationIds(Long tenantId, Long userId);

	List<Long> listOrganizationUserIds(Long tenantId, Long organizationId);

	List<AssignableUser> listOrganizationUsers(Long tenantId, Long organizationId);

	default TaskAuthority taskAuthority(Long tenantId, Long userId) {
		return new TaskAuthority(userId, null, false, List.of());
	}

	record TaskAuthority(Long userId, Integer level, boolean generalManager, List<Long> managedDepartmentIds) {
		public TaskAuthority {
			managedDepartmentIds = List.copyOf(managedDepartmentIds);
		}
	}

	default List<AssignableUser> listTenantUsers(Long tenantId) {
		return List.of();
	}

	record AssignableUser(Long userId, String displayName, String username, boolean recommendedApprover) {
	}

}
