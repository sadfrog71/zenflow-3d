package com.deepdimen.matrix.work.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.work.entity.WorkTaskDispatchRunEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskDispatchRunMapper;
import com.deepdimen.matrix.work.service.WorkTaskV2Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Managed dispatch bridge; a suggestion never directly mutates task assignment. */
@Component
@RequiredArgsConstructor
public class ManagedTaskDispatchWorker {

	private final TaskDispatchWorkerScope scope;

	private final TaskDispatchSkillInputs inputs;

	private final ManagedTaskDispatchRunner runner;

	private final WorkTaskDispatchRunMapper runs;

	private final WorkTaskV2Service commands;

	private final ObjectMapper json;

	public void execute(WorkTaskDispatchRunEntity run, String leaseOwner) throws Exception {
		try (var lease = scope.open(run.getTenantId(), run.getId(), leaseOwner)) {
			ManagedTaskDispatchRunner.Suggestion suggestion;
			try {
				suggestion = runner.execute();
			}
			catch (ManagedTaskDispatchFailure failure) {
				// Preserve the actual Skill status. A manual outcome does not relabel
				// UNKNOWN as FAILED.
				Map<String, Object> metadata = new LinkedHashMap<>();
				metadata.put("businessRunId", failure.businessRunId());
				metadata.put("skillId", failure.skillId());
				if (failure.result() != null) {
					metadata.put("skillRunId", failure.result().skillRunId());
					metadata.put("actualReleaseId", failure.result().actualReleaseId());
					metadata.put("skillRunStatus", failure.result().status().name());
				}
				String reason = failure.result() == null ? "TASK_SKILL_PREPARATION_FAILED"
						: failure.result().status() == com.deepdimen.matrix.skill.api.SkillContract.RunStatus.SUCCEEDED
								? "TASK_SKILL_RESULT_REJECTED" : "TASK_SKILL_" + failure.result().status().name();
				if (record(run, leaseOwner, metadata))
					commands.failDispatch(run.getTenantId(), run.getId(), reason, leaseOwner);
				return;
			}
			// Revalidate policy, saved candidates and live lease after model execution.
			inputs.require(Long.valueOf(suggestion.businessRunId()));
			scope.require();
			Map<String, Object> metadata = new LinkedHashMap<>();
			metadata.put("leaseOwner", leaseOwner);
			metadata.put("result", suggestion.output());
			metadata.put("skillHash", suggestion.sourcePackageHash());
			metadata.put("businessRunId", suggestion.businessRunId());
			metadata.put("skillId", suggestion.skillId());
			metadata.put("actualReleaseId", suggestion.actualReleaseId());
			metadata.put("skillRunId", suggestion.skillRunId());
			metadata.put("skillRunStatus", "SUCCEEDED");
			if (!record(run, leaseOwner, metadata))
				return;
			if (suggestion.assigneeId() == null) {
				commands.failDispatch(run.getTenantId(), run.getId(), "TASK_SKILL_NO_SUGGESTION", leaseOwner);
				return;
			}
			commands.completeDispatch(run.getTenantId(), run.getId(), suggestion.assigneeId(), metadata);
		}
	}

	private boolean record(WorkTaskDispatchRunEntity run, String owner, Map<String, Object> metadata) throws Exception {
		// Persistence still requires ownership even when an execution authorization has
		// been revoked.
		return runs.update(null,
				Wrappers.<WorkTaskDispatchRunEntity>lambdaUpdate()
					.eq(WorkTaskDispatchRunEntity::getTenantId, run.getTenantId())
					.eq(WorkTaskDispatchRunEntity::getId, run.getId())
					.eq(WorkTaskDispatchRunEntity::getStatus, "RUNNING")
					.eq(WorkTaskDispatchRunEntity::getLeaseOwner, owner)
					.gt(WorkTaskDispatchRunEntity::getLeaseUntil, LocalDateTime.now(ZoneId.of("Asia/Shanghai")))
					.set(WorkTaskDispatchRunEntity::getResultJson, json.writeValueAsString(metadata))) == 1;
	}

}
