package com.deepdimen.matrix.work.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.entity.SysUserRole;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.admin.mapper.SysUserRoleMapper;
import com.deepdimen.matrix.admin.service.SysMenuService;
import com.deepdimen.matrix.common.data.tenant.TenantBroker;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.entity.WorkTaskDispatchRunEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskDispatchRunMapper;
import com.deepdimen.matrix.work.mapper.WorkTaskMapper;
import com.deepdimen.matrix.work.security.WorkTaskRules;
import com.deepdimen.matrix.work.service.WorkTaskV2Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Durable task runs; enable only after the task schema has been migrated. */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "matrix.work.task-workers-enabled", havingValue = "true")
public class TaskDispatchWorker {

	private final WorkTaskDispatchRunMapper runs;

	private final WorkTaskMapper tasks;

	private final WorkTaskV2Service commands;

	private final TenantUserDirectory directory;

	private final TaskModelRunner models;

	private final TaskPolicyService policies;

	private final SysUserRoleMapper userRoles;

	private final SysMenuService menus;

	private final com.deepdimen.matrix.admin.mapper.SysRoleMapper roles;

	private final ObjectMapper json;

	@org.springframework.beans.factory.annotation.Autowired
	private ManagedTaskDispatchWorker managedDispatch;

	@Scheduled(fixedDelayString = "${matrix.work.dispatch-delay-ms:5000}")
	public void tick() {
		List<WorkTaskDispatchRunEntity> pending = TenantBroker
			.noneAs(() -> runs.selectList(Wrappers.<WorkTaskDispatchRunEntity>lambdaQuery()
				.and(q -> q.eq(WorkTaskDispatchRunEntity::getStatus, "PENDING")
					.or(r -> r.eq(WorkTaskDispatchRunEntity::getStatus, "RUNNING")
						.lt(WorkTaskDispatchRunEntity::getLeaseUntil, now())))
				.orderByAsc(WorkTaskDispatchRunEntity::getCreatedAt)
				.last("LIMIT 5")));
		for (var run : pending) {
			if (run.getTenantId() == null)
				continue;
			try {
				TenantBroker.runAs(run.getTenantId(), tenant -> execute(run));
			}
			catch (Exception ex) {
				log.warn("Task dispatch worker failed for run {}", run.getId());
			}
		}
	}

