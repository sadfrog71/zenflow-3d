package com.deepdimen.matrix.zenflow.capsule;

import com.deepdimen.matrix.capsule.mapper.CapsuleMapper;
import com.deepdimen.matrix.capsule.mapper.CapsuleRevisionMapper;
import com.deepdimen.matrix.capsule.service.CapsuleService;
import com.deepdimen.matrix.capsule.service.impl.CapsuleServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Assemble the core service without exposing inherited enterprise controllers. */
@Configuration(proxyBeanMethods=false)
public class ZenflowCapsuleConfiguration {
    // The reference data configuration is conditional on early DataSource registration.
    // Assemble its existing audit filler explicitly in the standalone product as well.
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(com.baomidou.mybatisplus.core.handlers.MetaObjectHandler.class)
    public com.deepdimen.matrix.common.data.mybatis.MybatisPlusMetaObjectHandler productAuditFields() {
        return new com.deepdimen.matrix.common.data.mybatis.MybatisPlusMetaObjectHandler();
    }
    @Bean public CapsuleService capsuleService(CapsuleMapper capsules,CapsuleRevisionMapper revisions){return new CapsuleServiceImpl(capsules,revisions);}
}
