package com.deepdimen.matrix.work.brief;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.work.api.vo.WorkBriefVO;
import com.deepdimen.matrix.work.entity.WorkBriefReportEntity;
import com.deepdimen.matrix.work.entity.WorkBriefRunEntity;
import com.deepdimen.matrix.work.mapper.WorkBriefRunMapper;
import java.util.Objects;

/** Execution and content review are distinct facts. Missing history stays unknown. */
public final class WorkBriefExecutionMetadata {

	private WorkBriefExecutionMetadata() {
	}

	public static void populate(WorkBriefVO target, WorkBriefReportEntity report, WorkBriefRunMapper runs) {
		if (report.getRunId() == null || report.getTenantId() == null || report.getOwnerUserId() == null)
			return;
		var run = runs.selectOne(Wrappers.<WorkBriefRunEntity>lambdaQuery()
			.eq(WorkBriefRunEntity::getId, report.getRunId())
			.eq(WorkBriefRunEntity::getTenantId, report.getTenantId())
			.eq(WorkBriefRunEntity::getOwnerUserId, report.getOwnerUserId()));
		if (run == null || !Objects.equals(run.getId(), report.getRunId())
				|| !Objects.equals(run.getTenantId(), report.getTenantId())
				|| !Objects.equals(run.getOwnerUserId(), report.getOwnerUserId()))
			return;
		target.setExecutionStatus(run.getStatus());
		target.setSkillRunId(run.getSkillRunId());
	}

}
