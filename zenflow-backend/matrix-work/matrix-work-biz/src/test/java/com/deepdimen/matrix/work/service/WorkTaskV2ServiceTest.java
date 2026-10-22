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

class WorkTaskV2ServiceTest {

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

	@Test
	void maintenanceInvalidatesInactiveTransferWithoutChangingExecutor() {
		var t = task(WorkTaskStatus.IN_PROGRESS);
		t.setPendingTransferId(99L);
		when(tasks.selectById(55L)).thenReturn(t);
		var transfer = new WorkTaskTransferEntity();
		transfer.setId(99L);
		transfer.setTaskId(55L);
		transfer.setTenantId(1L);
		transfer.setStatus("PENDING");
		transfer.setToUserId(30L);
		when(transfers.selectOne(any())).thenReturn(transfer);
		when(directory.isAssignableUser(1L, 30L)).thenReturn(false);
		service.maintain(1L, 55L);
		assertEquals("INVALIDATED", transfer.getStatus());
		assertNull(t.getPendingTransferId());
		assertEquals(10L, t.getPrimaryAssigneeId());
		verify(transfers).updateById(transfer);
	}

	@Test
	void maintenanceDoesNotRepeatExistingDailyReminder() {
		var t = task(WorkTaskStatus.IN_PROGRESS);
		t.setDueAt(LocalDateTime.now().minusDays(1));
		when(tasks.selectById(55L)).thenReturn(t);
		when(events.selectCount(any())).thenReturn(1L);
		service.maintain(1L, 55L);
		verify(events, never()).insert(any(WorkTaskEventEntity.class));
	}

	@Test
	void createDefaultsAcceptorToCreatorAndConvertsTimeWithoutCollapsingExecutor() {
		var result = service.create("create-key-1", create());
		assertEquals(10L, result.getOwnerId());
		assertEquals(20L, result.getPrimaryAssigneeId());
		assertEquals(LocalDateTime.parse("2026-09-18T10:00:00"), result.getDueAt());
		assertEquals(ZoneOffset.ofHours(8), result.getDueAtOffset().getOffset());
		ArgumentCaptor<WorkTaskEventEntity> event = ArgumentCaptor.forClass(WorkTaskEventEntity.class);
		verify(events).insert(event.capture());
		assertEquals("PENDING", event.getValue().getPublishStatus());
		assertNotNull(event.getValue().getResultJson());
	}

	@Test void createRejectsUpwardAssignment() {
        when(directory.taskAuthority(1L,20L)).thenReturn(new TenantUserDirectory.TaskAuthority(20L,1,false,List.of()));
        assertEquals("ASSIGNEE_NOT_ALLOWED",assertThrows(ResponseStatusException.class,()->service.create("create-key-1",create())).getReason());
        verify(tasks,never()).insert(any(WorkTaskEntity.class));
    }

	@Test
	void missingPermissionCannotMutateEvenOwnTask() {
		task(WorkTaskStatus.TODO);
		auth("work_task_view");
		assertThrows(ResponseStatusException.class,
				() -> service.command(55L, "start", null, "start-key-1", command()));
		verify(tasks, never()).updateById(any(WorkTaskEntity.class));
	}

	@Test
	void staleVersionCannotMutate() {
		task(WorkTaskStatus.TODO);
		var c = command();
		c.setExpectedVersion(2);
		assertEquals("TASK_VERSION_CONFLICT",
				assertThrows(ResponseStatusException.class, () -> service.command(55L, "start", null, "start-key-1", c))
					.getReason());
	}

	@Test
	void transferDoesNotReplaceExecutorUntilReceiverAccepts() {
		var t = task(WorkTaskStatus.IN_PROGRESS);
		var c = command();
		c.setToUserId(30L);
		c.setReason("交接");
		service.command(55L, "transfer", null, "transfer-key", c);
		assertEquals(10L, t.getPrimaryAssigneeId());
		assertNotNull(t.getPendingTransferId());
		ArgumentCaptor<WorkTaskTransferEntity> capture = ArgumentCaptor.forClass(WorkTaskTransferEntity.class);
		verify(transfers).insert(capture.capture());
		assertEquals("PENDING", capture.getValue().getStatus());
	}

