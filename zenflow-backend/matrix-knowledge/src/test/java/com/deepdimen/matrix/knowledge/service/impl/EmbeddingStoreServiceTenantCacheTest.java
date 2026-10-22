package com.deepdimen.matrix.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;

class EmbeddingStoreServiceTenantCacheTest {

	@AfterEach
	void clearTenantContext() {
		TenantContextHolder.clear();
	}

	@Test
	void sameCollectionNameUsesDifferentCacheKeysAcrossTenants() {
		TenantContextHolder.setTenantId(1001L);
		String firstTenantKey = EmbeddingStoreServiceImpl.tenantKey("shared-collection");

		TenantContextHolder.setTenantId(2002L);
		String secondTenantKey = EmbeddingStoreServiceImpl.tenantKey("shared-collection");

		assertNotEquals(firstTenantKey, secondTenantKey);
		assertEquals("1001:shared-collection", firstTenantKey);
		assertEquals("2002:shared-collection", secondTenantKey);
	}

	@Test
	void vectorStoreCacheFailsClosedWithoutTenantContext() {
		assertThrows(IllegalStateException.class,
				() -> EmbeddingStoreServiceImpl.tenantKey("shared-collection"));
	}

}
