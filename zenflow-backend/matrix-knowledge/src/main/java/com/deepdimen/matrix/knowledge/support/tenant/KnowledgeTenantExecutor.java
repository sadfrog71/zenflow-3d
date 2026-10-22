package com.deepdimen.matrix.knowledge.support.tenant;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.knowledge.mapper.KnowledgeTenantMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs startup and scheduled knowledge work inside an explicit tenant context.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeTenantExecutor {

	private final KnowledgeTenantMapper tenantMapper;

	public void runForCurrentOrAll(String operation, Consumer<Long> action) {
		Long currentTenantId = TenantContextHolder.getTenantId();
		if (currentTenantId != null) {
			runForTenant(operation, currentTenantId, action);
			return;
		}

		List<Long> tenantIds = tenantMapper.selectActiveTenantIds();
		if (tenantIds == null || tenantIds.isEmpty()) {
			log.warn("知识后台任务未找到有效租户: operation={}", operation);
			return;
		}
		for (Long tenantId : new LinkedHashSet<>(tenantIds)) {
			if (tenantId != null) {
				runForTenant(operation, tenantId, action);
			}
		}
	}

	private void runForTenant(String operation, Long tenantId, Consumer<Long> action) {
		try {
			TenantBroker.runAs(tenantId, ignored -> action.accept(tenantId));
		}
		catch (RuntimeException ex) {
			log.error("租户知识后台任务失败: operation={}, tenantId={}, error={}",
					operation, tenantId, ex.getMessage(), ex);
		}
	}

}
