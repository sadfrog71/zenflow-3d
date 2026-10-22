package com.deepdimen.matrix.work.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.skill.api.SkillConsumerPolicy;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.api.service.TaskSkillResolver;
import com.deepdimen.matrix.work.entity.WorkTaskPolicyEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskPolicyMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Task-policy bindings; only the parse slot may migrate after all execution checks pass.
 */
@Component
@RequiredArgsConstructor
public class TaskSkillConsumerPolicy implements SkillConsumerPolicy {

	private final WorkTaskPolicyMapper policies;

	private final TenantUserDirectory users;

	private final ObjectProvider<TaskSkillResolver> legacyResolvers;

	private final ObjectMapper json;

	private final ObjectProvider<com.deepdimen.matrix.skill.api.SkillExecutionService> executors;

	private final ObjectProvider<TaskDispatchSkillInputs> dispatchInputs;

	private static final List<String> SLOTS = List.of("TASK_PARSE", "TASK_DISPATCH");

	@Override
	public String consumerType() {
		return "TASK";
	}

	@Override
	public List<BindingTarget> bindingTargets(String tenantId, String actorId, String beforeId) {
		long tenant = requireManager(tenantId, actorId);
		Long cursor = beforeId == null ? null : positive(beforeId);
		var policy = policies
			.selectOne(Wrappers.<WorkTaskPolicyEntity>lambdaQuery().eq(WorkTaskPolicyEntity::getTenantId, tenant));
		if (policy == null)
			return List.of();
		validatePolicy(policy, tenant);
		if (cursor != null && policy.getId() >= cursor)
			return List.of();
		return SLOTS.stream()
			.map(slot -> new BindingTarget(new Consumer(consumerType(), policy.getId().toString(), slot, "default"),
					slot.equals("TASK_PARSE") ? "任务文本解析" : "任务派发建议", Boolean.TRUE.equals(policy.getEnabled())))
			.toList();
	}

	@Override
	public void authorizeBinding(String tenantId, String actorId, Consumer consumer, BindingConfiguration binding) {
		requirePolicy(tenantId, actorId, consumer);
		validateConfiguration(binding);
	}

	private void validateConfiguration(BindingConfiguration binding) {
		try {
			var reader = json.reader()
				.with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
			if (!reader.readTree(binding.audienceJson()).equals(json.readTree("{\"scope\":\"POLICY\"}"))
					|| !reader.readTree(binding.fallbackPolicyJson()).equals(json.readTree("{\"mode\":\"NONE\"}"))
					|| !reader.readTree(binding.parameterJson()).equals(json.readTree("{}"))
					|| !"EXPLICIT".equals(binding.activationMode()))
				throw invalid("TASK_BINDING_CONFIGURATION_INVALID");
		}
		catch (java.io.IOException | NullPointerException ex) {
			throw invalid("TASK_BINDING_CONFIGURATION_INVALID");
		}
	}

	@Override
	public void validateActivation(String tenantId, String actorId, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		authorizeBinding(tenantId, actorId, consumer, binding);
		var policy = requirePolicy(tenantId, actorId, consumer);
		validateRelease(policy, consumer, release);
		requireExecutor(consumer, release);
	}

	@Override
	public boolean permitsBackgroundExecution(Identity identity, Consumer consumer, String businessRunId) {
		if (SecurityContextHolder.getContext().getAuthentication() != null
				|| !"TASK_DISPATCH".equals(consumer.slotCode()))
			return false;
		var inputs = dispatchInputs.getIfAvailable();
		if (inputs == null)
			return false;
		try {
			return inputs.authorize(identity, consumer, businessRunId) != null;
		}
		catch (IllegalArgumentException denied) {
			return false;
		}
	}

