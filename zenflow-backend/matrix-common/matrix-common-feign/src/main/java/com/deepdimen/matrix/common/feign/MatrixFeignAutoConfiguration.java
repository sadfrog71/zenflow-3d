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

package com.deepdimen.matrix.common.feign;

import com.deepdimen.matrix.common.feign.endpoint.FeignClientEndpoint;
import com.deepdimen.matrix.common.feign.interceptor.MatrixFeignInnerRequestInterceptor;
import com.deepdimen.matrix.common.feign.interceptor.MatrixFeignRequestCloseInterceptor;
import feign.Feign;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.MatrixFeignClientsRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * @author caoyuan
 * @date 2020/2/8
 * <p>
 * feign 自动化配置
 */
@Configuration
@ConditionalOnClass(Feign.class)
@Import(MatrixFeignClientsRegistrar.class)
@AutoConfigureAfter(EnableFeignClients.class)
public class MatrixFeignAutoConfiguration {

    /**
     * feign actuator endpoint
     * @param context
     * @return FeignClientEndpoint
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnAvailableEndpoint
    public FeignClientEndpoint feignClientEndpoint(ApplicationContext context) {
        return new FeignClientEndpoint(context);
    }

    /**
     * add http connection close header
     *
     * @return MatrixFeignRequestCloseInterceptor
     */
    @Bean
    public MatrixFeignRequestCloseInterceptor matrixFeignRequestCloseInterceptor() {
        return new MatrixFeignRequestCloseInterceptor();
    }

    /**
     * add inner request header
     *
     * @return MatrixFeignInnerRequestInterceptor
     */
    @Bean
    public MatrixFeignInnerRequestInterceptor matrixFeignInnerRequestInterceptor() {
        return new MatrixFeignInnerRequestInterceptor();
    }
}
