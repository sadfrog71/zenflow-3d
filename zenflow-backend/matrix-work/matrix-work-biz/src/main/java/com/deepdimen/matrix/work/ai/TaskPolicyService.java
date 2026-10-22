package com.deepdimen.matrix.work.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.component.PermissionService;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.work.entity.WorkTaskPolicyEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskPolicyMapper;
import java.net.URI;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TaskPolicyService {

	private final WorkTaskPolicyMapper policies;

	private final TaskModelRunner models;

	private final TenantUserDirectory directory;

	private final TaskSkillAvailability skillAvailability;

	@org.springframework.beans.factory.annotation.Value("${matrix.work.task-workers-enabled:false}")
	private boolean workersEnabled;

	public WorkTaskPolicyEntity current(Long tenant) {
		if (tenant == null)
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TASK_IDENTITY_REQUIRED");
		WorkTaskPolicyEntity policy = policies
			.selectOne(Wrappers.<WorkTaskPolicyEntity>lambdaQuery().eq(WorkTaskPolicyEntity::getTenantId, tenant));
		if (policy != null)
			return policy;
		policy = new WorkTaskPolicyEntity();
		policy.setTenantId(tenant);
		policy.setVersion(0);
		policy.setEnabled(false);
		policy.setEmailEnabled(false);
		policy.setDueSoonHours(24);
		policy.setAcceptanceStaleHours(48);
		policy.setNudgeIntervalMinutes(10);
		policy.setNudgeDailyLimit(3);
		return policy;
	}

	public WorkTaskPolicyEntity forManagement() {
		requireManager();
		return current(TenantContextHolder.getTenantId());
	}

	public Map<String, Object> capabilities() {
		Long tenant = TenantContextHolder.getTenantId();
		if (SecurityUtils.getUser() == null || !directory.isAssignableUser(tenant, SecurityUtils.getUser().getId()))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TASK_MEMBER_REQUIRED");
		WorkTaskPolicyEntity p = current(tenant);
		var skillStatus = skillAvailability.inspect(tenant, SecurityUtils.getUser().getId(), p, workersEnabled);
		boolean ai = skillStatus.dispatchAvailable();
		String parseReason = TaskSkillAvailability.reason(p, workersEnabled, skillStatus.parseAvailable(),
				"TASK_PARSE");
		String dispatchReason = TaskSkillAvailability.reason(p, workersEnabled, ai, "TASK_DISPATCH");
		var authority = directory.taskAuthority(tenant, SecurityUtils.getUser().getId());
		var views = new java.util.ArrayList<>(
				java.util.List.of("all", "assigned", "executing", "acceptance", "transfer"));
		if (!authority.managedDepartmentIds().isEmpty())
			views.add("department");
		if (authority.generalManager())
			views.add("enterprise");
		return Map.of("views", views, "canCreate", new PermissionService().hasPermission("work_task_create"),
				"aiAvailable", ai, "parseAvailable", skillStatus.parseAvailable(), "voiceSupported", true,
				"emailEnabled", Boolean.TRUE.equals(p.getEmailEnabled()), "reason", dispatchReason, "parseReason",
				parseReason, "dispatchReason", dispatchReason, "enterpriseTimeZone", "Asia/Shanghai");
	}

	@Transactional
	public WorkTaskPolicyEntity save(WorkTaskPolicyEntity input) {
		requireManager();
		Long tenant = TenantContextHolder.getTenantId();
		WorkTaskPolicyEntity old = current(tenant);
		if (input.getVersion() == null || !input.getVersion().equals(old.getVersion()))
			throw new ResponseStatusException(HttpStatus.CONFLICT, "TASK_POLICY_VERSION_CONFLICT");
		range(input.getDueSoonHours(), 1, 720);
		range(input.getAcceptanceStaleHours(), 1, 720);
		range(input.getNudgeIntervalMinutes(), 1, 1440);
		range(input.getNudgeDailyLimit(), 1, 20);
		if (Boolean.TRUE.equals(input.getEnabled())) {
			if (input.getChatModelId() == null)
				throw new IllegalArgumentException("请选择平台对话模型");
			models.resolveSkill(tenant, input.getDispatchSkillPackageId());
			models.resolveSkill(tenant, input.getParseSkillPackageId());
		}
		if (Boolean.TRUE.equals(input.getEmailEnabled())) {
			if (input.getEmailBizCode() == null || input.getEmailBizCode().isBlank())
				throw new IllegalArgumentException("请选择既有邮件通道代码");
			try {
				URI uri = URI.create(input.getWebBaseUrl());
				if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
						|| uri.getFragment() != null)
					throw new IllegalArgumentException();
			}
			catch (RuntimeException ex) {
				throw new IllegalArgumentException("邮件任务入口须为有效 HTTPS 地址");
			}
		}
		TaskSkillManagementModes.preserveForSave(input, old);
		input.setId(old.getId());
		input.setTenantId(tenant);
		input.setVersion(old.getVersion() + 1);
		if (old.getId() == null)
			policies.insert(input);
		else if (policies.update(input,
				Wrappers.<WorkTaskPolicyEntity>lambdaUpdate()
					.eq(WorkTaskPolicyEntity::getTenantId, tenant)
					.eq(WorkTaskPolicyEntity::getId, old.getId())
					.eq(WorkTaskPolicyEntity::getVersion, old.getVersion())) != 1)
			throw new ResponseStatusException(HttpStatus.CONFLICT, "TASK_POLICY_VERSION_CONFLICT");
		return input;
	}

	private void requireManager() {
		Long tenant = TenantContextHolder.getTenantId();
		if (SecurityUtils.getUser() == null || !directory.isAssignableUser(tenant, SecurityUtils.getUser().getId())
				|| !new PermissionService().hasPermission("work_task_policy_manage"))
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TASK_POLICY_PERMISSION_REQUIRED");
	}

	private void range(Integer n, int min, int max) {
		if (n == null || n < min || n > max)
			throw new IllegalArgumentException("任务提醒参数超出允许范围");
	}

}
