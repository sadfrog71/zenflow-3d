package com.deepdimen.matrix.work.brief;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.work.security.CurrentOrganizationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkBriefIdentityResolver {

	private final CurrentOrganizationResolver organizationResolver;

	public WorkBriefIdentity current() {
		MatrixUser user = SecurityUtils.getUser();
		Long tenantId = TenantContextHolder.getTenantId();
		if (user == null || user.getId() == null || tenantId == null
				|| !tenantId.equals(user.getTenantId())) {
			throw new IllegalStateException("未获取到当前租户或用户身份");
		}
		return new WorkBriefIdentity(tenantId, user.getId(), organizationResolver.resolve(), user.getRoleIds(),
				user.getDeptIds());
	}
}
