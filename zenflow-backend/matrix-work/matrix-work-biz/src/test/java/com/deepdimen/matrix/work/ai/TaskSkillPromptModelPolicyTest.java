package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.entity.WorkTaskPolicyEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskPolicyMapper;
import java.util.List;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class TaskSkillPromptModelPolicyTest {

	private final WorkTaskPolicyMapper mapper = mock(WorkTaskPolicyMapper.class);

	private final TenantUserDirectory users = mock(TenantUserDirectory.class);

	private final WorkTaskPolicyEntity policy = new WorkTaskPolicyEntity();

	private final TaskSkillPromptModelPolicy adapter = new TaskSkillPromptModelPolicy(mapper, users);

	private final Identity actor = new Identity("42", "3", "3", "trace");

	private Consumer consumer(String slot) {
		return new Consumer("TASK", "7", slot, "default");
	}

	private ResolvedSkillSnapshot release(String model) {
		return new ResolvedSkillSnapshot("1", "2", "digest", "9", RuntimeKind.PROMPT,
				new ExecutorRef("PROMPT", null, model, null), "{}", "{}", "original", List.of(), "{}", List.of());
	}

	@BeforeEach
	void setup() {
		policy.setTenantId(42L);
		policy.setId(7L);
		policy.setEnabled(true);
		policy.setChatModelId(77L);
		when(mapper.selectOne(any())).thenReturn(policy);
		when(users.isAssignableUser(42L, 3L)).thenReturn(true);
	}

	@Test
	void bothSlotsUseThePolicyModelOnlyWhenItMatchesTheFrozenReference() {
		for (String slot : List.of("TASK_PARSE", "TASK_DISPATCH")) {
			var selected = adapter.resolve(actor, consumer(slot), release("77"));
			assertEquals("77", selected.modelId());
			assertEquals(slot, selected.purpose());
		}
		assertThrows(IllegalArgumentException.class,
				() -> adapter.resolve(actor, consumer("WORK_BRIEF"), release("77")));
	}

	@Test
	void policyModelChangesOrMissingReferencesCannotUseTheDefaultModel() {
		assertEquals("TASK_MODEL_BINDING_CHANGED", assertThrows(IllegalArgumentException.class,
				() -> adapter.resolve(actor, consumer("TASK_PARSE"), release(null)))
			.getMessage());
		policy.setChatModelId(88L);
		assertEquals("TASK_MODEL_BINDING_CHANGED", assertThrows(IllegalArgumentException.class,
				() -> adapter.resolve(actor, consumer("TASK_PARSE"), release("77")))
			.getMessage());
	}

	@Test
	void tenantOwnershipMembershipEnabledStatusAndSelfSubjectAreRechecked() {
		assertThrows(IllegalArgumentException.class,
				() -> adapter.resolve(new Identity("42", "3", "4", "trace"), consumer("TASK_PARSE"), release("77")));
		when(users.isAssignableUser(42L, 3L)).thenReturn(false);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.resolve(actor, consumer("TASK_PARSE"), release("77")));
		when(users.isAssignableUser(42L, 3L)).thenReturn(true);
		policy.setTenantId(43L);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.resolve(actor, consumer("TASK_PARSE"), release("77")));
		policy.setTenantId(42L);
		policy.setEnabled(false);
		assertThrows(IllegalArgumentException.class,
				() -> adapter.resolve(actor, consumer("TASK_PARSE"), release("77")));
	}

	@Test
	void parsingPromptCarriesServerModeAndOriginalBusinessOutputRules() {
		var prompt = adapter.resolve(actor, consumer("TASK_PARSE"), release("77")).userPrompt();
		assertEquals(TaskParseContract.PROMPT, prompt);
		assertTrue(prompt.contains("parseMode"));
		assertTrue(prompt.contains("maxTasks"));
		assertTrue(prompt.contains("+08:00"));
		assertTrue(prompt.contains("用户确认"));
		assertFalse(
				adapter.resolve(actor, consumer("TASK_DISPATCH"), release("77")).userPrompt().contains("parseMode"));
	}

}
