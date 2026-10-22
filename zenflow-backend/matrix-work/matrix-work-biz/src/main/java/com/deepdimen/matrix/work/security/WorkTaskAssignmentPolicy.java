package com.deepdimen.matrix.work.security;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkTaskAssignmentPolicy {

	private final TenantUserDirectory tenantUserDirectory;

	public void requireAssignable(Long ownerId) {
		MatrixUser current = SecurityUtils.getUser();
		Long tenantId = TenantContextHolder.getTenantId();
		if (current == null || tenantId == null || !tenantUserDirectory.isAssignableUser(tenantId, ownerId)) {
			throw new IllegalArgumentException("负责人不属于当前租户的可分配组织成员");
		}
	}

	public void requireAssignable(Long userId, Long organizationId, String roleLabel) {
		MatrixUser current = SecurityUtils.getUser();
		Long tenantId = TenantContextHolder.getTenantId();
		if (current == null || tenantId == null || organizationId == null
				|| !tenantUserDirectory.isOrganizationMember(tenantId, userId, organizationId)) {
			throw new IllegalArgumentException(roleLabel + "不属于当前业务组织的可选人员");
		}
	}

	public void requireAssignable(Long ownerId, List<Long> assigneeIds, Long organizationId) {
		MatrixUser current = SecurityUtils.getUser();
		Long tenantId = TenantContextHolder.getTenantId();
		if (current == null || tenantId == null || organizationId == null) {
			throw new IllegalArgumentException("缺少任务人员校验上下文");
		}
		Set<Long> assignableIds = new HashSet<>(tenantUserDirectory.listOrganizationUserIds(tenantId, organizationId));
		if (!assignableIds.contains(ownerId)) {
			throw new IllegalArgumentException("验收负责人不属于当前业务组织的可选人员");
		}
		if (assigneeIds.stream().anyMatch(userId -> !assignableIds.contains(userId))) {
			throw new IllegalArgumentException("执行人员不属于当前业务组织的可选人员");
		}
	}

}
