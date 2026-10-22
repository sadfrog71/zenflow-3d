package com.deepdimen.matrix.work.notification;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.log.annotation.SysLog;
import com.deepdimen.matrix.work.ai.TaskPolicyService;
import com.deepdimen.matrix.work.entity.WorkTaskEventEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskEventMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/work/task", headers = "X-Task-Contract=2")
public class TaskNotificationAdminController {

	private final TaskPolicyService policies;

	private final WorkTaskEventMapper events;

	private final JdbcTemplate jdbc;

	@GetMapping("/notification-deliveries")
	public R<?> pending(@RequestParam(defaultValue = "1") int current) {
		policies.forManagement();
		int offset = (Math.max(1, Math.min(current, 10000)) - 1) * 20;
		return R.ok(jdbc.queryForList(
				"SELECT e.id AS eventId,e.task_id AS taskId,e.type,e.publish_status AS status,e.attempts,e.last_error AS reason FROM ai_work_task_event e WHERE e.tenant_id=? AND e.publish_status IN ('FAILED','PARTIAL','RETRY') ORDER BY e.occurred_at DESC,e.id DESC LIMIT 20 OFFSET ?",
				TenantContextHolder.getTenantId(), offset));
	}

	public record Retry(@NotBlank @Size(max = 2000) String reason, boolean retryUncertainEmails) {
	}

	@PostMapping("/events/{id}/retry-notifications")
	@Transactional
	@SysLog("重试任务通知")
	public R<?> retry(@PathVariable Long id, @Valid @RequestBody Retry request) {
		policies.forManagement();
		Long tenant = TenantContextHolder.getTenantId();
		var event = events.selectOne(Wrappers.<WorkTaskEventEntity>lambdaQuery()
			.eq(WorkTaskEventEntity::getTenantId, tenant)
			.eq(WorkTaskEventEntity::getId, id)
			.last("FOR UPDATE"));
		if (event == null)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TASK_EVENT_NOT_FOUND");
		if (!java.util.List.of("FAILED", "PARTIAL").contains(event.getPublishStatus()))
			throw new ResponseStatusException(HttpStatus.CONFLICT, "DELIVERY_NOT_RETRYABLE");
		if ("PARTIAL".equals(event.getPublishStatus()) && !request.retryUncertainEmails())
			throw new ResponseStatusException(HttpStatus.CONFLICT, "UNCERTAIN_EMAIL_CONFIRMATION_REQUIRED");
		if (request.retryUncertainEmails())
			jdbc.update(
					"UPDATE sys_business_message_delivery SET status='PENDING',updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND biz_key=? AND status='UNKNOWN'",
					tenant, "task-event:" + id);
		events.update(null,
				Wrappers.<WorkTaskEventEntity>lambdaUpdate()
					.eq(WorkTaskEventEntity::getTenantId, tenant)
					.eq(WorkTaskEventEntity::getId, id)
					.set(WorkTaskEventEntity::getPublishStatus, "PENDING")
					.set(WorkTaskEventEntity::getAttempts, 0)
					.set(WorkTaskEventEntity::getNextAttemptAt, LocalDateTime.now(ZoneId.of("Asia/Shanghai")))
					.set(WorkTaskEventEntity::getLeaseUntil, null)
					.set(WorkTaskEventEntity::getLastError, "MANUAL_RETRY"));
		return R.ok();
	}

}
