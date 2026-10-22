package com.deepdimen.matrix.knowledge.support.handler.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;

class AbstractModelBuildHandlerCacheTest {

	@AfterEach
	void clearTenantContext() {
		TenantContextHolder.clear();
	}

	@Test
	void sameModelNameUsesDifferentCacheKeysAcrossTenants() {
		TenantContextHolder.setTenantId(1001L);
		String firstTenantKey = AbstractModelBuildHandler.tenantCacheKey("Chat", "shared-model");

		TenantContextHolder.setTenantId(2002L);
		String secondTenantKey = AbstractModelBuildHandler.tenantCacheKey("Chat", "shared-model");

		assertNotEquals(firstTenantKey, secondTenantKey);
		assertEquals("1001:Chat:shared-model", firstTenantKey);
		assertEquals("2002:Chat:shared-model", secondTenantKey);
	}

	@Test
	void modelCacheFailsClosedWithoutTenantContext() {
		assertThrows(IllegalStateException.class,
				() -> AbstractModelBuildHandler.tenantCacheKey("Chat", "shared-model"));
	}

}
