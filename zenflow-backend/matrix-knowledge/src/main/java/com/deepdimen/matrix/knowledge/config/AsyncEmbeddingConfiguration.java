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
 * 向量化异步处理配置
 * <p>
 * 用于配置切片向量化的异步线程池,支持并发调用大模型API以提升处理效率
 * </p>
 *
 * @author matrix
 * @date 2024-03-14
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "matrix.ai.embedding.async")
public class AsyncEmbeddingConfiguration {

	/**
	 * 核心线程数
	 */
	private int corePoolSize = 5;

	/**
	 * 最大线程数
	 */
	private int maxPoolSize = 10;

	/**
	 * 队列容量
	 */
	private int queueCapacity = 200;

	/**
	 * 线程名称前缀
	 */
	private String threadNamePrefix = "embedding-async-";

	/**
	 * 线程空闲时间(秒)
	 */
	private int keepAliveSeconds = 60;

	/**
	 * 批次大小 - 每批次并发处理的切片数量
	 */
	private int batchSize = 5;

	/**
	 * 任务超时时间(秒)
	 */
	private int taskTimeout = 300;

	/**
	 * 是否启用异步处理
	 */
	private boolean enabled = true;

	/**
	 * 创建向量化异步线程池
	 */
	@Bean("embeddingExecutor")
	public Executor embeddingExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

		// 核心线程数
		executor.setCorePoolSize(corePoolSize);

		// 最大线程数
		executor.setMaxPoolSize(maxPoolSize);

		// 队列容量
		executor.setQueueCapacity(queueCapacity);

		// 线程名称前缀
		executor.setThreadNamePrefix(threadNamePrefix);

		// 线程空闲时间
		executor.setKeepAliveSeconds(keepAliveSeconds);

		// 拒绝策略: 由调用线程处理
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

		// 等待所有任务完成后关闭线程池
		executor.setWaitForTasksToCompleteOnShutdown(true);

		// 等待终止时间
		executor.setAwaitTerminationSeconds(60);

		// CompletableFuture 不会自动传播 ThreadLocal。向量化任务会查询租户模型并回写切片，
		// 必须在提交时捕获租户上下文，并在工作线程结束后恢复原状。
		executor.setTaskDecorator(tenantContextTaskDecorator());

		// 初始化
		executor.initialize();

		log.info("初始化向量化异步线程池: corePoolSize={}, maxPoolSize={}, queueCapacity={}, batchSize={}", corePoolSize,
				maxPoolSize, queueCapacity, batchSize);

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
