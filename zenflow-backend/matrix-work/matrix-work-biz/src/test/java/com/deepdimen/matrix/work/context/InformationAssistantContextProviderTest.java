package com.deepdimen.matrix.work.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.Citation;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolveRequest;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderScope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.testkit.AssistantContextProviderContract;
import com.deepdimen.matrix.work.api.entity.InformationEvidence;
import com.deepdimen.matrix.work.api.vo.InformationDetailVO;
import com.deepdimen.matrix.work.mapper.InformationEvidenceMapper;
import com.deepdimen.matrix.work.mapper.InformationItemMapper;

@ExtendWith(MockitoExtension.class)
class InformationAssistantContextProviderTest extends AssistantContextProviderContract {

	private static final IdentityEnvelope AUTHORIZED = identity("42", "200");

	@Mock
	private InformationItemMapper itemMapper;

	@Mock
	private InformationEvidenceMapper evidenceMapper;

	private InformationAssistantContextProvider provider;

	private InformationDetailVO item;

	private InformationEvidence evidence;

	@BeforeEach
	void setUp() {
		provider = new InformationAssistantContextProvider(itemMapper, evidenceMapper);
		item = new InformationDetailVO();
		item.setId(21L);
		item.setTitle("Authorized information");
		item.setSummary("information-summary");
		item.setContent("information-content");
		item.setPublishedAt(LocalDateTime.of(2026, 8, 1, 8, 0));
		item.setDeliveredAt(LocalDateTime.of(2026, 8, 1, 9, 0));
		evidence = new InformationEvidence();
		evidence.setId(22L);
		evidence.setItemId(21L);
		evidence.setTitle("Source evidence");
		evidence.setRawContent("evidence-content");
		evidence.setContentHash("evidence-hash");
		evidence.setCapturedAt(LocalDateTime.of(2026, 8, 1, 7, 0));
		evidence.setCreateTime(LocalDateTime.of(2026, 8, 1, 7, 30));
		lenient().when(itemMapper.selectContextItems(anyLong(), anyLong(), anyLong(), anyString(), any(), any(),
				anyList(), anyInt())).thenReturn(List.of(item));
		lenient().when(evidenceMapper.selectList(any())).thenReturn(List.of(evidence));
		lenient().when(itemMapper.selectPortalDetail(anyLong(), anyLong(), anyLong(), anyLong()))
			.thenAnswer(invocation -> Long.valueOf(200L).equals(invocation.getArgument(2))
					&& Long.valueOf(42L).equals(invocation.getArgument(3)) ? item : null);
		lenient().when(evidenceMapper.selectById(22L)).thenReturn(evidence);
	}

	@Test
	void evidenceUsesItsOwnStableResourceAndTotalFragmentLimit() {
		var full = provider.query(query(List.of(), new ProviderLimits(20, 12000, 3000)));
		var limited = provider.query(query(List.of(), new ProviderLimits(1, 12000, 3000)));

		assertEquals(2, full.fragments().size());
		assertEquals("INFORMATION_EVIDENCE", full.fragments().get(1).citation().sourceType());
		assertEquals("22", full.fragments().get(1).citation().sourceId());
		assertEquals(1, limited.fragments().size());
		assertTrue(limited.truncated());
	}

	@Test
	void explicitIdsAreVerifiedBeforeResultTruncation() {
		var result = provider.query(query(List.of("21", "99"), new ProviderLimits(1, 12000, 3000)));

		assertEquals(ProviderStatus.FORBIDDEN_SCOPE, result.status());
		assertEquals(1, result.permissionFiltered());
		assertTrue(result.fragments().isEmpty());
	}

	@Test
	void fragmentsAreSensitiveAndCitationRejectsProviderMismatch() {
		var result = provider.query(query(List.of(), new ProviderLimits(20, 12000, 3000)));
		Citation forged = new Citation("information:21", ProviderId.WORK, "INFORMATION_ITEM", "21", "v1",
				"INTERNAL", "InformationDetail", Map.of("id", "21"));

		assertTrue(result.fragments()
			.stream()
			.allMatch(fragment -> fragment.dataClassification() == DataClassification.SENSITIVE));
		assertEquals(ProviderStatus.FORBIDDEN_SCOPE,
				provider.resolve(new CitationResolveRequest(forged, AUTHORIZED)).status());
		verify(itemMapper, never()).selectPortalDetail(anyLong(), anyLong(), anyLong(), anyLong());
	}

	@Override
	protected AssistantContextProvider provider() {
		return provider;
	}

	@Override
	protected ProviderQuery query() {
		return query(List.of(), new ProviderLimits(20, 12000, 3000));
	}

	@Override
	protected CitationResolveRequest authorizedCitation() {
		return new CitationResolveRequest(citation(), AUTHORIZED);
	}

	@Override
	protected IdentityEnvelope unauthorizedIdentity() {
		return identity("43", "201");
	}

	private ProviderQuery query(List<String> ids, ProviderLimits limits) {
		return new ProviderQuery(AssistantContextContract.VERSION, "request-1", "run-1", AUTHORIZED.correlationId(),
				AUTHORIZED, "information", new ProviderScope(null, null, ids, null, Map.of()), limits, List.of());
	}

	private static IdentityEnvelope identity(String userId, String orgId) {
		return new IdentityEnvelope("100", userId, orgId, List.of(orgId), List.of(), List.of(), "information-test",
				"ASSISTANT_CONTEXT");
	}

	private static Citation citation() {
		return new Citation("information:21", ProviderId.INFORMATION, "INFORMATION_ITEM", "21", "v1", "INTERNAL",
				"InformationDetail", Map.of("id", "21"));
	}

}
