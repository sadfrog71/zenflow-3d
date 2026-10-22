package com.deepdimen.matrix.work.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.work.api.enums.WorkTaskStatus;
import com.deepdimen.matrix.work.entity.*;
import com.deepdimen.matrix.work.mapper.*;
import com.deepdimen.matrix.work.security.WorkTaskRules;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * Server-owned immutable dispatch input; a takeover reuses the same business invocation.
 */
@Component
@RequiredArgsConstructor
class TaskDispatchSkillInputs {

	private final TaskDispatchWorkerScope scope;

	private final WorkTaskMapper tasks;

	private final WorkTaskPolicyMapper policies;

	private final WorkTaskSkillInvocationMapper invocations;

	private final TenantUserDirectory users;

	private final ObjectMapper json;

	WorkTaskSkillInvocationEntity prepare() {
		var context = context();
		var existing = invocations.findDispatch(context.run.getTenantId(), context.run.getId());
		if (existing != null)
			return validate(context, existing);
		var creator = users.taskAuthority(context.run.getTenantId(), context.run.getCreatorId());
		var candidates = users.listTenantUsers(context.run.getTenantId()).stream().filter(candidate -> {
			var target = users.taskAuthority(context.run.getTenantId(), candidate.userId());
			return users.isAssignableUser(context.run.getTenantId(), candidate.userId()) && !target.generalManager()
					&& WorkTaskRules.canAssign(creator.level(), target.level());
		}).toList();
		if (candidates.isEmpty())
			throw denied();
		var input = json.createObjectNode()
			.put("title", context.task.getTitle())
			.put("content", context.task.getDescription());
		var candidateNodes = input.putArray("candidates");
		for (var candidate : candidates)
			candidateNodes.addObject()
				.put("userId", candidate.userId().toString())
				.put("displayName", candidate.displayName())
				.put("username", candidate.username())
				.put("recommendedApprover", candidate.recommendedApprover());
		if (input.toString().length() > 131072)
			throw denied();
		var record = new WorkTaskSkillInvocationEntity();
		record.setTenantId(context.run.getTenantId());
		record.setOwnerUserId(context.run.getCreatorId());
		record.setOrgId(context.task.getOrganizationId());
		record.setPolicyId(context.policy.getId());
		record.setPolicyVersion(context.run.getPolicyVersion());
		record.setModelId(context.run.getModelId());
		record.setSourcePackageId(context.run.getSkillPackageId());
		record.setDispatchRunId(context.run.getId());
		record.setSlotCode("TASK_DISPATCH");
		record.setInputSnapshot(input.toString());
		record.setStatus("PREPARED");
		record.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
		try {
			if (invocations.insert(record) != 1 || record.getId() == null)
				throw denied();
		}
		catch (DuplicateKeyException conflict) {
			var winner = invocations.findDispatch(context.run.getTenantId(), context.run.getId());
			if (winner == null)
				throw conflict;
			return validate(context(), winner);
		}
		return validate(context(), record);
	}

	WorkTaskSkillInvocationEntity require(Long businessRunId) {
		var context = context();
		var record = invocations.findDispatch(context.run.getTenantId(), context.run.getId());
		if (record == null || !Objects.equals(businessRunId, record.getId()))
			throw denied();
		return validate(context, record);
	}

	WorkTaskSkillInvocationEntity authorize(com.deepdimen.matrix.skill.api.SkillContract.Identity identity,
			com.deepdimen.matrix.skill.api.SkillContract.Consumer consumer, String businessRunId) {
		final long id;
		try {
			id = Long.parseLong(businessRunId);
			if (id <= 0 || !Long.toString(id).equals(businessRunId))
				throw denied();
		}
		catch (NumberFormatException invalid) {
			throw denied();
		}
		if (!"TASK".equals(consumer.type()) || !"TASK_DISPATCH".equals(consumer.slotCode())
				|| !"default".equals(consumer.bindingKey()) || !identity.actorId().equals(identity.subjectId())
				|| !("work-task-dispatch:" + businessRunId).equals(identity.correlationId()))
			throw denied();
		var record = require(id);
		if (!record.getTenantId().toString().equals(identity.tenantId())
				|| !record.getOwnerUserId().toString().equals(identity.actorId())
				|| !record.getPolicyId().toString().equals(consumer.id()))
			throw denied();
		return record;
	}

