package com.deepdimen.matrix.work.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "matrix.work.task-workers-enabled", havingValue = "true")
public class TaskSchedulingConfiguration {

}
