package com.deepdimen.matrix.assistant.matrix;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class MatrixAssistantIdentityResolver implements AssistantIdentityResolver {

	private static final String ORGANIZATION_HEADER = "X-ORG-ID";

	private static final String CORRELATION_HEADER = "X-Correlation-ID";

	private final HttpServletRequest request;
	private final TenantUserDirectory tenantUserDirectory;

	public MatrixAssistantIdentityResolver(HttpServletRequest request,
			TenantUserDirectory tenantUserDirectory) {
		this.request = request;
		this.tenantUserDirectory = tenantUserDirectory;
	}

	@Override
	public IdentityEnvelope currentIdentity() {
		MatrixUser user = SecurityUtils.getUser();
		Long tenantId = TenantContextHolder.getTenantId();
		if (user == null || user.getId() == null || tenantId == null
				|| !tenantId.equals(user.getTenantId())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未获取到有效登录身份");
		}
		List<Long> accessibleOrganizations = tenantUserDirectory.listOrganizationIds(tenantId, user.getId());
		Long currentOrganization = resolveCurrentOrganization(tenantId, user.getId(), accessibleOrganizations);
		List<Long> roleIds = user.getRoleIds() == null ? List.of() : user.getRoleIds();
		List<Long> departmentIds = departmentIds(user);
		String correlationId = request.getHeader(CORRELATION_HEADER);
		if (correlationId == null || correlationId.isBlank()) {
			correlationId = UUID.randomUUID().toString();
		}
		return new IdentityEnvelope(String.valueOf(tenantId), String.valueOf(user.getId()),
				currentOrganization == null ? null : String.valueOf(currentOrganization),
				accessibleOrganizations.stream().map(String::valueOf).toList(),
				roleIds.stream().map(String::valueOf).toList(),
				departmentIds.stream().map(String::valueOf).toList(), correlationId, "ASSISTANT_CONTEXT");
	}

	private List<Long> departmentIds(MatrixUser user) {
		LinkedHashSet<Long> departmentIds = new LinkedHashSet<>();
		if (user.getDeptId() != null) {
			departmentIds.add(user.getDeptId());
		}
		if (user.getDeptIds() != null) {
			departmentIds.addAll(user.getDeptIds());
		}
		return List.copyOf(departmentIds);
	}

	private Long resolveCurrentOrganization(Long tenantId, Long userId,
			List<Long> accessibleOrganizations) {
		String raw = request.getHeader(ORGANIZATION_HEADER);
		Long requested = tenantUserDirectory.findDefaultOrganizationId(tenantId, userId);
		if (raw != null && !raw.isBlank()) {
			try {
				requested = Long.valueOf(raw);
			}
			catch (NumberFormatException ex) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "非法的组织上下文");
			}
		}
		if (requested != null && (!accessibleOrganizations.contains(requested)
				|| !tenantUserDirectory.isOrganizationMember(tenantId, userId, requested))) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该组织数据");
		}
		return requested;
	}

}
