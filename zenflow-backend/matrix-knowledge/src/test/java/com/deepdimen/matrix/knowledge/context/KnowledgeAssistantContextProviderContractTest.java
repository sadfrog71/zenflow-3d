package com.deepdimen.matrix.knowledge.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolveRequest;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderScope;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.testkit.AssistantContextProviderContract;
import com.deepdimen.matrix.knowledge.dto.AiSliceReCallDTO;
import com.deepdimen.matrix.knowledge.entity.AiDatasetEntity;
import com.deepdimen.matrix.knowledge.entity.AiDocumentEntity;
import com.deepdimen.matrix.knowledge.entity.AiSliceEntity;
import com.deepdimen.matrix.knowledge.mapper.AiDocumentMapper;
import com.deepdimen.matrix.knowledge.mapper.AiSliceMapper;
import com.deepdimen.matrix.knowledge.service.AiSliceService;

class KnowledgeAssistantContextProviderContractTest extends AssistantContextProviderContract {

	private final IdentityEnvelope identity = new IdentityEnvelope("100", "42", "200", List.of("200"), List.of("7"),
			List.of("9"), "knowledge-contract", "ASSISTANT_CONTEXT");

	private final KnowledgeDatasetAccessService accessService = mock(KnowledgeDatasetAccessService.class);

	private final AiSliceService sliceService = mock(AiSliceService.class);

	private final AiSliceMapper sliceMapper = mock(AiSliceMapper.class);

	private final AiDocumentMapper documentMapper = mock(AiDocumentMapper.class);

	private final KnowledgeAssistantContextProvider provider = providerFixture();

	@Override
	protected AssistantContextProvider provider() {
		return provider;
	}

	@Override
	protected ProviderQuery query() {
		return new ProviderQuery(AssistantContextContract.VERSION, "knowledge-query", identity.correlationId(),
				identity, "应急系统操作", new ProviderScope(null, null, List.of("31"), "knowledge", Map.of()),
				new ProviderLimits(10, 12000, 3000));
	}

	@Override
	protected CitationResolveRequest authorizedCitation() {
		return new CitationResolveRequest(provider.query(query()).fragments().get(0).citation(), identity);
	}

	@Override
	protected IdentityEnvelope unauthorizedIdentity() {
		return new IdentityEnvelope("100", "99", "200", List.of("200"), List.of(), List.of(),
				"knowledge-contract-denied", "ASSISTANT_CONTEXT");
	}

	@Test
	void blankBrowseQueryDoesNotReportTheKnowledgeProviderAsUnavailable() {
		ProviderQuery browseQuery = new ProviderQuery(AssistantContextContract.VERSION, "knowledge-browse",
				identity.correlationId(), identity, "", new ProviderScope(null, null, List.of(), "knowledge", Map.of()),
				new ProviderLimits(10, 12000, 3000));

		var result = provider.query(browseQuery);

		assertEquals(AssistantContextContract.ProviderStatus.NO_MATCH, result.status());
		assertTrue(result.warnings().isEmpty());
		verify(sliceService, never()).recall(any());
	}

	@Test
	void fixedRetrievalReusesEvidenceChecksButCannotSilentlyUseTextFallback() {
		var expected = provider.query(query());
		clearInvocations(sliceService);
		var result = provider.queryMarketSkill(marketQuery(List.of("31")), (dataset, request) -> {
			assertEquals(31L, dataset.getId());
			var item = new AiSliceReCallDTO();
			item.setSliceId(Long.valueOf(expected.fragments().get(0).sourceId()));
			item.setScore("90%");
			return List.of(item);
		});
		assertEquals(expected.fragments().get(0).contentHash(), result.fragments().get(0).contentHash());
		verify(sliceService, never()).recall(any());
		assertThrows(IllegalArgumentException.class,
				() -> provider.queryMarketSkill(marketQuery(List.of("31")), (dataset, request) -> {
					throw new IllegalArgumentException("EXECUTOR_VERSION_DRIFT");
				}));
	}

	private ProviderQuery marketQuery(List<String> ids) {
		var marketIdentity = new IdentityEnvelope("100", "42", "200", List.of("200"), List.of("7"), List.of("9"),
				"market-skill:1", "MARKET_SKILL");
		return new ProviderQuery(AssistantContextContract.VERSION, "market-query", marketIdentity.correlationId(),
				marketIdentity, "应急系统操作", new ProviderScope(null, null, ids, "market", Map.of()),
				new ProviderLimits(100, 50000, 3000));
	}

