package com.deepdimen.matrix.work.brief;

import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.entity.WorkBriefPolicyEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ManagedWorkBriefRunnerTest {

	private final SkillBindingResolver resolver = mock(SkillBindingResolver.class);

	private final SkillExecutionService executor = mock(SkillExecutionService.class);

	private final WorkBriefPolicyEntity policy = new WorkBriefPolicyEntity();

	private final WorkBriefIdentity identity = new WorkBriefIdentity(42L, 3L, 5L, List.of(), List.of());

	private final WorkBriefSnapshot snapshot = new WorkBriefSnapshot(42L, 3L, LocalDateTime.now(), List.of(), List.of(),
			List.of(), List.of(), List.of(), null);

	private ManagedWorkBriefRunner runner;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setup() {
		ObjectProvider<SkillBindingResolver> rp = mock(ObjectProvider.class);
		when(rp.getIfAvailable()).thenReturn(resolver);
		ObjectProvider<SkillExecutionService> ep = mock(ObjectProvider.class);
		when(ep.getIfAvailable()).thenReturn(executor);
		runner = new ManagedWorkBriefRunner(rp, ep, new DeterministicWorkSummarySkill(),
				new ObjectMapper().findAndRegisterModules(), new WorkBriefReviewer());
		policy.setId(7L);
		policy.setSkillManagementMode("MANAGED");
		policy.setAllowDeterministicFallback(true);
	}

	@Test
	void usesFixedReleaseAndOverridesModelMetricsWithAuthoritativeCounts() {
		var frozen = new ResolvedSkillSnapshot("12", "11", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, null, null), "{}", "{}", "instructions", List.of(), "{}", List.of());
		when(resolver.resolve(any())).thenReturn(new Resolution(frozen, "token", Instant.now().plusSeconds(60)));
		when(executor.execute(any())).thenReturn(new ExecuteResult("10", RunStatus.SUCCEEDED,
				"{\"title\":\"Summary\",\"executiveSummary\":\"Done\",\"metrics\":{\"pendingTasks\":999}}", "ref",
				List.of(), "11", null, "{}", null));
		var result = runner.execute(identity, policy, snapshot, "8");
		assertEquals(0, result.draft().metrics().get("pendingTasks"));
		assertEquals("11", result.actualReleaseId());
		assertEquals("10", result.skillRunId());
		assertFalse(result.deterministicFallback());
		var captured = org.mockito.ArgumentCaptor.forClass(ExecuteRequest.class);
		verify(executor).execute(captured.capture());
		assertEquals("work-brief:8", captured.getValue().idempotencyKey());
		assertEquals("token", captured.getValue().resolvedSnapshotToken());
	}

	@Test void disabledBindingDoesNotFallbackEvenWhenLegacyPolicyAllowsIt(){
        when(resolver.resolve(any())).thenThrow(new IllegalArgumentException("BINDING_NOT_ACTIVE"));
        assertThrows(IllegalArgumentException.class,()->runner.execute(identity,policy,snapshot,"8"));
        verify(executor,never()).execute(any());
    }

	@Test
	void reviewFailureUsesCurrentBindingPermissionAndPreservesProvenance() {
		var frozen = new ResolvedSkillSnapshot("12", "11", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, null, null), "{}", "{}", "instructions", List.of(), "{}", List.of());
		when(resolver.resolve(any())).thenReturn(new Resolution(frozen, "token", Instant.now().plusSeconds(60)));
		when(executor.execute(any())).thenReturn(new ExecuteResult("10", RunStatus.SUCCEEDED,
				"{\"title\":\"\",\"executiveSummary\":\"invalid\"}", "ref", List.of(), "11", null, "{}", null));
		when(executor.fallbackAllowed(any(), eq("DETERMINISTIC"))).thenReturn(false);
		var rejected = runner.execute(identity, policy, snapshot, "8");
		assertFalse(rejected.deterministicFallback());
		assertFalse(new WorkBriefReviewer().review(snapshot, rejected.draft()).approved());
		when(executor.fallbackAllowed(any(), eq("DETERMINISTIC"))).thenReturn(true);
		var degraded = runner.execute(identity, policy, snapshot, "8");
		assertTrue(degraded.deterministicFallback());
		assertTrue(new WorkBriefReviewer().review(snapshot, degraded.draft()).approved());
		assertEquals("10", degraded.skillRunId());
		assertEquals("11", degraded.actualReleaseId());
		when(executor.fallbackAllowed(any(), eq("DETERMINISTIC")))
			.thenThrow(new IllegalArgumentException("BINDING_REVISION_CONFLICT"));
		assertThrows(ManagedWorkBriefFailure.class, () -> runner.execute(identity, policy, snapshot, "8"));
	}

	@Test
	void recordedModelFailureMayDegradeWithoutRetryButAuthorizationFailureCannot() {
		var frozen = new ResolvedSkillSnapshot("12", "11", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, null, null), "{}", "{}", "instructions", List.of(), "{}", List.of());
		when(resolver.resolve(any())).thenReturn(new Resolution(frozen, "token", Instant.now().plusSeconds(60)));
		var failure = new SkillExecutionFailure(new ExecuteResult("10", RunStatus.UNKNOWN, null, null, List.of(), "11",
				null, null, ErrorCode.RESULT_UNKNOWN), new IllegalStateException("timeout"));
		when(executor.execute(any())).thenThrow(failure);
		when(executor.fallbackAllowed(any(), eq("DETERMINISTIC"))).thenReturn(true);
		var result = runner.execute(identity, policy, snapshot, "8");
		assertTrue(result.deterministicFallback());
		assertEquals("10", result.skillRunId());
		verify(executor, times(1)).execute(any());
		doThrow(new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED")).when(executor).execute(any());
		assertThrows(IllegalArgumentException.class, () -> runner.execute(identity, policy, snapshot, "8"));
		verify(executor, times(1)).fallbackAllowed(any(), eq("DETERMINISTIC"));
	}

	@Test
	void invalidBusinessDateRequiresFallbackAuthorizationAndKeepsFailedVersion() {
		var frozen = new ResolvedSkillSnapshot("12", "11", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, null, null), "{}", "{}", "instructions", List.of(), "{}", List.of());
		when(resolver.resolve(any())).thenReturn(new Resolution(frozen, "token", Instant.now().plusSeconds(60)));
		when(executor.execute(any())).thenReturn(new ExecuteResult("10", RunStatus.SUCCEEDED,
				"{\"title\":\"Summary\",\"executiveSummary\":\"Done\",\"citations\":[{\"refType\":\"TASK\",\"refId\":1,\"occurredAt\":\"invalid-date\"}]}",
				"ref", List.of(), "11", null, "{}", null));
		var failed = assertThrows(ManagedWorkBriefFailure.class, () -> runner.execute(identity, policy, snapshot, "8"));
		assertEquals("12", failed.skillId());
		assertEquals("11", failed.releaseId());
		assertEquals("10", failed.skillRunId());
		assertEquals("OUTPUT_INVALID", failed.getMessage());
		when(executor.fallbackAllowed(any(), eq("DETERMINISTIC"))).thenReturn(true);
		var degraded = runner.execute(identity, policy, snapshot, "8");
		assertTrue(degraded.deterministicFallback());
		assertEquals("10", degraded.skillRunId());
		when(executor.fallbackAllowed(any(), eq("DETERMINISTIC")))
			.thenThrow(new IllegalArgumentException("RELEASE_NOT_AVAILABLE"));
		var revoked = assertThrows(ManagedWorkBriefFailure.class,
				() -> runner.execute(identity, policy, snapshot, "8"));
		assertEquals("RELEASE_NOT_AVAILABLE", revoked.getMessage());
	}

	@Test
	void recordedPreparationFailurePreservesProvenanceWithoutDeterministicFallback() {
		var frozen = new ResolvedSkillSnapshot("12", "11", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, null, null), "{}", "{}", "instructions", List.of(), "{}", List.of());
		when(resolver.resolve(any())).thenReturn(new Resolution(frozen, "token", Instant.now().plusSeconds(60)));
		when(executor.execute(any())).thenThrow(
				new SkillExecutionFailure(
						new ExecuteResult("10", RunStatus.FAILED, null, null, List.of(), "11", null, null,
								ErrorCode.EXECUTOR_VERSION_DRIFT),
						new IllegalArgumentException("MODEL_BINDING_CHANGED")));
		var failure = assertThrows(ManagedWorkBriefFailure.class,
				() -> runner.execute(identity, policy, snapshot, "8"));
		assertEquals("10", failure.skillRunId());
		assertEquals("11", failure.releaseId());
		assertEquals("12", failure.skillId());
		verify(executor, never()).fallbackAllowed(any(), any());
		verify(executor, times(1)).execute(any());
	}

}
