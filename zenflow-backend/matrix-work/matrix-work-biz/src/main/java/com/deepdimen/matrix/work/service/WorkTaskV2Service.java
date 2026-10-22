package com.deepdimen.matrix.work.service;

import static com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy.hasPermission;
import static com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy.requirePermission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.work.api.dto.*;
import com.deepdimen.matrix.work.api.service.TaskFileAccess;
import org.springframework.beans.factory.ObjectProvider;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.entity.*;
import com.deepdimen.matrix.work.mapper.*;
import com.deepdimen.matrix.work.security.WorkTaskRules;
import com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy;
import com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy.Actor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Transactional task commands; publishing and model calls run only after commit in
 * workers.
 */
@Service
public class WorkTaskV2Service {

	private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

	private final WorkTaskMapper tasks;

	private final WorkTaskAssignmentMapper assignments;

	private final WorkTaskTransferMapper transfers;

	private final WorkTaskSubmissionMapper submissions;

	private final WorkTaskEventMapper events;

	private final WorkTaskDispatchRunMapper dispatchRuns;

	private final WorkTaskPolicyMapper policies;

	private final WorkTaskFileMapper files;

	private final ObjectProvider<TaskFileAccess> fileAccess;

	private final TenantUserDirectory directory;

	private final WorkTaskVisibilityPolicy visibility;

	private final ObjectMapper json;

	private final TransactionTemplate transaction;

	public WorkTaskV2Service(WorkTaskMapper tasks, WorkTaskAssignmentMapper assignments,
			WorkTaskTransferMapper transfers, WorkTaskSubmissionMapper submissions, WorkTaskEventMapper events,
			WorkTaskDispatchRunMapper dispatchRuns, WorkTaskPolicyMapper policies, TenantUserDirectory directory,
			WorkTaskVisibilityPolicy visibility, ObjectMapper json, PlatformTransactionManager transactionManager,
			WorkTaskFileMapper files, ObjectProvider<TaskFileAccess> fileAccess) {
		this.tasks = tasks;
		this.assignments = assignments;
		this.transfers = transfers;
		this.submissions = submissions;
		this.events = events;
		this.dispatchRuns = dispatchRuns;
		this.policies = policies;
		this.directory = directory;
		this.visibility = visibility;
		this.json = json;
		this.transaction = new TransactionTemplate(transactionManager);
		this.files = files;
		this.fileAccess = fileAccess;
	}

	public record Filter(String view, WorkTaskStatus status, String keyword, String risk, Long personId, Long deptId,
			String taskLevel) {
	}

	public Page<WorkTaskDetailVO> page(long current, long size, Filter filter) {
		Actor actor = readActor();
		Page<WorkTaskEntity> rows = tasks.selectPage(new Page<>(positive(current), bounded(size)),
				filtered(actor, filter));
		Page<WorkTaskDetailVO> result = new Page<>(rows.getCurrent(), rows.getSize(), rows.getTotal());
		result.setRecords(rows.getRecords().stream().map(t -> view(t, actor, false)).toList());
		return result;
	}

	public void requireHistoryVisible(Long id) {
		Actor actor = readActor();
		if (pendingReceiverOnly(visible(id, actor), actor))
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TASK_HISTORY_NOT_FOUND");
	}

	public WorkTaskDetailVO detail(Long id) {
		Actor actor = readActor();
		return view(visible(id, actor), actor, true);
	}

	public Map<String, Object> statistics(Filter filter) {
		Actor actor = readActor();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("total", tasks.selectCount(filtered(actor, filter)));
		Map<String, Long> byStatus = new LinkedHashMap<>();
		for (WorkTaskStatus status : WorkTaskStatus.values())
			byStatus.put(status.name(),
					tasks.selectCount(filtered(actor, filter).eq(WorkTaskEntity::getStatus, status)));
		result.put("byStatus", byStatus);
		result.put("overdue", tasks.selectCount(risk(filtered(actor, filter), "OVERDUE")));
		result.put("asOf", OffsetDateTime.now(ZONE));
		return result;
	}

