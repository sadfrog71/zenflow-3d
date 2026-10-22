package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.entity.WorkTaskPolicyEntity;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class TaskSkillAvailabilityTest {

	private final TaskModelRunner legacy = mock(TaskModelRunner.class);

	private final SkillBindingResolver resolver = mock(SkillBindingResolver.class);

	private final SkillExecutionService executor = mock(SkillExecutionService.class);

	private final WorkTaskPolicyEntity policy = new WorkTaskPolicyEntity();

	private TaskSkillAvailability availability;

	@BeforeEach
	void setup() {
		availability = new TaskSkillAvailability(legacy, provider(resolver), provider(executor));
		policy.setId(7L);
		policy.setEnabled(true);
		policy.setChatModelId(77L);
		policy.setParseSkillPackageId(11L);
		policy.setDispatchSkillPackageId(12L);
		when(legacy.available()).thenReturn(true);
	}

	@Test
	void legacySlotsAreIndependentAndFeatureGateStillApplies() {
		assertEquals(new TaskSkillAvailability.Status(true, true), availability.inspect(42L, 3L, policy, true));
		policy.setDispatchSkillPackageId(null);
		assertEquals(new TaskSkillAvailability.Status(true, false), availability.inspect(42L, 3L, policy, true));
		policy.setDispatchSkillPackageId(12L);
		policy.setParseSkillPackageId(null);
		assertEquals(new TaskSkillAvailability.Status(false, true), availability.inspect(42L, 3L, policy, true));
		assertEquals(new TaskSkillAvailability.Status(false, false), availability.inspect(42L, 3L, policy, false));
		verifyZeroInteractions(resolver, executor);
	}

	@Test
	void managedSlotsUseReadOnlyBindingInspectionAndNeverLegacyOrExecution() {
		policy.setParseSkillManagementMode("MANAGED");
		policy.setDispatchSkillManagementMode("MANAGED");
		var snapshot = new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, "77", null), "{}", "{}", "original", List.of(), "{}", List.of());
		when(resolver.inspect(any(), any())).thenReturn(snapshot);
		when(executor.available(any(), eq(snapshot))).thenReturn(true);
		assertEquals(new TaskSkillAvailability.Status(true, true), availability.inspect(42L, 3L, policy, true));
		verify(resolver).inspect(eq(new Identity("42", "3", "3", "task-capabilities:7")),
				eq(new Consumer("TASK", "7", "TASK_PARSE", "default")));
		verify(resolver).inspect(any(), eq(new Consumer("TASK", "7", "TASK_DISPATCH", "default")));
		verify(resolver, never()).resolve(any());
		verify(executor, never()).execute(any());
		verifyZeroInteractions(legacy);
	}

	@Test
	void revokedUnauthorizedMissingAndUnsupportedManagedBindingsAreUnavailable() {
		policy.setParseSkillManagementMode("MANAGED");
		when(resolver.inspect(any(), any())).thenThrow(new IllegalArgumentException("RELEASE_NOT_AVAILABLE"));
		assertFalse(availability.inspect(42L, 3L, policy, true).parseAvailable());
		doReturn(null).when(resolver).inspect(any(), any());
		assertFalse(availability.inspect(42L, 3L, policy, true).parseAvailable());
		var absent = new TaskSkillAvailability(legacy, provider(null), provider(executor));
		assertFalse(absent.inspect(42L, 3L, policy, true).parseAvailable());
		verify(executor, never()).execute(any());
		verify(resolver, never()).resolve(any());
	}

	@Test
	void disabledOrUnknownModesDoNotAdvertiseManagedExecution() {
		policy.setEnabled(false);
		assertEquals(new TaskSkillAvailability.Status(false, false), availability.inspect(42L, 3L, policy, true));
		verifyZeroInteractions(legacy, resolver, executor);
		policy.setEnabled(true);
		policy.setParseSkillManagementMode("invalid");
		policy.setDispatchSkillManagementMode(null);
		assertEquals(new TaskSkillAvailability.Status(false, false), availability.inspect(42L, 3L, policy, true));
	}

	@SuppressWarnings("unchecked")
	private <T> ObjectProvider<T> provider(T value) {
		ObjectProvider<T> result = mock(ObjectProvider.class);
		when(result.getIfAvailable()).thenReturn(value);
		return result;
	}

}