	private Context context() {
		var run = scope.require();
		var task = tasks.selectOne(Wrappers.<WorkTaskEntity>lambdaQuery()
			.eq(WorkTaskEntity::getTenantId, run.getTenantId())
			.eq(WorkTaskEntity::getId, run.getTaskId()));
		var policy = policies.selectOne(
				Wrappers.<WorkTaskPolicyEntity>lambdaQuery().eq(WorkTaskPolicyEntity::getTenantId, run.getTenantId()));
		if (task == null || !Objects.equals(task.getTenantId(), run.getTenantId())
				|| !Objects.equals(task.getId(), run.getTaskId()) || task.getStatus() != WorkTaskStatus.DRAFT
				|| !Objects.equals(task.getCurrentDispatchRunId(), run.getId())
				|| !Objects.equals(task.getVersion(), run.getInputTaskVersion()) || policy == null
				|| policy.getId() == null || !Objects.equals(policy.getTenantId(), run.getTenantId())
				|| !Boolean.TRUE.equals(policy.getEnabled())
				|| !"MANAGED".equals(policy.getDispatchSkillManagementMode())
				|| !Objects.equals(policy.getVersion(), run.getPolicyVersion()) || run.getModelId() == null
				|| run.getModelId() <= 0 || !Objects.equals(policy.getChatModelId(), run.getModelId())
				|| run.getSkillPackageId() == null || run.getSkillPackageId() <= 0
				|| !Objects.equals(policy.getDispatchSkillPackageId(), run.getSkillPackageId()))
			throw denied();
		return new Context(run, task, policy);
	}

	private WorkTaskSkillInvocationEntity validate(Context c, WorkTaskSkillInvocationEntity record) {
		if (record.getId() == null || !Objects.equals(record.getTenantId(), c.run.getTenantId())
				|| !Objects.equals(record.getOwnerUserId(), c.run.getCreatorId())
				|| !Objects.equals(record.getOrgId(), c.task.getOrganizationId())
				|| !Objects.equals(record.getPolicyId(), c.policy.getId())
				|| !Objects.equals(record.getPolicyVersion(), c.run.getPolicyVersion())
				|| !Objects.equals(record.getDispatchRunId(), c.run.getId())
				|| !Objects.equals(record.getModelId(), c.run.getModelId())
				|| !Objects.equals(record.getSourcePackageId(), c.run.getSkillPackageId())
				|| !"TASK_DISPATCH".equals(record.getSlotCode()) || record.getParentTaskId() != null
				|| !Set.of("PREPARED", "RAW_RESULT").contains(record.getStatus()))
			throw denied();
		try {
			if (record.getInputSnapshot() == null || record.getInputSnapshot().length() > 131072)
				throw denied();
			var input = json.reader()
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
				.with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.readTree(record.getInputSnapshot());
			if (input == null || !input.isObject() || input.size() != 3 || !input.path("title").isTextual()
					|| !input.path("content").isTextual()
					|| !Objects.equals(input.path("title").textValue(), c.task.getTitle())
					|| !Objects.equals(input.path("content").textValue(), c.task.getDescription())
					|| !input.path("candidates").isArray() || input.path("candidates").isEmpty())
				throw denied();
			var creator = users.taskAuthority(c.run.getTenantId(), c.run.getCreatorId());
			Set<Long> ids = new HashSet<>();
			for (var candidate : input.path("candidates")) {
				long id = candidateId(candidate.path("userId"));
				if (!ids.add(id) || !users.isAssignableUser(c.run.getTenantId(), id))
					throw denied();
				var target = users.taskAuthority(c.run.getTenantId(), id);
				if (target.generalManager() || !WorkTaskRules.canAssign(creator.level(), target.level()))
					throw denied();
			}
		}
		catch (java.io.IOException ex) {
			throw denied();
		}
		return record;
	}

	private long candidateId(JsonNode value) {
		try {
			if (value.isIntegralNumber() && value.canConvertToLong() && value.longValue() > 0)
				return value.longValue();
			if (value.isTextual()) {
				long id = Long.parseLong(value.textValue());
				if (id > 0 && Long.toString(id).equals(value.textValue()))
					return id;
			}
		}
		catch (NumberFormatException invalid) {
			throw denied();
		}
		throw denied();
	}

	private record Context(WorkTaskDispatchRunEntity run, WorkTaskEntity task, WorkTaskPolicyEntity policy) {
	}

	private IllegalArgumentException denied() {
		return new IllegalArgumentException("TASK_DISPATCH_INPUT_NOT_AUTHORIZED");
	}

}
