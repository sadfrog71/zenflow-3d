package com.deepdimen.matrix.work.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Map;

import com.deepdimen.matrix.admin.api.service.BusinessMessagePublisher;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.admin.mapper.SysUserMapper;
import com.deepdimen.matrix.admin.service.SysMessageService;
import com.deepdimen.matrix.work.ai.TaskPolicyService;
import com.deepdimen.matrix.work.mapper.WorkTaskEventMapper;
import com.deepdimen.matrix.work.mapper.WorkTaskMapper;
import com.deepdimen.matrix.work.service.WorkTaskV2Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

/** Preserves the production worker gates without scheduling jobs or sending messages. */
class TaskWorkerActivationTest {

	@Test
	void allWorkersAreDisabledWhenNoPropertiesAreConfigured() {
		check(Map.of(), false, false);
	}

	@Test
	void explicitSubSwitchesCannotBypassAnAbsentMasterSwitch() {
		check(Map.of("matrix.work.task-notifications-enabled", true, "matrix.work.task-maintenance-enabled", true),
				false, false);
	}

	@Test
	void explicitSubSwitchesCannotBypassADisabledMasterSwitch() {
		check(Map.of("matrix.work.task-workers-enabled", false, "matrix.work.task-notifications-enabled", true,
				"matrix.work.task-maintenance-enabled", true), false, false);
	}

	@Test
	void enabledMasterKeepsTheExistingDefaultSubSwitches() {
		check(Map.of("matrix.work.task-workers-enabled", true), true, true);
	}

	@Test
	void notificationCanBeDisabledWithoutDisablingMaintenance() {
		check(Map.of("matrix.work.task-workers-enabled", true, "matrix.work.task-notifications-enabled", false), false,
				true);
	}

	@Test
	void maintenanceCanBeDisabledWithoutDisablingNotification() {
		check(Map.of("matrix.work.task-workers-enabled", true, "matrix.work.task-maintenance-enabled", false), true,
				false);
	}

	@Test
	void bothIndependentGatesCanRemainOffWithTheMasterEnabled() {
		check(Map.of("matrix.work.task-workers-enabled", true, "matrix.work.task-notifications-enabled", false,
				"matrix.work.task-maintenance-enabled", false), false, false);
	}

	@Test
	void bothExplicitlyEnabledWorkersAreRegistered() {
		check(Map.of("matrix.work.task-workers-enabled", true, "matrix.work.task-notifications-enabled", true,
				"matrix.work.task-maintenance-enabled", true), true, true);
	}

	private void check(Map<String, Object> properties, boolean notifications, boolean maintenance) {
		try (var context = new AnnotationConfigApplicationContext()) {
			context.getEnvironment()
				.getPropertySources()
				.addFirst(new MapPropertySource("synthetic-worker-gates", properties));
			context.registerBean(WorkTaskEventMapper.class, () -> mock(WorkTaskEventMapper.class));
			context.registerBean(WorkTaskMapper.class, () -> mock(WorkTaskMapper.class));
			context.registerBean(BusinessMessagePublisher.class, () -> mock(BusinessMessagePublisher.class));
			context.registerBean(TenantUserDirectory.class, () -> mock(TenantUserDirectory.class));
			context.registerBean(TaskPolicyService.class, () -> mock(TaskPolicyService.class));
			context.registerBean(SysMessageService.class, () -> mock(SysMessageService.class));
			context.registerBean(SysUserMapper.class, () -> mock(SysUserMapper.class));
			context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
			context.registerBean(WorkTaskV2Service.class, () -> mock(WorkTaskV2Service.class));
			context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
			context.register(TaskNotificationWorker.class, TaskMaintenanceWorker.class);
			// Deliberately do not import scheduling configuration or register scheduled
			// processors.
			context.refresh();
			assertEquals(notifications, !context.getBeansOfType(TaskNotificationWorker.class).isEmpty());
			assertEquals(maintenance, !context.getBeansOfType(TaskMaintenanceWorker.class).isEmpty());
		}
	}

}
