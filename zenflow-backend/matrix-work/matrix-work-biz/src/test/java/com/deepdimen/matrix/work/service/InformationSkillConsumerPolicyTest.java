package com.deepdimen.matrix.work.service;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.api.entity.InformationPolicy;
import com.deepdimen.matrix.work.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InformationSkillConsumerPolicyTest {

	final InformationPolicyMapper policies = mock(InformationPolicyMapper.class);

	final TenantUserDirectory users = mock(TenantUserDirectory.class);

	final ObjectMapper json = new ObjectMapper();

	final InformationSkillCollectionScope scope = new InformationSkillCollectionScope(policies,
			mock(InformationRunMapper.class), users, json);

	final InformationSkillResultStore results = mock(InformationSkillResultStore.class);

	final SkillExecutionService executor = mock(SkillExecutionService.class);

	final ObjectProvider<SkillExecutionService> executors = mock(ObjectProvider.class);

	final InformationSkillConsumerPolicy adapter = new InformationSkillConsumerPolicy(policies, users, scope, results,
			executors, json);

	final InformationPolicy policy = new InformationPolicy();

	final SkillConsumerPolicy.BindingConfiguration binding = new SkillConsumerPolicy.BindingConfiguration("9", "2",
			true, "{\"scope\":\"POLICY\"}", "EXPLICIT", "{}", "{\"mode\":\"NONE\"}");

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(42L);
		auth("knowledge_skill_enable", "work_information_policy");
		when(users.isAssignableUser(42L, 3L)).thenReturn(true);
		when(executors.getIfAvailable()).thenReturn(executor);
		policy.setId(7L);
		policy.setEnabled(1);
		policy.setModelName("fixed");
		policy.setSkillPolicyVersion(4);
		when(policies.selectOne(any())).thenReturn(policy);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
		TransactionSynchronizationManager.setActualTransactionActive(false);
	}

	void auth(String... permissions) {
		var u = mock(MatrixUser.class);
		when(u.getId()).thenReturn(3L);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(u, "n/a",
					Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
	}

	Consumer consumer(String slot) {
		return new Consumer("INFORMATION", "7", slot, "default");
	}

	ResolvedSkillSnapshot release(String slot) {
		return new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, "fixed", null), InformationSkillContract.inputSchema(json, slot),
				InformationSkillContract.outputSchema(slot), InformationSkillInstructions.source(slot).instruction(),
				List.of(), "{}", List.of());
	}

	@Test
	void bothSlotsRequireExactContractAndAnAvailableExecutor() {
		for (String slot : List.of("ANALYZE", "REVIEW")) {
			assertThrows(IllegalArgumentException.class,
					() -> adapter.validateActivation("42", "3", consumer(slot), binding, release(slot)));
			when(executor.available(consumer(slot), release(slot))).thenReturn(true);
			adapter.validateActivation("42", "3", consumer(slot), binding, release(slot));
		}
		policy.setModelName("other");
		assertThrows(IllegalArgumentException.class,
				() -> adapter.validateActivation("42", "3", consumer("ANALYZE"), binding, release("ANALYZE")));
		verify(executor, never()).execute(any());
	}

	@Test void migrationRequiresTransactionAndCurrentVersionAndNeverDowngradesOnDisable() {
        when(executor.available(any(),any())).thenReturn(true);
        assertThrows(IllegalArgumentException.class,()->adapter.bindingActivated("42","3",consumer("ANALYZE"),binding,release("ANALYZE")));
        TransactionSynchronizationManager.setActualTransactionActive(true);when(policies.markManaged(42L,7L,4,"fixed")).thenReturn(1).thenReturn(0);
        adapter.bindingActivated("42","3",consumer("ANALYZE"),binding,release("ANALYZE"));
        assertThrows(IllegalArgumentException.class,()->adapter.bindingActivated("42","3",consumer("REVIEW"),binding,release("REVIEW")));
        var disabled=new SkillConsumerPolicy.BindingConfiguration("9","2",false,binding.audienceJson(),"EXPLICIT","{}",binding.fallbackPolicyJson());
        adapter.bindingActivated("42","3",consumer("ANALYZE"),disabled,release("ANALYZE"));
        verify(policies,times(2)).markManaged(42L,7L,4,"fixed");
    }

	@Test
	void managementDoesNotGrantExecutionAndReadOnlyInspectionCannotOpenACollection() {
		var identity = new Identity("42", "3", "3", "task-capabilities");
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(identity, consumer("ANALYZE"), binding, release("ANALYZE")));
		policy.setSkillManagementMode("MANAGED");
		auth("work_information_run");
		adapter.authorizeInspection(identity, consumer("ANALYZE"), binding, release("ANALYZE"));
		assertThrows(IllegalArgumentException.class, scope::require);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeBinding("42", "3", consumer("ANALYZE"), binding));
		SecurityContextHolder.clearContext();
		assertFalse(adapter.permitsBackgroundExecution(
				new Identity("42", "information-collector", "information-collector", "work-information:9"),
				consumer("ANALYZE"), "9"));
	}

	@Test
	void clientCannotWidenAudienceOrEnableFallback() {
		var unsafe = new SkillConsumerPolicy.BindingConfiguration("9", "2", true, "{\"scope\":\"TENANT\"}", "EXPLICIT",
				"{}", binding.fallbackPolicyJson());
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeBinding("42", "3", consumer("ANALYZE"), unsafe));
		TenantContextHolder.setTenantId(43L);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeBinding("42", "3", consumer("ANALYZE"), binding));
	}

}
