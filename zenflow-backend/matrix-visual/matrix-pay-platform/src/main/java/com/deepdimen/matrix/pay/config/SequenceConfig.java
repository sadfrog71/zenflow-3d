package com.deepdimen.matrix.pay.config;

import cn.hutool.core.date.DateUtil;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.sequence.builder.DbSeqBuilder;
import com.deepdimen.matrix.common.sequence.properties.BaseSequenceProperties;
import com.deepdimen.matrix.common.sequence.sequence.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author caoyuan
 * @date 2019-05-26
 * <p>
 * 设置发号器生成规则
 */
@Configuration
public class SequenceConfig {

    /**
     * 订单流水号发号器
     *
     * @param properties 配置
     * @return
     */
    @Bean
    public Sequence paySequence(BaseSequenceProperties properties) {
        return DbSeqBuilder.create()
                .bizName(() -> String.format("pay_%s_%s", TenantContextHolder.getTenantId(), DateUtil.today()))
                .step(properties.getStep())
                .retryTimes(properties.getDb().getRetryTimes())
                .tableName(properties.getDb().getTableName())
                .build();
    }

    /**
     * 通道编号发号器
     *
     * @param properties 配置
     * @return
     */
    @Bean
    public Sequence channelSequence(BaseSequenceProperties properties) {
        return DbSeqBuilder.create()
                .bizName(() -> String.format("channel_%s", TenantContextHolder.getTenantId()))
                .step(properties.getStep())
                .retryTimes(properties.getDb().getRetryTimes())
                .tableName(properties.getDb().getTableName())
                .build();
    }

}
