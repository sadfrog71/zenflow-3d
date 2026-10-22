package com.deepdimen.matrix.work.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.skill.api.SkillPromptModelPolicy;
import com.deepdimen.matrix.work.entity.WorkTaskPolicyEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskPolicyMapper;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Model selection is not task authorization; the input adapter and task services retain
 * that responsibility.
 */
@Component
@RequiredArgsConstructor
public class TaskSkillPromptModelPolicy implements SkillPromptModelPolicy {

	private final WorkTaskPolicyMapper policies;

	private final TenantUserDirectory users;

	@Override
	public String consumerType() {
		return "TASK";
	}

	@Override
	public Selection resolve(Identity identity, Consumer consumer, ResolvedSkillSnapshot snapshot) {
		if (!consumerType().equals(consumer.type())
				|| !Set.of("TASK_PARSE", "TASK_DISPATCH").contains(consumer.slotCode())
				|| !"default".equals(consumer.bindingKey()) || !identity.actorId().equals(identity.subjectId()))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		long tenant = positive(identity.tenantId()), subject = positive(identity.subjectId()),
				policyId = positive(consumer.id());
		if (!users.isAssignableUser(tenant, subject))
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		var policy = policies.selectOne(Wrappers.<WorkTaskPolicyEntity>lambdaQuery()
			.eq(WorkTaskPolicyEntity::getTenantId, tenant)
			.eq(WorkTaskPolicyEntity::getId, policyId));
		if (policy == null || !Long.valueOf(tenant).equals(policy.getTenantId())
				|| !Long.valueOf(policyId).equals(policy.getId()) || !Boolean.TRUE.equals(policy.getEnabled()))
			throw new IllegalArgumentException("TASK_POLICY_UNAVAILABLE");
		if (snapshot.runtimeKind() != RuntimeKind.PROMPT || !"PROMPT".equals(snapshot.executorRef().executorKey()))
			throw new IllegalArgumentException("AGENT_SKILL_UNSUPPORTED");
		Long modelId = policy.getChatModelId();
		if (modelId == null || modelId <= 0 || !modelId.toString().equals(snapshot.executorRef().resourceId()))
			throw new IllegalArgumentException("TASK_MODEL_BINDING_CHANGED");
		return new Selection(modelId.toString(), consumer.slotCode(), "TASK_PARSE".equals(consumer.slotCode())
				? TaskParseContract.PROMPT : TaskDispatchSkillContractValidator.PROMPT);
	}

	private long positive(String value) {
		try {
			long id = Long.parseLong(value);
			if (id <= 0 || !Long.toString(id).equals(value))
				throw new NumberFormatException();
			return id;
		}
		catch (NumberFormatException ex) {
			throw new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
		}
	}

}
