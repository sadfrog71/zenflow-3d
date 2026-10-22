/*
 *    Copyright (c) 2018-2025, deepdimen All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * Neither the name of the deepdimen.com developer nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * Author: caoyuan (caoyuanchn@gmail.com)
 */

package com.deepdimen.matrix.common.sentinel;

import com.alibaba.cloud.sentinel.feign.SentinelFeignAutoConfiguration;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.callback.RequestOriginParser;
import com.deepdimen.matrix.common.sentinel.feign.MatrixSentinelFeign;
import com.deepdimen.matrix.common.sentinel.handle.GlobalBizExceptionHandler;
import com.deepdimen.matrix.common.sentinel.handle.MatrixUrlBlockHandler;
import com.deepdimen.matrix.common.sentinel.parser.MatrixHeaderRequestOriginParser;
import feign.Feign;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

/**
 * @author caoyuan
 * @date 2020-02-12
 * <p>
 * sentinel 配置
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureBefore(SentinelFeignAutoConfiguration.class)
public class MatrixSentinelAutoConfiguration {

    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(name = "spring.cloud.openfeign.sentinel.enabled",matchIfMissing = true)
    public Feign.Builder feignSentinelBuilder() {
        return MatrixSentinelFeign.builder();
    }

    @Bean
    @ConditionalOnMissingBean
    /**
     * 创建一个块异常处理器实例
     * 如果没有定义块异常处理器，则创建一个MatrixUrlBlockHandler实例
     */
    public BlockExceptionHandler blockExceptionHandler() {
        return new MatrixUrlBlockHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    /**
     * 创建一个请求源解析器实例
     * 如果没有定义请求源解析器，则创建一个MatrixHeaderRequestOriginParser实例
     */
    public RequestOriginParser requestOriginParser() {
        return new MatrixHeaderRequestOriginParser();
    }

    /**
     * 创建一个全局业务异常处理器实例
     * 用于处理全局的业务异常
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public GlobalBizExceptionHandler globalBizExceptionHandler() {
        return new GlobalBizExceptionHandler();
    }


}
