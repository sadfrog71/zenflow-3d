package com.deepdimen.matrix.work.brief;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.skill.api.SkillConsumerPolicy.BindingConfiguration;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.skill.api.SkillExecutionService;
import com.deepdimen.matrix.work.entity.WorkBriefPolicyEntity;
import com.deepdimen.matrix.work.mapper.WorkBriefPolicyMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class WorkBriefSkillConsumerPolicyTest {

	private final WorkBriefPolicyMapper mapper = mock(WorkBriefPolicyMapper.class);

	private final TenantUserDirectory users = mock(TenantUserDirectory.class);

	private final WorkBriefPolicyEntity policy = new WorkBriefPolicyEntity();

	private final Consumer consumer = new Consumer("WORK_BRIEF", "7", "WORK_BRIEF", "default");

	private WorkBriefSkillConsumerPolicy adapter;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setup() {
		TenantContextHolder.setTenantId(42L);
		var user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(3L);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a",
					List.of(new SimpleGrantedAuthority("knowledge_skill_enable"))));
		policy.setId(7L);
		policy.setTenantId(42L);
		policy.setOrgId(5L);
		policy.setEnabled(true);
		policy.setAllowDeterministicFallback(false);
		when(mapper.selectOne(any())).thenReturn(policy);
		when(users.isAssignableUser(42L, 3L)).thenReturn(true);
		when(users.isOrganizationMember(42L, 3L, 5L)).thenReturn(true);
		adapter = new WorkBriefSkillConsumerPolicy(mapper, users, new ObjectMapper(), mock(ObjectProvider.class));
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void draftUsesExistingPolicyScopeWithoutChangingIt() {
		adapter.authorizeBinding("42", "3", consumer, config("NONE"));
		assertTrue(policy.getEnabled());
		assertFalse(policy.getAllowDeterministicFallback());
		verify(mapper, never()).updateById(any(WorkBriefPolicyEntity.class));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeBinding("42", "3", consumer, config("DETERMINISTIC")));
	}

	@Test
	void rejectsCrossTenantActorAndOrganization() {
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeBinding("43", "3", consumer, config("NONE")));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeBinding("42", "4", consumer, config("NONE")));
		assertThrows(IllegalArgumentException.class, () -> adapter.authorizeBinding("42", "3",
				new Consumer("WORK_BRIEF", "007", "WORK_BRIEF", "default"), config("NONE")));
		when(users.isOrganizationMember(42L, 3L, 5L)).thenReturn(false);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeBinding("42", "3", consumer, config("NONE")));
	}

	@Test
	void missingManagedExecutorCannotFallBackToLegacyActivation() {
		var release = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, null, null), "{}", WorkBriefSkillContractValidatorTest.VALID,
				"instructions", List.of(), "{}", List.of());
		var error = assertThrows(IllegalArgumentException.class,
				() -> adapter.validateActivation("42", "3", consumer, config("NONE"), release));
		assertEquals("EXECUTOR_UNAVAILABLE", error.getMessage());
	}

	@Test
	void executionUsesOwnBusinessScopeWithoutGrantingManagementPermission() {
		var current = SecurityContextHolder.getContext().getAuthentication();
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(current.getPrincipal(), "n/a", List.of()));
		var release = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, null, null), "{}", WorkBriefSkillContractValidatorTest.VALID,
				"instructions", List.of(), "{}", List.of());
		adapter.authorizeExecution(new Identity("42", "3", "3", "run"), consumer, config("NONE"), release);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeBinding("42", "3", consumer, config("NONE")));
		assertThrows(IllegalArgumentException.class, () -> adapter
			.authorizeExecution(new Identity("42", "3", "4", "run"), consumer, config("NONE"), release));
		policy.setEnabled(false);
		assertThrows(IllegalArgumentException.class, () -> adapter
			.authorizeExecution(new Identity("42", "3", "3", "run"), consumer, config("NONE"), release));
	}

	@Test
	void incompatibleOutputIsRejectedBeforeActivationAndExecution() {
		var release = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, null, null), "{}", "{}", "instructions", List.of(), "{}", List.of());
		var activation = assertThrows(IllegalArgumentException.class,
				() -> adapter.validateActivation("42", "3", consumer, config("NONE"), release));
		assertEquals("WORK_BRIEF_OUTPUT_CONTRACT_INCOMPATIBLE", activation.getMessage());
		var execution = assertThrows(IllegalArgumentException.class, () -> adapter
			.authorizeExecution(new Identity("42", "3", "3", "run"), consumer, config("NONE"), release));
		assertEquals("WORK_BRIEF_OUTPUT_CONTRACT_INCOMPATIBLE", execution.getMessage());
	}

	@Test
    void targetSelectorUsesMembershipAndNeverReturnsForeignPolicy() {
        when(users.listOrganizationIds(42L,3L)).thenReturn(List.of(5L));
        var foreign = new WorkBriefPolicyEntity();foreign.setId(8L);foreign.setTenantId(43L);foreign.setOrgId(5L);
        var otherOrg = new WorkBriefPolicyEntity();otherOrg.setId(9L);otherOrg.setTenantId(42L);otherOrg.setOrgId(6L);
        var defaults = new WorkBriefPolicyEntity();defaults.setId(10L);defaults.setTenantId(42L);defaults.setOrgId(0L);defaults.setEnabled(false);
        when(mapper.selectList(any())).thenReturn(List.of(policy,foreign,otherOrg,defaults));
        var options = adapter.bindingTargets("42","3",null);
        assertEquals(List.of("7","10"),options.stream().map(o -> o.consumer().id()).toList());
        assertTrue(options.get(0).enabled());assertFalse(options.get(1).enabled());
        assertThrows(IllegalArgumentException.class,()->adapter.bindingTargets("42","3","01"));
        when(users.isAssignableUser(42L,3L)).thenReturn(false);
        assertThrows(IllegalArgumentException.class,()->adapter.bindingTargets("42","3",null));
    }

	private BindingConfiguration config(String mode) {
		return new BindingConfiguration("9", "2", true, "{\"scope\":\"POLICY\"}", "EXPLICIT", "{}",
				"{\"mode\":\"" + mode + "\"}");
	}

}
