package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.skill.api.SkillConsumerPolicy.BindingConfiguration;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.api.service.TaskSkillResolver;
import com.deepdimen.matrix.work.entity.WorkTaskPolicyEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskPolicyMapper;
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

class TaskSkillConsumerPolicyTest {

	private final WorkTaskPolicyMapper mapper = mock(WorkTaskPolicyMapper.class);

	private final TenantUserDirectory users = mock(TenantUserDirectory.class);

	private final TaskSkillResolver legacy = mock(TaskSkillResolver.class);

	private final WorkTaskPolicyEntity policy = new WorkTaskPolicyEntity();

	private TaskSkillConsumerPolicy adapter;

	private final TaskDispatchSkillInputs dispatchInputs = mock(TaskDispatchSkillInputs.class);

	private final com.deepdimen.matrix.skill.api.SkillExecutionService executor = mock(
			com.deepdimen.matrix.skill.api.SkillExecutionService.class);

	private ObjectProvider<com.deepdimen.matrix.skill.api.SkillExecutionService> executors;

	private Consumer consumer(String slot) {
		return new Consumer("TASK", "7", slot, "default");
	}

	private BindingConfiguration config() {
		return new BindingConfiguration("9", "2", false, "{\"scope\":\"POLICY\"}", "EXPLICIT", "{}",
				"{\"mode\":\"NONE\"}");
	}

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setup() {
		TenantContextHolder.setTenantId(42L);
		authenticate("knowledge_skill_enable", "work_task_policy_manage");
		policy.setId(7L);
		policy.setTenantId(42L);
		policy.setEnabled(true);
		policy.setChatModelId(8L);
		policy.setParseSkillPackageId(11L);
		policy.setDispatchSkillPackageId(12L);
		when(mapper.selectOne(any())).thenReturn(policy);
		when(users.isAssignableUser(42L, 3L)).thenReturn(true);
		ObjectProvider<TaskSkillResolver> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(legacy);
		executors = mock(ObjectProvider.class);
		when(executors.getIfAvailable()).thenReturn(executor);
		ObjectProvider<TaskDispatchSkillInputs> dispatchProvider = mock(ObjectProvider.class);
		when(dispatchProvider.getIfAvailable()).thenReturn(dispatchInputs);
		adapter = new TaskSkillConsumerPolicy(mapper, users, provider, new ObjectMapper(), executors, dispatchProvider);
		when(legacy.resolve(42L, 11L))
			.thenReturn(new TaskSkillResolver.TaskSkill(11L, "v1", "parse instructions", "digest", "hash"));
		when(legacy.resolve(42L, 12L))
			.thenReturn(new TaskSkillResolver.TaskSkill(12L, "v1", "dispatch instructions", "digest", "hash"));
	}

	void authenticate(String... permissions) {
		var user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(3L);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a",
					java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
		org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
	}

	@Test
	void exposesTwoIndependentSlotsForTheExistingPolicyWithoutMutatingIt() {
		var targets = adapter.bindingTargets("42", "3", null);
		assertEquals(List.of("TASK_PARSE", "TASK_DISPATCH"),
				targets.stream().map(t -> t.consumer().slotCode()).toList());
		assertEquals(List.of("7", "7"), targets.stream().map(t -> t.consumer().id()).toList());
		for (var target : targets)
			adapter.authorizeBinding("42", "3", target.consumer(), config());
		assertTrue(adapter.bindingTargets("42", "3", "7").isEmpty());
		verify(mapper, never()).updateById(any(WorkTaskPolicyEntity.class));
	}

