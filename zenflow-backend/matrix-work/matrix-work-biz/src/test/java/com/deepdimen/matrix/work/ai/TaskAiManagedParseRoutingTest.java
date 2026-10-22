package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.work.api.service.TaskSkillResolver.TaskSkill;
import com.deepdimen.matrix.work.entity.*;
import com.deepdimen.matrix.work.service.WorkTaskV2Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class TaskAiManagedParseRoutingTest {

	private final TaskPolicyService policies = mock(TaskPolicyService.class);

	private final TaskModelRunner legacy = mock(TaskModelRunner.class);

	private final WorkTaskV2Service tasks = mock(WorkTaskV2Service.class);

	private final ManagedTaskParseRunner managed = mock(ManagedTaskParseRunner.class);

	private final ObjectMapper json = new ObjectMapper();

	private final TaskAiController controller = new TaskAiController(policies, legacy, tasks, json, managed);

	private final WorkTaskPolicyEntity policy = new WorkTaskPolicyEntity();

	private final TaskAiController.ParseInput input = new TaskAiController.ParseInput("original", null);

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(42L);
		var user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(3L);
		SecurityContextHolder.getContext()
			.setAuthentication(new UsernamePasswordAuthenticationToken(user, "n/a", List.of()));
		policy.setId(7L);
		policy.setEnabled(true);
		policy.setChatModelId(77L);
		policy.setParseSkillPackageId(11L);
		when(policies.current(42L)).thenReturn(policy);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void managedParseReturnsItsProvenanceAndNeverCallsLegacy() throws Exception {
		policy.setParseSkillManagementMode("MANAGED");
		var preview = new ManagedTaskParseRunner.Preview(json.createArrayNode(), json.createArrayNode(), "11", "hash",
				"77", "8", "12", "22", "10", true);
		when(managed.execute(7L, null, "original")).thenReturn(preview);
		assertSame(preview, controller.parse(input).getData());
		verify(managed).execute(7L, null, "original");
		verifyZeroInteractions(legacy);
	}

	@Test
	void managedFailureCannotSilentlyUseTheOldPackage() {
		policy.setParseSkillManagementMode("MANAGED");
		var failure = new ManagedTaskParseFailure("8", "12", "22", "10", new IllegalStateException("UNKNOWN"));
		when(managed.execute(7L, null, "original")).thenThrow(failure);
		assertSame(failure, assertThrows(ManagedTaskParseFailure.class, () -> controller.parse(input)));
		verifyZeroInteractions(legacy);
	}

	@Test
	void legacyParseRemainsIndependentOfManagedDispatch() throws Exception {
		policy.setDispatchSkillManagementMode("MANAGED");
		var source = new TaskSkill(11L, "v1", "original", "digest", "hash");
		when(legacy.resolveSkill(42L, 11L)).thenReturn(source);
		when(legacy.run(eq(42L), eq(3L), any(), eq("TASK_PARSE"), eq(77L), eq(source), any(), any()))
			.thenReturn(new TaskModelRunner.Result(
					json.readTree("{\"tasks\":[{\"title\":\"T\",\"content\":\"C\"}],\"warnings\":[]}"), "77", 11L,
					"digest", "hash"));
		var result = (java.util.Map<?, ?>) controller.parse(input).getData();
		assertEquals(true, result.get("confirmationRequired"));
		assertEquals("11", result.get("skillPackageId"));
		verify(legacy).run(eq(42L), eq(3L), any(), eq("TASK_PARSE"), eq(77L), eq(source), any(), any());
		verifyZeroInteractions(managed);
	}

	@Test
	void unknownModeOrDisabledPolicyRejectsBothPaths() {
		policy.setParseSkillManagementMode("INVALID");
		assertThrows(IllegalArgumentException.class, () -> controller.parse(input));
		policy.setParseSkillManagementMode("MANAGED");
		policy.setEnabled(false);
		assertThrows(IllegalArgumentException.class, () -> controller.parse(input));
		verifyZeroInteractions(legacy, managed);
	}

	@Test
	void originalParentAuthorizationPrecedesEitherModelPath() throws Exception {
		var parent = mock(WorkTaskDetailVO.class);
		when(parent.getAllowedActions()).thenReturn(List.of());
		when(tasks.detail(99L)).thenReturn(parent);
		for (String mode : List.of("LEGACY", "MANAGED")) {
			policy.setParseSkillManagementMode(mode);
			assertThrows(org.springframework.web.server.ResponseStatusException.class,
					() -> controller.parse(new TaskAiController.ParseInput("text", 99L)));
		}
		verifyZeroInteractions(legacy, managed);
	}

}
