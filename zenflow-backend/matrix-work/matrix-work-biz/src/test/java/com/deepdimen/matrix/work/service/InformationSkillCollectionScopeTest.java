package com.deepdimen.matrix.work.service;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.work.api.entity.*;
import com.deepdimen.matrix.work.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class InformationSkillCollectionScopeTest {

	final InformationPolicyMapper policies = mock(InformationPolicyMapper.class);

	final InformationRunMapper runs = mock(InformationRunMapper.class);

	final TenantUserDirectory users = mock(TenantUserDirectory.class);

	final InformationSkillCollectionScope scope = new InformationSkillCollectionScope(policies, runs, users,
			new ObjectMapper());

	final InformationPolicy policy = new InformationPolicy();

	final InformationRun run = new InformationRun();

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(42L);
		SecurityContextHolder.clearContext();
		policy.setId(7L);
		policy.setOrgId(5L);
		policy.setEnabled(1);
		policy.setModelName("configured-model");
		run.setId(8L);
		run.setPolicyId(7L);
		run.setOrgId(5L);
		run.setStatus("RUNNING");
		when(policies.selectOne(any())).thenReturn(policy);
		when(runs.selectOne(any())).thenReturn(run);
	}

	@AfterEach
	void cleanup() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void idsWithoutACollectionScopeCannotAuthorizeExecution() {
		assertThrows(IllegalArgumentException.class, scope::require);
		try (var lease = scope.open(7L, 8L)) {
			var context = scope.require();
			assertEquals(42L, context.tenantId());
			assertEquals("information-collector", context.actorId());
			assertEquals(8L, context.collectionRunId());
			assertTrue(context.policySnapshot().contains("configured-model"));
		}
		assertThrows(IllegalArgumentException.class, scope::require);
	}

	@Test
	void changedPolicyDisabledPolicyOrFinishedCollectionRevokesExistingScope() {
		try (var lease = scope.open(7L, 8L)) {
			policy.setModelName("changed");
			assertThrows(IllegalArgumentException.class, scope::require);
			policy.setModelName("configured-model");
			policy.setEnabled(0);
			assertThrows(IllegalArgumentException.class, scope::require);
			policy.setEnabled(1);
			run.setStatus("SUCCESS");
			assertThrows(IllegalArgumentException.class, scope::require);
		}
	}

	@Test
	void wrongOrganizationPolicyOrRunCannotOpen() {
		run.setOrgId(6L);
		assertThrows(IllegalArgumentException.class, () -> scope.open(7L, 8L));
		run.setOrgId(5L);
		run.setPolicyId(9L);
		assertThrows(IllegalArgumentException.class, () -> scope.open(7L, 8L));
		run.setPolicyId(7L);
		run.setId(9L);
		assertThrows(IllegalArgumentException.class, () -> scope.open(7L, 8L));
	}

	@Test
	void changedTenantOrSessionCannotBorrowBackgroundAuthority() {
		try (var lease = scope.open(7L, 8L)) {
			TenantContextHolder.setTenantId(43L);
			assertThrows(IllegalArgumentException.class, scope::require);
			TenantContextHolder.setTenantId(42L);
			SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken("caller", "none"));
			assertThrows(IllegalArgumentException.class, scope::require);
		}
		assertThrows(IllegalArgumentException.class, () -> scope.open(7L, 8L));
	}

	@Test
	void scopeCannotNestOrLeakToAnotherThread() {
		try (var lease = scope.open(7L, 8L)) {
			assertThrows(IllegalArgumentException.class, () -> scope.open(7L, 8L));
			java.util.concurrent.CompletableFuture.runAsync(() -> {
				assertThrows(IllegalArgumentException.class, scope::require);
				assertThrows(IllegalArgumentException.class, lease::close);
			}).join();
			assertEquals(7L, scope.require().policyId());
		}
		assertThrows(IllegalArgumentException.class, scope::require);
	}

	@Test
	void manualCollectionRechecksMembershipAndRequiresRunPermission() {
		var user = mock(com.deepdimen.matrix.common.security.service.MatrixUser.class);
		when(user.getId()).thenReturn(3L);
		when(users.isAssignableUser(42L, 3L)).thenReturn(true);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a", java.util.List
				.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("work_information_run"))));
		try (var lease = scope.open(7L, 8L)) {
			assertEquals("3", scope.require().actorId());
			when(users.isAssignableUser(42L, 3L)).thenReturn(false);
			assertThrows(IllegalArgumentException.class, scope::require);
		}
		when(users.isAssignableUser(42L, 3L)).thenReturn(true);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a", java.util.List.of()));
		assertThrows(IllegalArgumentException.class, () -> scope.open(7L, 8L));
	}

}
