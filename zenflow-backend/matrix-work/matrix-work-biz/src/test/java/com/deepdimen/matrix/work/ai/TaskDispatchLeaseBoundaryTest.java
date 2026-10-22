package com.deepdimen.matrix.work.ai;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.deepdimen.matrix.admin.api.entity.*;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.admin.mapper.*;
import com.deepdimen.matrix.admin.service.SysMenuService;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.api.service.TaskSkillResolver.TaskSkill;
import com.deepdimen.matrix.work.entity.*;
import com.deepdimen.matrix.work.mapper.*;
import com.deepdimen.matrix.work.service.WorkTaskV2Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TaskDispatchLeaseBoundaryTest {

	private final WorkTaskDispatchRunMapper runs = mock(WorkTaskDispatchRunMapper.class);

	private final WorkTaskMapper tasks = mock(WorkTaskMapper.class);

	private final WorkTaskV2Service commands = mock(WorkTaskV2Service.class);

	private final TenantUserDirectory directory = mock(TenantUserDirectory.class);

	private final TaskModelRunner models = mock(TaskModelRunner.class);

	private final TaskPolicyService policies = mock(TaskPolicyService.class);

	private final SysUserRoleMapper userRoles = mock(SysUserRoleMapper.class);

	private final SysMenuService menus = mock(SysMenuService.class);

	private final SysRoleMapper roles = mock(SysRoleMapper.class);

	private final TaskDispatchWorker worker = new TaskDispatchWorker(runs, tasks, commands, directory, models, policies,
			userRoles, menus, roles, new ObjectMapper());

	private final WorkTaskDispatchRunEntity run = new WorkTaskDispatchRunEntity();

	private final WorkTaskPolicyEntity policy = new WorkTaskPolicyEntity();

	@BeforeEach
	void setup() {
		for (Class<?> type : List.of(WorkTaskDispatchRunEntity.class, WorkTaskEntity.class, SysUserRole.class,
				SysRole.class))
			if (TableInfoHelper.getTableInfo(type) == null)
				TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "lease-test"), type);
		run.setId(8L);
		run.setTaskId(9L);
		run.setTenantId(1L);
		run.setCreatorId(2L);
		run.setInputTaskVersion(0);
		run.setPolicyVersion(4);
		run.setSkillPackageId(11L);
		run.setModelId(77L);
		var task = new WorkTaskEntity();
		task.setTenantId(1L);
		task.setCurrentDispatchRunId(8L);
		task.setStatus(WorkTaskStatus.DRAFT);
		task.setVersion(0);
		task.setTitle("T");
		task.setDescription("C");
		when(tasks.selectById(9L)).thenReturn(task);
		when(directory.isAssignableUser(1L, 2L)).thenReturn(true);
		var role = new SysUserRole();
		role.setRoleId(3L);
		when(userRoles.selectList(any())).thenReturn(List.of(role));
		when(roles.selectCount(any())).thenReturn(1L);
		var menu = new SysMenu();
		menu.setPermission("work_task_create");
		when(menus.findMenuByRoleId(3L)).thenReturn(List.of(menu));
		policy.setEnabled(true);
		policy.setVersion(4);
		when(policies.current(1L)).thenReturn(policy);
		when(directory.taskAuthority(1L, 2L))
			.thenReturn(new TenantUserDirectory.TaskAuthority(2L, 1, false, List.of()));
		when(directory.taskAuthority(1L, 4L))
			.thenReturn(new TenantUserDirectory.TaskAuthority(4L, 2, false, List.of()));
		when(directory.listTenantUsers(1L))
			.thenReturn(List.of(new TenantUserDirectory.AssignableUser(4L, "Name", "user", false)));
		when(models.resolveSkill(1L, 11L)).thenReturn(new TaskSkill(11L, "1", "prompt", "digest", "hash"));
	}

	@Test
	void managedSlotUsesManagedBridgeAndNeverCallsLegacyModel() throws Exception {
		var managed = mock(ManagedTaskDispatchWorker.class);
		org.springframework.test.util.ReflectionTestUtils.setField(worker, "managedDispatch", managed);
		policy.setDispatchSkillManagementMode("MANAGED");
		when(runs.update(isNull(), any())).thenReturn(1);
		worker.execute(run);
		verify(managed).execute(eq(run), matches("[a-f0-9-]{36}"));
		verifyZeroInteractions(models, commands);
	}

	@Test
	void managedBridgeFailureCannotFallBackToLegacyModel() throws Exception {
		var managed = mock(ManagedTaskDispatchWorker.class);
		org.springframework.test.util.ReflectionTestUtils.setField(worker, "managedDispatch", managed);
		policy.setDispatchSkillManagementMode("MANAGED");
		when(runs.update(isNull(), any())).thenReturn(1);
		doThrow(new IllegalStateException("unavailable")).when(managed).execute(any(), any());
		worker.execute(run);
		verifyZeroInteractions(models);
		verify(commands).failDispatch(eq(1L), eq(8L), eq("AI_DISPATCH_REQUIRES_MANUAL"), anyString());
	}

	@Test void lostSnapshotWriteStopsBeforeGenerationAndDoesNotFailAnotherOwnersRun() throws Exception {
        when(runs.update(isNull(),any())).thenReturn(1).thenReturn(0);
        worker.execute(run);
        verify(models,never()).run(any(),any(),any(),any(),any(),any(),any(),any());
        verifyZeroInteractions(commands);
        var captured=org.mockito.ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(runs,times(2)).update(isNull(),captured.capture());
        String condition=captured.getAllValues().get(1).getSqlSegment();
        var fields=TableInfoHelper.getTableInfo(WorkTaskDispatchRunEntity.class).getFieldList();
        String owner=fields.stream().filter(f->f.getProperty().equals("leaseOwner")).findFirst().orElseThrow().getColumn();
        String until=fields.stream().filter(f->f.getProperty().equals("leaseUntil")).findFirst().orElseThrow().getColumn();
        assertTrue(condition.contains(owner+" ="),condition);assertTrue(condition.contains(until+" >"),condition);
    }

	@Test void managedOrUnknownDispatchModeNeverFallsBackToLegacyModel() throws Exception {
        when(runs.update(isNull(),any())).thenReturn(1);
        for(String mode:List.of("MANAGED","unknown")) {
            policy.setDispatchSkillManagementMode(mode);worker.execute(run);
        }
        verifyZeroInteractions(models);
        verify(commands,times(2)).failDispatch(eq(1L),eq(8L),eq("AI_DISPATCH_REQUIRES_MANUAL"),anyString());
    }

}
