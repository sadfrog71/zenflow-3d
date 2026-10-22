package com.deepdimen.matrix.assistant.matrix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;

import jakarta.servlet.http.HttpServletRequest;

class MatrixAssistantIdentityResolverTest {

	private static final long TENANT_ID = 100L;

	private static final long USER_ID = 200L;

	private final HttpServletRequest request = mock(HttpServletRequest.class);

	private final TenantUserDirectory directory = mock(TenantUserDirectory.class);

	private final MatrixAssistantIdentityResolver resolver = new MatrixAssistantIdentityResolver(request, directory);

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
		TenantContextHolder.clear();
	}

	@Test
	void mapsCurrentOrganizationRolesAndDepartmentsWithoutConflatingOrganizationMembership() {
		authenticate(List.of(7L, 8L), 10L, List.of(10L, 11L));
		when(directory.listOrganizationIds(TENANT_ID, USER_ID)).thenReturn(List.of(21L, 22L));
		when(directory.findDefaultOrganizationId(TENANT_ID, USER_ID)).thenReturn(22L);
		when(directory.isOrganizationMember(TENANT_ID, USER_ID, 22L)).thenReturn(true);

		var identity = resolver.currentIdentity();

		assertEquals("100", identity.tenantId());
		assertEquals("200", identity.userId());
		assertEquals("22", identity.currentOrgId());
		assertEquals(List.of("21", "22"), identity.accessibleOrgIds());
		assertEquals(List.of("7", "8"), identity.roleIds());
		assertEquals(List.of("10", "11"), identity.deptIds());
	}

	@Test
	void includesPrimaryDepartmentWhenLegacyTokenOmitsDepartmentList() {
		authenticate(List.of(), 10L, null);
		when(directory.listOrganizationIds(TENANT_ID, USER_ID)).thenReturn(List.of(21L));
		when(directory.findDefaultOrganizationId(TENANT_ID, USER_ID)).thenReturn(21L);
		when(directory.isOrganizationMember(TENANT_ID, USER_ID, 21L)).thenReturn(true);

		assertEquals(List.of("10"), resolver.currentIdentity().deptIds());
	}

	@Test
	void rejectsOrganizationHeaderOutsideCanonicalMembership() {
		authenticate(List.of(), 10L, List.of(10L));
		when(request.getHeader("X-ORG-ID")).thenReturn("99");
		when(directory.listOrganizationIds(TENANT_ID, USER_ID)).thenReturn(List.of(21L));

		assertThrows(ResponseStatusException.class, resolver::currentIdentity);
	}

	private void authenticate(List<Long> roleIds, Long deptId, List<Long> deptIds) {
		TenantContextHolder.setTenantId(TENANT_ID);
		MatrixUser user = new MatrixUser(USER_ID, "user", roleIds, deptId, deptIds, List.of(), null, null,
				"user", "user", null, TENANT_ID, "password", true, true, "TOB", true, null, true, List.of());
		SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
	}

}
