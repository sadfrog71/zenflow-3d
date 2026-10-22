package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.entity.WorkTaskPolicyEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Read-only per-slot capability hints; execution always rechecks current authorization.
 */
@Component
@RequiredArgsConstructor
public class TaskSkillAvailability {

	private final TaskModelRunner legacy;

	private final ObjectProvider<SkillBindingResolver> resolvers;

	private final ObjectProvider<SkillExecutionService> executors;

	public Status inspect(Long tenant, Long actor, WorkTaskPolicyEntity policy, boolean workersEnabled) {
		if (!workersEnabled || !Boolean.TRUE.equals(policy.getEnabled()))
			return new Status(false, false);
		boolean model = policy.getChatModelId() != null && policy.getChatModelId() > 0;
		boolean needsLegacy = "LEGACY".equals(policy.getParseSkillManagementMode())
				|| "LEGACY".equals(policy.getDispatchSkillManagementMode());
		boolean legacyAvailable = needsLegacy && model && legacy.available();
		boolean parse = false;
		if ("LEGACY".equals(policy.getParseSkillManagementMode()))
			parse = legacyAvailable && policy.getParseSkillPackageId() != null;
		else if ("MANAGED".equals(policy.getParseSkillManagementMode()))
			parse = managed(tenant, actor, policy, "TASK_PARSE");
		boolean dispatch = "LEGACY".equals(policy.getDispatchSkillManagementMode()) && legacyAvailable
				&& policy.getDispatchSkillPackageId() != null;
		if ("MANAGED".equals(policy.getDispatchSkillManagementMode()))
			dispatch = managed(tenant, actor, policy, "TASK_DISPATCH");
		return new Status(parse, dispatch);
	}

	private boolean managed(Long tenant, Long actor, WorkTaskPolicyEntity policy, String slot) {
		if (tenant == null || actor == null || policy.getId() == null)
			return false;
		try {
			var resolver = resolvers.getIfAvailable();
			var executor = executors.getIfAvailable();
			if (resolver == null || executor == null)
				return false;
			var consumer = new Consumer("TASK", policy.getId().toString(), slot, "default");
			var identity = new Identity(tenant.toString(), actor.toString(), actor.toString(),
					"task-capabilities:" + policy.getId());
			var snapshot = resolver.inspect(identity, consumer);
			return snapshot != null && executor.available(consumer, snapshot);
		}
		catch (IllegalArgumentException | IllegalStateException unavailable) {
			return false;
		}
	}

	public static String reason(WorkTaskPolicyEntity policy, boolean workersEnabled, boolean available, String slot) {
		if (available)
			return "";
		String label = "TASK_PARSE".equals(slot) ? "任务解析" : "派发建议";
		if (!workersEnabled)
			return label + "暂不可用，请联系管理员启用任务后台处理";
		if (!Boolean.TRUE.equals(policy.getEnabled()))
			return label + "暂不可用，任务 AI 策略尚未启用";
		String mode = "TASK_PARSE".equals(slot) ? policy.getParseSkillManagementMode()
				: policy.getDispatchSkillManagementMode();
		if ("MANAGED".equals(mode))
			return label + "暂不可用，请联系管理员检查已启用的 Skill 绑定、发布版本和访问权限";
		if ("LEGACY".equals(mode))
			return label + "暂不可用，请联系管理员检查对应用途 Skill 和对话模型配置";
		return label + "暂不可用，请联系管理员检查任务 AI 配置";
	}

	public record Status(boolean parseAvailable, boolean dispatchAvailable) {
	}

}
