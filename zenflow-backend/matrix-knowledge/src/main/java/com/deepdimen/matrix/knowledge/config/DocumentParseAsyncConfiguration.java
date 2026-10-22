package com.deepdimen.matrix.knowledge.config;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 文档解析异步线程池配置.
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "matrix.ai.document.parse.async")
public class DocumentParseAsyncConfiguration {

	public static final String DOCUMENT_PARSE_EXECUTOR = "documentParseExecutor";

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
	private int queueCapacity = 100;

	/**
	 * 线程名称前缀.
	 */
	private String threadNamePrefix = "document-parse-async-";

	/**
	 * 线程空闲时间(秒).
	 */
	private int keepAliveSeconds = 60;

	@Bean(DOCUMENT_PARSE_EXECUTOR)
	public Executor documentParseExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(corePoolSize);
		executor.setMaxPoolSize(maxPoolSize);
		executor.setQueueCapacity(queueCapacity);
		executor.setThreadNamePrefix(threadNamePrefix);
		executor.setKeepAliveSeconds(keepAliveSeconds);
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.setTaskDecorator(tenantContextTaskDecorator());
		executor.initialize();

		log.info("初始化文档解析异步线程池: corePoolSize={}, maxPoolSize={}, queueCapacity={}", corePoolSize, maxPoolSize,
				queueCapacity);
		return executor;
	}

	private TaskDecorator tenantContextTaskDecorator() {
		return runnable -> {
			Long tenantId = TenantContextHolder.getTenantId();
			Boolean tenantSkip = TenantContextHolder.getTenantSkip();
			return () -> {
				Long previousTenantId = TenantContextHolder.getTenantId();
				Boolean previousTenantSkip = TenantContextHolder.getTenantSkip();
				try {
					TenantContextHolder.clear();
					if (tenantId != null) {
						TenantContextHolder.setTenantId(tenantId);
					}
					if (Boolean.TRUE.equals(tenantSkip)) {
						TenantContextHolder.setTenantSkip();
					}
					runnable.run();
				}
				finally {
					TenantContextHolder.clear();
					if (previousTenantId != null) {
						TenantContextHolder.setTenantId(previousTenantId);
					}
					if (Boolean.TRUE.equals(previousTenantSkip)) {
						TenantContextHolder.setTenantSkip();
					}
				}
			};
		};
	}

}
