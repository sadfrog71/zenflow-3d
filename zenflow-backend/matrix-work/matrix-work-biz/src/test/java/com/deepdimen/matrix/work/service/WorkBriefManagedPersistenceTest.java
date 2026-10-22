package com.deepdimen.matrix.work.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.service.BusinessMessageReader;
import com.deepdimen.matrix.work.brief.*;
import com.deepdimen.matrix.work.entity.*;
import com.deepdimen.matrix.work.mapper.*;
import com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkBriefManagedPersistenceTest {

	private final WorkBriefRunMapper runs = mock(WorkBriefRunMapper.class);

	private final WorkBriefReportMapper reports = mock(WorkBriefReportMapper.class);

	private final WorkBriefSkillRunner runner = mock(WorkBriefSkillRunner.class);

	private final WorkBriefIdentity identity = new WorkBriefIdentity(42L, 3L, 5L, List.of(), List.of());

	private WorkBriefService service;

	@BeforeEach
	void setup() {
		var tasks = mock(WorkTaskMapper.class);
		var visibility = mock(WorkTaskVisibilityPolicy.class);
		when(visibility.queryFor(42L, 3L)).thenReturn(Wrappers.lambdaQuery());
		when(tasks.selectList(any())).thenReturn(List.of());
		var schedules = mock(WorkScheduleMapper.class);
		when(schedules.selectList(any())).thenReturn(List.of());
		var policies = mock(WorkBriefPolicyMapper.class);
		var policy = new WorkBriefPolicyEntity();
		policy.setId(7L);
		policy.setTenantId(42L);
		policy.setOrgId(5L);
		policy.setEnabled(true);
		policy.setSkillId(1L);
		policy.setSkillVersion("legacy-version");
		policy.setSkillManagementMode("MANAGED");
		policy.setEnabledSources("[]");
		policy.setAllowDeterministicFallback(true);
		when(policies.selectOne(any())).thenReturn(policy);
		doAnswer(call -> {
			WorkBriefRunEntity run = call.getArgument(0);
			run.setId(8L);
			when(runs.selectOne(any())).thenReturn(run);
			return 1;
		}).when(runs).insert(any(WorkBriefRunEntity.class));
		when(runs.updateById(any(WorkBriefRunEntity.class))).thenReturn(1);
		doAnswer(call -> {
			WorkBriefReportEntity report = call.getArgument(0);
			report.setId(9L);
			return 1;
		}).when(reports).insert(any(WorkBriefReportEntity.class));
		service = new WorkBriefService(tasks, visibility, schedules, runs, reports, policies,
				mock(InformationItemMapper.class), mock(WorkEvidenceMapper.class), mock(BusinessMessageReader.class),
				runner, new WorkBriefReviewer(), new ObjectMapper().findAndRegisterModules());
	}

	@Test
    void degradedParentAndReportRetainActualReleaseWithoutInventingZeroUsage() {
        when(runner.execute(any(),any(),any(),eq("8"))).thenAnswer(call -> {
            WorkBriefSnapshot snapshot=call.getArgument(2);
            return new WorkBriefSkillRunner.Execution(new DeterministicWorkSummarySkill().execute(snapshot),
                    null,true,"12","11","10");
        });
        var result=service.run(identity,false);
        assertEquals("DEGRADED",result.getExecutionStatus());assertEquals("APPROVED",result.getReviewerStatus());
        assertEquals(10L,result.getSkillRunId());assertEquals("release:11",result.getSkillVersion());
        var saved=ArgumentCaptor.forClass(WorkBriefRunEntity.class);
        verify(runs).updateById(saved.capture());
        assertEquals("DEGRADED",saved.getValue().getStatus());assertEquals(12L,saved.getValue().getSkillId());
        assertNull(saved.getValue().getTotalTokens());
        assertEquals("MANAGED_FALLBACK_USAGE_UNAVAILABLE",saved.getValue().getUsageSource());
        verify(runner,never()).deterministic(any(),any());
    }

	@Test
    void failedParentKeepsActualRunAndDoesNotSaveAReportOrInvokeLegacyFallback() {
        when(runner.execute(any(),any(),any(),eq("8"))).thenThrow(new ManagedWorkBriefFailure("12","11","10",
                new IllegalArgumentException("OUTPUT_INVALID")));
        assertThrows(IllegalStateException.class,()->service.run(identity,false));
        var saved=ArgumentCaptor.forClass(WorkBriefRunEntity.class);
        verify(runs).updateById(saved.capture());
        assertEquals("FAILED",saved.getValue().getStatus());assertEquals(10L,saved.getValue().getSkillRunId());
        assertEquals(12L,saved.getValue().getSkillId());assertEquals("release:11",saved.getValue().getSkillVersion());
        assertNull(saved.getValue().getTotalTokens());
        verify(reports,never()).insert(any(WorkBriefReportEntity.class));
        verify(runner,never()).deterministic(any(),any());
    }

}
