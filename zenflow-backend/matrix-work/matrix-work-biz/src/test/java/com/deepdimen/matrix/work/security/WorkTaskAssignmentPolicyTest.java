package com.deepdimen.matrix.work.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class WorkTaskAssignmentPolicyTest {

	private static final long TENANT_ID = 2052188107445968897L;

	private final TenantUserDirectory directory = mock(TenantUserDirectory.class);

	private final WorkTaskAssignmentPolicy policy = new WorkTaskAssignmentPolicy(directory);

	@BeforeEach
	void authenticate() {
		TenantContextHolder.setTenantId(TENANT_ID);
		MatrixUser user = new MatrixUser(1L, "admin", List.of(), 10L, List.of(10L), List.of(), null, null, "admin",
				"admin", null, TENANT_ID, "password", true, true, "TOB", true, null, true, List.of());
		SecurityContextHolder.getContext()
			.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
	}

	@AfterEach
	void clearAuthentication() {
		SecurityContextHolder.clearContext();
		TenantContextHolder.clear();
	}

	@Test
	void acceptsOnlyUsersConfirmedByTheCanonicalTenantDirectory() {
		when(directory.isAssignableUser(TENANT_ID, 2L)).thenReturn(true);
		assertDoesNotThrow(() -> policy.requireAssignable(2L));
	}

	@Test
	void rejectsUnknownOrCrossTenantAssignees() {
		when(directory.isAssignableUser(TENANT_ID, 99L)).thenReturn(false);
		assertThrows(IllegalArgumentException.class, () -> policy.requireAssignable(99L));
	}

	@Test
	void requiresExecutorAndAcceptorToBelongToTheSelectedOrganization() {
		when(directory.isOrganizationMember(TENANT_ID, 2L, 10L)).thenReturn(true);
		when(directory.isOrganizationMember(TENANT_ID, 3L, 10L)).thenReturn(false);

		assertDoesNotThrow(() -> policy.requireAssignable(2L, 10L, "执行人员"));
		assertThrows(IllegalArgumentException.class, () -> policy.requireAssignable(3L, 10L, "验收负责人"));
	}

	@Test
	void validatesOwnerAndExecutorsFromOneOrganizationDirectorySnapshot() {
		when(directory.listOrganizationUserIds(TENANT_ID, 10L)).thenReturn(List.of(2L, 3L, 4L));

		assertDoesNotThrow(() -> policy.requireAssignable(2L, List.of(3L, 4L), 10L));
		assertThrows(IllegalArgumentException.class, () -> policy.requireAssignable(9L, List.of(3L), 10L));
		assertThrows(IllegalArgumentException.class, () -> policy.requireAssignable(2L, List.of(9L), 10L));
	}

}
