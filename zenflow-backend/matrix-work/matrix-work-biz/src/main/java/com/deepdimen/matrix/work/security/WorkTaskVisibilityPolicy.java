package com.deepdimen.matrix.work.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.work.entity.WorkTaskEntity;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** One read predicate shared by task lists, details and aggregations. */
@Component
@RequiredArgsConstructor
public class WorkTaskVisibilityPolicy {

	private final TenantUserDirectory directory;

	private final CurrentOrganizationResolver organizationResolver;

	public record Actor(Long tenantId, Long userId, Long organizationId, TenantUserDirectory.TaskAuthority authority) {
	}

	public Actor actor() {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		var user = SecurityUtils.getUser();
		if (auth == null || !auth.isAuthenticated() || user == null)
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
		Long tenant = TenantContextHolder.getTenantId();
		if (tenant == null || !directory.isAssignableUser(tenant, user.getId()))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACTIVE_TENANT_MEMBERSHIP_REQUIRED");
		return new Actor(tenant, user.getId(), organizationResolver.resolve(),
				directory.taskAuthority(tenant, user.getId()));
	}

	public static boolean hasPermission(String permission) {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		return auth != null && auth.isAuthenticated()
				&& auth.getAuthorities().stream().anyMatch(a -> permission.equals(a.getAuthority()));
	}

	public static void requirePermission(String permission) {
		if (!hasPermission(permission))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TASK_ACTION_FORBIDDEN");
	}

	public Set<Long> managedUsers(Actor actor) {
		Set<Long> ids = new LinkedHashSet<>();
		for (Long dept : actor.authority().managedDepartmentIds())
			ids.addAll(directory.listOrganizationUserIds(actor.tenantId(), dept));
		return ids;
	}

	/**
	 * Server-side integrations resolve fresh authority; caller identity must be
	 * authenticated by their existing boundary.
	 */
	public LambdaQueryWrapper<WorkTaskEntity> queryFor(Long tenantId, Long userId) {
		if (tenantId == null || userId == null || !directory.isAssignableUser(tenantId, userId))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ACTIVE_TENANT_MEMBERSHIP_REQUIRED");
		return query(new Actor(tenantId, userId, directory.findDefaultOrganizationId(tenantId, userId),
				directory.taskAuthority(tenantId, userId)));
	}

	public LambdaQueryWrapper<WorkTaskEntity> query(Actor actor) {
		var query = Wrappers.<WorkTaskEntity>lambdaQuery().eq(WorkTaskEntity::getTenantId, actor.tenantId());
		if (actor.authority().generalManager())
			return query;
		Set<Long> managed = managedUsers(actor);
		query.and(q -> {
			q.eq(WorkTaskEntity::getCreatorId, actor.userId())
				.or()
				.eq(WorkTaskEntity::getOwnerId, actor.userId())
				.or()
				.eq(WorkTaskEntity::getPrimaryAssigneeId, actor.userId())
				.or()
				.exists("SELECT 1 FROM ai_work_task_assignment a WHERE a.task_id=ai_work_task.id"
						+ " AND a.tenant_id={0} AND a.user_id={1} AND a.role='ASSIGNEE'"
						+ " AND a.assignment_status='ACTIVE' AND a.del_flag='0'", actor.tenantId(), actor.userId())
				.or()
				.exists("SELECT 1 FROM ai_work_task_transfer tr WHERE tr.id=ai_work_task.pending_transfer_id"
						+ " AND tr.task_id=ai_work_task.id AND tr.tenant_id={0} AND tr.to_user_id={1} AND tr.status='PENDING'",
						actor.tenantId(), actor.userId())
				.or()
				.exists("SELECT 1 FROM ai_work_task p WHERE p.id=ai_work_task.parent_id"
						+ " AND p.tenant_id={0} AND p.primary_assignee_id={1} AND p.del_flag='0'", actor.tenantId(),
						actor.userId());
			if (!managed.isEmpty())
				q.or()
					.in(WorkTaskEntity::getCreatorId, managed)
					.or()
					.in(WorkTaskEntity::getOwnerId, managed)
					.or()
					.in(WorkTaskEntity::getPrimaryAssigneeId, managed)
					.or()
					.exists("SELECT 1 FROM ai_work_task_assignment ma WHERE ma.task_id=ai_work_task.id"
							+ " AND ma.tenant_id={0} AND ma.role='ASSIGNEE' AND ma.assignment_status='ACTIVE'"
							+ " AND ma.del_flag='0' AND ma.user_id IN ("
							+ managed.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))
							+ ")", actor.tenantId());
		});
		return query;
	}

}
