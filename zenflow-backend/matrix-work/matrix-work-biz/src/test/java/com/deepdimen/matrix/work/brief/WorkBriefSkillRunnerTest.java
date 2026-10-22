package com.deepdimen.matrix.work.brief;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.deepdimen.matrix.assistant.generation.AssistantGenerationAdapter;
import com.deepdimen.matrix.assistant.generation.AssistantGenerationAdapter.GenerationRequest;
import com.deepdimen.matrix.assistant.generation.AssistantGenerationAdapter.GenerationResult;
import com.deepdimen.matrix.work.brief.WorkBriefSnapshot.TaskFact;
import com.deepdimen.matrix.work.entity.WorkBriefPolicyEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

class WorkBriefSkillRunnerTest {

	@Test
	void invokesDesignatedSkillAndFallsBackWhenOutputIsInvalid() {
		AssistantGenerationAdapter adapter = mock(AssistantGenerationAdapter.class);
		when(adapter.generate(any())).thenReturn(new GenerationResult(true, "not-json", null));
		WorkBriefSkillRunner runner = new WorkBriefSkillRunner(adapter, new DeterministicWorkSummarySkill(),
				new ObjectMapper().findAndRegisterModules());
		WorkBriefPolicyEntity policy = new WorkBriefPolicyEntity();
		policy.setAllowDeterministicFallback(true);
		LocalDateTime now = LocalDateTime.of(2026, 8, 8, 9, 0);
		WorkBriefSnapshot snapshot = new WorkBriefSnapshot(10L, 20L, now,
				List.of(new TaskFact(1L, "租户内待办", "", "HIGH", "TODO", now.plusHours(2), "MANUAL")),
				List.of(), List.of(), List.of(), List.of(), null);

		WorkBriefSkillRunner.Execution execution = runner.execute(
				new WorkBriefIdentity(10L, 20L, 30L, List.of(), List.of()), policy, snapshot);

		assertTrue(execution.deterministicFallback());
		assertEquals(1, execution.draft().metrics().get("pendingTasks"));
		ArgumentCaptor<GenerationRequest> request = ArgumentCaptor.forClass(GenerationRequest.class);
		verify(adapter).generate(request.capture());
		assertEquals(List.of(WorkBriefSkillRunner.SKILL_CODE), request.getValue().explicitSkillCodes());
		assertEquals("WORK_BRIEF", request.getValue().routeChannel());
		assertEquals("10", request.getValue().identity().tenantId());
	}

}
