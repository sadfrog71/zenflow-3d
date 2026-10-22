package com.deepdimen.matrix.work.brief;

import com.deepdimen.matrix.work.api.vo.WorkBriefVO;
import com.deepdimen.matrix.work.entity.WorkBriefReportEntity;
import com.deepdimen.matrix.work.entity.WorkBriefRunEntity;
import com.deepdimen.matrix.work.mapper.WorkBriefRunMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkBriefExecutionMetadataTest {

	@Test
	void degradedExecutionIsIndependentOfApprovedContent() {
		var report = report();
		var run = new WorkBriefRunEntity();
		run.setId(8L);
		run.setTenantId(42L);
		run.setOwnerUserId(3L);
		run.setStatus("DEGRADED");
		run.setSkillRunId(10L);
		var mapper = mock(WorkBriefRunMapper.class);
		when(mapper.selectOne(any())).thenReturn(run);
		var vo = new WorkBriefVO();
		vo.setReviewerStatus("APPROVED");
		WorkBriefExecutionMetadata.populate(vo, report, mapper);
		assertEquals("DEGRADED", vo.getExecutionStatus());
		assertEquals(10L, vo.getSkillRunId());
		assertEquals("APPROVED", vo.getReviewerStatus());
	}

	@Test
	void absentOrMismatchedRunNeverInventsSuccessOrLeaksAnotherOwner() {
		var mapper = mock(WorkBriefRunMapper.class);
		var vo = new WorkBriefVO();
		WorkBriefExecutionMetadata.populate(vo, report(), mapper);
		assertNull(vo.getExecutionStatus());
		var run = new WorkBriefRunEntity();
		run.setId(8L);
		run.setTenantId(42L);
		run.setOwnerUserId(4L);
		run.setStatus("SUCCEEDED");
		run.setSkillRunId(10L);
		when(mapper.selectOne(any())).thenReturn(run);
		WorkBriefExecutionMetadata.populate(vo, report(), mapper);
		assertNull(vo.getExecutionStatus());
		assertNull(vo.getSkillRunId());
	}

	private WorkBriefReportEntity report() {
		var report = new WorkBriefReportEntity();
		report.setRunId(8L);
		report.setTenantId(42L);
		report.setOwnerUserId(3L);
		return report;
	}

}