	public Page<Map<String, Object>> workloads(long current, long size, String keyword, Filter filter) {
		Actor actor = readActor();
		Set<Long> allowedPeople = actor.authority().generalManager() ? null : visibility.managedUsers(actor);
		if (allowedPeople != null)
			allowedPeople.add(actor.userId());
		List<TenantUserDirectory.AssignableUser> people = directory.listTenantUsers(actor.tenantId())
			.stream()
			.filter(u -> allowedPeople == null || allowedPeople.contains(u.userId()))
			.filter(u -> matches(u, keyword))
			.filter(u -> filter == null || filter.deptId() == null
					|| directory.isOrganizationMember(actor.tenantId(), u.userId(), filter.deptId()))
			.filter(u -> filter == null || filter.personId() == null || Objects.equals(u.userId(), filter.personId()))
			.sorted(Comparator.comparing(TenantUserDirectory.AssignableUser::userId))
			.toList();
		return slice(people, current, size, u -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("userId", u.userId());
			row.put("displayName", u.displayName());
			var q = filtered(actor, filter);
			executor(q, u.userId(), actor.tenantId());
			row.put("total", tasks.selectCount(q));
			var active = filtered(actor, filter);
			executor(active, u.userId(), actor.tenantId());
			row.put("active", tasks.selectCount(
					active.notIn(WorkTaskEntity::getStatus, List.of(WorkTaskStatus.DONE, WorkTaskStatus.CANCELLED))));
			var late = filtered(actor, filter);
			executor(late, u.userId(), actor.tenantId());
			row.put("overdue", tasks.selectCount(risk(late, "OVERDUE")));
			return row;
		});
	}

	public Page<Map<String, Object>> candidates(String purpose, Long taskId, String keyword, Long deptId, long current,
			long size) {
		Actor actor = readActor();
		if (!Set.of("EXECUTOR", "ACCEPTOR", "TRANSFER").contains(purpose))
			throw bad("INVALID_CANDIDATE_PURPOSE");
		if (taskId != null)
			visible(taskId, actor);
		if ("TRANSFER".equals(purpose)) {
			if (taskId == null)
				throw bad("TASK_REQUIRED");
			var task = visible(taskId, actor);
			if (!Objects.equals(task.getPrimaryAssigneeId(), actor.userId()))
				throw forbidden();
			requirePermission("work_task_transfer");
		}
		else
			requirePermission("work_task_create");
		var people = directory.listTenantUsers(actor.tenantId())
			.stream()
			.filter(u -> matches(u, keyword))
			.filter(u -> deptId == null || directory.isOrganizationMember(actor.tenantId(), u.userId(), deptId))
			.sorted(Comparator.comparing(TenantUserDirectory.AssignableUser::userId))
			.toList();
		return slice(people, current, size, u -> {
			var target = directory.taskAuthority(actor.tenantId(), u.userId());
			boolean permitted = "ACCEPTOR".equals(purpose) || ("TRANSFER".equals(purpose)
					? actor.authority().level() != null && Objects.equals(actor.authority().level(), target.level())
							&& !Objects.equals(u.userId(), actor.userId())
					: WorkTaskRules.canAssign(actor.authority().level(), target.level()));
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("userId", u.userId());
			row.put("displayName", u.displayName());
			row.put("username", u.username());
			row.put("departmentIds", directory.listOrganizationIds(actor.tenantId(), u.userId()));
			row.put("assignable", permitted);
			row.put("unavailableReason", permitted ? "" : actor.authority().level() == null || target.level() == null
					? "ORG_RELATION_UNRESOLVED" : "ASSIGNEE_NOT_ALLOWED");
			return row;
		});
	}

	public Page<Map<String, Object>> eventPage(Long id, String category, long current, long size) {
		Actor actor = readActor();
		WorkTaskEntity task = visible(id, actor);
		if (pendingReceiverOnly(task, actor))
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TASK_HISTORY_NOT_FOUND");
		var query = Wrappers.<WorkTaskEventEntity>lambdaQuery()
			.eq(WorkTaskEventEntity::getTenantId, actor.tenantId())
			.eq(WorkTaskEventEntity::getTaskId, id);
		if (category != null && !category.isBlank()) {
			if ("PROGRESS".equalsIgnoreCase(category))
				query.in(WorkTaskEventEntity::getType,
						List.of("TASK_PROGRESS", "TASK_STARTED", "TASK_BLOCKED", "TASK_RESUMED", "TASK_SUBMITTED"));
			else if ("NOTIFICATION".equalsIgnoreCase(category))
				query.eq(WorkTaskEventEntity::getType, "TASK_NUDGED");
			else if ("CHANGE".equalsIgnoreCase(category))
				query.notIn(WorkTaskEventEntity::getType, List.of("TASK_PROGRESS", "TASK_STARTED", "TASK_BLOCKED",
						"TASK_RESUMED", "TASK_SUBMITTED", "TASK_NUDGED"));
			else
				throw bad("INVALID_EVENT_CATEGORY");
		}
		Page<WorkTaskEventEntity> page = events.selectPage(new Page<>(positive(current), bounded(size)),
				query.orderByDesc(WorkTaskEventEntity::getOccurredAt, WorkTaskEventEntity::getId));
		Page<Map<String, Object>> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
		result.setRecords(page.getRecords().stream().map(e -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", e.getId());
			item.put("type", e.getType());
			item.put("actorId", e.getActorId());
			item.put("occurredAt", e.getOccurredAt());
			item.put("reason", e.getReason());
			item.put("taskVersion", e.getTaskVersion());
			item.put("payload", readJson(e.getPayloadJson(), Map.class));
			item.put("notificationStatus", e.getPublishStatus());
			return item;
		}).toList());
		return result;
	}

	public WorkTaskDetailVO create(String key, WorkTaskV2CreateDTO request) {
		Actor actor = readActor();
		requirePermission("work_task_create");
		return idempotent(actor, key, "create", request, () -> {
			WorkTaskEntity task = newTask(actor, request, null);
			var result = view(task, actor, false);
			event(actor, key, "create", request, task, "TASK_CREATED", null,
					Map.of("request", request, "sourceVerification", "CLIENT_SUPPLIED"), result, 0);
			return result;
		});
	}

	public WorkTaskDetailVO command(Long id, String action, Long transferId, String key, WorkTaskCommandDTO request) {
		Actor actor = readActor();
		String route = id + "/" + action + (transferId == null ? "" : "/" + transferId);
		return idempotent(actor, key, route, request, () -> {
			WorkTaskEntity task = lockVisible(id, actor);
			WorkTaskEventEntity completed = receipt(actor, key, true);
			if (completed != null)
				return replay(completed, actor, route, request);
			checkVersion(task, request.getExpectedVersion());
			requireV2(task);
			if (closed(task))
				throw conflict("TASK_CLOSED");
			requireActionAuthority(task, actor, action);
			if ("submit".equals(action) && task.getPendingTransferId() != null)
				throw conflict("TRANSFER_PENDING");
			if (!allowedActions(task, actor).contains(action))
				throw conflict("TASK_ACTION_UNAVAILABLE");
			if (request.getFileIds() != null && !request.getFileIds().isEmpty()
					&& !Set.of("progress", "submit").contains(action))
				throw bad("FILES_NOT_SUPPORTED_FOR_ACTION");
			validateFiles(actor, request.getFileIds());
			Map<String, Object> before = snapshot(task);
			Map<String, Object> extra = new LinkedHashMap<>();
			switch (action) {
				case "start" -> task.setStatus(WorkTaskStatus.IN_PROGRESS);
				case "progress" -> requireText(request.getContent(), "PROGRESS_CONTENT_REQUIRED");
				case "block" -> {
					requireText(request.getComment(), "BLOCK_REASON_REQUIRED");
					task.setStatus(WorkTaskStatus.BLOCKED);
				}
				case "resume" -> {
					requireText(request.getComment(), "RESUME_REASON_REQUIRED");
					task.setStatus(WorkTaskStatus.IN_PROGRESS);
				}
				case "submit" -> submit(task, actor, request);
				case "accept" -> accept(task, actor, request);
				case "update" -> update(task, actor, request);
				case "acceptor" -> {
					requireText(request.getReason(), "CHANGE_REASON_REQUIRED");
					validMember(actor.tenantId(), request.getAcceptorId());
					task.setOwnerId(request.getAcceptorId());
				}
				case "transfer" -> transfer(task, actor, request);
				case "transfer-decision" -> decideTransfer(task, actor, transferId, request);
				case "nudge" -> extra.put("recipientIds", nudge(task, actor, request));
				case "dispatch" -> manualOrRetry(task, actor, request);
				default -> throw bad("UNKNOWN_TASK_ACTION");
			}
			if (tasks.updateById(task) != 1)
				throw conflict("TASK_VERSION_CONFLICT");
			syncAssignments(task);
			extra.put("before", before);
			extra.put("after", snapshot(task));
			extra.put("command", request);
			if ("accept".equals(action))
				extra.put("selfAcceptance", Objects.equals(actor.userId(), task.getPrimaryAssigneeId()));
			WorkTaskDetailVO result = view(task, actor, false);
			Long eventId = event(actor, key, route, request, task, eventType(action, request),
					first(request.getReason(), request.getComment()), extra, result, 0);
			attach(actor, task, request.getFileIds(), "submit".equals(action) ? "RESULT" : "PROGRESS", eventId,
					"submit".equals(action) ? task.getCurrentSubmissionId() : null);
			return result;
		});
	}

	public WorkTaskDetailVO children(Long id, String key, WorkTaskChildrenDTO request) {
		Actor actor = readActor();
		requirePermission("work_task_create");
		return idempotent(actor, key, id + "/children", request, () -> {
			WorkTaskEntity parent = lockVisible(id, actor);
			WorkTaskEventEntity completed = receipt(actor, key, true);
			if (completed != null)
				return replay(completed, actor, id + "/children", request);
			requireV2(parent);
			checkVersion(parent, request.getExpectedVersion());
			if (!allowedActions(parent, actor).contains("children"))
				throw forbidden();
			if (parent.getParentId() != null)
				throw conflict("TASK_DEPTH_EXCEEDED");
			if (request.getChildren() == null || request.getChildren().isEmpty() || request.getChildren().size() > 30)
				throw bad("INVALID_CHILDREN_COUNT");
			List<WorkTaskEntity> children = new ArrayList<>();
			for (var child : request.getChildren())
				children.add(newTask(actor, child, parent));
			if (tasks.updateById(parent) != 1)
				throw conflict("TASK_VERSION_CONFLICT");
			var result = view(parent, actor, true);
			event(actor, key, id + "/children", request, parent, "TASK_CHILDREN_CREATED", null,
					Map.of("childIds", children.stream().map(WorkTaskEntity::getId).toList()), result, 0);
			int seq = 1;
			for (var child : children)
				event(actor, key, id + "/children", request, child, "TASK_CREATED", null,
						Map.of("parentId", id, "content", child.getDescription()), view(child, actor, false), seq++);
			return result;
		});
	}

	private WorkTaskEntity newTask(Actor actor, WorkTaskV2CreateDTO request, WorkTaskEntity parent) {
		requireText(request.getTitle(), "TITLE_REQUIRED");
		requireText(request.getContent(), "CONTENT_REQUIRED");
		if (request.getTitle().length() > 200 || request.getContent().length() > 30000)
			throw bad("CONTENT_TOO_LONG");
		if (request.getDueAt() == null)
			throw bad("DUE_AT_REQUIRED");
		validateFiles(actor, request.getFileIds());
		String mode = request.getDispatchMode() == null ? "MANUAL" : request.getDispatchMode();
		if (!Set.of("MANUAL", "AI").contains(mode))
			throw bad("INVALID_DISPATCH_MODE");
		Long acceptor = request.getAcceptorId() == null ? actor.userId() : request.getAcceptorId();
		validMember(actor.tenantId(), acceptor);
		if ("MANUAL".equals(mode))
			validExecutor(actor.tenantId(), actor.userId(), request.getPrimaryAssigneeId(), false);
		else if (request.getPrimaryAssigneeId() != null)
			throw bad("AI_EXECUTOR_MUST_BE_EMPTY");
		WorkTaskEntity task = new WorkTaskEntity();
		task.setId(IdWorker.getId());
		task.setTenantId(actor.tenantId());
		task.setOrganizationId(actor.organizationId());
		task.setCreatorId(actor.userId());
		task.setOwnerId(acceptor);
		task.setTitle(request.getTitle().trim());
		task.setDescription(request.getContent());
		task.setPriority(priority(request.getPriority()));
		task.setTaskContractVersion(2);
		task.setContentFormat("MARKDOWN");
		task.setContentVersion(1);
		task.setVersion(0);
		task.setDueAt(request.getDueAt().atZoneSameInstant(ZONE).toLocalDateTime());
		task.setDueTimezone(ZONE.getId());
		task.setDispatchMode(mode);
		task.setDispatchStatus("MANUAL".equals(mode) ? "ASSIGNED" : "PENDING");
		task.setStatus("MANUAL".equals(mode) ? WorkTaskStatus.TODO : WorkTaskStatus.DRAFT);
		task.setPrimaryAssigneeId("MANUAL".equals(mode) ? request.getPrimaryAssigneeId() : null);
		task.setParentId(parent == null ? null : parent.getId());
		task.setDelFlag("0");
		task.setCreateTime(now());
		task.setUpdateTime(now());
		if (parent != null && task.getDueAt().isAfter(parent.getDueAt()))
			throw conflict("CHILD_DUE_AFTER_PARENT");
		task.setSourceType(request.getSource() == null ? "MANUAL" : request.getSource().getOrDefault("type", "MANUAL"));
		// Source text is an attributed client assertion, never trusted as authorized
		// source-object access.
		if ("AI".equals(mode))
			enqueueDispatch(task, actor, 0);
		if (tasks.insert(task) != 1)
			throw conflict("TASK_CREATE_FAILED");
		syncAssignments(task);
		attach(actor, task, request.getFileIds(), "SOURCE", null, null);
		return task;
	}

	private void submit(WorkTaskEntity task, Actor actor, WorkTaskCommandDTO request) {
		requireText(request.getContent(), "SUBMISSION_CONTENT_REQUIRED");
		if (task.getPendingTransferId() != null)
			throw conflict("TRANSFER_PENDING");
		if (tasks.selectCount(Wrappers.<WorkTaskEntity>lambdaQuery()
			.eq(WorkTaskEntity::getTenantId, actor.tenantId())
			.eq(WorkTaskEntity::getParentId, task.getId())
			.ne(WorkTaskEntity::getStatus, WorkTaskStatus.DONE)) > 0)
			throw conflict("SUBTASKS_NOT_CLOSED");
		WorkTaskSubmissionEntity submission = new WorkTaskSubmissionEntity();
		submission.setId(IdWorker.getId());
		submission.setTenantId(actor.tenantId());
		submission.setTaskId(task.getId());
		submission
			.setSubmissionNo(Math.toIntExact(submissions.selectCount(Wrappers.<WorkTaskSubmissionEntity>lambdaQuery()
				.eq(WorkTaskSubmissionEntity::getTenantId, actor.tenantId())
				.eq(WorkTaskSubmissionEntity::getTaskId, task.getId()))) + 1);
		submission.setContentMarkdown(request.getContent());
		submission.setSubmittedBy(actor.userId());
		submission.setSubmittedAt(now());
		submission.setAcceptorAtSubmit(task.getOwnerId());
		submissions.insert(submission);
		task.setCurrentSubmissionId(submission.getId());
		task.setSubmittedAt(submission.getSubmittedAt());
		task.setStatus(WorkTaskStatus.WAITING_ACCEPTANCE);
	}

	private void accept(WorkTaskEntity task, Actor actor, WorkTaskCommandDTO request) {
		if (!Objects.equals(task.getCurrentSubmissionId(), request.getSubmissionId())
				|| request.getSubmissionId() == null)
			throw conflict("SUBMISSION_VERSION_CONFLICT");
		if (!Set.of("PASS", "RETURN").contains(Objects.toString(request.getDecision(), "")))
			throw bad("INVALID_ACCEPTANCE_DECISION");
		if ("RETURN".equals(request.getDecision()))
			requireText(request.getComment(), "RETURN_REASON_REQUIRED");
		WorkTaskSubmissionEntity submission = submissions.selectOne(Wrappers.<WorkTaskSubmissionEntity>lambdaQuery()
			.eq(WorkTaskSubmissionEntity::getTenantId, actor.tenantId())
			.eq(WorkTaskSubmissionEntity::getTaskId, task.getId())
			.eq(WorkTaskSubmissionEntity::getId, request.getSubmissionId()));
		if (submission == null || submission.getDecision() != null)
			throw conflict("SUBMISSION_VERSION_CONFLICT");
		submission.setDecision(request.getDecision());
		submission.setDecidedBy(actor.userId());
		submission.setDecidedAt(now());
		submission.setDecisionComment(request.getComment());
		submissions.updateById(submission);
		task.setStatus("PASS".equals(request.getDecision()) ? WorkTaskStatus.DONE : WorkTaskStatus.IN_PROGRESS);
		if (task.getStatus() == WorkTaskStatus.DONE)
			task.setClosedAt(now());
	}

	private void update(WorkTaskEntity task, Actor actor, WorkTaskCommandDTO request) {
		requireText(request.getReason(), "CHANGE_REASON_REQUIRED");
		if (request.getPrimaryAssigneeId() != null || request.getAcceptorId() != null)
			throw bad("USE_RESPONSIBILITY_COMMAND");
		if (task.getStatus() == WorkTaskStatus.WAITING_ACCEPTANCE)
			throw conflict("RETURN_BEFORE_EDIT");
		if (request.getTitle() != null) {
			requireText(request.getTitle(), "TITLE_REQUIRED");
			task.setTitle(request.getTitle());
		}
		if (request.getContent() != null) {
			requireText(request.getContent(), "CONTENT_REQUIRED");
			task.setDescription(request.getContent());
			task.setContentVersion(task.getContentVersion() + 1);
		}
		if (request.getPriority() != null)
			task.setPriority(priority(request.getPriority()));
		if (request.getDueAt() != null) {
			LocalDateTime due = request.getDueAt().atZoneSameInstant(ZONE).toLocalDateTime();
			if (task.getParentId() != null) {
				WorkTaskEntity parent = tasks.selectById(task.getParentId());
				if (parent == null || !Objects.equals(parent.getTenantId(), actor.tenantId())
						|| due.isAfter(parent.getDueAt()))
					throw conflict("CHILD_DUE_AFTER_PARENT");
			}
			if (tasks.selectCount(Wrappers.<WorkTaskEntity>lambdaQuery()
				.eq(WorkTaskEntity::getTenantId, actor.tenantId())
				.eq(WorkTaskEntity::getParentId, task.getId())
				.gt(WorkTaskEntity::getDueAt, due)) > 0)
				throw conflict("CHILD_DUE_AFTER_PARENT");
			task.setDueAt(due);
		}
	}

	private void transfer(WorkTaskEntity task, Actor actor, WorkTaskCommandDTO request) {
		requireText(request.getReason(), "TRANSFER_REASON_REQUIRED");
		if (task.getPendingTransferId() != null)
			throw conflict("TRANSFER_PENDING");
		validPeer(actor.tenantId(), actor.userId(), request.getToUserId());
		WorkTaskTransferEntity transfer = new WorkTaskTransferEntity();
		transfer.setId(IdWorker.getId());
		transfer.setTenantId(actor.tenantId());
		transfer.setTaskId(task.getId());
		transfer.setFromUserId(actor.userId());
		transfer.setToUserId(request.getToUserId());
		transfer.setReason(request.getReason());
		transfer.setStatus("PENDING");
		transfer.setVersion(0);
		transfer.setRequestedAt(now());
		transfers.insert(transfer);
		task.setPendingTransferId(transfer.getId());
	}

	private void decideTransfer(WorkTaskEntity task, Actor actor, Long id, WorkTaskCommandDTO request) {
		if (id == null || !Objects.equals(task.getPendingTransferId(), id))
			throw conflict("TRANSFER_VERSION_CONFLICT");
		WorkTaskTransferEntity transfer = transfer(task);
		if (transfer == null || !"PENDING".equals(transfer.getStatus())
				|| !Objects.equals(transfer.getVersion(), request.getTransferVersion())
				|| !Objects.equals(transfer.getFromUserId(), task.getPrimaryAssigneeId()))
			throw conflict("TRANSFER_VERSION_CONFLICT");
		String decision = Objects.toString(request.getDecision(), "");
		if ("CANCEL".equals(decision))
			decision = "WITHDRAW";
		if (!Set.of("ACCEPT", "REJECT", "WITHDRAW").contains(decision))
			throw bad("INVALID_TRANSFER_DECISION");
		if ("WITHDRAW".equals(decision)) {
			if (!Objects.equals(actor.userId(), transfer.getFromUserId()))
				throw forbidden();
		}
		else if (!Objects.equals(actor.userId(), transfer.getToUserId()))
			throw forbidden();
		requireText(request.getReason(), "TRANSFER_DECISION_REASON_REQUIRED");
		if ("ACCEPT".equals(decision)) {
			validPeer(actor.tenantId(), transfer.getFromUserId(), transfer.getToUserId());
			task.setPrimaryAssigneeId(transfer.getToUserId());
		}
		transfer.setStatus(switch (decision) {
			case "ACCEPT" -> "ACCEPTED";
			case "REJECT" -> "REJECTED";
			default -> "WITHDRAWN";
		});
		transfer.setVersion(transfer.getVersion() + 1);
		transfer.setRespondedAt(now());
		transfer.setResponseReason(request.getReason());
		transfers.updateById(transfer);
		task.setPendingTransferId(null);
	}

	private List<Long> nudge(WorkTaskEntity task, Actor actor, WorkTaskCommandDTO request) {
		requireText(request.getReason(), "NUDGE_REASON_REQUIRED");
		Long receiver = task.getStatus() == WorkTaskStatus.WAITING_ACCEPTANCE ? task.getOwnerId()
				: task.getPrimaryAssigneeId();
		validMember(actor.tenantId(), receiver);
		WorkTaskPolicyEntity policy = policy(actor.tenantId());
		int interval = policy == null ? 10 : policy.getNudgeIntervalMinutes();
		int daily = policy == null ? 3 : policy.getNudgeDailyLimit();
		var today = Wrappers.<WorkTaskEventEntity>lambdaQuery()
			.eq(WorkTaskEventEntity::getTenantId, actor.tenantId())
			.eq(WorkTaskEventEntity::getTaskId, task.getId())
			.eq(WorkTaskEventEntity::getActorId, actor.userId())
			.eq(WorkTaskEventEntity::getType, "TASK_NUDGED")
			.ge(WorkTaskEventEntity::getOccurredAt, LocalDate.now(ZONE).atStartOfDay());
		if (events.selectCount(today) >= daily || events.selectCount(Wrappers.<WorkTaskEventEntity>lambdaQuery()
			.eq(WorkTaskEventEntity::getTenantId, actor.tenantId())
			.eq(WorkTaskEventEntity::getTaskId, task.getId())
			.eq(WorkTaskEventEntity::getActorId, actor.userId())
			.eq(WorkTaskEventEntity::getType, "TASK_NUDGED")
			.gt(WorkTaskEventEntity::getOccurredAt, now().minusMinutes(interval))) > 0)
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "NUDGE_RATE_LIMIT");
		return List.of(receiver);
	}

	private void manualOrRetry(WorkTaskEntity task, Actor actor, WorkTaskCommandDTO request) {
		if ("MANUAL".equals(request.getMode())) {
			validExecutor(actor.tenantId(), actor.userId(), request.getPrimaryAssigneeId(), false);
			task.setPrimaryAssigneeId(request.getPrimaryAssigneeId());
			task.setDispatchStatus("ASSIGNED");
			task.setStatus(WorkTaskStatus.TODO);
		}
		else if ("RETRY_AI".equals(request.getMode()))
			enqueueDispatch(task, actor, task.getVersion() + 1);
		else
			throw bad("INVALID_DISPATCH_MODE");
	}

	private void enqueueDispatch(WorkTaskEntity task, Actor actor, int version) {
		WorkTaskPolicyEntity policy = policy(actor.tenantId());
		if (policy == null || !Boolean.TRUE.equals(policy.getEnabled()) || policy.getDispatchSkillPackageId() == null
				|| policy.getChatModelId() == null)
			throw conflict("TASK_AI_NOT_CONFIGURED");
		WorkTaskDispatchRunEntity run = new WorkTaskDispatchRunEntity();
		run.setId(IdWorker.getId());
		run.setTenantId(actor.tenantId());
		run.setTaskId(task.getId());
		run.setCreatorId(actor.userId());
		run.setAttemptNo(Math.toIntExact(dispatchRuns.selectCount(Wrappers.<WorkTaskDispatchRunEntity>lambdaQuery()
			.eq(WorkTaskDispatchRunEntity::getTenantId, actor.tenantId())
			.eq(WorkTaskDispatchRunEntity::getTaskId, task.getId()))) + 1);
		run.setStatus("PENDING");
		run.setInputTaskVersion(version);
		run.setPolicyVersion(policy.getVersion());
		run.setSkillPackageId(policy.getDispatchSkillPackageId());
		run.setModelId(policy.getChatModelId());
		run.setCreatedAt(now());
		dispatchRuns.insert(run);
		task.setCurrentDispatchRunId(run.getId());
		task.setDispatchStatus("PENDING");
	}

	/**
	 * Internal worker completion. No HTTP endpoint and no impersonated interactive
	 * identity.
	 */
	public void completeDispatch(Long tenantId, Long runId, Long assigneeId, Map<String, Object> metadata) {
		requireWorkerTenant(tenantId);
		transaction.executeWithoutResult(tx -> {
			var run = dispatchRuns.selectById(runId);
			if (run == null || !Objects.equals(tenantId, run.getTenantId()))
				throw conflict("AI_RUN_NOT_FOUND");
			WorkTaskEntity task = lockInternal(tenantId, run.getTaskId());
			run = lockDispatchRun(tenantId, runId);
			if (run == null || task == null || !Objects.equals(run.getTaskId(), task.getId())
					|| !Objects.equals(task.getCurrentDispatchRunId(), runId)
					|| task.getStatus() != WorkTaskStatus.DRAFT
					|| !Objects.equals(task.getVersion(), run.getInputTaskVersion())
					|| !"RUNNING".equals(run.getStatus()) || run.getLeaseUntil() == null
					|| !run.getLeaseUntil().isAfter(now()) || metadata == null || run.getLeaseOwner() == null
					|| run.getLeaseOwner().isBlank()
					|| !Objects.equals(run.getLeaseOwner(), metadata.get("leaseOwner")))
				throw conflict("AI_RESULT_STALE");
			var currentPolicy = policies.selectOne(Wrappers.<WorkTaskPolicyEntity>lambdaQuery()
				.eq(WorkTaskPolicyEntity::getTenantId, tenantId)
				.last("FOR UPDATE"));
			if (currentPolicy == null || !Boolean.TRUE.equals(currentPolicy.getEnabled())
					|| !Objects.equals(currentPolicy.getVersion(), run.getPolicyVersion())
					|| !Objects.equals(currentPolicy.getChatModelId(), run.getModelId())
					|| !Objects.equals(currentPolicy.getDispatchSkillPackageId(), run.getSkillPackageId()))
				throw conflict("POLICY_CHANGED");
			validExecutor(tenantId, run.getCreatorId(), assigneeId, true);
			if (dispatchRuns.update(null,
					Wrappers.<WorkTaskDispatchRunEntity>lambdaUpdate()
						.eq(WorkTaskDispatchRunEntity::getTenantId, tenantId)
						.eq(WorkTaskDispatchRunEntity::getId, runId)
						.eq(WorkTaskDispatchRunEntity::getStatus, "RUNNING")
						.eq(WorkTaskDispatchRunEntity::getLeaseOwner, metadata.get("leaseOwner"))
						.gt(WorkTaskDispatchRunEntity::getLeaseUntil, now())
						.set(WorkTaskDispatchRunEntity::getStatus, "ASSIGNED")
						.set(WorkTaskDispatchRunEntity::getFinishedAt, now())
						.set(WorkTaskDispatchRunEntity::getResultJson, writeJson(metadata))) != 1)
				throw conflict("AI_RESULT_STALE");
			task.setPrimaryAssigneeId(assigneeId);
			task.setStatus(WorkTaskStatus.TODO);
			task.setDispatchStatus("ASSIGNED");
			if (tasks.updateById(task) != 1)
				throw conflict("TASK_VERSION_CONFLICT");
			syncAssignments(task);
			Actor actor = new Actor(tenantId, 0L, task.getOrganizationId(),
					directory.taskAuthority(tenantId, run.getCreatorId()));
			systemEvent(actor, "dispatch:" + runId, task, "TASK_ASSIGNED",
					Map.of("assigneeId", assigneeId, "runId", runId));
		});
	}

	public void failDispatch(Long tenantId, Long runId, String reasonCode) {
		failDispatch(tenantId, runId, reasonCode, null);
	}

	public void failDispatch(Long tenantId, Long runId, String reasonCode, String leaseOwner) {
		requireWorkerTenant(tenantId);
		transaction.executeWithoutResult(tx -> {
			var run = dispatchRuns.selectById(runId);
			if (run == null || !Objects.equals(tenantId, run.getTenantId()))
				throw conflict("AI_RUN_NOT_FOUND");
			var task = lockInternal(tenantId, run.getTaskId());
			run = lockDispatchRun(tenantId, runId);
			if (run == null || task == null || !Objects.equals(run.getTaskId(), task.getId())
					|| !Objects.equals(task.getCurrentDispatchRunId(), runId)
					|| task.getStatus() != WorkTaskStatus.DRAFT
					|| !Objects.equals(task.getVersion(), run.getInputTaskVersion())
					|| !Set.of("PENDING", "RUNNING").contains(run.getStatus()))
				return;
			if ("RUNNING".equals(run.getStatus())) {
				if (leaseOwner != null
						? !Objects.equals(leaseOwner, run.getLeaseOwner()) || run.getLeaseUntil() == null
								|| !run.getLeaseUntil().isAfter(now())
						: run.getLeaseUntil() != null && run.getLeaseUntil().isAfter(now()))
					return;
			}
			var failureUpdate = Wrappers.<WorkTaskDispatchRunEntity>lambdaUpdate()
				.eq(WorkTaskDispatchRunEntity::getTenantId, tenantId)
				.eq(WorkTaskDispatchRunEntity::getId, runId)
				.eq(WorkTaskDispatchRunEntity::getStatus, run.getStatus());
			if ("RUNNING".equals(run.getStatus()) && leaseOwner != null)
				failureUpdate.eq(WorkTaskDispatchRunEntity::getLeaseOwner, leaseOwner)
					.gt(WorkTaskDispatchRunEntity::getLeaseUntil, now());
			if (dispatchRuns.update(null,
					failureUpdate.set(WorkTaskDispatchRunEntity::getStatus, "NEEDS_MANUAL")
						.set(WorkTaskDispatchRunEntity::getReasonCode, reasonCode)
						.set(WorkTaskDispatchRunEntity::getFinishedAt, now())) != 1)
				return;
			task.setDispatchStatus("NEEDS_MANUAL");
			if (tasks.updateById(task) != 1)
				throw conflict("TASK_VERSION_CONFLICT");
			Actor actor = new Actor(tenantId, 0L, task.getOrganizationId(),
					directory.taskAuthority(tenantId, run.getCreatorId()));
			systemEvent(actor, "dispatch-failed:" + runId, task, "TASK_DISPATCH_FAILED",
					Map.of("reasonCode", reasonCode));
		});
	}

	/** One maintenance transaction per task; notifications remain in the event outbox. */
	public void maintain(Long tenantId, Long taskId) {
		requireWorkerTenant(tenantId);
		transaction.executeWithoutResult(tx -> {
			WorkTaskEntity task = lockInternal(tenantId, taskId);
			if (task == null || closed(task) || !Objects.equals(task.getTaskContractVersion(), 2))
				return;
			Actor actor = new Actor(tenantId, 0L, task.getOrganizationId(),
					directory.taskAuthority(tenantId, task.getCreatorId()));
			var pending = transfer(task);
			if (pending != null && "PENDING".equals(pending.getStatus())
					&& !directory.isAssignableUser(tenantId, pending.getToUserId())) {
				pending.setStatus("INVALIDATED");
				pending.setRespondedAt(now());
				pending.setResponseReason("RECEIVER_INACTIVE");
				transfers.updateById(pending);
				task.setPendingTransferId(null);
				if (tasks.updateById(task) != 1)
					throw conflict("TASK_VERSION_CONFLICT");
				maintenanceEvent(actor, task, "TASK_TRANSFER_INVALIDATED", "transfer-invalid:" + pending.getId());
			}
			String day = LocalDate.now(ZONE).toString();
			WorkTaskPolicyEntity policy = policy(tenantId);
			int soon = policy == null || policy.getDueSoonHours() == null ? 24 : policy.getDueSoonHours();
			int stale = policy == null || policy.getAcceptanceStaleHours() == null ? 48
					: policy.getAcceptanceStaleHours();
			if (task.getDueAt() != null && task.getDueAt().isBefore(now()))
				maintenanceEvent(actor, task, "TASK_OVERDUE", "overdue:" + taskId + ":" + day);
			else if (task.getDueAt() != null && !task.getDueAt().isAfter(now().plusHours(soon)))
				maintenanceEvent(actor, task, "TASK_DUE_SOON", "due-soon:" + taskId + ":" + day);
			if (task.getStatus() == WorkTaskStatus.WAITING_ACCEPTANCE && task.getSubmittedAt() != null
					&& task.getSubmittedAt().isBefore(now().minusHours(stale)))
				maintenanceEvent(actor, task, "TASK_ACCEPTANCE_STALE", "acceptance-stale:" + taskId + ":" + day);
			if ((task.getPrimaryAssigneeId() != null
					&& !directory.isAssignableUser(tenantId, task.getPrimaryAssigneeId()))
					|| (task.getOwnerId() != null && !directory.isAssignableUser(tenantId, task.getOwnerId())))
				maintenanceEvent(actor, task, "TASK_RESPONSIBLE_INACTIVE",
						"responsible-inactive:" + taskId + ":" + day);
		});
	}

	private void maintenanceEvent(Actor actor, WorkTaskEntity task, String type, String key) {
		if (events.selectCount(Wrappers.<WorkTaskEventEntity>lambdaQuery()
			.eq(WorkTaskEventEntity::getTenantId, actor.tenantId())
			.eq(WorkTaskEventEntity::getActorType, "SYSTEM")
			.eq(WorkTaskEventEntity::getActorId, 0L)
			.eq(WorkTaskEventEntity::getRequestKey, key)
			.eq(WorkTaskEventEntity::getEventSeq, 0)) == 0)
			systemEvent(actor, key, task, type, Map.of("reasonCode", type));
	}

	private WorkTaskDispatchRunEntity lockDispatchRun(Long tenantId, Long runId) {
		return dispatchRuns.selectOne(Wrappers.<WorkTaskDispatchRunEntity>lambdaQuery()
			.eq(WorkTaskDispatchRunEntity::getTenantId, tenantId)
			.eq(WorkTaskDispatchRunEntity::getId, runId)
			.last("FOR UPDATE"));
	}

	private WorkTaskEntity lockInternal(Long tenantId, Long taskId) {
		WorkTaskEntity task = tasks.selectById(taskId);
		if (task == null || !Objects.equals(task.getTenantId(), tenantId))
			return null;
		if (task.getParentId() != null)
			tasks.lock(tenantId, task.getParentId());
		return tasks.lock(tenantId, taskId);
	}

	private void requireWorkerTenant(Long tenantId) {
		if (tenantId == null || !Objects.equals(TenantContextHolder.getTenantId(), tenantId))
			throw forbidden();
	}

	private WorkTaskDetailVO idempotent(Actor actor, String key, String route, Object request,
			Supplier<WorkTaskDetailVO> operation) {
		if (key == null || !key.matches("[A-Za-z0-9_-]{8,100}"))
			throw bad("INVALID_IDEMPOTENCY_KEY");
		WorkTaskEventEntity prior = receipt(actor, key);
		if (prior != null)
			return replay(prior, actor, route, request);
		try {
			return transaction.execute(tx -> operation.get());
		}
		catch (DuplicateKeyException ex) {
			prior = receipt(actor, key);
			if (prior == null)
				throw ex;
			return replay(prior, actor, route, request);
		}
	}

	private WorkTaskEventEntity receipt(Actor actor, String key) {
		return receipt(actor, key, false);
	}

	private WorkTaskEventEntity receipt(Actor actor, String key, boolean currentRead) {
		var query = Wrappers.<WorkTaskEventEntity>lambdaQuery()
			.eq(WorkTaskEventEntity::getTenantId, actor.tenantId())
			.eq(WorkTaskEventEntity::getActorType, "USER")
			.eq(WorkTaskEventEntity::getActorId, actor.userId())
			.eq(WorkTaskEventEntity::getRequestKey, key)
			.eq(WorkTaskEventEntity::getEventSeq, 0);
		if (currentRead)
			query.last("FOR UPDATE");
		return events.selectOne(query);
	}

	private WorkTaskDetailVO replay(WorkTaskEventEntity prior, Actor actor, String route, Object request) {
		if (!Objects.equals(prior.getRequestHash(), hash(route, request)))
			throw conflict("IDEMPOTENCY_CONFLICT");
		visible(prior.getTaskId(), actor);
		return readJson(prior.getResultJson(), WorkTaskDetailVO.class);
	}

	private Long event(Actor actor, String key, String route, Object request, WorkTaskEntity task, String type,
			String reason, Map<String, Object> payload, Object result, int seq) {
		WorkTaskEventEntity event = new WorkTaskEventEntity();
		event.setId(IdWorker.getId());
		event.setTenantId(actor.tenantId());
		event.setTaskId(task.getId());
		event.setType(type);
		event.setActorType("USER");
		event.setActorId(actor.userId());
		event.setActorOrgId(actor.organizationId());
		event.setTaskVersion(task.getVersion());
		event.setOccurredAt(now());
		Map<String, Object> delivered = new LinkedHashMap<>(payload);
		delivered.put("taskId", task.getId());
		delivered.put("title", task.getTitle());
		delivered.put("recipientUserIds", recipients(task, type, payload));
		event.setReason(reason);
		event.setPayloadJson(writeJson(delivered));
		event.setRequestKey(key);
		event.setRequestHash(hash(route, request));
		event.setEventSeq(seq);
		event.setResultJson(writeJson(result));
		event.setPublishStatus("PENDING");
		event.setAttempts(0);
		event.setNextAttemptAt(now());
		events.insert(event);
		return event.getId();
	}

	private void systemEvent(Actor actor, String key, WorkTaskEntity task, String type, Map<String, Object> payload) {
		WorkTaskEventEntity e = new WorkTaskEventEntity();
		e.setId(IdWorker.getId());
		e.setTenantId(actor.tenantId());
		e.setTaskId(task.getId());
		e.setType(type);
		e.setActorType("SYSTEM");
		e.setActorId(0L);
		e.setActorOrgId(actor.organizationId());
		e.setTaskVersion(task.getVersion());
		Map<String, Object> delivered = new LinkedHashMap<>(payload);
		delivered.put("title", task.getTitle());
		delivered.put("taskId", task.getId());
		delivered.put("recipientUserIds", recipients(task, type, payload));
		e.setOccurredAt(now());
		e.setPayloadJson(writeJson(delivered));
		e.setRequestKey(key);
		e.setRequestHash(hash(type, payload));
		e.setEventSeq(0);
		e.setResultJson(writeJson(snapshot(task)));
		e.setPublishStatus("PENDING");
		e.setAttempts(0);
		e.setNextAttemptAt(now());
		events.insert(e);
	}

	private WorkTaskEntity lockVisible(Long id, Actor actor) {
		WorkTaskEntity found = visible(id, actor);
		// Every child mutation takes its parent lock first, serializing
		// parent-submit/child changes.
		if (found.getParentId() != null && tasks.lock(actor.tenantId(), found.getParentId()) == null)
			throw conflict("PARENT_NOT_FOUND");
		WorkTaskEntity task = tasks.lock(actor.tenantId(), id);
		if (task == null)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND");
		visible(id, actor); // Recheck after waiting for the row lock.
		return task;
	}

	private WorkTaskEntity visible(Long id, Actor actor) {
		var task = tasks.selectOne(visibility.query(actor).eq(WorkTaskEntity::getId, id));
		if (task == null || !Objects.equals(task.getTenantId(), actor.tenantId()))
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND");
		return task;
	}

	private Actor readActor() {
		Actor actor = visibility.actor();
		requirePermission("work_task_view");
		return actor;
	}

	private WorkTaskDetailVO view(WorkTaskEntity task, Actor actor, boolean expand) {
		WorkTaskDetailVO vo = new WorkTaskDetailVO();
		BeanUtils.copyProperties(task, vo);
		vo.setAcceptorId(task.getOwnerId());
		vo.setContractVersion(task.getTaskContractVersion() == null ? 1 : task.getTaskContractVersion());
		if (Integer.valueOf(2).equals(task.getTaskContractVersion()) && task.getDueAt() != null)
			vo.setDueAtOffset(task.getDueAt().atZone(ZONE).toOffsetDateTime());
		vo.setAssigneeIds(task.getTaskContractVersion() != null && task.getTaskContractVersion() == 2
				? task.getPrimaryAssigneeId() == null ? List.of() : List.of(task.getPrimaryAssigneeId())
				: assignments
					.selectList(Wrappers.<WorkTaskAssignmentEntity>lambdaQuery()
						.eq(WorkTaskAssignmentEntity::getTenantId, actor.tenantId())
						.eq(WorkTaskAssignmentEntity::getTaskId, task.getId())
						.eq(WorkTaskAssignmentEntity::getRole, "ASSIGNEE")
						.eq(WorkTaskAssignmentEntity::getAssignmentStatus, "ACTIVE"))
					.stream()
					.map(WorkTaskAssignmentEntity::getUserId)
					.distinct()
					.toList());
		vo.setAllowedActions(allowedActions(task, actor));
		vo.setRiskFlags(risks(task));
		vo.setServerTime(OffsetDateTime.now(ZONE));
		vo.setEnterpriseTimeZone(ZONE.getId());
		vo.setPendingTransfer(transfer(task));
		if (expand && !pendingReceiverOnly(task, actor)) {
			vo.setChildren(tasks.selectList(visibility.query(actor)
				.eq(WorkTaskEntity::getParentId, task.getId())
				.orderByAsc(WorkTaskEntity::getId)));
			if (task.getCurrentSubmissionId() != null)
				vo.setCurrentSubmission(submissions.selectOne(Wrappers.<WorkTaskSubmissionEntity>lambdaQuery()
					.eq(WorkTaskSubmissionEntity::getTenantId, actor.tenantId())
					.eq(WorkTaskSubmissionEntity::getTaskId, task.getId())
					.eq(WorkTaskSubmissionEntity::getId, task.getCurrentSubmissionId())));
		}
		return vo;
	}

	private boolean pendingReceiverOnly(WorkTaskEntity task, Actor actor) {
		if (Objects.equals(task.getCreatorId(), actor.userId()) || Objects.equals(task.getOwnerId(), actor.userId())
				|| Objects.equals(task.getPrimaryAssigneeId(), actor.userId()) || actor.authority().generalManager())
			return false;
		Set<Long> managed = visibility.managedUsers(actor);
		if (managed.contains(task.getCreatorId()) || managed.contains(task.getOwnerId())
				|| managed.contains(task.getPrimaryAssigneeId()))
			return false;
		var transfer = transfer(task);
		return transfer != null && "PENDING".equals(transfer.getStatus())
				&& Objects.equals(transfer.getToUserId(), actor.userId());
	}

	private void requireActionAuthority(WorkTaskEntity task, Actor actor, String action) {
		boolean executor = Objects.equals(task.getPrimaryAssigneeId(), actor.userId());
		boolean creator = Objects.equals(task.getCreatorId(), actor.userId());
		boolean permitted = switch (action) {
			case "start", "progress", "block", "resume", "submit" -> executor && hasPermission("work_task_execute");
			case "accept" -> Objects.equals(task.getOwnerId(), actor.userId()) && hasPermission("work_task_accept");
			case "update", "acceptor", "dispatch" -> creator && hasPermission("work_task_create");
			case "transfer" -> executor && hasPermission("work_task_transfer");
			case "transfer-decision" -> {
				var transfer = transfer(task);
				yield hasPermission("work_task_transfer") && transfer != null
						&& (Objects.equals(transfer.getToUserId(), actor.userId())
								|| Objects.equals(transfer.getFromUserId(), actor.userId()));
			}
			case "nudge" -> hasPermission("work_task_nudge")
					&& (task.getStatus() == WorkTaskStatus.WAITING_ACCEPTANCE ? executor : creator);
			default -> false;
		};
		if (!permitted)
			throw forbidden();
	}

	public List<String> allowedActions(WorkTaskEntity task, Actor actor) {
		if (!Objects.equals(task.getTaskContractVersion(), 2) || closed(task))
			return List.of();
		List<String> actions = new ArrayList<>();
		boolean executor = Objects.equals(task.getPrimaryAssigneeId(), actor.userId());
		boolean acceptor = Objects.equals(task.getOwnerId(), actor.userId());
		boolean creator = Objects.equals(task.getCreatorId(), actor.userId());
		for (String action : List.of("start", "progress", "block", "resume", "submit", "accept", "transfer",
				"children")) {
			String permission = switch (action) {
				case "accept" -> "work_task_accept";
				case "transfer" -> "work_task_transfer";
				case "children" -> "work_task_create";
				default -> "work_task_execute";
			};
			if (hasPermission(permission) && WorkTaskRules.canExecute(task.getStatus(), action, executor, acceptor)
					&& !("submit".equals(action) && task.getPendingTransferId() != null)
					&& !("transfer".equals(action) && task.getPendingTransferId() != null)
					&& !("children".equals(action) && task.getParentId() != null))
				actions.add(action);
		}
		if (creator && hasPermission("work_task_create")) {
			if (task.getStatus() != WorkTaskStatus.WAITING_ACCEPTANCE)
				actions.add("update");
			actions.add("acceptor");
			if (task.getStatus() == WorkTaskStatus.DRAFT)
				actions.add("dispatch");
		}
		var transfer = transfer(task);
		if (transfer != null && "PENDING".equals(transfer.getStatus()) && hasPermission("work_task_transfer")
				&& (Objects.equals(transfer.getToUserId(), actor.userId())
						|| Objects.equals(transfer.getFromUserId(), actor.userId())))
			actions.add("transfer-decision");
		if (hasPermission("work_task_nudge") && (task.getStatus() == WorkTaskStatus.WAITING_ACCEPTANCE ? executor
				: creator && task.getPrimaryAssigneeId() != null))
			actions.add("nudge");
		return actions;
	}

	private WorkTaskTransferEntity transfer(WorkTaskEntity task) {
		if (task.getPendingTransferId() == null)
			return null;
		return transfers.selectOne(Wrappers.<WorkTaskTransferEntity>lambdaQuery()
			.eq(WorkTaskTransferEntity::getTenantId, task.getTenantId())
			.eq(WorkTaskTransferEntity::getTaskId, task.getId())
			.eq(WorkTaskTransferEntity::getId, task.getPendingTransferId()));
	}

	private LambdaQueryWrapper<WorkTaskEntity> filtered(Actor actor, Filter filter) {
		var q = visibility.query(actor);
		if (filter == null)
			return q.orderByAsc(WorkTaskEntity::getDueAt, WorkTaskEntity::getId);
		String view = filter.view();
		if (view != null && !view.isBlank())
			switch (view) {
				case "created", "assigned" -> q.eq(WorkTaskEntity::getCreatorId, actor.userId());
				case "executing", "tasks" -> executor(q, actor.userId(), actor.tenantId());
				case "acceptance" -> q.eq(WorkTaskEntity::getOwnerId, actor.userId());
				case "transfer" -> q.exists(
						"SELECT 1 FROM ai_work_task_transfer t WHERE t.id=ai_work_task.pending_transfer_id AND t.tenant_id={0} AND t.to_user_id={1} AND t.status='PENDING'",
						actor.tenantId(), actor.userId());
				case "enterprise" -> {
					if (!actor.authority().generalManager())
						throw forbidden();
				}
				case "department" -> {
					Set<Long> people = visibility.managedUsers(actor);
					if (people.isEmpty())
						throw forbidden();
					participants(q, people, actor.tenantId());
				}
				case "all" -> {
				}
				default -> throw bad("INVALID_TASK_VIEW");
			}
		q.eq(filter.status() != null, WorkTaskEntity::getStatus, filter.status());
		if (filter.keyword() != null && !filter.keyword().isBlank())
			q.like(WorkTaskEntity::getTitle, filter.keyword());
		if (filter.personId() != null)
			executor(q, filter.personId(), actor.tenantId());
		if (filter.deptId() != null) {
			var people = directory.listOrganizationUserIds(actor.tenantId(), filter.deptId());
			if (people.isEmpty())
				q.apply("1=0");
			else
				participants(q, new LinkedHashSet<>(people), actor.tenantId());
		}
		if ("ROOT".equals(filter.taskLevel()))
			q.isNull(WorkTaskEntity::getParentId);
		else if ("CHILD".equals(filter.taskLevel()))
			q.isNotNull(WorkTaskEntity::getParentId);
		else if (filter.taskLevel() != null && !filter.taskLevel().isBlank() && !"ALL".equals(filter.taskLevel()))
			throw bad("INVALID_TASK_LEVEL");
		if (filter.risk() != null && !filter.risk().isBlank())
			risk(q, filter.risk());
		return q.orderByAsc(WorkTaskEntity::getDueAt, WorkTaskEntity::getId);
	}

	private void participants(LambdaQueryWrapper<WorkTaskEntity> q, Set<Long> people, Long tenantId) {
		q.and(p -> p.in(WorkTaskEntity::getCreatorId, people)
			.or()
			.in(WorkTaskEntity::getOwnerId, people)
			.or()
			.in(WorkTaskEntity::getPrimaryAssigneeId, people)
			.or()
			.exists("SELECT 1 FROM ai_work_task_assignment a WHERE a.task_id=ai_work_task.id AND a.tenant_id={0} AND a.role='ASSIGNEE' AND a.assignment_status='ACTIVE' AND a.del_flag='0' AND a.user_id IN ("
					+ people.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")", tenantId));
	}

	private void executor(LambdaQueryWrapper<WorkTaskEntity> q, Long userId, Long tenantId) {
		q.and(p -> p.eq(WorkTaskEntity::getPrimaryAssigneeId, userId)
			.or()
			.exists("SELECT 1 FROM ai_work_task_assignment a WHERE a.task_id=ai_work_task.id AND a.tenant_id={0} AND a.user_id={1} AND a.role='ASSIGNEE' AND a.assignment_status='ACTIVE' AND a.del_flag='0'",
					tenantId, userId));
	}

	private LambdaQueryWrapper<WorkTaskEntity> risk(LambdaQueryWrapper<WorkTaskEntity> q, String risk) {
		q.notIn(WorkTaskEntity::getStatus, List.of(WorkTaskStatus.DONE, WorkTaskStatus.CANCELLED));
		switch (risk) {
			case "OVERDUE" -> q.lt(WorkTaskEntity::getDueAt, now());
			case "DUE_SOON" ->
				q.ge(WorkTaskEntity::getDueAt, now()).le(WorkTaskEntity::getDueAt, now().plusHours(dueSoonHours()));
			case "ACCEPTANCE_STALE" -> q.eq(WorkTaskEntity::getStatus, WorkTaskStatus.WAITING_ACCEPTANCE)
				.le(WorkTaskEntity::getSubmittedAt, now().minusHours(staleHours()));
			default -> throw bad("INVALID_TASK_RISK");
		}
		return q;
	}

	private List<String> risks(WorkTaskEntity task) {
		if (closed(task))
			return List.of();
		List<String> result = new ArrayList<>();
		LocalDateTime now = now();
		if (task.getDueAt() != null) {
			if (task.getDueAt().isBefore(now))
				result.add("OVERDUE");
			else if (!task.getDueAt().isAfter(now.plusHours(dueSoonHours())))
				result.add("DUE_SOON");
		}
		if (task.getStatus() == WorkTaskStatus.WAITING_ACCEPTANCE && task.getSubmittedAt() != null
				&& !task.getSubmittedAt().isAfter(now.minusHours(staleHours())))
			result.add("ACCEPTANCE_STALE");
		return result;
	}

	private int dueSoonHours() {
		var policy = policy(TenantContextHolder.getTenantId());
		return policy == null ? 24 : policy.getDueSoonHours();
	}

	private int staleHours() {
		var policy = policy(TenantContextHolder.getTenantId());
		return policy == null ? 48 : policy.getAcceptanceStaleHours();
	}

	private WorkTaskPolicyEntity policy(Long tenantId) {
		return policies
			.selectOne(Wrappers.<WorkTaskPolicyEntity>lambdaQuery().eq(WorkTaskPolicyEntity::getTenantId, tenantId));
	}

	private void syncAssignments(WorkTaskEntity task) {
		List<WorkTaskAssignmentEntity> rows = assignments.selectList(Wrappers.<WorkTaskAssignmentEntity>lambdaQuery()
			.eq(WorkTaskAssignmentEntity::getTenantId, task.getTenantId())
			.eq(WorkTaskAssignmentEntity::getTaskId, task.getId()));
		Map<String, Long> desired = new LinkedHashMap<>();
		desired.put("OWNER", task.getOwnerId());
		if (task.getPrimaryAssigneeId() != null)
			desired.put("ASSIGNEE", task.getPrimaryAssigneeId());
		for (var row : rows) {
			boolean active = Objects.equals(desired.get(row.getRole()), row.getUserId());
			row.setAssignmentStatus(active ? "ACTIVE" : "INACTIVE");
			if (active && "ASSIGNEE".equals(row.getRole()) && task.getStatus() != WorkTaskStatus.TODO
					&& task.getStatus() != WorkTaskStatus.DRAFT && row.getAcceptedAt() == null)
				row.setAcceptedAt(now());
			if (active && task.getStatus() == WorkTaskStatus.DONE)
				row.setCompletedAt(task.getClosedAt());
			assignments.updateById(row);
		}
		for (var entry : desired.entrySet())
			if (rows.stream()
				.noneMatch(
						r -> entry.getKey().equals(r.getRole()) && Objects.equals(entry.getValue(), r.getUserId()))) {
				var row = new WorkTaskAssignmentEntity();
				row.setId(IdWorker.getId());
				row.setTenantId(task.getTenantId());
				row.setTaskId(task.getId());
				row.setUserId(entry.getValue());
				row.setRole(entry.getKey());
				row.setAssignmentStatus("ACTIVE");
				row.setDelFlag("0");
				assignments.insert(row);
			}
	}

	private void validMember(Long tenantId, Long userId) {
		if (userId == null || !directory.isAssignableUser(tenantId, userId))
			throw bad("TENANT_MEMBER_UNAVAILABLE");
	}

	private void validExecutor(Long tenantId, Long actorId, Long targetId, boolean ai) {
		validMember(tenantId, actorId);
		validMember(tenantId, targetId);
		var actor = directory.taskAuthority(tenantId, actorId);
		var target = directory.taskAuthority(tenantId, targetId);
		if (!WorkTaskRules.canAssign(actor.level(), target.level()) || ai && target.generalManager())
			throw bad("ASSIGNEE_NOT_ALLOWED");
	}

	private void validPeer(Long tenantId, Long from, Long to) {
		validMember(tenantId, from);
		validMember(tenantId, to);
		var source = directory.taskAuthority(tenantId, from);
		var target = directory.taskAuthority(tenantId, to);
		if (source.level() == null || target.level() == null || source.level() <= 0 || target.level() <= 0)
			throw bad("ORG_RELATION_UNRESOLVED");
		if (Objects.equals(from, to) || !Objects.equals(source.level(), target.level()))
			throw bad("TRANSFER_PEER_REQUIRED");
	}

	private void validateFiles(Actor actor, List<Long> ids) {
		if (ids == null || ids.isEmpty())
			return;
		if (ids.size() > 30 || ids.stream().anyMatch(Objects::isNull))
			throw bad("INVALID_FILE_IDS");
		TaskFileAccess access = fileAccess.getIfAvailable();
		if (access == null)
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TASK_FILE_ACCESS_NOT_CONFIGURED");
		access.requireAttachable(actor.tenantId(), actor.userId(), ids);
	}

	private void attach(Actor actor, WorkTaskEntity task, List<Long> ids, String purpose, Long eventId,
			Long submissionId) {
		if (ids == null)
			return;
		for (Long id : new LinkedHashSet<>(ids)) {
			var file = new WorkTaskFileEntity();
			file.setId(IdWorker.getId());
			file.setTenantId(actor.tenantId());
			file.setTaskId(task.getId());
			file.setFileId(id);
			file.setPurpose(purpose);
			file.setUploadedBy(actor.userId());
			file.setEventId(eventId);
			file.setSubmissionId(submissionId);
			files.insert(file);
		}
	}

	private List<Long> recipients(WorkTaskEntity task, String type, Map<String, Object> payload) {
		Set<Long> recipients = new LinkedHashSet<>();
		if ("TASK_NUDGED".equals(type) && payload.get("recipientIds") instanceof List<?> ids) {
			ids.forEach(id -> recipients.add(Long.valueOf(id.toString())));
		}
		else if ("TASK_TRANSFER_REQUESTED".equals(type)) {
			var pending = transfer(task);
			if (pending != null)
				recipients.add(pending.getToUserId());
			recipients.add(task.getPrimaryAssigneeId());
		}
		else if ("TASK_SUBMITTED".equals(type))
			recipients.add(task.getOwnerId());
		else {
			recipients.add(task.getCreatorId());
			recipients.add(task.getPrimaryAssigneeId());
			recipients.add(task.getOwnerId());
			if (payload.get("before") instanceof Map<?, ?> before) {
				if ("TASK_ACCEPTOR_CHANGED".equals(type) && before.get("ownerId") != null)
					recipients.add(Long.valueOf(before.get("ownerId").toString()));
				if (type.startsWith("TASK_TRANSFER_") && before.get("primaryAssigneeId") != null)
					recipients.add(Long.valueOf(before.get("primaryAssigneeId").toString()));
				if (type.startsWith("TASK_TRANSFER_") && before.get("pendingTransferId") != null) {
					var prior = transfers.selectOne(Wrappers.<WorkTaskTransferEntity>lambdaQuery()
						.eq(WorkTaskTransferEntity::getTenantId, task.getTenantId())
						.eq(WorkTaskTransferEntity::getTaskId, task.getId())
						.eq(WorkTaskTransferEntity::getId, Long.valueOf(before.get("pendingTransferId").toString())));
					if (prior != null)
						recipients.add(prior.getToUserId());
				}
			}
		}
		recipients.remove(null);
		return recipients.stream().filter(id -> directory.isAssignableUser(task.getTenantId(), id)).toList();
	}

	private static void requireText(String value, String code) {
		if (value == null || value.isBlank())
			throw bad(code);
	}

	private static void requireV2(WorkTaskEntity task) {
		if (!Objects.equals(task.getTaskContractVersion(), 2))
			throw conflict("LEGACY_TASK_USE_V1");
	}

	private static boolean closed(WorkTaskEntity task) {
		return task.getStatus() == WorkTaskStatus.DONE || task.getStatus() == WorkTaskStatus.CANCELLED;
	}

	private static void checkVersion(WorkTaskEntity task, Integer version) {
		if (version == null || !Objects.equals(task.getVersion(), version))
			throw conflict("TASK_VERSION_CONFLICT");
	}

	private static String priority(String value) {
		if (value == null)
			return "NORMAL";
		if (!Set.of("LOW", "NORMAL", "HIGH", "URGENT").contains(value))
			throw bad("INVALID_PRIORITY");
		return value;
	}

	private static String first(String first, String second) {
		return first == null ? second : first;
	}

	private static LocalDateTime now() {
		return LocalDateTime.now(ZONE);
	}

	private static long bounded(long size) {
		return Math.max(1, Math.min(100, size));
	}

	private static long positive(long value) {
		return Math.max(1, value);
	}

	private static ResponseStatusException bad(String code) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
	}

	private static ResponseStatusException conflict(String code) {
		return new ResponseStatusException(HttpStatus.CONFLICT, code);
	}

	private static ResponseStatusException forbidden() {
		return new ResponseStatusException(HttpStatus.FORBIDDEN, "TASK_ACTION_FORBIDDEN");
	}

	private boolean matches(TenantUserDirectory.AssignableUser user, String keyword) {
		return keyword == null || keyword.isBlank()
				|| (Objects.toString(user.displayName(), "") + " " + Objects.toString(user.username(), ""))
					.toLowerCase(Locale.ROOT)
					.contains(keyword.toLowerCase(Locale.ROOT));
	}

	private <T, R> Page<R> slice(List<T> values, long current, long size, java.util.function.Function<T, R> mapper) {
		long safeSize = bounded(size), safeCurrent = positive(current);
		Page<R> page = new Page<>(safeCurrent, safeSize, values.size());
		long offset = safeCurrent > Long.MAX_VALUE / safeSize ? values.size() : (safeCurrent - 1) * safeSize;
		page.setRecords(values.stream().skip(Math.min(offset, values.size())).limit(safeSize).map(mapper).toList());
		return page;
	}

	private Map<String, Object> snapshot(WorkTaskEntity task) {
		return json.convertValue(task, Map.class);
	}

	private String writeJson(Object value) {
		try {
			return json.writeValueAsString(value);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Task event serialization failed", e);
		}
	}

	private <T> T readJson(String value, Class<T> type) {
		try {
			return json.readValue(value, type);
		}
		catch (JsonProcessingException e) {
			throw new IllegalStateException("Task event deserialization failed", e);
		}
	}

	private String hash(String route, Object request) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256")
					.digest((route + "\n" + writeJson(request)).getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String eventType(String action, WorkTaskCommandDTO request) {
		return switch (action) {
			case "start" -> "TASK_STARTED";
			case "progress" -> "TASK_PROGRESS";
			case "block" -> "TASK_BLOCKED";
			case "resume" -> "TASK_RESUMED";
			case "submit" -> "TASK_SUBMITTED";
			case "accept" -> "PASS".equals(request.getDecision()) ? "TASK_ACCEPTED" : "TASK_RETURNED";
			case "acceptor" -> "TASK_ACCEPTOR_CHANGED";
			case "transfer" -> "TASK_TRANSFER_REQUESTED";
			case "transfer-decision" ->
				"TASK_TRANSFER_" + ("CANCEL".equals(request.getDecision()) ? "WITHDRAW" : request.getDecision());
			case "nudge" -> "TASK_NUDGED";
			case "dispatch" -> "TASK_DISPATCH_REQUESTED";
			default -> "TASK_UPDATED";
		};
	}

}