	@Test
	void marketEntryReusesRecallAndVersionedEvidenceWithoutChangingAssistantPurpose() {
		var market = provider.queryMarketSkill(marketQuery(List.of("31")));
		assertEquals(AssistantContextContract.ProviderStatus.OK, market.status());
		assertEquals(provider.query(query()).fragments(), market.fragments());
		assertEquals(AssistantContextContract.ProviderStatus.FORBIDDEN_SCOPE,
				provider.query(marketQuery(List.of("31"))).status());
		assertEquals(AssistantContextContract.ProviderStatus.FORBIDDEN_SCOPE,
				provider.queryMarketSkill(query()).status());
	}

	@Test
	void marketNeverAutomaticallySearchesAllAccessibleDatasets() {
		assertEquals(AssistantContextContract.ProviderStatus.FORBIDDEN_SCOPE,
				provider.queryMarketSkill(marketQuery(List.of())).status());
		assertEquals(AssistantContextContract.ProviderStatus.FORBIDDEN_SCOPE,
				provider.queryMarketSkill(marketQuery(List.of("-1"))).status());
		verify(sliceService, never()).recall(any());
	}

	@Test
	void permissionRevocationAfterRecallWithholdsAllMarketEvidence() {
		var allowed = accessService.accessibleDatasets(identity, List.of(31L));
		when(accessService.accessibleDatasets(any(), any())).thenReturn(allowed, List.of());
		var result = provider.queryMarketSkill(marketQuery(List.of("31")));
		assertEquals(AssistantContextContract.ProviderStatus.FORBIDDEN_SCOPE, result.status());
		assertTrue(result.fragments().isEmpty());
	}

	@Test
	void changedSourceAfterRecallCannotReachMarketGeneration() {
		AiSliceEntity original = sliceMapper.selectById(32L);
		AiSliceEntity changed = new AiSliceEntity();
		changed.setId(32L);
		changed.setDocumentId(33L);
		changed.setContent("changed");
		changed.setCreateTime(original.getCreateTime());
		changed.setUpdateTime(original.getUpdateTime());
		when(sliceMapper.selectById(32L)).thenReturn(original, changed);
		var result = provider.queryMarketSkill(marketQuery(List.of("31")));
		assertEquals(AssistantContextContract.ProviderStatus.UNAVAILABLE, result.status());
		assertTrue(result.fragments().isEmpty());
	}

	@Test
	void marketBoundsEvidenceAndCannotRecallAnUnauthorizedDataset() {
		var dataset = accessService.accessibleDatasets(identity, List.of(31L)).get(0);
		dataset.setTopK(100);
		sliceMapper.selectById(32L).setContent("x".repeat(20000));
		var result = provider.queryMarketSkill(marketQuery(List.of("31")));
		assertEquals(12000, result.fragments().get(0).excerpt().length());
		assertTrue(result.truncated());
		var recall = org.mockito.ArgumentCaptor
			.forClass(com.deepdimen.matrix.knowledge.dto.AiSliceReCallRequestDTO.class);
		verify(sliceService).recall(recall.capture());
		assertEquals(20, recall.getValue().getTopK());
		when(accessService.accessibleDatasets(any(), any())).thenReturn(List.of());
		assertEquals(AssistantContextContract.ProviderStatus.FORBIDDEN_SCOPE,
				provider.queryMarketSkill(marketQuery(List.of("31"))).status());
		verify(sliceService).recall(any());
	}

	private KnowledgeAssistantContextProvider providerFixture() {
		AiDatasetEntity dataset = new AiDatasetEntity();
		dataset.setId(31L);
		dataset.setTenantId("100");
		dataset.setName("生产运行制度");
		dataset.setTopK(5);
		when(accessService.accessibleDatasets(any(), any())).thenReturn(List.of(dataset));
		when(accessService.canAccess(any(), eq(31L)))
			.thenAnswer(invocation -> "42".equals(invocation.<IdentityEnvelope>getArgument(0).userId()));

		AiSliceReCallDTO recall = new AiSliceReCallDTO();
		recall.setSliceId(32L);
		recall.setScore("0.99");
		recall.setContent("先确认断路器状态，再启动搅拌机。");
		when(sliceService.recall(any())).thenReturn(List.of(recall));
		AiSliceEntity slice = new AiSliceEntity();
		slice.setId(32L);
		slice.setDocumentId(33L);
		slice.setContent(recall.getContent());
		slice.setCreateTime(LocalDateTime.of(2026, 8, 1, 8, 0));
		slice.setUpdateTime(LocalDateTime.of(2026, 8, 2, 8, 0));
		when(sliceMapper.selectById(32L)).thenReturn(slice);
		AiDocumentEntity document = new AiDocumentEntity();
		document.setId(33L);
		document.setDatasetId(31L);
		document.setName("高锰酸钾应急系统操作规程");
		when(documentMapper.selectById(33L)).thenReturn(document);
		return new KnowledgeAssistantContextProvider(accessService, sliceService, sliceMapper, documentMapper);
	}

}
