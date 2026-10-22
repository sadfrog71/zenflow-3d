package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.entity.*;
import com.deepdimen.matrix.work.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class TaskDispatchSkillInputsTest {

	private final WorkTaskDispatchRunMapper runs = mock(WorkTaskDispatchRunMapper.class);

	private final TaskDispatchCreatorAuthorization creators = mock(TaskDispatchCreatorAuthorization.class);

	private final WorkTaskMapper tasks = mock(WorkTaskMapper.class);

	private final WorkTaskPolicyMapper policies = mock(WorkTaskPolicyMapper.class);

	private final WorkTaskSkillInvocationMapper invocations = mock(WorkTaskSkillInvocationMapper.class);

	private final TenantUserDirectory users = mock(TenantUserDirectory.class);

	private final TaskDispatchWorkerScope scope = new TaskDispatchWorkerScope(runs, creators,
			Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC));

	private final TaskDispatchSkillInputs inputs = new TaskDispatchSkillInputs(scope, tasks, policies, invocations,
			users, new ObjectMapper());

	private final WorkTaskDispatchRunEntity run = new WorkTaskDispatchRunEntity();

	private final WorkTaskEntity task = new WorkTaskEntity();

	private final WorkTaskPolicyEntity policy = new WorkTaskPolicyEntity();

	private WorkTaskSkillInvocationEntity saved;

	private TaskDispatchWorkerScope.Lease lease;

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(42L);
		SecurityContextHolder.clearContext();
		run.setId(8L);
		run.setTenantId(42L);
		run.setTaskId(9L);
		run.setCreatorId(3L);
		run.setStatus("RUNNING");
		run.setLeaseOwner("owner");
		run.setLeaseUntil(LocalDateTime.of(2026, 9, 26, 16, 1));
		run.setPolicyVersion(4);
		run.setInputTaskVersion(5);
		run.setModelId(77L);
		run.setSkillPackageId(11L);
		when(runs.selectOne(any())).thenReturn(run);
		when(creators.authorized(42L, 3L)).thenReturn(true);
		task.setId(9L);
		task.setTenantId(42L);
		task.setOrganizationId(2L);
		task.setStatus(WorkTaskStatus.DRAFT);
		task.setCurrentDispatchRunId(8L);
		task.setVersion(5);
		task.setTitle("T");
		task.setDescription("C");
		when(tasks.selectOne(any())).thenReturn(task);
		policy.setId(7L);
		policy.setTenantId(42L);
		policy.setEnabled(true);
		policy.setDispatchSkillManagementMode("MANAGED");
		policy.setVersion(4);
		policy.setChatModelId(77L);
		policy.setDispatchSkillPackageId(11L);
		when(policies.selectOne(any())).thenReturn(policy);
		when(users.taskAuthority(42L, 3L)).thenReturn(new TenantUserDirectory.TaskAuthority(3L, 1, false, List.of()));
		when(users.taskAuthority(42L, 4L)).thenReturn(new TenantUserDirectory.TaskAuthority(4L, 2, false, List.of()));
		when(users.isAssignableUser(42L, 4L)).thenReturn(true);
		when(users.listTenantUsers(42L))
			.thenReturn(List.of(new TenantUserDirectory.AssignableUser(4L, "Name", "name", false)));
		when(invocations.findDispatch(42L, 8L)).thenAnswer(call -> saved);
		when(invocations.insert(any(WorkTaskSkillInvocationEntity.class))).thenAnswer(call -> {
			saved = call.getArgument(0);
			saved.setId(10L);
			return 1;
		});
		lease = scope.open(42L, 8L, "owner");
	}

	@AfterEach
	void clear() {
		lease.close();
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void preparationFreezesServerInputAndReusesSameInvocationOnRetry() {
		var first = inputs.prepare();
		String original = first.getInputSnapshot();
		when(users.listTenantUsers(42L)).thenReturn(List.of());
		var again = inputs.prepare();
		assertEquals(10L, again.getId());
		assertEquals(original, again.getInputSnapshot());
		assertEquals(8L, again.getDispatchRunId());
		assertEquals(4, again.getPolicyVersion());
		assertEquals(77L, again.getModelId());
		verify(invocations, times(1)).insert(any(WorkTaskSkillInvocationEntity.class));
		assertSame(first, inputs.require(10L));
		assertThrows(IllegalArgumentException.class, () -> inputs.require(99L));
	}

	@Test
	void taskPolicyAndCandidateChangesInvalidateRatherThanRegenerateInput() {
		inputs.prepare();
		policy.setVersion(5);
		assertThrows(IllegalArgumentException.class, () -> inputs.require(10L));
		policy.setVersion(4);
		task.setVersion(6);
		assertThrows(IllegalArgumentException.class, () -> inputs.require(10L));
		task.setVersion(5);
		when(users.isAssignableUser(42L, 4L)).thenReturn(false);
		assertThrows(IllegalArgumentException.class, () -> inputs.require(10L));
		verify(invocations, times(1)).insert(any(WorkTaskSkillInvocationEntity.class));
	}

	@Test
	void lostLeaseWrongModeAndChangedInputDoNotGrantAuthorization() {
		policy.setDispatchSkillManagementMode("LEGACY");
		assertThrows(IllegalArgumentException.class, inputs::prepare);
		policy.setDispatchSkillManagementMode("MANAGED");
		var record = inputs.prepare();
		record.setInputSnapshot(record.getInputSnapshot().replace("\"T\"", "\"tampered\""));
		assertThrows(IllegalArgumentException.class, () -> inputs.require(10L));
		run.setLeaseOwner("new-owner");
		assertThrows(IllegalArgumentException.class, inputs::prepare);
	}

	@Test
	void duplicateInsertReusesTheExistingInvocationAndDoesNotReplaceItsInput() {
		var winner = inputs.prepare();
		reset(invocations);
		when(invocations.findDispatch(42L, 8L)).thenReturn(null).thenReturn(winner);
		when(invocations.insert(any(WorkTaskSkillInvocationEntity.class)))
			.thenThrow(new org.springframework.dao.DuplicateKeyException("duplicate"));
		assertSame(winner, inputs.prepare());
		verify(invocations, times(1)).insert(any(WorkTaskSkillInvocationEntity.class));
	}

	@Test
	void canonicalStringUserIdsRemainCompatibleWithPlatformLongSerialization() {
		var mapper = new ObjectMapper();
		var module = new com.fasterxml.jackson.databind.module.SimpleModule();
		module.addSerializer(Long.class, com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance);
		mapper.registerModule(module);
		var compatible = new TaskDispatchSkillInputs(scope, tasks, policies, invocations, users, mapper);
		var result = compatible.prepare();
		assertTrue(result.getInputSnapshot().contains("\"userId\":\"4\""));
		assertSame(result, compatible.require(10L));
		result.setInputSnapshot(result.getInputSnapshot().replace("\"userId\":\"4\"", "\"userId\":\"04\""));
		assertThrows(IllegalArgumentException.class, () -> compatible.require(10L));
	}

	@Test
	void callerIdentityMustMatchTheDurableInvocationAndTrustedScope() {
		var saved = inputs.prepare();
		var identity = new com.deepdimen.matrix.skill.api.SkillContract.Identity("42", "3", "3",
				"work-task-dispatch:10");
		var consumer = new com.deepdimen.matrix.skill.api.SkillContract.Consumer("TASK", "7", "TASK_DISPATCH",
				"default");
		assertSame(saved, inputs.authorize(identity, consumer, "10"));
		for (String id : List.of("010", "99", "-1"))
			assertThrows(IllegalArgumentException.class, () -> inputs.authorize(identity, consumer, id));
		for (var wrong : List.of(
				new com.deepdimen.matrix.skill.api.SkillContract.Identity("43", "3", "3", "work-task-dispatch:10"),
				new com.deepdimen.matrix.skill.api.SkillContract.Identity("42", "4", "4", "work-task-dispatch:10"),
				new com.deepdimen.matrix.skill.api.SkillContract.Identity("42", "3", "4", "work-task-dispatch:10"),
				new com.deepdimen.matrix.skill.api.SkillContract.Identity("42", "3", "3", "arbitrary")))
			assertThrows(IllegalArgumentException.class, () -> inputs.authorize(wrong, consumer, "10"));
		assertThrows(IllegalArgumentException.class, () -> inputs.authorize(identity,
				new com.deepdimen.matrix.skill.api.SkillContract.Consumer("TASK", "8", "TASK_DISPATCH", "default"),
				"10"));
		lease.close();
		assertThrows(IllegalArgumentException.class, () -> inputs.authorize(identity, consumer, "10"));
	}

}
