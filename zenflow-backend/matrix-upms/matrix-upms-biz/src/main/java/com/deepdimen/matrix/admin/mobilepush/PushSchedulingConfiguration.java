package com.deepdimen.matrix.admin.mobilepush;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "matrix.mobile-push.enabled", havingValue = "true")
public class PushSchedulingConfiguration {

}
