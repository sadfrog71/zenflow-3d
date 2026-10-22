package com.deepdimen.matrix.work.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.skill.api.SkillResultStore;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.entity.*;
import com.deepdimen.matrix.work.mapper.*;
import com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy;
import com.deepdimen.matrix.work.security.WorkTaskVisibilityPolicy.Actor;
import com.deepdimen.matrix.work.service.WorkTaskV2Service;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.Objects;

import org.springframework.stereotype.Component;

/**
 * Keeps original authorized input and raw suggestions. It never creates or dispatches a
 * task.
 */
@Component
public class TaskSkillResultStore implements SkillResultStore {

	private final WorkTaskSkillInvocationMapper runs;

	private final WorkTaskPolicyMapper policies;

	private final WorkTaskVisibilityPolicy visibility;

	private final WorkTaskV2Service tasks;

	private final ObjectMapper json;

	private final TaskDispatchSkillInputs dispatchInputs;

	public TaskSkillResultStore(WorkTaskSkillInvocationMapper runs, WorkTaskPolicyMapper policies,
			WorkTaskVisibilityPolicy visibility, WorkTaskV2Service tasks, ObjectMapper json) {
		this(runs, policies, visibility, tasks, json, null);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public TaskSkillResultStore(WorkTaskSkillInvocationMapper runs, WorkTaskPolicyMapper policies,
			WorkTaskVisibilityPolicy visibility, WorkTaskV2Service tasks, ObjectMapper json,
			TaskDispatchSkillInputs dispatchInputs) {
		this.runs = runs;
		this.policies = policies;
		this.visibility = visibility;
		this.tasks = tasks;
		this.json = json;
		this.dispatchInputs = dispatchInputs;
	}

	@Override
	public String consumerType() {
		return "TASK";
	}

	@Override
	public boolean supportsConsumer(Consumer consumer) {
		return consumerType().equals(consumer.type())
				&& ("TASK_PARSE".equals(consumer.slotCode())
						|| ("TASK_DISPATCH".equals(consumer.slotCode()) && dispatchInputs != null))
				&& "default".equals(consumer.bindingKey());
	}

	public WorkTaskSkillInvocationEntity prepareParse(Long policyId, Long parentId, String text) {
		Actor actor = currentActor();
		var policy = policy(actor.tenantId(), policyId);
		if (text == null || text.isBlank() || text.length() > 30000 || policy.getParseSkillPackageId() == null)
			throw invalid();
		checkParent(parentId);
		var run = new WorkTaskSkillInvocationEntity();
		run.setTenantId(actor.tenantId());
		run.setOwnerUserId(actor.userId());
		run.setOrgId(actor.organizationId());
		run.setPolicyId(policy.getId());
		run.setPolicyVersion(policy.getVersion());
		run.setModelId(policy.getChatModelId());
		run.setSourcePackageId(policy.getParseSkillPackageId());
		run.setSlotCode("TASK_PARSE");
		run.setParentTaskId(parentId);
		run.setInputSnapshot(json.createObjectNode()
			.put("text", text)
			.put("parseMode", TaskParseContract.mode(parentId))
			.put("maxTasks", TaskParseContract.maxTasks(parentId))
			.put("currentTime", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString())
			.toString());
		run.setStatus("PREPARED");
		run.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Shanghai")));
		if (runs.insert(run) != 1 || run.getId() == null)
			throw new IllegalStateException("TASK_INPUT_NOT_SAVED");
		return run;
	}

	@Override
	public String authorizeInput(Identity identity, Consumer consumer, String businessRunId, String inputJson) {
		var run = authorized(identity, consumer, businessRunId);
		JsonNode supplied = object(inputJson, 131072), saved = object(run.getInputSnapshot(), 131072);
		if (!saved.equals(supplied))
			throw invalid();
		if ("TASK_PARSE".equals(consumer.slotCode()) && (!saved.path("parseMode").isTextual()
				|| !TaskParseContract.mode(run.getParentTaskId()).equals(saved.path("parseMode").textValue())
				|| !saved.path("maxTasks").isIntegralNumber() || !saved.path("maxTasks").canConvertToInt()
				|| saved.path("maxTasks").intValue() != TaskParseContract.maxTasks(run.getParentTaskId())))
			throw invalid();
		return saved.toString();
	}

	@Override
	public String save(Identity identity, Consumer consumer, String businessRunId, String skillRunId,
			String outputJson) {
		var run = authorized(identity, consumer, businessRunId);
		long skillRun = id(skillRunId);
		JsonNode output = validateOutput(run, outputJson);
		if (runs.saveOnce(run.getTenantId(), run.getOwnerUserId(), run.getPolicyId(), run.getId(), consumer.slotCode(),
				skillRun, output.toString()) != 1) {
			var existing = authorized(identity, consumer, businessRunId);
			if (!Long.valueOf(skillRun).equals(existing.getSkillRunId())
					|| !output.equals(validateOutput(existing, existing.getSkillOutputJson())))
				throw new IllegalStateException("TASK_SKILL_RESULT_CONFLICT");
		}
		return reference(businessRunId, skillRunId);
	}

	@Override
	public String read(Identity identity, Consumer consumer, String businessRunId, String skillRunId,
			String resultRef) {
		if (!reference(businessRunId, skillRunId).equals(resultRef))
			throw invalid();
		var run = authorized(identity, consumer, businessRunId);
		if (!Long.valueOf(id(skillRunId)).equals(run.getSkillRunId()) || !"RAW_RESULT".equals(run.getStatus()))
			throw invalid();
		return validateOutput(run, run.getSkillOutputJson()).toString();
	}

	private WorkTaskSkillInvocationEntity authorized(Identity identity, Consumer consumer, String businessRunId) {
		if ("TASK_DISPATCH".equals(consumer.slotCode())) {
			if (dispatchInputs == null)
				throw invalid();
			var record = dispatchInputs.authorize(identity, consumer, businessRunId);
			if (record == null)
				throw invalid();
			return record;
		}
		Actor actor = currentActor();
		if (!identity.actorId().equals(identity.subjectId()) || !actor.tenantId().toString().equals(identity.tenantId())
				|| !actor.userId().toString().equals(identity.actorId()) || !consumerType().equals(consumer.type())
				|| !"TASK_PARSE".equals(consumer.slotCode()) || !"default".equals(consumer.bindingKey()))
			throw invalid();
		var run = runs.findOwned(actor.tenantId(), actor.userId(), id(consumer.id()), id(businessRunId),
				consumer.slotCode());
		if (run == null || !Objects.equals(run.getTenantId(), actor.tenantId())
				|| !Objects.equals(run.getOwnerUserId(), actor.userId())
				|| !Objects.equals(run.getPolicyId(), id(consumer.id()))
				|| !Objects.equals(run.getId(), id(businessRunId))
				|| !Objects.equals(run.getOrgId(), actor.organizationId())
				|| !consumer.slotCode().equals(run.getSlotCode())
				|| !("PREPARED".equals(run.getStatus()) || "RAW_RESULT".equals(run.getStatus())))
			throw invalid();
		var policy = policy(actor.tenantId(), run.getPolicyId());
		if (!Objects.equals(policy.getVersion(), run.getPolicyVersion())
				|| !Objects.equals(policy.getChatModelId(), run.getModelId())
				|| !Objects.equals(policy.getParseSkillPackageId(), run.getSourcePackageId()))
			throw new IllegalArgumentException("TASK_POLICY_CHANGED");
		checkParent(run.getParentTaskId());
		return run;
	}

	private JsonNode validateOutput(WorkTaskSkillInvocationEntity run, String value) {
		if ("TASK_DISPATCH".equals(run.getSlotCode()))
			return TaskDispatchOutputValidator.validate(json, value, run.getInputSnapshot());
		return TaskParseOutputValidator.validate(json, value, run.getParentTaskId());
	}

	private Actor currentActor() {
		Actor actor = visibility.actor();
		WorkTaskVisibilityPolicy.requirePermission("work_task_create");
		return actor;
	}

	private void checkParent(Long parentId) {
		if (parentId != null && (parentId <= 0 || !tasks.detail(parentId).getAllowedActions().contains("children")))
			throw invalid();
	}

	private WorkTaskPolicyEntity policy(Long tenant, Long policyId) {
		if (policyId == null || policyId <= 0)
			throw invalid();
		var policy = policies.selectOne(Wrappers.<WorkTaskPolicyEntity>lambdaQuery()
			.eq(WorkTaskPolicyEntity::getTenantId, tenant)
			.eq(WorkTaskPolicyEntity::getId, policyId));
		if (policy == null || !Objects.equals(policy.getTenantId(), tenant) || !Objects.equals(policy.getId(), policyId)
				|| !Boolean.TRUE.equals(policy.getEnabled()) || policy.getVersion() == null
				|| policy.getChatModelId() == null
				|| !"MANAGED".equals(TaskSkillManagementModes.requireMode(policy.getParseSkillManagementMode())))
			throw invalid();
		return policy;
	}

	private JsonNode object(String value, int limit) {
		if (value == null || value.length() > limit)
			throw invalid();
		try {
			var result = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value);
			if (result == null || !result.isObject())
				throw invalid();
			return result;
		}
		catch (java.io.IOException ex) {
			throw invalid();
		}
	}

	private String reference(String business, String skill) {
		return "work-task-skill:" + id(business) + ":" + id(skill);
	}

	private long id(String value) {
		try {
			long n = Long.parseLong(value);
			if (n <= 0 || !Long.toString(n).equals(value))
				throw invalid();
			return n;
		}
		catch (NumberFormatException ex) {
			throw invalid();
		}
	}

	private IllegalArgumentException invalid() {
		return new IllegalArgumentException("TASK_SKILL_INPUT_OR_RESULT_NOT_ACCESSIBLE");
	}

}
