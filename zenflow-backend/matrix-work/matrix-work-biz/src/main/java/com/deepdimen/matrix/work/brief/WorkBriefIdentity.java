package com.deepdimen.matrix.work.brief;

import java.util.List;

public record WorkBriefIdentity(Long tenantId, Long ownerUserId, Long orgId, List<Long> roleIds, List<Long> deptIds) {

	public WorkBriefIdentity {
		roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
		deptIds = deptIds == null ? List.of() : List.copyOf(deptIds);
	}
}
