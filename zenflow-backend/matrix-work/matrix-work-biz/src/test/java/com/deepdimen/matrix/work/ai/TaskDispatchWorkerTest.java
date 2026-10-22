package com.deepdimen.matrix.work.ai;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.admin.mapper.SysUserRoleMapper;
import com.deepdimen.matrix.admin.service.SysMenuService;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.entity.*;
import com.deepdimen.matrix.work.mapper.*;
import com.deepdimen.matrix.work.service.WorkTaskV2Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class TaskDispatchWorkerTest {

	WorkTaskDispatchRunMapper runs = mock(WorkTaskDispatchRunMapper.class);

	WorkTaskMapper tasks = mock(WorkTaskMapper.class);

	WorkTaskV2Service commands = mock(WorkTaskV2Service.class);

	TenantUserDirectory directory = mock(TenantUserDirectory.class);

	TaskModelRunner models = mock(TaskModelRunner.class);

	TaskPolicyService policies = mock(TaskPolicyService.class);

	SysUserRoleMapper roles = mock(SysUserRoleMapper.class);

	SysMenuService menus = mock(SysMenuService.class);

	TaskDispatchWorker worker = new TaskDispatchWorker(runs, tasks, commands, directory, models, policies, roles, menus,
			mock(com.deepdimen.matrix.admin.mapper.SysRoleMapper.class), new ObjectMapper());

	WorkTaskDispatchRunEntity run;

	@BeforeEach
	void setup() {
		for (Class<?> type : java.util.List.of(WorkTaskDispatchRunEntity.class, WorkTaskEntity.class))
			if (TableInfoHelper.getTableInfo(type) == null)
				TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "worker-test"), type);
		run = new WorkTaskDispatchRunEntity();
		run.setId(8L);
		run.setTaskId(9L);
		run.setTenantId(1L);
		run.setCreatorId(2L);
		run.setInputTaskVersion(0);
	}

	@Test void losingClaimCannotCallModelOrMutateTask() throws Exception {
        when(runs.update(isNull(),any())).thenReturn(0);
        worker.execute(run);
        verifyNoMoreInteractions(tasks,models,commands);
    }

	@Test void supersededRunIsClosedWithoutCallingModel() throws Exception {
        when(runs.update(isNull(),any())).thenReturn(1);
        var task=new WorkTaskEntity();task.setTenantId(1L);task.setCurrentDispatchRunId(99L);task.setStatus(WorkTaskStatus.DRAFT);task.setVersion(0);
        when(tasks.selectById(9L)).thenReturn(task);
        worker.execute(run);
        ArgumentCaptor<LambdaUpdateWrapper> updates=ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(runs,times(2)).update(isNull(),updates.capture());
        assertTrue(updates.getAllValues().get(1).getSqlSet().contains("status"));
        assertTrue(updates.getAllValues().get(1).getParamNameValuePairs().containsValue("SUPERSEDED"));
        verifyNoMoreInteractions(models,commands);
    }

	@Test void revokedCreatorCannotReachModelAndFailureUsesClaimOwner() throws Exception {
        when(runs.update(isNull(),any())).thenReturn(1);
        var task=new WorkTaskEntity();task.setTenantId(1L);task.setCurrentDispatchRunId(8L);task.setStatus(WorkTaskStatus.DRAFT);task.setVersion(0);
        when(tasks.selectById(9L)).thenReturn(task);
        when(directory.isAssignableUser(1L,2L)).thenReturn(false);
        worker.execute(run);
        verify(commands).failDispatch(eq(1L),eq(8L),eq("AI_DISPATCH_REQUIRES_MANUAL"),matches("[a-f0-9-]{36}"));
        verifyNoMoreInteractions(models);
    }

}
