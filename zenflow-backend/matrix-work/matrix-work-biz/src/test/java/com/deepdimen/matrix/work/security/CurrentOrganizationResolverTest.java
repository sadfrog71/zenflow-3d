package com.deepdimen.matrix.work.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class CurrentOrganizationResolverTest {

	private static final long TENANT_ID = 2052188107445968897L;
	private static final long ORGANIZATION_ID = 2052188108150611969L;
	private final HttpServletRequest request = mock(HttpServletRequest.class);
	private final TenantUserDirectory directory = mock(TenantUserDirectory.class);
	private final CurrentOrganizationResolver resolver = new CurrentOrganizationResolver(request, directory);

	@BeforeEach
	void authenticate() {
		TenantContextHolder.setTenantId(TENANT_ID);
		// Deliberately retain a stale department claim to prove that canonical
		// tenant membership, rather than the token snapshot, is authoritative.
		MatrixUser user = new MatrixUser(1L, "admin", List.of(), 10L, List.of(10L), List.of(),
			null, null, "admin", "admin", null, 1L, "password", true, true,
			"TOB", true, null, true, List.of());
		SecurityContextHolder.getContext().setAuthentication(
			UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
	}

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
		TenantContextHolder.clear();
	}

	@Test
	void acceptsCanonicalMembershipEvenWhenTokenDepartmentIsStale() {
		when(request.getHeader(CurrentOrganizationResolver.HEADER)).thenReturn(String.valueOf(ORGANIZATION_ID));
		when(directory.isOrganizationMember(TENANT_ID, 1L, ORGANIZATION_ID)).thenReturn(true);

		assertEquals(ORGANIZATION_ID, resolver.resolve());
	}

	@Test
	void rejectsOrganizationOutsideCanonicalMembership() {
		when(request.getHeader(CurrentOrganizationResolver.HEADER)).thenReturn(String.valueOf(ORGANIZATION_ID));
		when(directory.isOrganizationMember(TENANT_ID, 1L, ORGANIZATION_ID)).thenReturn(false);

		assertThrows(ResponseStatusException.class, resolver::resolve);
	}
}