	@Override
	public void authorizeExecution(Identity identity, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		if (!identity.actorId().equals(identity.subjectId()) || !binding.enabled())
			throw invalid("CONSUMER_NOT_AUTHORIZED");
		final WorkTaskPolicyEntity policy;
		if ("TASK_DISPATCH".equals(consumer.slotCode())) {
			if (SecurityContextHolder.getContext().getAuthentication() != null)
				throw invalid("CONSUMER_NOT_AUTHORIZED");
			var inputs = dispatchInputs.getIfAvailable();
			String prefix = "work-task-dispatch:";
			if (inputs == null || !identity.correlationId().startsWith(prefix))
				throw invalid("CONSUMER_NOT_AUTHORIZED");
			var record = inputs.authorize(identity, consumer, identity.correlationId().substring(prefix.length()));
			if (record == null)
				throw invalid("CONSUMER_NOT_AUTHORIZED");
			policy = policies.selectOne(Wrappers.<WorkTaskPolicyEntity>lambdaQuery()
				.eq(WorkTaskPolicyEntity::getTenantId, record.getTenantId())
				.eq(WorkTaskPolicyEntity::getId, record.getPolicyId()));
			validatePolicy(policy, positive(identity.tenantId()));
			if (!java.util.Objects.equals(policy.getId(), record.getPolicyId())
					|| !java.util.Objects.equals(policy.getVersion(), record.getPolicyVersion())
					|| !"MANAGED".equals(policy.getDispatchSkillManagementMode()))
				throw invalid("TASK_POLICY_CHANGED");
		}
		else {
			policy = requirePolicy(identity.tenantId(), identity.actorId(), consumer, false);
			if (!"TASK_PARSE".equals(consumer.slotCode())
					|| !"MANAGED".equals(TaskSkillManagementModes.requireMode(policy.getParseSkillManagementMode())))
				throw invalid("CONSUMER_NOT_AUTHORIZED");
		}
		validateConfiguration(binding);
		if (!binding.releaseId().equals(release.releaseId())
				|| !binding.revisionId().equals(release.bindingRevisionId()))
			throw invalid("BINDING_REVISION_CONFLICT");
		validateRelease(policy, consumer, release);
	}

	@Override
	public void authorizeInspection(Identity identity, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		if (!"TASK_DISPATCH".equals(consumer.slotCode())) {
			authorizeExecution(identity, consumer, binding, release);
			return;
		}
		if (!identity.actorId().equals(identity.subjectId()) || !binding.enabled())
			throw invalid("CONSUMER_NOT_AUTHORIZED");
		var policy = requirePolicy(identity.tenantId(), identity.actorId(), consumer, false);
		if (!"MANAGED".equals(TaskSkillManagementModes.requireMode(policy.getDispatchSkillManagementMode())))
			throw invalid("CONSUMER_NOT_AUTHORIZED");
		validateConfiguration(binding);
		if (!binding.releaseId().equals(release.releaseId())
				|| !binding.revisionId().equals(release.bindingRevisionId()))
			throw invalid("BINDING_REVISION_CONFLICT");
		validateRelease(policy, consumer, release);
	}

	@Override
	public void bindingActivated(String tenantId, String actorId, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
			throw invalid("TASK_BINDING_TRANSACTION_REQUIRED");
		authorizeBinding(tenantId, actorId, consumer, binding);
		if (!binding.enabled())
			return; // Disabling never restores the legacy path.
		var policy = requirePolicy(tenantId, actorId, consumer);
		TaskSkillManagementModes.requireMode("TASK_PARSE".equals(consumer.slotCode())
				? policy.getParseSkillManagementMode() : policy.getDispatchSkillManagementMode());
		if (!binding.releaseId().equals(release.releaseId())
				|| !binding.revisionId().equals(release.bindingRevisionId()))
			throw invalid("BINDING_REVISION_CONFLICT");
		validateRelease(policy, consumer, release);
		requireExecutor(consumer, release);
		if (policy.getVersion() == null)
			throw invalid("TASK_POLICY_VERSION_CONFLICT");
		int updated = "TASK_PARSE".equals(consumer.slotCode())
				? policies.markParseManaged(policy.getTenantId(), policy.getId(), policy.getVersion(),
						policy.getChatModelId(), policy.getParseSkillPackageId())
				: policies.markDispatchManaged(policy.getTenantId(), policy.getId(), policy.getVersion(),
						policy.getChatModelId(), policy.getDispatchSkillPackageId());
		if (updated != 1)
			throw invalid("TASK_POLICY_VERSION_CONFLICT");
	}

	private void requireExecutor(Consumer consumer, ResolvedSkillSnapshot release) {
		var executor = executors.getIfAvailable();
		if (executor == null || !executor.available(consumer, release))
			throw invalid("EXECUTOR_UNAVAILABLE");
	}

