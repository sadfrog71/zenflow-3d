package com.deepdimen.matrix.work.notification;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.deepdimen.matrix.admin.api.service.BusinessMessagePublisher;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.admin.mapper.SysUserMapper;
import com.deepdimen.matrix.admin.service.SysMessageService;
import com.deepdimen.matrix.work.ai.TaskPolicyService;
import com.deepdimen.matrix.work.entity.WorkTaskEventEntity;
import com.deepdimen.matrix.work.entity.WorkTaskPolicyEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class TaskNotificationWorkerTest {

	WorkTaskEventMapper events = mock(WorkTaskEventMapper.class);

	BusinessMessagePublisher publisher = mock(BusinessMessagePublisher.class);

	TenantUserDirectory directory = mock(TenantUserDirectory.class);

	TaskPolicyService policies = mock(TaskPolicyService.class);

	SysMessageService smtp = mock(SysMessageService.class);

	SysUserMapper users = mock(SysUserMapper.class);

	JdbcTemplate jdbc = mock(JdbcTemplate.class);

	TaskNotificationWorker worker = new TaskNotificationWorker(events, publisher, directory, policies, smtp, users,
			jdbc, new ObjectMapper());

	WorkTaskEventEntity event;

	@BeforeEach
	void setup() {
		if (TableInfoHelper.getTableInfo(WorkTaskEventEntity.class) == null)
			TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "notification-test"),
					WorkTaskEventEntity.class);
		event = new WorkTaskEventEntity();
		event.setId(8L);
		event.setTaskId(9L);
		event.setTenantId(1L);
		event.setType("TASK_CREATED");
		event.setAttempts(0);
		event.setPayloadJson("{\"recipientUserIds\":[\"2\"],\"title\":\"Sensitive task title\"}");
	}

	@Test void losingLeaseDoesNotPublish() throws Exception {
        when(events.update(isNull(),any())).thenReturn(0);worker.deliver(event);
        verifyNoMoreInteractions(publisher,smtp);
    }

	@Test void publisherFailureIsPersistedForRetryWithoutCallingMail() throws Exception {
        when(events.update(isNull(),any())).thenReturn(1);when(directory.isAssignableUser(1L,2L)).thenReturn(true);
        when(publisher.publish(any())).thenThrow(new IllegalStateException("unavailable"));
        worker.deliver(event);
        ArgumentCaptor<LambdaUpdateWrapper> updates=ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(events,times(2)).update(isNull(),updates.capture());
        assertTrue(updates.getAllValues().get(1).getParamNameValuePairs().containsValue("RETRY"));
        verifyNoMoreInteractions(smtp);
    }

	@Test void notificationNeverIncludesSensitiveTaskContentAndInactiveRecipientsAreOmitted() throws Exception {
        when(events.update(isNull(),any())).thenReturn(1);when(directory.isAssignableUser(1L,2L)).thenReturn(false);
        var policy=new WorkTaskPolicyEntity();policy.setEmailEnabled(false);when(policies.current(1L)).thenReturn(policy);
        worker.deliver(event);
        verifyNoMoreInteractions(publisher,smtp,users);
    }

}
