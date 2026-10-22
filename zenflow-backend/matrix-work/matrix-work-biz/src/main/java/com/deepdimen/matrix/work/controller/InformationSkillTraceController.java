package com.deepdimen.matrix.work.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.work.api.entity.InformationRun;
import com.deepdimen.matrix.work.entity.InformationSkillInvocationEntity;
import com.deepdimen.matrix.work.mapper.InformationRunMapper;
import com.deepdimen.matrix.work.mapper.InformationSkillInvocationMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Business association metadata only; snapshots and generated content are not exposed.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/work/information/admin/run")
public class InformationSkillTraceController {

	@org.springframework.beans.factory.annotation.Autowired
	private org.springframework.beans.factory.ObjectProvider<com.deepdimen.matrix.skill.api.SkillRunMetadataReader> metadata;

	private final InformationRunMapper runs;

	private final InformationSkillInvocationMapper invocations;

	@GetMapping("/{runId}/skill-traces")
	@HasPermission("work_information_view")
	public R<List<Trace>> get(@PathVariable Long runId, @RequestParam(required = false) Long beforeId) {
		return R.ok(read(runId, beforeId));
	}

	List<Trace> read(Long runId, Long beforeId) {
		Long tenant = TenantContextHolder.getTenantId();
		if (tenant == null || tenant <= 0 || runId == null || runId <= 0 || (beforeId != null && beforeId <= 0))
			throw new IllegalArgumentException("INFORMATION_RUN_NOT_FOUND");
		var run = runs.selectOne(Wrappers.<InformationRun>query().eq("tenant_id", tenant).eq("id", runId));
		if (run == null || !runId.equals(run.getId()))
			throw new IllegalArgumentException("INFORMATION_RUN_NOT_FOUND");
		var query = Wrappers.<InformationSkillInvocationEntity>query()
			.select("id", "actor_id", "tenant_id", "collection_run_id", "policy_id", "slot_code", "document_digest",
					"parent_invocation_id", "skill_run_id", "status", "created_at")
			.eq("tenant_id", tenant)
			.eq("collection_run_id", runId)
			.eq("policy_id", run.getPolicyId())
			.lt(beforeId != null, "id", beforeId)
			.orderByDesc("id")
			.last("LIMIT 50");
		return invocations.selectList(query).stream().map(row -> {
			if (!tenant.equals(row.getTenantId()) || !runId.equals(row.getCollectionRunId())
					|| !run.getPolicyId().equals(row.getPolicyId()))
				throw new IllegalArgumentException("INFORMATION_RUN_NOT_FOUND");
			var reader = metadata == null ? null : metadata.getIfAvailable();
			var execution = reader == null ? null
					: reader.find(tenant.toString(), row.getActorId(),
							new com.deepdimen.matrix.skill.api.SkillContract.Consumer("INFORMATION",
									row.getPolicyId().toString(), row.getSlotCode(), "default"),
							row.getId().toString());
			if (execution != null && row.getSkillRunId() != null
					&& !row.getSkillRunId().toString().equals(execution.skillRunId()))
				throw new IllegalStateException("SKILL_RUN_ASSOCIATION_MISMATCH");
			return new Trace(row.getId().toString(), row.getSlotCode(), row.getDocumentDigest(),
					row.getParentInvocationId() == null ? null : row.getParentInvocationId().toString(),
					row.getSkillRunId() == null ? null : row.getSkillRunId().toString(), row.getStatus(),
					row.getCreatedAt(), execution);
		}).toList();
	}

	public record Trace(String id, String slotCode, String documentDigest, String parentInvocationId, String skillRunId,
			String resultState, LocalDateTime createdAt,
			com.deepdimen.matrix.skill.api.SkillRunMetadataReader.Metadata execution) {
	}

}
