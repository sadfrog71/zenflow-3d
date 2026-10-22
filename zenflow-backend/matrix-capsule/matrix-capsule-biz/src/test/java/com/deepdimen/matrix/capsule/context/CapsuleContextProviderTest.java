package com.deepdimen.matrix.capsule.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.deepdimen.matrix.capsule.api.enums.CapsuleRecordType;
import com.deepdimen.matrix.capsule.api.vo.CapsuleDetailVO;
import com.deepdimen.matrix.capsule.api.vo.CapsuleVersionVO;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.capsule.service.CapsuleService;

@ExtendWith(MockitoExtension.class)
class CapsuleContextProviderTest extends AssistantContextProviderContract {

	private static final IdentityEnvelope AUTHORIZED = identity("2");

	@Mock
	private CapsuleService capsuleService;

	private CapsuleContextProvider provider;

	private CapsuleDetailVO record;

	@BeforeEach
	void setUp() {
		provider = new CapsuleContextProvider(capsuleService);
		record = new CapsuleDetailVO();
		record.setId(99L);
		record.setTitle("Authorized capsule");
		record.setContent("capsule-content");
		record.setContentHash("capsule-hash");
		record.setRecordType(CapsuleRecordType.IDEA);
		record.setRevisionNo(1);
		record.setCapturedAt(LocalDateTime.of(2026, 8, 1, 8, 0));
		record.setUpdateTime(LocalDateTime.of(2026, 8, 1, 9, 0));
		CapsuleVersionVO version = new CapsuleVersionVO();
		version.setRevisionNo(1);
		lenient().when(capsuleService.searchForContext(any(), anyString(), isNull(), isNull(), isNull(), anyList(), anyInt()))
			.thenReturn(List.of(record));
		lenient().when(capsuleService.detail(new CapsuleIdentity(1L, 2L), 99L)).thenReturn(record);
		lenient().when(capsuleService.versions(new CapsuleIdentity(1L, 2L), 99L)).thenReturn(List.of(version));
	}

	@Test
	void explicitIdsCannotExpandBeyondOwnedResources() {
		ProviderQuery request = query(List.of("99", "100"), Map.of(), new ProviderLimits(20, 12000, 3000));
		when(capsuleService.countOwnedExplicitIds(new CapsuleIdentity(1L, 2L), List.of(99L, 100L))).thenReturn(1L);

		var result = provider.query(request);

		assertEquals(ProviderStatus.FORBIDDEN_SCOPE, result.status());
		assertEquals(1, result.permissionFiltered());
		assertTrue(result.fragments().isEmpty());
		verify(capsuleService, never()).searchForContext(any(), any(), any(), any(), any(), any(), eq(21));
	}

	@Test
	void unknownFilterIsRejectedWithoutRunningDomainQuery() {
		var result = provider.query(query(List.of(), Map.of("sql", "select * from ai_capsule"), null));

		assertEquals(ProviderStatus.FORBIDDEN_SCOPE, result.status());
		verify(capsuleService, never()).searchForContext(any(), any(), any(), any(), any(), any(), anyInt());
	}

	@Test
	void limitsAndSensitiveClassificationAreEnforcedByProvider() {
		record.setContent("long-content");

		var result = provider.query(query(List.of(), Map.of(), new ProviderLimits(1, 5, 3000)));

		assertEquals(1, result.fragments().size());
		assertEquals(5, result.fragments().get(0).excerpt().length());
		assertEquals(DataClassification.SENSITIVE, result.fragments().get(0).dataClassification());
		assertTrue(result.truncated());
	}

	@Test
	void citationResolutionRejectsAProviderOrStableResourceMismatch() {
		Citation forged = new Citation("capsule:99:1", ProviderId.WORK, "IDEA", "99", "1", "INTERNAL",
				"CapsuleDetail", Map.of("id", "99"));

		assertEquals(ProviderStatus.FORBIDDEN_SCOPE,
				provider.resolve(new CitationResolveRequest(forged, AUTHORIZED)).status());
		verify(capsuleService, never()).detail(any(), any());
	}

	@Override
	protected AssistantContextProvider provider() {
		return provider;
	}

	@Override
	protected ProviderQuery query() {
		return query(List.of(), Map.of(), new ProviderLimits(20, 12000, 3000));
	}

	@Override
	protected CitationResolveRequest authorizedCitation() {
		return new CitationResolveRequest(citation(), AUTHORIZED);
	}

	@Override
	protected IdentityEnvelope unauthorizedIdentity() {
		return identity("3");
	}

	private ProviderQuery query(List<String> explicitIds, Map<String, String> filters, ProviderLimits limits) {
		return new ProviderQuery(AssistantContextContract.VERSION, "request-1", "run-1", AUTHORIZED.correlationId(),
				AUTHORIZED, "capsule", new ProviderScope(null, null, explicitIds, null, filters), limits, List.of());
	}

	private static IdentityEnvelope identity(String userId) {
		return new IdentityEnvelope("1", userId, "10", List.of("10"), List.of(), List.of(), "capsule-test",
				"ASSISTANT_CONTEXT");
	}

	private static Citation citation() {
		return new Citation("capsule:99:1", ProviderId.CAPSULE, "IDEA", "99", "1", "INTERNAL", "CapsuleDetail",
				Map.of("id", "99"));
	}

}
