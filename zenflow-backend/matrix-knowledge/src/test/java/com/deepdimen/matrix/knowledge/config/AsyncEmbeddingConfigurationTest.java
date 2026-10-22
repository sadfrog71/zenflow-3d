package com.deepdimen.matrix.knowledge.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;

class AsyncEmbeddingConfigurationTest {

	@AfterEach
	void clearTenant() {
		TenantContextHolder.clear();
	}

	@Test
	void propagatesTenantToEmbeddingWorkerAndClearsItAfterTask() throws Exception {
		AsyncEmbeddingConfiguration configuration = new AsyncEmbeddingConfiguration();
		configuration.setCorePoolSize(1);
		configuration.setMaxPoolSize(1);
		ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) configuration.embeddingExecutor();
		try {
			TenantContextHolder.setTenantId(1001L);
			Long propagated = CompletableFuture.supplyAsync(TenantContextHolder::getTenantId, executor)
				.get(5, TimeUnit.SECONDS);
			assertEquals(1001L, propagated);

			TenantContextHolder.clear();
			Long cleared = CompletableFuture.supplyAsync(TenantContextHolder::getTenantId, executor)
				.get(5, TimeUnit.SECONDS);
			assertNull(cleared);
		}
		finally {
			executor.shutdown();
		}
	}

}
