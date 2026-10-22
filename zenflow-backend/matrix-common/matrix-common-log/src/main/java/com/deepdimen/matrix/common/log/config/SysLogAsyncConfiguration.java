/*
 * Copyright (c) 2020 deepdimen Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.deepdimen.matrix.common.log.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 系统日志异步线程池配置.
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "matrix.log.async")
public class SysLogAsyncConfiguration {

	public static final String SYS_LOG_EXECUTOR = "sysLogExecutor";

	/**
	 * 核心线程数.
	 */
	private int corePoolSize = 2;

	/**
	 * 最大线程数.
	 */
	private int maxPoolSize = 4;

	/**
	 * 队列容量.
	 */
	private int queueCapacity = 2000;

	/**
	 * 线程名前缀.
	 */
	private String threadNamePrefix = "sys-log-async-";

	/**
	 * 线程空闲时间(秒).
	 */
	private int keepAliveSeconds = 60;

	@Bean(SYS_LOG_EXECUTOR)
	public Executor sysLogExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(corePoolSize);
		executor.setMaxPoolSize(maxPoolSize);
		executor.setQueueCapacity(queueCapacity);
		executor.setThreadNamePrefix(threadNamePrefix);
		executor.setKeepAliveSeconds(keepAliveSeconds);
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(false);
		executor.initialize();

		log.info("初始化系统日志异步线程池: corePoolSize={}, maxPoolSize={}, queueCapacity={}", corePoolSize, maxPoolSize,
				queueCapacity);
		return executor;
	}

}
