/*
 *
 *      Copyright (c) 2018-2025, deepdimen All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright
 *  notice, this list of conditions and the following disclaimer in the
 *  documentation and/or other materials provided with the distribution.
 *  Neither the name of the deepdimen.com developer nor the names of its
 *  contributors may be used to endorse or promote products derived from
 *  this software without specific prior written permission.
 *  Author: caoyuan (caoyuanchn@gmail.com)
 *
 */

package com.deepdimen.matrix.common.log;

import com.deepdimen.matrix.admin.api.feign.RemoteLogService;
import com.deepdimen.matrix.common.core.util.KeyStrResolver;
import com.deepdimen.matrix.common.log.aspect.SysLogAspect;
import com.deepdimen.matrix.common.log.config.MatrixLogProperties;
import com.deepdimen.matrix.common.log.config.SysLogAsyncConfiguration;
import com.deepdimen.matrix.common.log.event.SysLogListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @author caoyuan
 * @date 2018/6/28
 * <p>
 * 日志自动配置
 */
@EnableAsync
@Configuration(proxyBeanMethods = false)
@Import(SysLogAsyncConfiguration.class)
@EnableConfigurationProperties(MatrixLogProperties.class)
@ConditionalOnProperty(value = "matrix.log.enabled", matchIfMissing = true)
public class LogAutoConfiguration {

	@Bean
	public SysLogListener sysLogListener(MatrixLogProperties logProperties, RemoteLogService remoteLogService) {
		return new SysLogListener(remoteLogService, logProperties);
	}

	@Bean
	public SysLogAspect sysLogAspect(KeyStrResolver resolver) {
		return new SysLogAspect(resolver);
	}

}
