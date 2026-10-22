package com.deepdimen.matrix.knowledge.support.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.mapper.KnowledgeTenantMapper;

class KnowledgeTenantExecutorTest {

	@AfterEach
	void clearTenantContext() {
		TenantContextHolder.clear();
	}

	@Test
	void backgroundWorkRunsOncePerDistinctTenantAndRestoresContext() {
		KnowledgeTenantMapper mapper = mock(KnowledgeTenantMapper.class);
		when(mapper.selectActiveTenantIds()).thenReturn(List.of(1001L, 1001L, 2002L));
		KnowledgeTenantExecutor executor = new KnowledgeTenantExecutor(mapper);
		List<Long> observed = new ArrayList<>();

		executor.runForCurrentOrAll("test", tenantId -> {
			assertEquals(tenantId, TenantContextHolder.getTenantId());
			observed.add(tenantId);
		});

		assertEquals(List.of(1001L, 2002L), observed);
		assertNull(TenantContextHolder.getTenantId());
	}

	@Test
	void requestScopedEventOnlyRunsForCurrentTenant() {
		KnowledgeTenantMapper mapper = mock(KnowledgeTenantMapper.class);
		KnowledgeTenantExecutor executor = new KnowledgeTenantExecutor(mapper);
		TenantContextHolder.setTenantId(3003L);
		List<Long> observed = new ArrayList<>();

		executor.runForCurrentOrAll("test", observed::add);

		assertEquals(List.of(3003L), observed);
		assertEquals(3003L, TenantContextHolder.getTenantId());
	}

}
