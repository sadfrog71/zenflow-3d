package com.deepdimen.matrix.discover.assistant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.discover.assistant.NativeAssistantReadinessService.ReadinessApprovalRequest;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.entity.AiAssistantNativeReadinessEntity;
import com.deepdimen.matrix.discover.mapper.AiAssistantNativeReadinessMapper;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NativeAssistantReadinessServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-27T00:00:00Z");

	private final AiAssistantNativeReadinessMapper mapper = mock(AiAssistantNativeReadinessMapper.class);

	private final AiAgentService agentService = mock(AiAgentService.class);

	private final AtomicReference<AiAssistantNativeReadinessEntity> latest = new AtomicReference<>();

	private NativeAssistantReadinessService service;

	@BeforeEach
	void setUp() {
		when(agentService.getById(2200L)).thenReturn(profile("{\"modelId\":\"99\"}"));
		when(mapper.insert(any(AiAssistantNativeReadinessEntity.class))).thenAnswer(invocation -> {
			latest.set(invocation.getArgument(0));
			return 1;
		});
		when(mapper.selectList(any())).thenAnswer(ignored -> latest.get() == null ? List.of() : List.of(latest.get()));
		service = new NativeAssistantReadinessService(mapper, agentService, new ObjectMapper(),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void approvedNonRegressingReportOpensTheGate() {
		service.approve(identity(), approval(0.80, 0.82, 0.10, 0.08));

		assertDoesNotThrow(() -> service.assertReady(identity(), 2200L));
		assertEquals("APPROVED", latest.get().getDecisionStatus());
	}

	@Test
	void qualityRegressionCannotBeApproved() {
		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> service.approve(identity(), approval(0.80, 0.70, 0.10, 0.12)));

		assertEquals("GOLDEN_REPORT_QUALITY_REGRESSION", error.getMessage());
	}

	@Test
	void profileChangeInvalidatesPriorApproval() {
		service.approve(identity(), approval(0.80, 0.82, 0.10, 0.08));
		when(agentService.getById(2200L)).thenReturn(profile("{\"modelId\":\"100\"}"));

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> service.assertReady(identity(), 2200L));
		assertEquals("NATIVE_CUTOVER_PROFILE_CHANGED", error.getMessage());
	}

	@Test
	void revocationClosesTheGateWithoutDeletingAuditHistory() {
		service.approve(identity(), approval(0.80, 0.82, 0.10, 0.08));
		service.revoke(identity(), 2200L, "quality incident");

		IllegalStateException error = assertThrows(IllegalStateException.class,
				() -> service.assertReady(identity(), 2200L));
		assertEquals("NATIVE_CUTOVER_NOT_APPROVED", error.getMessage());
		assertEquals("REVOKED", latest.get().getDecisionStatus());
	}

	private ReadinessApprovalRequest approval(double baselineCitation, double candidateCitation,
			double baselineUnsupported, double candidateUnsupported) {
		return new ReadinessApprovalRequest(2200L, "a".repeat(64), 0, 0, 5,
				List.of("KNOWLEDGE", "WORK", "INFORMATION", "CAPSULE", "MIXED"), baselineCitation, candidateCitation,
				baselineUnsupported, candidateUnsupported, 900, 700, 4000, 3500, 0.02, 0.018, "CNY",
				"work-domain-owner", NOW.plusSeconds(7 * 24 * 60 * 60), "golden set accepted");
	}

	private AiAgentEntity profile(String config) {
		AiAgentEntity profile = new AiAgentEntity();
		profile.setId(2200L);
		profile.setTenantId("11");
		profile.setAgentType("native");
		profile.setStatus("published");
		profile.setConfig(config);
		return profile;
	}

	private IdentityEnvelope identity() {
		return new IdentityEnvelope("11", "7", "3", List.of("3"), List.of(), List.of(), "readiness-test",
				"ASSISTANT_ADMIN");
	}

}