	@Test
	void pendingTransferPreventsSubmitButAllowsProgress() {
		var t = task(WorkTaskStatus.IN_PROGRESS);
		t.setPendingTransferId(99L);
		assertFalse(service.allowedActions(t, actor).contains("submit"));
		assertTrue(service.allowedActions(t, actor).contains("progress"));
	}

	@Test
	void parentCannotSubmitWithUnclosedChild() {
		task(WorkTaskStatus.IN_PROGRESS);
		when(tasks.selectCount(any())).thenReturn(1L);
		var c = command();
		c.setContent("成果");
		assertEquals("SUBTASKS_NOT_CLOSED", assertThrows(ResponseStatusException.class,
				() -> service.command(55L, "submit", null, "submit-key-1", c))
			.getReason());
		verify(submissions, never()).insert(any(WorkTaskSubmissionEntity.class));
	}

	@Test
	void parentIsLockedBeforeChildForEveryMutation() {
		var t = task(WorkTaskStatus.TODO);
		t.setParentId(9L);
		var p = new WorkTaskEntity();
		p.setId(9L);
		when(tasks.lock(1L, 9L)).thenReturn(p);
		service.command(55L, "start", null, "start-key-1", command());
		var order = inOrder(tasks);
		order.verify(tasks).lock(1L, 9L);
		order.verify(tasks).lock(1L, 55L);
	}

	@Test
	void creatorCannotReplaceExecutorThroughContentUpdate() {
		task(WorkTaskStatus.TODO);
		var c = command();
		c.setReason("change");
		c.setPrimaryAssigneeId(30L);
		assertEquals("USE_RESPONSIBILITY_COMMAND", assertThrows(ResponseStatusException.class,
				() -> service.command(55L, "update", null, "update-key-1", c))
			.getReason());
	}

	@Test
	void closedTaskOffersNoActionsEvenAllPermissions() {
		assertTrue(service.allowedActions(task(WorkTaskStatus.DONE), actor).isEmpty());
	}

	@Test
	void readReceiptRejectsDifferentCommandBeforeWriting() {
		var e = new WorkTaskEventEntity();
		e.setRequestHash("different");
		when(events.selectOne(any())).thenReturn(e);
		assertEquals("IDEMPOTENCY_CONFLICT",
				assertThrows(ResponseStatusException.class, () -> service.create("create-key-1", create()))
					.getReason());
		verify(tasks, never()).insert(any(WorkTaskEntity.class));
	}

	@Test
	void oldMultiExecutorTaskOffersNoV2Mutation() {
		var t = task(WorkTaskStatus.TODO);
		t.setTaskContractVersion(1);
		assertTrue(service.allowedActions(t, actor).isEmpty());
	}

	@Test
	void receiverAcceptsAndOnlyThenOldAssignmentBecomesInactive() {
		var t = task(WorkTaskStatus.IN_PROGRESS);
		t.setPendingTransferId(99L);
		var transfer = new WorkTaskTransferEntity();
		transfer.setId(99L);
		transfer.setTenantId(1L);
		transfer.setTaskId(55L);
		transfer.setFromUserId(10L);
		transfer.setToUserId(30L);
		transfer.setStatus("PENDING");
		transfer.setVersion(0);
		when(transfers.selectOne(any())).thenReturn(transfer);
		var receiver = new Actor(1L, 30L, 200L, new TenantUserDirectory.TaskAuthority(30L, 2, false, List.of()));
		when(visibility.actor()).thenReturn(receiver);
		var old = new WorkTaskAssignmentEntity();
		old.setUserId(10L);
		old.setRole("ASSIGNEE");
		old.setAssignmentStatus("ACTIVE");
		when(assignments.selectList(any())).thenReturn(List.of(old));
		var c = command();
		c.setDecision("ACCEPT");
		c.setTransferVersion(0);
		c.setReason("接收");
		service.command(55L, "transfer-decision", 99L, "transfer-accept", c);
		assertEquals(30L, t.getPrimaryAssigneeId());
		assertNull(t.getPendingTransferId());
		assertEquals("ACCEPTED", transfer.getStatus());
		assertEquals("INACTIVE", old.getAssignmentStatus());
	}

