package com.deepdimen.matrix.work.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.work.api.dto.*;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.entity.*;
import com.deepdimen.matrix.work.mapper.*;
import com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy;
import com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy.Actor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.*;
import java.util.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

class WorkTaskDispatchFinalizationTest {

	final WorkTaskMapper tasks = mock(WorkTaskMapper.class);

	final WorkTaskAssignmentMapper assignments = mock(WorkTaskAssignmentMapper.class);

	final WorkTaskTransferMapper transfers = mock(WorkTaskTransferMapper.class);

	final WorkTaskSubmissionMapper submissions = mock(WorkTaskSubmissionMapper.class);

	final WorkTaskEventMapper events = mock(WorkTaskEventMapper.class);

	final WorkTaskDispatchRunMapper runs = mock(WorkTaskDispatchRunMapper.class);

	final WorkTaskPolicyMapper policies = mock(WorkTaskPolicyMapper.class);

	final TenantUserDirectory directory = mock(TenantUserDirectory.class);

	final WorkTaskVisibilityPolicy visibility = mock(WorkTaskVisibilityPolicy.class);

	final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);

	final Actor actor = new Actor(1L, 10L, 100L, new TenantUserDirectory.TaskAuthority(10L, 2, false, List.of()));

	WorkTaskV2Service service;

	@BeforeEach
	void setup() {
		for (Class<?> type : List.of(WorkTaskEntity.class, WorkTaskAssignmentEntity.class, WorkTaskTransferEntity.class,
				WorkTaskSubmissionEntity.class, WorkTaskEventEntity.class, WorkTaskDispatchRunEntity.class,
				WorkTaskPolicyEntity.class))
			if (TableInfoHelper.getTableInfo(type) == null)
				TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "v2"), type);
		TenantContextHolder.setTenantId(1L);
		auth("work_task_view", "work_task_create", "work_task_execute", "work_task_transfer", "work_task_accept",
				"work_task_nudge");
		when(visibility.actor()).thenReturn(actor);
		when(visibility.query(any()))
			.thenAnswer(i -> com.baomidou.mybatisplus.core.toolkit.Wrappers.<WorkTaskEntity>lambdaQuery()
				.eq(WorkTaskEntity::getTenantId, 1L));
		when(visibility.managedUsers(any())).thenReturn(new HashSet<>());
		when(directory.isAssignableUser(eq(1L), anyLong())).thenReturn(true);
		when(directory.taskAuthority(eq(1L), anyLong()))
			.thenAnswer(i -> new TenantUserDirectory.TaskAuthority(i.getArgument(1), 2, false, List.of()));
		when(assignments.selectList(any())).thenReturn(List.of());
		when(tasks.insert(any(WorkTaskEntity.class))).thenReturn(1);
		when(tasks.updateById(any(WorkTaskEntity.class))).thenAnswer(i -> {
			WorkTaskEntity t = i.getArgument(0);
			t.setVersion(t.getVersion() + 1);
			return 1;
		});
		when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		service = new WorkTaskV2Service(tasks, assignments, transfers, submissions, events, runs, policies, directory,
				visibility, new ObjectMapper().registerModule(new JavaTimeModule()), tx, mock(WorkTaskFileMapper.class),
				mock(org.springframework.beans.factory.ObjectProvider.class));
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	void auth(String... permissions) {
		SecurityContextHolder.getContext()
			.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("actor", null,
					Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList()));
	}

	WorkTaskEntity task(WorkTaskStatus status) {
		var t = new WorkTaskEntity();
		t.setId(55L);
		t.setTenantId(1L);
		t.setOrganizationId(100L);
		t.setCreatorId(10L);
		t.setOwnerId(20L);
		t.setPrimaryAssigneeId(10L);
		t.setVersion(3);
		t.setTaskContractVersion(2);
		t.setContentVersion(1);
		t.setStatus(status);
		t.setTitle("task");
		t.setDescription("content");
		t.setDueAt(LocalDateTime.now().plusDays(1));
		when(tasks.selectOne(any())).thenReturn(t);
		when(tasks.lock(1L, 55L)).thenReturn(t);
		return t;
	}

	WorkTaskCommandDTO command() {
		var c = new WorkTaskCommandDTO();
		c.setExpectedVersion(3);
		return c;
	}

	WorkTaskV2CreateDTO create() {
		var c = new WorkTaskV2CreateDTO();
		c.setTitle("task");
		c.setContent("content");
		c.setPrimaryAssigneeId(20L);
		c.setDueAt(OffsetDateTime.parse("2026-09-18T02:00:00Z"));
		return c;
	}

	WorkTaskDispatchRunEntity run() {
		var task = task(WorkTaskStatus.DRAFT);
		task.setCurrentDispatchRunId(8L);
		when(tasks.selectById(55L)).thenReturn(task);
		var run = new WorkTaskDispatchRunEntity();
		run.setId(8L);
		run.setTenantId(1L);
		run.setTaskId(55L);
		run.setCreatorId(10L);
		run.setInputTaskVersion(3);
		run.setPolicyVersion(4);
		run.setModelId(77L);
		run.setSkillPackageId(11L);
		run.setStatus("RUNNING");
		run.setLeaseOwner("owner");
		run.setLeaseUntil(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusMinutes(1));
		when(runs.selectById(8L)).thenReturn(run);
		when(runs.selectOne(any())).thenReturn(run);
		when(runs.update(isNull(), any())).thenReturn(1);
		var policy = new WorkTaskPolicyEntity();
		policy.setEnabled(true);
		policy.setVersion(4);
		policy.setChatModelId(77L);
		policy.setDispatchSkillPackageId(11L);
		when(policies.selectOne(any())).thenReturn(policy);
		return run;
	}

	@Test
	void successfulFinalizationLocksFreshRunAndPolicyBeforeAtomicAssignment() {
		run();
		service.completeDispatch(1L, 8L, 20L, Map.of("leaseOwner", "owner"));
		var order = inOrder(tasks, runs, policies);
		order.verify(runs).selectById(8L);
		order.verify(tasks).selectById(55L);
		order.verify(tasks).lock(1L, 55L);
		order.verify(runs).selectOne(any());
		order.verify(policies).selectOne(any());
		order.verify(runs).update(isNull(), any());
		order.verify(tasks).updateById(any(WorkTaskEntity.class));
		verify(tx).commit(any());
	}

	@Test
	void takeoverBetweenInitialReadAndTaskLockCannotAssignOrFailNewOwner() {
		var old = run();
		var fresh = new WorkTaskDispatchRunEntity();
		org.springframework.beans.BeanUtils.copyProperties(old, fresh);
		fresh.setLeaseOwner("other");
		when(runs.selectOne(any())).thenReturn(fresh);
		assertEquals("AI_RESULT_STALE", assertThrows(ResponseStatusException.class,
				() -> service.completeDispatch(1L, 8L, 20L, Map.of("leaseOwner", "owner")))
			.getReason());
		service.failDispatch(1L, 8L, "failed", "owner");
		verify(tasks, never()).updateById(any(WorkTaskEntity.class));
		verify(runs, never()).update(isNull(), any());
	}

	@Test
	void changedPolicyPreventsAssignment() {
		run();
		var changed = new WorkTaskPolicyEntity();
		changed.setEnabled(true);
		changed.setVersion(5);
		when(policies.selectOne(any())).thenReturn(changed);
		assertEquals("POLICY_CHANGED", assertThrows(ResponseStatusException.class,
				() -> service.completeDispatch(1L, 8L, 20L, Map.of("leaseOwner", "owner")))
			.getReason());
		verify(tasks, never()).updateById(any(WorkTaskEntity.class));
		verify(runs, never()).update(isNull(), any());
	}

	@Test
	void expiredLeaseCannotFinalizeEvenWithMatchingOwner() {
		var run = run();
		run.setLeaseUntil(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusSeconds(1));
		assertThrows(ResponseStatusException.class,
				() -> service.completeDispatch(1L, 8L, 20L, Map.of("leaseOwner", "owner")));
		service.failDispatch(1L, 8L, "failed", "owner");
		verify(tasks, never()).updateById(any(WorkTaskEntity.class));
	}

	@Test
	void failedRunCasCannotMutateTaskOrEmitEvent() {
		run();
		when(runs.update(isNull(), any())).thenReturn(0);
		assertThrows(ResponseStatusException.class,
				() -> service.completeDispatch(1L, 8L, 20L, Map.of("leaseOwner", "owner")));
		service.failDispatch(1L, 8L, "failed", "owner");
		verify(tasks, never()).updateById(any(WorkTaskEntity.class));
		verify(events, never()).insert(any(WorkTaskEventEntity.class));
		verify(tx).rollback(any());
	}

	@Test
	void taskConflictRollsBackRunTransition() {
		run();
		when(tasks.updateById(any(WorkTaskEntity.class))).thenReturn(0);
		assertThrows(ResponseStatusException.class,
				() -> service.completeDispatch(1L, 8L, 20L, Map.of("leaseOwner", "owner")));
		verify(runs).update(isNull(), any());
		verify(tx).rollback(any());
		verify(tx, never()).commit(any());
	}

}
