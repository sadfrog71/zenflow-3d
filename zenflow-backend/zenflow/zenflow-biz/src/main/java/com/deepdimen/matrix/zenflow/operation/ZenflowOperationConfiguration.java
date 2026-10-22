package com.deepdimen.matrix.zenflow.operation;

import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods=false)
@ConditionalOnExpression("${zenflow.garden.enabled:false} or ${zenflow.media.enabled:false}")
public class ZenflowOperationConfiguration {
    @Bean ZenflowOperationReceipts zenflowOperationReceipts(JdbcTemplate jdbc,ZenflowConsumerAccess access,ObjectMapper json){return new ZenflowOperationReceipts(jdbc,access,json);}
}
