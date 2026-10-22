package com.deepdimen.matrix.work.security;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class CurrentOrganizationResolver {

	public static final String HEADER = "X-ORG-ID";

	private final HttpServletRequest request;
	private final TenantUserDirectory tenantUserDirectory;

	public Long resolve() {
		MatrixUser user = SecurityUtils.getUser();
		if (user == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
		}
		Long tenantId = TenantContextHolder.getTenantId();
		if (tenantId == null) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "缺少租户上下文");
		}
		String raw = request.getHeader(HEADER);
		Long requested = null;
		if (raw != null && !raw.isBlank()) {
			try {
				requested = Long.valueOf(raw);
			}
			catch (NumberFormatException ex) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "非法的组织上下文");
			}
		}
		else {
			requested = tenantUserDirectory.findDefaultOrganizationId(tenantId, user.getId());
		}
		if (requested == null
				|| !tenantUserDirectory.isOrganizationMember(tenantId, user.getId(), requested)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该组织数据");
		}
		return requested;
	}
}