	public void execute(WorkTaskDispatchRunEntity run) throws Exception {
		String lease = UUID.randomUUID().toString();
		var claim = Wrappers.<WorkTaskDispatchRunEntity>lambdaUpdate()
			.eq(WorkTaskDispatchRunEntity::getTenantId, run.getTenantId())
			.eq(WorkTaskDispatchRunEntity::getId, run.getId())
			.and(q -> q.eq(WorkTaskDispatchRunEntity::getStatus, "PENDING")
				.or(r -> r.eq(WorkTaskDispatchRunEntity::getStatus, "RUNNING")
					.lt(WorkTaskDispatchRunEntity::getLeaseUntil, now())))
			.set(WorkTaskDispatchRunEntity::getStatus, "RUNNING")
			.set(WorkTaskDispatchRunEntity::getLeaseOwner, lease)
			.set(WorkTaskDispatchRunEntity::getLeaseUntil, now().plusMinutes(2));
		if (runs.update(null, claim) != 1)
			return;
		try {
			var task = tasks.selectById(run.getTaskId());
			if (task == null || !Objects.equals(task.getTenantId(), run.getTenantId())
					|| task.getStatus() != WorkTaskStatus.DRAFT
					|| !Objects.equals(task.getCurrentDispatchRunId(), run.getId())
					|| !Objects.equals(task.getVersion(), run.getInputTaskVersion())) {
				runs.update(null, owned(run, lease).set(WorkTaskDispatchRunEntity::getStatus, "SUPERSEDED")
					.set(WorkTaskDispatchRunEntity::getFinishedAt, now()));
				return;
			}
			if (!creatorAuthorized(run))
				throw new IllegalArgumentException("CREATOR_PERMISSION_REVOKED");
			var policy = policies.current(run.getTenantId());
			if (!Boolean.TRUE.equals(policy.getEnabled())
					|| !Objects.equals(policy.getVersion(), run.getPolicyVersion()))
				throw new IllegalArgumentException("POLICY_CHANGED");
			if ("MANAGED".equals(TaskSkillManagementModes.requireMode(policy.getDispatchSkillManagementMode()))) {
				if (managedDispatch == null)
					throw new IllegalStateException("MANAGED_DISPATCH_EXECUTOR_UNAVAILABLE");
				managedDispatch.execute(run, lease);
				return;
			}
			var authority = directory.taskAuthority(run.getTenantId(), run.getCreatorId());
			var candidates = directory.listTenantUsers(run.getTenantId()).stream().filter(user -> {
				var target = directory.taskAuthority(run.getTenantId(), user.userId());
				return !target.generalManager() && WorkTaskRules.canAssign(authority.level(), target.level());
			}).toList();
			if (candidates.isEmpty())
				throw new IllegalArgumentException("NO_LEGAL_CANDIDATES");
			var skill = models.resolveSkill(run.getTenantId(), run.getSkillPackageId());
			String snapshot = json.writeValueAsString(candidates);
			String input = json.writeValueAsString(
					Map.of("title", task.getTitle(), "content", task.getDescription(), "candidates", candidates));
			if (runs.update(null,
					owned(run, lease).set(WorkTaskDispatchRunEntity::getCandidateSnapshot, snapshot)
						.set(WorkTaskDispatchRunEntity::getSkillHash, skill.packageSha256())
						.set(WorkTaskDispatchRunEntity::getInputHash,
								java.util.HexFormat.of()
									.formatHex(java.security.MessageDigest.getInstance("SHA-256")
										.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8))))) != 1)
				return;
			var result = models.run(run.getTenantId(), run.getCreatorId(), "task-dispatch-" + run.getId(),
					"TASK_DISPATCH", run.getModelId(), skill,
					"返回 {\"assigneeId\":\"候选人员 userId\",\"reason\":\"简短原因\"}。只能选择 candidates 中的一人；无法判断时 assigneeId 为 null。",
					input);
			Long selected = Long.valueOf(result.value().path("assigneeId").asText());
			if (candidates.stream().noneMatch(u -> Objects.equals(u.userId(), selected)) || !creatorAuthorized(run))
				throw new IllegalArgumentException("CANDIDATE_OR_CREATOR_INVALID");
			commands.completeDispatch(run.getTenantId(), run.getId(), selected,
					Map.of("leaseOwner", lease, "result", result.value(), "skillHash", skill.packageSha256()));
		}
		catch (Exception ex) {
			commands.failDispatch(run.getTenantId(), run.getId(), "AI_DISPATCH_REQUIRES_MANUAL", lease);
		}
	}

	private boolean creatorAuthorized(WorkTaskDispatchRunEntity run) {
		return TaskDispatchCreatorAuthorization.authorized(run.getTenantId(), run.getCreatorId(), directory, userRoles,
				menus, roles);
	}

	private com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WorkTaskDispatchRunEntity> owned(
			WorkTaskDispatchRunEntity run, String lease) {
		return Wrappers.<WorkTaskDispatchRunEntity>lambdaUpdate()
			.eq(WorkTaskDispatchRunEntity::getTenantId, run.getTenantId())
			.eq(WorkTaskDispatchRunEntity::getId, run.getId())
			.eq(WorkTaskDispatchRunEntity::getStatus, "RUNNING")
			.eq(WorkTaskDispatchRunEntity::getLeaseOwner, lease)
			.gt(WorkTaskDispatchRunEntity::getLeaseUntil, now());
	}

	private static LocalDateTime now() {
		return LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
	}

}
