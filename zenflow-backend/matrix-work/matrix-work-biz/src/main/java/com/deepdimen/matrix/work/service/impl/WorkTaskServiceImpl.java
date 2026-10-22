package com.deepdimen.matrix.work.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.deepdimen.matrix.admin.api.dto.BusinessMessageRequest;
import com.deepdimen.matrix.admin.api.service.BusinessMessagePublisher;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory.AssignableUser;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.work.api.dto.WorkTaskCreateDTO;
import com.deepdimen.matrix.work.api.dto.WorkTaskTransitionDTO;
import com.deepdimen.matrix.work.api.dto.WorkTaskUpdateDTO;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.entity.WorkTaskAssignmentEntity;
import com.deepdimen.matrix.work.entity.WorkTaskEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskAssignmentMapper;
import com.deepdimen.matrix.work.mapper.WorkTaskMapper;
import com.deepdimen.matrix.work.security.CurrentOrganizationResolver;
import com.deepdimen.matrix.work.security.WorkTaskAssignmentPolicy;
import com.deepdimen.matrix.work.service.WorkTaskService;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkTaskServiceImpl extends ServiceImpl<WorkTaskMapper, WorkTaskEntity> implements WorkTaskService {

	private static final String ROLE_OWNER = "OWNER";

	private static final String ROLE_ASSIGNEE = "ASSIGNEE";

	private static final String ASSIGNMENT_ACTIVE = "ACTIVE";

	private static final String ASSIGNMENT_INACTIVE = "INACTIVE";

	private static final int MAX_ASSIGNEES = 100;

	private static final Map<WorkTaskStatus, Set<WorkTaskStatus>> ALLOWED_TRANSITIONS = buildAllowedTransitions();

	private final WorkTaskAssignmentPolicy assignmentPolicy;

	private final WorkTaskAssignmentMapper assignmentMapper;

	private final TenantUserDirectory tenantUserDirectory;

	private final CurrentOrganizationResolver organizationResolver;

	private final ObjectProvider<BusinessMessagePublisher> messagePublisherProvider;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public WorkTaskEntity create(WorkTaskCreateDTO request) {
		Long organizationId = organizationResolver.resolve();
		List<Long> assigneeIds = normalizeAssignees(request.getAssigneeIds(), request.getOwnerId());
		assignmentPolicy.requireAssignable(request.getOwnerId(), assigneeIds, organizationId);
		WorkTaskEntity task = new WorkTaskEntity();
		task.setTitle(request.getTitle());
		task.setDescription(request.getDescription());
		task.setPriority(request.getPriority() == null ? "NORMAL" : request.getPriority());
		task.setStatus(WorkTaskStatus.TODO);
		task.setCreatorId(SecurityUtils.getUser().getId());
		task.setOwnerId(request.getOwnerId());
		task.setOrganizationId(organizationId);
		task.setDueAt(request.getDueAt());
		task.setSourceType(request.getSourceType() == null ? "MANUAL" : request.getSourceType());
		task.setSourceRefId(request.getSourceRefId());
		task.setVersion(0);
		save(task);
		syncAssignments(task.getId(), request.getOwnerId(), assigneeIds);
		task.setAssigneeIds(assigneeIds);
		publishNewAssignees(task, assigneeIds, Set.of());
		return task;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public WorkTaskEntity update(Long id, WorkTaskUpdateDTO request) {
		WorkTaskEntity task = requireTaskInCurrentOrganization(id);
		requireLegacyWrite(task);
		Long actorId = SecurityUtils.getUser().getId();
		if (!Objects.equals(task.getCreatorId(), actorId)) {
			throw new IllegalArgumentException("只有任务创建人可以修改任务内容和负责人");
		}
		Long organizationId = task.getOrganizationId();
		List<Long> previousAssignees = activeAssigneeIds(id);
		List<Long> assigneeIds = request.getAssigneeIds() == null
				? normalizeAssignees(previousAssignees, request.getOwnerId())
				: normalizeAssignees(request.getAssigneeIds(), request.getOwnerId());
		assignmentPolicy.requireAssignable(request.getOwnerId(), assigneeIds, organizationId);
		Long previousOwnerId = task.getOwnerId();
		task.setTitle(request.getTitle());
		if (request.getDescription() != null) {
			task.setDescription(request.getDescription());
		}
		if (request.getPriority() != null) {
			task.setPriority(request.getPriority());
		}
		task.setOwnerId(request.getOwnerId());
		if (request.getDueAt() != null) {
			task.setDueAt(request.getDueAt());
		}
		if (!updateById(task)) {
			throw new IllegalStateException("任务已被其他操作更新，请刷新后重试");
		}
		syncAssignments(task.getId(), request.getOwnerId(), assigneeIds);
		task.setAssigneeIds(assigneeIds);
		publishNewAssignees(task, assigneeIds, Set.copyOf(previousAssignees));
		if (!Objects.equals(previousOwnerId, task.getOwnerId()) && !Objects.equals(actorId, task.getOwnerId())) {
			int version = task.getVersion() == null ? 0 : task.getVersion();
			publishMessage(BusinessMessageRequest.builder()
				.bizKey("TASK:" + task.getId() + ":REASSIGNED:" + version + ":" + task.getOwnerId())
				.sourceType("TASK")
				.sourceId(String.valueOf(task.getId()))
				.eventType("TASK_ACCEPTOR_CHANGED")
				.title("你已被指定为任务验收负责人")
				.content(task.getTitle())
				.priority("P1")
				.actionRequired(true)
				.orgId(task.getOrganizationId())
				.targetPath("/todo")
				.targetQuery(Map.of("focus", "tasks", "taskId", String.valueOf(task.getId())))
				.recipientUserIds(List.of(task.getOwnerId()))
				.build());
		}
		return task;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteTask(Long id) {
		WorkTaskEntity task = requireTaskInCurrentOrganization(id);
		requireLegacyWrite(task);
		Long actorId = SecurityUtils.getUser().getId();
		if (!Objects.equals(task.getCreatorId(), actorId)) {
			throw new IllegalArgumentException("只有任务创建人可以删除任务");
		}
		assignmentMapper
			.delete(Wrappers.<WorkTaskAssignmentEntity>lambdaQuery().eq(WorkTaskAssignmentEntity::getTaskId, id));
		if (!removeById(id)) {
			throw new IllegalStateException("任务删除失败，请刷新后重试");
		}
	}

	@Override
	public Page<WorkTaskEntity> pageForCurrentUser(Page<WorkTaskEntity> page, String view, WorkTaskStatus status,
			String keyword) {
		Long userId = SecurityUtils.getUser().getId();
		var query = Wrappers.<WorkTaskEntity>lambdaQuery();
		if (!com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy.hasPermission("work_task_view"))
			query.and(q -> q.isNull(WorkTaskEntity::getTaskContractVersion)
				.or()
				.ne(WorkTaskEntity::getTaskContractVersion, 2));
		query.eq(WorkTaskEntity::getOrganizationId, organizationResolver.resolve());
		if ("assigned".equals(view)) {
			query.eq(WorkTaskEntity::getCreatorId, userId);
		}
		else {
			Long tenantId = TenantContextHolder.getTenantId();
			query.and(scope -> {
				scope.eq(WorkTaskEntity::getOwnerId, userId);
				scope.or()
					.exists("SELECT 1 FROM ai_work_task_assignment assignment"
							+ " WHERE assignment.task_id = ai_work_task.id"
							+ " AND assignment.tenant_id = {0} AND assignment.user_id = {1}"
							+ " AND assignment.role = 'ASSIGNEE' AND assignment.assignment_status = 'ACTIVE'"
							+ " AND assignment.del_flag = '0'", tenantId, userId);
			});
		}
		query.eq(status != null, WorkTaskEntity::getStatus, status)
			.like(StrUtil.isNotBlank(keyword), WorkTaskEntity::getTitle, keyword)
			.orderByAsc(WorkTaskEntity::getDueAt)
			.orderByDesc(WorkTaskEntity::getCreateTime);
		Page<WorkTaskEntity> result = page(page, query);
		hydrateAssignees(result.getRecords());
		return result;
	}

	@Override
	public WorkTaskEntity requireVisibleTask(Long id) {
		WorkTaskEntity task = requireTaskInCurrentOrganization(id);
		if (Objects.equals(task.getTaskContractVersion(), 2))
			com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy.requirePermission("work_task_view");
		Long userId = SecurityUtils.getUser().getId();
		List<Long> assigneeIds = activeAssigneeIds(id);
		if (!Objects.equals(task.getOwnerId(), userId) && !Objects.equals(task.getCreatorId(), userId)
				&& !assigneeIds.contains(userId)) {
			throw new IllegalArgumentException("未找到对应任务");
		}
		task.setAssigneeIds(assigneeIds);
		return task;
	}

	@Override
	public List<AssignableUser> listAssignableUsers() {
		Long tenantId = TenantContextHolder.getTenantId();
		Long organizationId = organizationResolver.resolve();
		return tenantUserDirectory.listOrganizationUsers(tenantId, organizationId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public WorkTaskEntity transition(Long id, WorkTaskTransitionDTO request) {
		WorkTaskEntity task = requireTaskInCurrentOrganization(id);
		requireLegacyWrite(task);
		Long actorId = SecurityUtils.getUser().getId();
		List<Long> assigneeIds = activeAssigneeIds(id);
		Set<WorkTaskStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(task.getStatus(), Set.of());
		if (!allowed.contains(request.getTargetStatus())) {
			throw new IllegalStateException("任务状态不允许从 " + task.getStatus() + " 变更为 " + request.getTargetStatus());
		}
		requireTransitionPermission(task, request.getTargetStatus(), actorId, assigneeIds);
		task.setStatus(request.getTargetStatus());
		if (!updateById(task)) {
			throw new IllegalStateException("任务已被其他操作更新，请刷新后重试");
		}
		task.setAssigneeIds(assigneeIds);
		publishTransitionMessage(task);
		return task;
	}

	private void requireTransitionPermission(WorkTaskEntity task, WorkTaskStatus targetStatus, Long actorId,
			List<Long> assigneeIds) {
		boolean creator = Objects.equals(task.getCreatorId(), actorId);
		boolean owner = Objects.equals(task.getOwnerId(), actorId);
		boolean assignee = assigneeIds.contains(actorId);
		boolean allowed = switch (task.getStatus()) {
			case DRAFT -> creator && targetStatus == WorkTaskStatus.TODO;
			case TODO -> switch (targetStatus) {
				case IN_PROGRESS -> assignee;
				case DONE -> owner;
				case CANCELLED -> creator;
				default -> false;
			};
			case IN_PROGRESS -> assignee
					&& (targetStatus == WorkTaskStatus.BLOCKED || targetStatus == WorkTaskStatus.WAITING_ACCEPTANCE);
			case BLOCKED -> assignee && targetStatus == WorkTaskStatus.IN_PROGRESS;
			case WAITING_ACCEPTANCE ->
				owner && (targetStatus == WorkTaskStatus.DONE || targetStatus == WorkTaskStatus.IN_PROGRESS);
			case DONE, CANCELLED -> false;
		};
		if (!allowed) {
			throw new IllegalArgumentException("当前用户无权执行该任务状态变更");
		}
	}

	private void publishTransitionMessage(WorkTaskEntity task) {
		Long actorId = SecurityUtils.getUser().getId();
		String eventType;
		String title;
		String priority;
		boolean actionRequired;
		List<Long> recipients;
		switch (task.getStatus()) {
			case BLOCKED -> {
				eventType = "TASK_BLOCKED";
				title = "任务进入阻塞状态";
				priority = "P1";
				actionRequired = true;
				recipients = List.of(task.getOwnerId());
			}
			case WAITING_ACCEPTANCE -> {
				eventType = "TASK_WAITING_ACCEPTANCE";
				title = "任务等待验收";
				priority = "P1";
				actionRequired = true;
				recipients = List.of(task.getOwnerId());
			}
			case DONE -> {
				eventType = "TASK_COMPLETED";
				title = "任务已完成";
				priority = "P2";
				actionRequired = false;
				recipients = task.getAssigneeIds();
			}
			default -> {
				return;
			}
		}
		recipients = recipients.stream().filter(id -> !Objects.equals(id, actorId)).distinct().toList();
		if (recipients.isEmpty()) {
			return;
		}
		int version = task.getVersion() == null ? 0 : task.getVersion();
		publishMessage(BusinessMessageRequest.builder()
			.bizKey("TASK:" + task.getId() + ":" + eventType + ":" + version)
			.sourceType("TASK")
			.sourceId(String.valueOf(task.getId()))
			.eventType(eventType)
			.title(title)
			.content(task.getTitle())
			.priority(priority)
			.actionRequired(actionRequired)
			.orgId(task.getOrganizationId())
			.targetPath("/todo")
			.targetQuery(Map.of("focus", "tasks", "taskId", String.valueOf(task.getId())))
			.recipientUserIds(recipients)
			.build());
	}

	private WorkTaskEntity requireTaskInCurrentOrganization(Long id) {
		WorkTaskEntity task = getById(id);
		Long organizationId = organizationResolver.resolve();
		if (task == null || !Objects.equals(task.getOrganizationId(), organizationId)) {
			throw new IllegalArgumentException("未找到对应任务");
		}
		return task;
	}

	private List<Long> normalizeAssignees(List<Long> assigneeIds, Long ownerId) {
		LinkedHashSet<Long> normalized = new LinkedHashSet<>();
		if (assigneeIds != null) {
			assigneeIds.stream().filter(Objects::nonNull).forEach(normalized::add);
		}
		if (normalized.isEmpty() && ownerId != null) {
			normalized.add(ownerId);
		}
		if (normalized.size() > MAX_ASSIGNEES) {
			throw new IllegalArgumentException("任务执行人不能超过" + MAX_ASSIGNEES + "人");
		}
		return List.copyOf(normalized);
	}

	private void syncAssignments(Long taskId, Long ownerId, List<Long> assigneeIds) {
		Map<AssignmentKey, Long> selected = new java.util.LinkedHashMap<>();
		selected.put(new AssignmentKey(ROLE_OWNER, ownerId), ownerId);
		assigneeIds.forEach(userId -> selected.put(new AssignmentKey(ROLE_ASSIGNEE, userId), userId));
		List<WorkTaskAssignmentEntity> existing = assignmentMapper.selectList(
				Wrappers.<WorkTaskAssignmentEntity>lambdaQuery().eq(WorkTaskAssignmentEntity::getTaskId, taskId));
		Map<AssignmentKey, WorkTaskAssignmentEntity> existingByRoleAndUser = existing.stream()
			.collect(java.util.stream.Collectors.toMap(
					assignment -> new AssignmentKey(assignment.getRole(), assignment.getUserId()),
					assignment -> assignment, (left, right) -> left, java.util.LinkedHashMap::new));
		existing.forEach(assignment -> {
			String status = selected.containsKey(new AssignmentKey(assignment.getRole(), assignment.getUserId()))
					? ASSIGNMENT_ACTIVE : ASSIGNMENT_INACTIVE;
			if (!status.equals(assignment.getAssignmentStatus())) {
				assignment.setAssignmentStatus(status);
				assignmentMapper.updateById(assignment);
			}
		});
		selected.forEach((key, userId) -> {
			WorkTaskAssignmentEntity assignment = existingByRoleAndUser.get(key);
			if (assignment != null) {
				return;
			}
			WorkTaskAssignmentEntity created = new WorkTaskAssignmentEntity();
			created.setTaskId(taskId);
			created.setUserId(userId);
			created.setRole(key.role());
			created.setAssignmentStatus(ASSIGNMENT_ACTIVE);
			assignmentMapper.insert(created);
		});
	}

	private List<Long> activeAssigneeIds(Long taskId) {
		return assignmentMapper
			.selectList(Wrappers.<WorkTaskAssignmentEntity>lambdaQuery()
				.eq(WorkTaskAssignmentEntity::getTaskId, taskId)
				.eq(WorkTaskAssignmentEntity::getRole, ROLE_ASSIGNEE)
				.eq(WorkTaskAssignmentEntity::getAssignmentStatus, ASSIGNMENT_ACTIVE)
				.orderByAsc(WorkTaskAssignmentEntity::getId))
			.stream()
			.map(WorkTaskAssignmentEntity::getUserId)
			.distinct()
			.toList();
	}

	private void hydrateAssignees(List<WorkTaskEntity> tasks) {
		if (tasks.isEmpty()) {
			return;
		}
		Map<Long, List<Long>> byTask = assignmentMapper
			.selectList(Wrappers.<WorkTaskAssignmentEntity>lambdaQuery()
				.in(WorkTaskAssignmentEntity::getTaskId, tasks.stream().map(WorkTaskEntity::getId).toList())
				.eq(WorkTaskAssignmentEntity::getRole, ROLE_ASSIGNEE)
				.eq(WorkTaskAssignmentEntity::getAssignmentStatus, ASSIGNMENT_ACTIVE)
				.orderByAsc(WorkTaskAssignmentEntity::getId))
			.stream()
			.collect(java.util.stream.Collectors.groupingBy(WorkTaskAssignmentEntity::getTaskId,
					java.util.LinkedHashMap::new, java.util.stream.Collectors
						.mapping(WorkTaskAssignmentEntity::getUserId, java.util.stream.Collectors.toList())));
		tasks.forEach(task -> task.setAssigneeIds(byTask.getOrDefault(task.getId(), List.of(task.getOwnerId()))));
	}

	private void publishNewAssignees(WorkTaskEntity task, List<Long> assigneeIds, Set<Long> previousAssignees) {
		Long actorId = SecurityUtils.getUser().getId();
		List<Long> recipients = assigneeIds.stream()
			.filter(id -> !previousAssignees.contains(id) && !Objects.equals(actorId, id))
			.toList();
		if (recipients.isEmpty()) {
			return;
		}
		int version = task.getVersion() == null ? 0 : task.getVersion();
		publishMessage(BusinessMessageRequest.builder()
			.bizKey("TASK:" + task.getId() + ":ASSIGNED:" + version + ":" + recipients)
			.sourceType("TASK")
			.sourceId(String.valueOf(task.getId()))
			.eventType("TASK_ASSIGNED")
			.title("收到一项新任务")
			.content(task.getTitle())
			.priority("P1")
			.actionRequired(true)
			.orgId(task.getOrganizationId())
			.targetPath("/todo")
			.targetQuery(Map.of("focus", "tasks", "taskId", String.valueOf(task.getId())))
			.recipientUserIds(recipients)
			.build());
	}

	private void publishMessage(BusinessMessageRequest request) {
		BusinessMessagePublisher publisher = messagePublisherProvider.getIfAvailable();
		if (publisher != null) {
			publisher.publish(request);
		}
	}

	private static Map<WorkTaskStatus, Set<WorkTaskStatus>> buildAllowedTransitions() {
		Map<WorkTaskStatus, Set<WorkTaskStatus>> transitions = new EnumMap<>(WorkTaskStatus.class);
		transitions.put(WorkTaskStatus.DRAFT, EnumSet.of(WorkTaskStatus.TODO));
		transitions.put(WorkTaskStatus.TODO,
				EnumSet.of(WorkTaskStatus.IN_PROGRESS, WorkTaskStatus.DONE, WorkTaskStatus.CANCELLED));
		transitions.put(WorkTaskStatus.IN_PROGRESS,
				EnumSet.of(WorkTaskStatus.BLOCKED, WorkTaskStatus.WAITING_ACCEPTANCE));
		transitions.put(WorkTaskStatus.BLOCKED, EnumSet.of(WorkTaskStatus.IN_PROGRESS));
		transitions.put(WorkTaskStatus.WAITING_ACCEPTANCE, EnumSet.of(WorkTaskStatus.DONE, WorkTaskStatus.IN_PROGRESS));
		return transitions;
	}

	private record AssignmentKey(String role, Long userId) {
	}

	private void requireLegacyWrite(WorkTaskEntity task) {
		if (Integer.valueOf(2).equals(task.getTaskContractVersion())) {
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.CONFLICT, "TASK_CONTRACT_UPGRADE_REQUIRED");
		}
	}

}
