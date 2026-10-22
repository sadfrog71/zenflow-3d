package com.deepdimen.matrix.work.ai;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.entity.WorkTaskDispatchRunEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskDispatchRunMapper;
import com.deepdimen.matrix.work.service.WorkTaskV2Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ManagedTaskDispatchWorkerTest {

	private final WorkTaskDispatchRunMapper runs = mock(WorkTaskDispatchRunMapper.class);

	private final TaskDispatchCreatorAuthorization creators = mock(TaskDispatchCreatorAuthorization.class);

	private final TaskDispatchWorkerScope scope = new TaskDispatchWorkerScope(runs, creators);

	private final TaskDispatchSkillInputs inputs = mock(TaskDispatchSkillInputs.class);

	private final ManagedTaskDispatchRunner runner = mock(ManagedTaskDispatchRunner.class);

	private final WorkTaskV2Service commands = mock(WorkTaskV2Service.class);

	private final ObjectMapper json = new ObjectMapper();

	private final ManagedTaskDispatchWorker worker = new ManagedTaskDispatchWorker(scope, inputs, runner, runs,
			commands, json);

	private final WorkTaskDispatchRunEntity run = new WorkTaskDispatchRunEntity();

	@BeforeEach
	void setup() throws Exception {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
				WorkTaskDispatchRunEntity.class);
		TenantContextHolder.setTenantId(42L);
		SecurityContextHolder.clearContext();
		run.setId(8L);
		run.setTenantId(42L);
		run.setCreatorId(3L);
		run.setStatus("RUNNING");
		run.setLeaseOwner("owner");
		run.setLeaseUntil(LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusMinutes(1));
		when(runs.selectOne(any())).thenReturn(run);
		when(runs.update(isNull(), any())).thenReturn(1);
		when(creators.authorized(42L, 3L)).thenReturn(true);
		when(runner.execute()).thenReturn(suggestion(4L));
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void successfulSuggestionUsesExistingCommandWithActualProvenanceAndClosesScope() throws Exception {
		worker.execute(run, "owner");
		var metadata = org.mockito.ArgumentCaptor.forClass(Map.class);
		verify(commands).completeDispatch(eq(42L), eq(8L), eq(4L), metadata.capture());
		assertEquals("22", metadata.getValue().get("actualReleaseId"));
		assertEquals("10", metadata.getValue().get("skillRunId"));
		verify(inputs).require(9L);
		assertThrows(IllegalArgumentException.class, scope::require);
	}

	@Test
    void nullSuggestionRequiresManualAssignment() throws Exception {
        when(runner.execute()).thenReturn(suggestion(null));
        worker.execute(run,"owner");
        verify(commands).failDispatch(42L,8L,"TASK_SKILL_NO_SUGGESTION","owner");
        verify(commands,never()).completeDispatch(any(),any(),any(),any());
    }

	@Test
	void unknownResultRecordsActualStatusWithoutRetryOrAssignment() throws Exception {
		var result = new ExecuteResult("10", RunStatus.UNKNOWN, null, "ref", List.of(), "22", null, null,
				ErrorCode.RESULT_UNKNOWN);
		when(runner.execute())
			.thenThrow(new ManagedTaskDispatchFailure("9", "12", result, new IllegalStateException("timeout")));
		worker.execute(run, "owner");
		verify(commands).failDispatch(42L, 8L, "TASK_SKILL_UNKNOWN", "owner");
		verify(runner, times(1)).execute();
		verify(commands, never()).completeDispatch(any(), any(), any(), any());
		var update = org.mockito.ArgumentCaptor
			.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
		verify(runs).update(isNull(), update.capture());
		assertTrue(update.getValue()
			.getParamNameValuePairs()
			.values()
			.stream()
			.anyMatch(v -> v.toString().contains("\"skillRunStatus\":\"UNKNOWN\"")));
		assertThrows(IllegalArgumentException.class, scope::require);
	}

	@Test
    void losingLeaseOrRecordCasPreventsFinalMutation() throws Exception {
        when(runs.update(isNull(),any())).thenReturn(0);
        worker.execute(run,"owner");
        verifyZeroInteractions(commands);
        when(runner.execute()).thenAnswer(call -> { run.setLeaseOwner("other"); return suggestion(4L); });
        assertThrows(IllegalArgumentException.class,()->worker.execute(run,"owner"));
        verifyZeroInteractions(commands);
        assertThrows(IllegalArgumentException.class,scope::require);
    }

	@Test
    void changedPolicyOrCandidatesStopBeforeFinalMutation() {
        when(inputs.require(9L)).thenThrow(new IllegalArgumentException("TASK_POLICY_CHANGED"));
        assertThrows(IllegalArgumentException.class,()->worker.execute(run,"owner"));
        verifyZeroInteractions(commands);
        assertThrows(IllegalArgumentException.class,scope::require);
    }

	private ManagedTaskDispatchRunner.Suggestion suggestion(Long assignee) throws Exception {
		return new ManagedTaskDispatchRunner.Suggestion(assignee,
				json.readTree("{\"assigneeId\":\"4\",\"reason\":\"suitable\"}"), "hash", "9", "12", "22", "10");
	}

}
