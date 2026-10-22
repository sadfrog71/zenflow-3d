package com.deepdimen.matrix.work.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepdimen.matrix.admin.api.service.BusinessMessagePublisher;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.work.api.dto.WorkTaskCreateDTO;
import com.deepdimen.matrix.work.api.dto.WorkTaskTransitionDTO;
import com.deepdimen.matrix.work.api.dto.WorkTaskUpdateDTO;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.entity.WorkTaskAssignmentEntity;
import com.deepdimen.matrix.work.entity.WorkTaskEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskAssignmentMapper;
import com.deepdimen.matrix.work.mapper.WorkTaskMapper;
import com.deepdimen.matrix.work.security.CurrentOrganizationResolver;
import com.deepdimen.matrix.work.security.WorkTaskAssignmentPolicy;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class WorkTaskServiceImplTest {

	private static final long TENANT_ID = 11L;

	private final WorkTaskMapper taskMapper = mock(WorkTaskMapper.class);

	private final WorkTaskAssignmentMapper assignmentMapper = mock(WorkTaskAssignmentMapper.class);

	private final WorkTaskAssignmentPolicy assignmentPolicy = mock(WorkTaskAssignmentPolicy.class);

	private final TenantUserDirectory tenantUserDirectory = mock(TenantUserDirectory.class);

	private final CurrentOrganizationResolver organizationResolver = mock(CurrentOrganizationResolver.class);

	@SuppressWarnings("unchecked")
	private final ObjectProvider<BusinessMessagePublisher> publisherProvider = mock(ObjectProvider.class);

	private final WorkTaskServiceImpl service = new WorkTaskServiceImpl(assignmentPolicy, assignmentMapper,
			tenantUserDirectory, organizationResolver, publisherProvider);

	@BeforeEach
	void setUp() {
		if (TableInfoHelper.getTableInfo(WorkTaskEntity.class) == null) {
			TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "work-task-test"),
					WorkTaskEntity.class);
		}
		TenantContextHolder.setTenantId(TENANT_ID);
		authenticate(1L);
		ReflectionTestUtils.setField(service, "baseMapper", taskMapper);
		when(organizationResolver.resolve()).thenReturn(10L);
		when(assignmentMapper.selectList(any())).thenReturn(List.of());
		when(publisherProvider.getIfAvailable()).thenReturn(null);
		doAnswer(invocation -> {
			WorkTaskEntity task = invocation.getArgument(0);
			task.setId(100L);
			return 1;
		}).when(taskMapper).insert(any(WorkTaskEntity.class));
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
		TenantContextHolder.clear();
	}

	@Test
	void persistsOwnerAndExecutorRolesFromTheServerContract() {
		WorkTaskCreateDTO request = new WorkTaskCreateDTO();
		request.setTitle("复核真机体验");
		request.setOwnerId(2L);
		request.setAssigneeIds(List.of(3L, 4L));

		WorkTaskEntity task = service.create(request);

		assertEquals(List.of(3L, 4L), task.getAssigneeIds());
		assertEquals(10L, task.getOrganizationId());
		verify(assignmentPolicy).requireAssignable(2L, List.of(3L, 4L), 10L);
		ArgumentCaptor<WorkTaskAssignmentEntity> assignment = ArgumentCaptor.forClass(WorkTaskAssignmentEntity.class);
		verify(assignmentMapper, org.mockito.Mockito.times(3)).insert(assignment.capture());
		assertEquals(List.of("OWNER", "ASSIGNEE", "ASSIGNEE"),
				assignment.getAllValues().stream().map(WorkTaskAssignmentEntity::getRole).toList());
		assertEquals(List.of(2L, 3L, 4L),
				assignment.getAllValues().stream().map(WorkTaskAssignmentEntity::getUserId).toList());
	}

	@Test
	void onlyExecutorCanSubmitAndOnlyOwnerCanAccept() {
		WorkTaskEntity task = task(100L, WorkTaskStatus.IN_PROGRESS, 1L, 2L);
		when(taskMapper.selectById(100L)).thenReturn(task);
		when(assignmentMapper.selectList(any())).thenReturn(List.of(assignment(100L, 1L)));
		when(taskMapper.updateById(any(WorkTaskEntity.class))).thenReturn(1);

		WorkTaskTransitionDTO submit = new WorkTaskTransitionDTO();
		submit.setTargetStatus(WorkTaskStatus.WAITING_ACCEPTANCE);
		assertEquals(WorkTaskStatus.WAITING_ACCEPTANCE, service.transition(100L, submit).getStatus());

		task.setStatus(WorkTaskStatus.WAITING_ACCEPTANCE);
		WorkTaskTransitionDTO accept = new WorkTaskTransitionDTO();
		accept.setTargetStatus(WorkTaskStatus.DONE);
		assertThrows(IllegalArgumentException.class, () -> service.transition(100L, accept));

		authenticate(2L);
		assertEquals(WorkTaskStatus.DONE, service.transition(100L, accept).getStatus());
	}

	@Test
	void completesCreateViewForwardCloseAndDeleteLifecycle() {
		when(taskMapper.updateById(any(WorkTaskEntity.class))).thenReturn(1);
		when(taskMapper.deleteById(100L)).thenReturn(1);
		when(assignmentMapper.delete(any())).thenReturn(1);

		WorkTaskCreateDTO create = new WorkTaskCreateDTO();
		create.setTitle("移动端任务闭环验收");
		create.setOwnerId(2L);
		create.setAssigneeIds(List.of(3L));
		WorkTaskEntity created = service.create(create);
		assertEquals(100L, created.getId());
		when(taskMapper.selectById(100L)).thenReturn(created);
		assertEquals("移动端任务闭环验收", service.requireVisibleTask(100L).getTitle());

		WorkTaskAssignmentEntity previousOwner = assignment(100L, 2L, "OWNER");
		WorkTaskAssignmentEntity previousAssignee = assignment(100L, 3L, "ASSIGNEE");
		when(assignmentMapper.selectList(any())).thenReturn(List.of(previousOwner, previousAssignee));
		WorkTaskUpdateDTO forward = new WorkTaskUpdateDTO();
		forward.setTitle(created.getTitle());
		forward.setDescription("改派给新的执行与验收人员");
		forward.setPriority("HIGH");
		forward.setOwnerId(4L);
		forward.setAssigneeIds(List.of(5L));
		WorkTaskEntity forwarded = service.update(100L, forward);
		assertEquals(4L, forwarded.getOwnerId());
		assertEquals(List.of(5L), forwarded.getAssigneeIds());
		assertEquals("INACTIVE", previousOwner.getAssignmentStatus());
		assertEquals("INACTIVE", previousAssignee.getAssignmentStatus());
		verify(assignmentMapper, org.mockito.Mockito.times(2)).updateById(any(WorkTaskAssignmentEntity.class));
		ArgumentCaptor<WorkTaskAssignmentEntity> lifecycleAssignments = ArgumentCaptor
			.forClass(WorkTaskAssignmentEntity.class);
		verify(assignmentMapper, org.mockito.Mockito.times(4)).insert(lifecycleAssignments.capture());
		assertEquals(List.of("OWNER", "ASSIGNEE", "OWNER", "ASSIGNEE"),
				lifecycleAssignments.getAllValues().stream().map(WorkTaskAssignmentEntity::getRole).toList());
		assertEquals(List.of(2L, 3L, 4L, 5L),
				lifecycleAssignments.getAllValues().stream().map(WorkTaskAssignmentEntity::getUserId).toList());

		authenticate(4L);
		WorkTaskTransitionDTO close = new WorkTaskTransitionDTO();
		close.setTargetStatus(WorkTaskStatus.DONE);
		assertEquals(WorkTaskStatus.DONE, service.transition(100L, close).getStatus());

		authenticate(1L);
		service.deleteTask(100L);
		verify(assignmentMapper).delete(any());
		verify(taskMapper).deleteById(100L);
	}

	@Test
	void legacyReadCannotBypassRevokedV2ViewPermission() {
		WorkTaskEntity task = task(100L, WorkTaskStatus.TODO, 1L, 1L);
		task.setTaskContractVersion(2);
		when(taskMapper.selectById(100L)).thenReturn(task);
		assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> service.requireVisibleTask(100L));
	}

	@Test
	void deniesTaskAccessAfterSwitchingOrganizationContext() {
		WorkTaskEntity task = task(100L, WorkTaskStatus.TODO, 1L, 1L);
		when(taskMapper.selectById(100L)).thenReturn(task);
		when(organizationResolver.resolve()).thenReturn(20L);

		assertThrows(IllegalArgumentException.class, () -> service.requireVisibleTask(100L));
		assertThrows(IllegalArgumentException.class, () -> service.deleteTask(100L));

		WorkTaskUpdateDTO update = new WorkTaskUpdateDTO();
		update.setTitle(task.getTitle());
		update.setPriority("NORMAL");
		update.setOwnerId(1L);
		assertThrows(IllegalArgumentException.class, () -> service.update(100L, update));

		WorkTaskTransitionDTO transition = new WorkTaskTransitionDTO();
		transition.setTargetStatus(WorkTaskStatus.DONE);
		assertThrows(IllegalArgumentException.class, () -> service.transition(100L, transition));
	}

	@Test
	void preservesOptionalFieldsWhenUpdateOmitsThem() {
		WorkTaskEntity task = task(100L, WorkTaskStatus.TODO, 1L, 2L);
		task.setDescription("原任务说明");
		task.setPriority("HIGH");
		task.setDueAt(LocalDateTime.of(2026, 9, 5, 17, 30));
		when(taskMapper.selectById(100L)).thenReturn(task);
		when(taskMapper.updateById(any(WorkTaskEntity.class))).thenReturn(1);

		WorkTaskUpdateDTO update = new WorkTaskUpdateDTO();
		update.setTitle("仅更新标题和负责人");
		update.setOwnerId(3L);

		WorkTaskEntity updated = service.update(100L, update);

		assertEquals("原任务说明", updated.getDescription());
		assertEquals("HIGH", updated.getPriority());
		assertEquals(LocalDateTime.of(2026, 9, 5, 17, 30), updated.getDueAt());
	}

	@Test
	void deniesDeletionByNonCreatorInTheSameOrganization() {
		WorkTaskEntity task = task(100L, WorkTaskStatus.TODO, 2L, 1L);
		when(taskMapper.selectById(100L)).thenReturn(task);

		assertThrows(IllegalArgumentException.class, () -> service.deleteTask(100L));
		verify(assignmentMapper, never()).delete(any());
		verify(taskMapper, never()).deleteById(100L);
	}

	@Test
	void appliesOrganizationScopeToMineAndAssignedTaskPages() {
		doAnswer(invocation -> invocation.getArgument(0)).when(taskMapper).selectPage(any(Page.class), any());

		service.pageForCurrentUser(new Page<>(1, 20), "mine", null, null);
		service.pageForCurrentUser(new Page<>(1, 20), "assigned", null, null);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Wrapper<WorkTaskEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(taskMapper, org.mockito.Mockito.times(2)).selectPage(any(Page.class), wrapperCaptor.capture());
		wrapperCaptor.getAllValues().forEach(wrapper -> {
			AbstractWrapper<?, ?, ?> query = (AbstractWrapper<?, ?, ?>) wrapper;
			query.getSqlSegment();
			assertEquals(true, query.getParamNameValuePairs().containsValue(10L));
		});
	}

	private void authenticate(Long userId) {
		MatrixUser user = new MatrixUser(userId, "admin", List.of(), 10L, List.of(10L), List.of(), null, null, "admin",
				"admin", null, TENANT_ID, "password", true, true, "TOB", true, null, true, List.of());
		SecurityContextHolder.getContext()
			.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
	}

	private WorkTaskEntity task(Long id, WorkTaskStatus status, Long creatorId, Long ownerId) {
		WorkTaskEntity task = new WorkTaskEntity();
		task.setId(id);
		task.setStatus(status);
		task.setCreatorId(creatorId);
		task.setOwnerId(ownerId);
		task.setOrganizationId(10L);
		task.setTitle("复核真机体验");
		task.setVersion(0);
		return task;
	}

	private WorkTaskAssignmentEntity assignment(Long taskId, Long userId) {
		return assignment(taskId, userId, "ASSIGNEE");
	}

	private WorkTaskAssignmentEntity assignment(Long taskId, Long userId, String role) {
		WorkTaskAssignmentEntity assignment = new WorkTaskAssignmentEntity();
		assignment.setTaskId(taskId);
		assignment.setUserId(userId);
		assignment.setRole(role);
		assignment.setAssignmentStatus("ACTIVE");
		return assignment;
	}

}
