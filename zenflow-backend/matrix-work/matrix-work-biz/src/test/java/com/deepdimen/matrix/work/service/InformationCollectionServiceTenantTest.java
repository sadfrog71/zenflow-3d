package com.deepdimen.matrix.work.service;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.work.mapper.InformationEvidenceMapper;
import com.deepdimen.matrix.work.mapper.InformationItemMapper;
import com.deepdimen.matrix.work.mapper.InformationPolicyMapper;
import com.deepdimen.matrix.work.mapper.InformationRunMapper;
import com.deepdimen.matrix.work.provider.TavilyDiscoveryProvider;
import com.deepdimen.matrix.work.api.entity.InformationItem;
import com.deepdimen.matrix.work.api.entity.InformationEvidence;
import com.deepdimen.matrix.work.api.entity.InformationPolicy;
import com.deepdimen.matrix.work.api.entity.InformationRun;
import org.springframework.dao.DuplicateKeyException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class InformationCollectionServiceTenantTest {

	private final InformationPolicyMapper policyMapper = mock(InformationPolicyMapper.class);
	private final InformationRunMapper runMapper = mock(InformationRunMapper.class);
	private final InformationItemMapper itemMapper = mock(InformationItemMapper.class);
	private final InformationEvidenceMapper evidenceMapper = mock(InformationEvidenceMapper.class);
	private final TavilyDiscoveryProvider discoveryProvider = mock(TavilyDiscoveryProvider.class);
	private final InformationReviewService reviewService = mock(InformationReviewService.class);
	private final InformationAdminService adminService = mock(InformationAdminService.class);
	private final InformationCollectionService service = new InformationCollectionService(policyMapper, runMapper,
			itemMapper, evidenceMapper, discoveryProvider, reviewService, adminService);

	@AfterEach
	void clearTenantContext() {
		TenantContextHolder.clear();
	}

	@Test
	void rejectsCollectionWhenTenantContextIsMissing() {
		IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.runPolicy(100L));

		assertEquals("AI资讯采集缺少租户上下文", error.getMessage());
		verify(policyMapper, never()).selectById(anyLong());
	}

	@Test
	void policyLookupUsesTheActiveTenantInterceptorContext() {
		TenantContextHolder.setTenantId(200L);

		assertThrows(IllegalArgumentException.class, () -> service.runPolicy(100L));

		verify(policyMapper).selectById(100L);
	}

	@Test
	void concurrentDuplicateItemDoesNotFailTheWholeRun() {
		TenantContextHolder.setTenantId(200L);
		InformationPolicy policy = new InformationPolicy();
		policy.setId(100L);
		policy.setOrgId(300L);
		policy.setEnabled(1);
		policy.setQueryText("水务 AI");
		policy.setDailyLimit(10);
		when(policyMapper.selectById(100L)).thenReturn(policy);
		when(discoveryProvider.discover(policy)).thenReturn(List.of(new TavilyDiscoveryProvider.DiscoveredDocument(
				"水务 AI 案例", "https://example.com/news", "水务 AI 智能体落地案例", null)));
		when(reviewService.review(any(), any(), any(), any())).thenReturn(
				new InformationReviewService.ReviewResult(true, "CASE", "水务 AI 案例", "摘要", "水务,AI", 0.9, ""));
		InformationItem concurrentItem = new InformationItem();
		concurrentItem.setId(200L);
		concurrentItem.setReviewStatus("APPROVED");
		when(itemMapper.selectOne(any())).thenReturn(null, concurrentItem);
		doThrow(new DuplicateKeyException("duplicate content hash")).when(itemMapper).insert(any(InformationItem.class));
		when(adminService.publish(any())).thenReturn(Map.of("published", 0));

		InformationRun result = service.runPolicy(100L);

		assertEquals("SUCCESS", result.getStatus());
		verify(evidenceMapper, never()).insert(any(InformationEvidence.class));
		verify(runMapper).updateById(any(InformationRun.class));
	}

}