	private void validateRelease(WorkTaskPolicyEntity policy, Consumer consumer, ResolvedSkillSnapshot release) {
		if (!Boolean.TRUE.equals(policy.getEnabled()) || policy.getChatModelId() == null
				|| policy.getChatModelId() <= 0)
			throw invalid("TASK_POLICY_UNAVAILABLE");
		if (release.runtimeKind() != RuntimeKind.PROMPT || !"PROMPT".equals(release.executorRef().executorKey())
				|| !release.resourceRefs().isEmpty() || !release.allowedParameters().isEmpty())
			throw invalid("AGENT_SKILL_UNSUPPORTED");
		var resolver = legacyResolvers.getIfAvailable();
		if (resolver == null)
			throw invalid("EXECUTOR_UNAVAILABLE");
		Long packageId = consumer.slotCode().equals("TASK_PARSE") ? policy.getParseSkillPackageId()
				: policy.getDispatchSkillPackageId();
		var source = resolver.resolve(policy.getTenantId(), packageId);
		if (!java.util.Objects.equals(source.packageId(), packageId)
				|| !java.util.Objects.equals(source.prompt(), release.instructionSnapshot()))
			throw invalid("TASK_SKILL_INSTRUCTION_DRIFT");
		if (!policy.getChatModelId().toString().equals(release.executorRef().resourceId()))
			throw invalid("TASK_MODEL_BINDING_CHANGED");
		if ("TASK_PARSE".equals(consumer.slotCode()))
			TaskParseSkillContractValidator.validate(json, release);
		else if ("TASK_DISPATCH".equals(consumer.slotCode()))
			TaskDispatchSkillContractValidator.validate(json, release);
	}

	private WorkTaskPolicyEntity requirePolicy(String tenantId, String actorId, Consumer consumer) {
		return requirePolicy(tenantId, actorId, consumer, true);
	}

	private WorkTaskPolicyEntity requirePolicy(String tenantId, String actorId, Consumer consumer, boolean management) {
		long tenant = requirePermissions(tenantId, actorId,
				management ? Set.of("knowledge_skill_enable", "work_task_policy_manage") : Set.of("work_task_create"));
		if (!consumerType().equals(consumer.type()) || !SLOTS.contains(consumer.slotCode())
				|| !"default".equals(consumer.bindingKey()))
			throw invalid("CONSUMER_NOT_AUTHORIZED");
		long id = positive(consumer.id());
		var policy = policies.selectOne(Wrappers.<WorkTaskPolicyEntity>lambdaQuery()
			.eq(WorkTaskPolicyEntity::getTenantId, tenant)
			.eq(WorkTaskPolicyEntity::getId, id));
		validatePolicy(policy, tenant);
		if (policy.getId() != id)
			throw invalid("CONSUMER_NOT_AUTHORIZED");
		return policy;
	}

	private long requireManager(String tenantId, String actorId) {
		return requirePermissions(tenantId, actorId, Set.of("knowledge_skill_enable", "work_task_policy_manage"));
	}

	private long requirePermissions(String tenantId, String actorId, Set<String> required) {
		long tenant = positive(tenantId), actor = positive(actorId);
		var auth = SecurityContextHolder.getContext().getAuthentication();
		var user = SecurityUtils.getUser();
		if (auth == null || !auth.isAuthenticated() || user == null || !Long.valueOf(actor).equals(user.getId())
				|| !Long.valueOf(tenant).equals(TenantContextHolder.getTenantId())
				|| !users.isAssignableUser(tenant, actor))
			throw invalid("CONSUMER_NOT_AUTHORIZED");
		var permissions = auth.getAuthorities()
			.stream()
			.map(a -> a.getAuthority())
			.collect(java.util.stream.Collectors.toSet());
		if (!permissions.containsAll(required))
			throw invalid("CONSUMER_NOT_AUTHORIZED");
		return tenant;
	}

	private void validatePolicy(WorkTaskPolicyEntity policy, long tenant) {
		if (policy == null || policy.getId() == null || policy.getId() <= 0
				|| !Long.valueOf(tenant).equals(policy.getTenantId()))
			throw invalid("CONSUMER_NOT_AUTHORIZED");
	}

	private long positive(String value) {
		try {
			long id = Long.parseLong(value);
			if (id <= 0 || !Long.toString(id).equals(value))
				throw invalid("CONSUMER_NOT_AUTHORIZED");
			return id;
		}
		catch (NumberFormatException ex) {
			throw invalid("CONSUMER_NOT_AUTHORIZED");
		}
	}

	private IllegalArgumentException invalid(String code) {
		return new IllegalArgumentException(code);
	}

}
