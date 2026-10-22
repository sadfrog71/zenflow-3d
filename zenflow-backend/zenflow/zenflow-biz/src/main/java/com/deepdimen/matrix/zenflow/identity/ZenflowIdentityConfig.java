package com.deepdimen.matrix.zenflow.identity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

@Configuration
@EnableConfigurationProperties(ZenflowProperties.class)
public class ZenflowIdentityConfig {
    private final ZenflowProperties properties;
    public ZenflowIdentityConfig(ZenflowProperties properties) { this.properties = properties; }
    @PostConstruct public void validate() { properties.validate(); }
}
