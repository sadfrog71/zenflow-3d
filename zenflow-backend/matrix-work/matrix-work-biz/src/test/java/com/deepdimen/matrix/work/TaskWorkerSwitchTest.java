package com.deepdimen.matrix.work;

import com.deepdimen.matrix.work.notification.TaskMaintenanceWorker;
import com.deepdimen.matrix.work.notification.TaskNotificationWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TaskWorkerSwitchTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(TaskNotificationWorker.class, TaskMaintenanceWorker.class);

    @Test
    void dispatchCanBeEnabledWithoutSendingQueuedNotificationsOrRunningMaintenance() {
        context.withPropertyValues("matrix.work.task-workers-enabled=true",
            "matrix.work.task-notifications-enabled=false", "matrix.work.task-maintenance-enabled=false")
            .run(result -> {
                assertThat(result).hasNotFailed();
                assertThat(result).doesNotHaveBean(TaskNotificationWorker.class);
                assertThat(result).doesNotHaveBean(TaskMaintenanceWorker.class);
            });
    }

    @Test
    void globalOffStillDisablesBothWorkers() {
        context.withPropertyValues("matrix.work.task-workers-enabled=false",
            "matrix.work.task-notifications-enabled=true", "matrix.work.task-maintenance-enabled=true")
            .run(result -> {
                assertThat(result).hasNotFailed();
                assertThat(result).doesNotHaveBean(TaskNotificationWorker.class);
                assertThat(result).doesNotHaveBean(TaskMaintenanceWorker.class);
            });
    }
}