	@Test
	void rejectedTransferPreservesExecutorAndClosesOnlyPendingRecord() {
		var t = task(WorkTaskStatus.IN_PROGRESS);
		t.setPendingTransferId(99L);
		var transfer = new WorkTaskTransferEntity();
		transfer.setId(99L);
		transfer.setTenantId(1L);
		transfer.setTaskId(55L);
		transfer.setFromUserId(10L);
		transfer.setToUserId(30L);
		transfer.setStatus("PENDING");
		transfer.setVersion(0);
		when(transfers.selectOne(any())).thenReturn(transfer);
		when(visibility.actor())
			.thenReturn(new Actor(1L, 30L, 200L, new TenantUserDirectory.TaskAuthority(30L, 2, false, List.of())));
		var c = command();
		c.setDecision("REJECT");
		c.setTransferVersion(0);
		c.setReason("无空闲");
		service.command(55L, "transfer-decision", 99L, "transfer-reject", c);
		assertEquals(10L, t.getPrimaryAssigneeId());
		assertEquals("REJECTED", transfer.getStatus());
		assertEquals(WorkTaskStatus.IN_PROGRESS, t.getStatus());
	}

	@Test
	void acceptorCanSelfAcceptButMustTargetCurrentSubmission() {
		var t = task(WorkTaskStatus.WAITING_ACCEPTANCE);
		t.setOwnerId(10L);
		t.setCurrentSubmissionId(66L);
		var sub = new WorkTaskSubmissionEntity();
		sub.setId(66L);
		when(submissions.selectOne(any())).thenReturn(sub);
		var c = command();
		c.setSubmissionId(66L);
		c.setDecision("PASS");
		service.command(55L, "accept", null, "acceptance-key", c);
		assertEquals(WorkTaskStatus.DONE, t.getStatus());
		assertEquals(10L, sub.getDecidedBy());
		assertNotNull(t.getClosedAt());
	}

	@Test
	void childCannotBeSplitAgain() {
		var t = task(WorkTaskStatus.TODO);
		t.setParentId(9L);
		var parent = new WorkTaskEntity();
		parent.setId(9L);
		when(tasks.lock(1L, 9L)).thenReturn(parent);
		var c = new WorkTaskChildrenDTO();
		c.setExpectedVersion(3);
		c.setChildren(List.of(create()));
		assertThrows(ResponseStatusException.class, () -> service.children(55L, "children-key", c));
		verify(tasks, never()).insert(any(WorkTaskEntity.class));
	}

	@Test
	void matchingIdempotencyKeyReturnsOriginalResponseAndDoesNotInsertAgain() {
		var request = create();
		var original = service.create("create-key-1", request);
		ArgumentCaptor<WorkTaskEventEntity> capture = ArgumentCaptor.forClass(WorkTaskEventEntity.class);
		verify(events).insert(capture.capture());
		when(events.selectOne(any())).thenReturn(capture.getValue());
		when(tasks.selectOne(any())).thenReturn(original);
		var replay = service.create("create-key-1", request);
		assertEquals(original.getId(), replay.getId());
		verify(tasks, times(1)).insert(any(WorkTaskEntity.class));
	}

	@Test
	void pendingReceiverCannotReadEventHistory() {
		var t = task(WorkTaskStatus.IN_PROGRESS);
		t.setPendingTransferId(99L);
		var tr = new WorkTaskTransferEntity();
		tr.setToUserId(30L);
		tr.setStatus("PENDING");
		when(transfers.selectOne(any())).thenReturn(tr);
		when(visibility.actor())
			.thenReturn(new Actor(1L, 30L, 200L, new TenantUserDirectory.TaskAuthority(30L, 2, false, List.of())));
		assertThrows(ResponseStatusException.class, () -> service.eventPage(55L, "CHANGE", 1, 20));
		verify(events, never()).selectPage(any(), any());
	}

	@Test
	void cancelAliasWithdrawsOnlyByOriginalExecutor() {
		var t = task(WorkTaskStatus.TODO);
		t.setPendingTransferId(99L);
		var tr = new WorkTaskTransferEntity();
		tr.setId(99L);
		tr.setFromUserId(10L);
		tr.setToUserId(30L);
		tr.setStatus("PENDING");
		tr.setVersion(0);
		when(transfers.selectOne(any())).thenReturn(tr);
		var c = command();
		c.setDecision("CANCEL");
		c.setTransferVersion(0);
		c.setReason("撤回");
		service.command(55L, "transfer-decision", 99L, "cancel-transfer", c);
		assertEquals("WITHDRAWN", tr.getStatus());
		assertEquals(10L, t.getPrimaryAssigneeId());
		assertNull(t.getPendingTransferId());
	}

}