	@Test
	void requiresBothManagementPermissionsAndRejectsForeignOrMalformedTargets() {
		authenticate("knowledge_skill_enable");
		assertThrows(IllegalArgumentException.class, () -> adapter.bindingTargets("42", "3", null));
		authenticate("work_task_policy_manage");
		assertThrows(IllegalArgumentException.class, () -> adapter.bindingTargets("42", "3", null));
		authenticate("knowledge_skill_enable", "work_task_policy_manage");
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeBinding("43", "3", consumer("TASK_PARSE"), config()));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeBinding("42", "4", consumer("TASK_PARSE"), config()));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeBinding("42", "3", consumer("WORK_BRIEF"), config()));
		assertThrows(IllegalArgumentException.class, () -> adapter.authorizeBinding("42", "3",
				new Consumer("TASK", "007", "TASK_PARSE", "default"), config()));
		policy.setTenantId(43L);
		assertThrows(IllegalArgumentException.class, () -> adapter.bindingTargets("42", "3", null));
	}

	@Test
	void taskBindingsCannotExpandAudienceOverrideParametersOrEnableFallback() {
		for (var binding : List.of(
				new BindingConfiguration("9", "2", false, "{\"scope\":\"ALL\"}", "EXPLICIT", "{}",
						"{\"mode\":\"NONE\"}"),
				new BindingConfiguration("9", "2", false, "{\"scope\":\"POLICY\"}", "EXPLICIT", "{\"assigneeId\":1}",
						"{\"mode\":\"NONE\"}"),
				new BindingConfiguration("9", "2", false, "{\"scope\":\"POLICY\"}", "EXPLICIT", "{}",
						"{\"mode\":\"DETERMINISTIC\"}")))
			assertThrows(IllegalArgumentException.class,
					() -> adapter.authorizeBinding("42", "3", consumer("TASK_PARSE"), binding));
	}

	@Test
	void checksEachOriginalPackageAndDoesNotActivateAnIncompleteExecutor() {
		for (String slot : List.of("TASK_PARSE", "TASK_DISPATCH")) {
			String prompt = slot.equals("TASK_PARSE") ? "parse instructions" : "dispatch instructions";
			var release = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
					new ExecutorRef("PROMPT", null, "8", null),
					slot.equals("TASK_PARSE") ? TaskParseSkillContractValidatorTest.INPUT
							: TaskDispatchSkillContractValidatorTest.INPUT,
					slot.equals("TASK_PARSE") ? TaskParseSkillContractValidatorTest.OUTPUT
							: TaskDispatchSkillContractValidatorTest.OUTPUT,
					prompt, List.of(), "{}", List.of());
			assertEquals("EXECUTOR_UNAVAILABLE",
					assertThrows(IllegalArgumentException.class,
							() -> adapter.validateActivation("42", "3", consumer(slot), config(), release))
						.getMessage());
			assertThrows(IllegalArgumentException.class, () -> adapter
				.authorizeExecution(new Identity("42", "3", "3", "run"), consumer(slot), config(), release));
		}
		verify(legacy).resolve(42L, 11L);
		verify(legacy).resolve(42L, 12L);
		var changed = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, null, null), "{}", "{}", "changed", List.of(), "{}", List.of());
		assertEquals("TASK_SKILL_INSTRUCTION_DRIFT",
				assertThrows(IllegalArgumentException.class,
						() -> adapter.validateActivation("42", "3", consumer("TASK_PARSE"), config(), changed))
					.getMessage());
	}

	private ResolvedSkillSnapshot parseRelease(String model) {
		return new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, model, null), TaskParseSkillContractValidatorTest.INPUT,
				TaskParseSkillContractValidatorTest.OUTPUT, "parse instructions", List.of(), "{}", List.of());
	}

	private BindingConfiguration activeBinding() {
		return new BindingConfiguration("9", "2", true, "{\"scope\":\"POLICY\"}", "EXPLICIT", "{}",
				"{\"mode\":\"NONE\"}");
	}

	@Test
	void parseExecutionRequiresCreatePermissionRatherThanBindingManagementPermission() {
		policy.setParseSkillManagementMode("MANAGED");
		var actor = new Identity("42", "3", "3", "run");
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(actor, consumer("TASK_PARSE"), activeBinding(), parseRelease("8")));
		authenticate("work_task_create");
		assertDoesNotThrow(
				() -> adapter.authorizeExecution(actor, consumer("TASK_PARSE"), activeBinding(), parseRelease("8")));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeBinding("42", "3", consumer("TASK_PARSE"), config()));
	}

	@Test
	void executionRejectsLegacyDisabledCrossSubjectAndWrongFrozenModel() {
		authenticate("work_task_create");
		var actor = new Identity("42", "3", "3", "run");
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(actor, consumer("TASK_PARSE"), activeBinding(), parseRelease("8")));
		policy.setParseSkillManagementMode("MANAGED");
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(actor, consumer("TASK_PARSE"), config(), parseRelease("8")));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(new Identity("42", "3", "4", "run"), consumer("TASK_PARSE"),
						activeBinding(), parseRelease("8")));
		assertEquals("TASK_MODEL_BINDING_CHANGED", assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(actor, consumer("TASK_PARSE"), activeBinding(), parseRelease("99")))
			.getMessage());
		policy.setEnabled(false);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(actor, consumer("TASK_PARSE"), activeBinding(), parseRelease("8")));
	}

	@Test
	void executionRechecksTenantMembershipAndExactBindingRevision() {
		authenticate("work_task_create");
		policy.setParseSkillManagementMode("MANAGED");
		var actor = new Identity("42", "3", "3", "run");
		when(users.isAssignableUser(42L, 3L)).thenReturn(false);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(actor, consumer("TASK_PARSE"), activeBinding(), parseRelease("8")));
		when(users.isAssignableUser(42L, 3L)).thenReturn(true);
		TenantContextHolder.setTenantId(43L);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(actor, consumer("TASK_PARSE"), activeBinding(), parseRelease("8")));
		TenantContextHolder.setTenantId(42L);
		var wrong = new BindingConfiguration("10", "2", true, "{\"scope\":\"POLICY\"}", "EXPLICIT", "{}",
				"{\"mode\":\"NONE\"}");
		assertEquals("BINDING_REVISION_CONFLICT",
				assertThrows(IllegalArgumentException.class,
						() -> adapter.authorizeExecution(actor, consumer("TASK_PARSE"), wrong, parseRelease("8")))
					.getMessage());
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(actor, consumer("TASK_DISPATCH"), activeBinding(), parseRelease("8")));
	}

	@Test
	void activationMigrationRequiresTransactionAndUsesTheCurrentPolicyCompareAndSet() {
		policy.setVersion(4);
		when(executor.available(any(), any())).thenReturn(true);
		assertEquals("TASK_BINDING_TRANSACTION_REQUIRED", assertThrows(IllegalArgumentException.class,
				() -> adapter.bindingActivated("42", "3", consumer("TASK_PARSE"), activeBinding(), parseRelease("8")))
			.getMessage());
		verify(mapper, never()).markParseManaged(any(), any(), any(), any(), any());
		org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
		when(mapper.markParseManaged(42L, 7L, 4, 8L, 11L)).thenReturn(1).thenReturn(0);
		adapter.bindingActivated("42", "3", consumer("TASK_PARSE"), activeBinding(), parseRelease("8"));
		assertEquals("TASK_POLICY_VERSION_CONFLICT", assertThrows(IllegalArgumentException.class,
				() -> adapter.bindingActivated("42", "3", consumer("TASK_PARSE"), activeBinding(), parseRelease("8")))
			.getMessage());
		verify(mapper, times(2)).markParseManaged(42L, 7L, 4, 8L, 11L);
		verify(mapper, never()).markDispatchManaged(any(), any(), any(), any(), any());
	}

	@Test
	void disabledBindingDoesNotRollBackManagedModeAndMigrationRechecksFrozenModel() {
		org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
		policy.setParseSkillManagementMode("MANAGED");
		adapter.bindingActivated("42", "3", consumer("TASK_PARSE"), config(), parseRelease("8"));
		assertEquals("MANAGED", policy.getParseSkillManagementMode());
		assertThrows(IllegalArgumentException.class,
				() -> adapter.bindingActivated("42", "3", consumer("TASK_PARSE"), activeBinding(), parseRelease("99")));
		assertThrows(IllegalArgumentException.class, () -> adapter.bindingActivated("42", "3",
				consumer("TASK_DISPATCH"), activeBinding(), parseRelease("8")));
		verify(mapper, never()).markParseManaged(any(), any(), any(), any(), any());
	}

	@Test
	void parseCanActivateOnlyWithItsExactAvailableExecutor() {
		var release = parseRelease("8");
		assertThrows(IllegalArgumentException.class,
				() -> adapter.validateActivation("42", "3", consumer("TASK_PARSE"), activeBinding(), release));
		when(executor.available(consumer("TASK_PARSE"), release)).thenReturn(true);
		assertDoesNotThrow(
				() -> adapter.validateActivation("42", "3", consumer("TASK_PARSE"), activeBinding(), release));
		verify(executor, times(2)).available(consumer("TASK_PARSE"), release);
		when(executors.getIfAvailable()).thenReturn(null);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.validateActivation("42", "3", consumer("TASK_PARSE"), activeBinding(), release));
		verify(executor, never()).execute(any());
		verify(mapper, never()).markParseManaged(any(), any(), any(), any(), any());
	}

	@Test
	void executorDisappearingBeforeMigrationCannotChangeThePolicyMode() {
		org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
		when(executor.available(any(), any())).thenReturn(false);
		assertEquals("EXECUTOR_UNAVAILABLE", assertThrows(IllegalArgumentException.class,
				() -> adapter.bindingActivated("42", "3", consumer("TASK_PARSE"), activeBinding(), parseRelease("8")))
			.getMessage());
		verify(mapper, never()).markParseManaged(any(), any(), any(), any(), any());
	}

	@Test
	void backgroundIdentityNeedsAuthorizedBusinessInputAndDoesNotBypassExecutionChecks() {
		var identity = new Identity("42", "3", "3", "work-task-dispatch:10");
		assertFalse(adapter.permitsBackgroundExecution(identity, consumer("TASK_DISPATCH"), "10"));
		verifyZeroInteractions(dispatchInputs);
		SecurityContextHolder.clearContext();
		when(dispatchInputs.authorize(identity, consumer("TASK_DISPATCH"), "10"))
			.thenReturn(new com.deepdimen.matrix.work.entity.WorkTaskSkillInvocationEntity());
		assertTrue(adapter.permitsBackgroundExecution(identity, consumer("TASK_DISPATCH"), "10"));
		doThrow(new IllegalArgumentException("TASK_WORKER_LEASE_NOT_AUTHORIZED")).when(dispatchInputs)
			.authorize(any(), any(), any());
		assertFalse(adapter.permitsBackgroundExecution(identity, consumer("TASK_DISPATCH"), "10"));
		assertFalse(adapter.permitsBackgroundExecution(identity, consumer("TASK_PARSE"), "10"));
		assertThrows(IllegalArgumentException.class, () -> adapter.authorizeExecution(identity,
				consumer("TASK_DISPATCH"), activeBinding(), parseRelease("8")));
	}

	@Test
	void dispatchExecutionRequiresAuthorizedDurableInputAndMatchingCurrentPolicy() {
		SecurityContextHolder.clearContext();
		policy.setVersion(4);
		policy.setDispatchSkillManagementMode("MANAGED");
		var actor = new Identity("42", "3", "3", "work-task-dispatch:10");
		var record = new com.deepdimen.matrix.work.entity.WorkTaskSkillInvocationEntity();
		record.setTenantId(42L);
		record.setOwnerUserId(3L);
		record.setPolicyId(7L);
		record.setPolicyVersion(4);
		when(dispatchInputs.authorize(actor, consumer("TASK_DISPATCH"), "10")).thenReturn(record);
		var release = TaskDispatchSkillContractValidatorTest.release(TaskDispatchSkillContractValidatorTest.INPUT,
				TaskDispatchSkillContractValidatorTest.OUTPUT);
		assertDoesNotThrow(
				() -> adapter.authorizeExecution(actor, consumer("TASK_DISPATCH"), activeBinding(), release));
		policy.setVersion(5);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(actor, consumer("TASK_DISPATCH"), activeBinding(), release));
		policy.setVersion(4);
		policy.setDispatchSkillManagementMode("LEGACY");
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(actor, consumer("TASK_DISPATCH"), activeBinding(), release));
		policy.setDispatchSkillManagementMode("MANAGED");
		doThrow(new IllegalArgumentException("LEASE_LOST")).when(dispatchInputs).authorize(any(), any(), any());
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(actor, consumer("TASK_DISPATCH"), activeBinding(), release));
	}

	@Test
	void dispatchActivationUsesItsOwnPackageAndCompareAndSetWithinTransaction() {
		var release = TaskDispatchSkillContractValidatorTest.release(TaskDispatchSkillContractValidatorTest.INPUT,
				TaskDispatchSkillContractValidatorTest.OUTPUT);
		policy.setVersion(4);
		when(executor.available(consumer("TASK_DISPATCH"), release)).thenReturn(true);
		adapter.validateActivation("42", "3", consumer("TASK_DISPATCH"), activeBinding(), release);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.bindingActivated("42", "3", consumer("TASK_DISPATCH"), activeBinding(), release));
		org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
		when(mapper.markDispatchManaged(42L, 7L, 4, 8L, 12L)).thenReturn(1).thenReturn(0);
		adapter.bindingActivated("42", "3", consumer("TASK_DISPATCH"), activeBinding(), release);
		assertEquals("TASK_POLICY_VERSION_CONFLICT",
				assertThrows(IllegalArgumentException.class,
						() -> adapter.bindingActivated("42", "3", consumer("TASK_DISPATCH"), activeBinding(), release))
					.getMessage());
		verify(mapper, times(2)).markDispatchManaged(42L, 7L, 4, 8L, 12L);
		verify(mapper, never()).markParseManaged(any(), any(), any(), any(), any());
		verify(executor, never()).execute(any());
	}

	@Test
	void interactiveDispatchInspectionDoesNotGrantWorkerExecution() {
		authenticate("work_task_create");
		var release = TaskDispatchSkillContractValidatorTest.release(TaskDispatchSkillContractValidatorTest.INPUT,
				TaskDispatchSkillContractValidatorTest.OUTPUT);
		policy.setDispatchSkillManagementMode("MANAGED");
		var identity = new Identity("42", "3", "3", "task-capabilities:7");
		adapter.authorizeInspection(identity, consumer("TASK_DISPATCH"), activeBinding(), release);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeExecution(identity, consumer("TASK_DISPATCH"), activeBinding(), release));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeInspection(new Identity("42", "3", "4", "task-capabilities:7"),
						consumer("TASK_DISPATCH"), activeBinding(), release));
		policy.setDispatchSkillManagementMode("LEGACY");
		assertThrows(IllegalArgumentException.class,
				() -> adapter.authorizeInspection(identity, consumer("TASK_DISPATCH"), activeBinding(), release));
		verifyZeroInteractions(dispatchInputs);
	}

}
