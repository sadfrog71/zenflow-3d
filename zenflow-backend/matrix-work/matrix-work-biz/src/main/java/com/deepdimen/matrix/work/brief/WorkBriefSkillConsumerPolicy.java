package com.deepdimen.matrix.work.brief;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.skill.api.SkillConsumerPolicy;
import com.deepdimen.matrix.skill.api.SkillContract.Consumer;
import com.deepdimen.matrix.skill.api.SkillContract.Identity;
import com.deepdimen.matrix.skill.api.SkillContract.ResolvedSkillSnapshot;
import com.deepdimen.matrix.skill.api.SkillContract.RuntimeKind;
import com.deepdimen.matrix.skill.api.SkillExecutionService;
import com.deepdimen.matrix.work.entity.WorkBriefPolicyEntity;
import com.deepdimen.matrix.work.mapper.WorkBriefPolicyMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Binding scope follows the existing brief policy; it does not grant access to business
 * data.
 */
@Component
@RequiredArgsConstructor
public class WorkBriefSkillConsumerPolicy implements SkillConsumerPolicy {

	private final WorkBriefPolicyMapper policies;

	private final TenantUserDirectory users;

	private final ObjectMapper json;

	private final ObjectProvider<SkillExecutionService> executionServices;

	@Override
	public String consumerType() {
		return "WORK_BRIEF";
	}

	@Override
	public java.util.List<BindingTarget> bindingTargets(String tenantId, String actorId, String beforeId) {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		var user = SecurityUtils.getUser();
		long tenant = positiveId(tenantId), actor = positiveId(actorId);
		if (auth == null || !auth.isAuthenticated() || user == null || !Long.valueOf(actor).equals(user.getId())
				|| !Long.valueOf(tenant).equals(TenantContextHolder.getTenantId())
				|| auth.getAuthorities().stream().noneMatch(a -> "knowledge_skill_enable".equals(a.getAuthority()))
				|| !users.isAssignableUser(tenant, actor))
			throw invalid("CONSUMER_NOT_AUTHORIZED");
		var organizations = new java.util.ArrayList<Long>(users.listOrganizationIds(tenant, actor));
		organizations.add(0L);
		Long cursor = beforeId == null ? null : positiveId(beforeId);
		return policies
			.selectList(Wrappers.<WorkBriefPolicyEntity>lambdaQuery()
				.eq(WorkBriefPolicyEntity::getTenantId, tenant)
				.in(WorkBriefPolicyEntity::getOrgId, organizations)
				.lt(cursor != null, WorkBriefPolicyEntity::getId, cursor)
				.orderByDesc(WorkBriefPolicyEntity::getId)
				.last("LIMIT 50"))
			.stream()
			.filter(p -> Long.valueOf(tenant).equals(p.getTenantId()) && organizations.contains(p.getOrgId()))
			.map(p -> new BindingTarget(new Consumer(consumerType(), p.getId().toString(), "WORK_BRIEF", "default"),
					p.getOrgId() == 0L ? "租户默认工作简报" : "组织 " + p.getOrgId() + " 的工作简报",
					Boolean.TRUE.equals(p.getEnabled())))
			.toList();
	}

	@Override
	public void authorizeBinding(String tenantId, String actorId, Consumer consumer, BindingConfiguration binding) {
		var policy = requirePolicy(tenantId, actorId, consumer);
		validateConfiguration(policy, binding);
	}

	private void validateConfiguration(WorkBriefPolicyEntity policy, BindingConfiguration binding) {
		try {
			var audience = json.readTree(binding.audienceJson());
			if (audience == null || !audience.isObject() || audience.size() != 1
					|| !"POLICY".equals(audience.path("scope").asText())) {
				throw invalid("简报受众必须沿用原策略范围");
			}
			var fallback = json.readTree(binding.fallbackPolicyJson());
			String mode = fallback == null ? "" : fallback.path("mode").asText();
			if (fallback == null || !fallback.isObject() || fallback.size() != 1 || !("NONE".equals(mode)
					|| ("DETERMINISTIC".equals(mode) && Boolean.TRUE.equals(policy.getAllowDeterministicFallback())))) {
				throw invalid("绑定不能扩大原策略的降级权限");
			}
			if (!"EXPLICIT".equals(binding.activationMode())) {
				throw invalid("简报托管绑定当前仅支持原业务显式调用");
			}
		}
		catch (java.io.IOException ex) {
			throw invalid("简报绑定配置无效");
		}
	}

	@Override
	public void validateActivation(String tenantId, String actorId, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		authorizeBinding(tenantId, actorId, consumer, binding);
		if (!Boolean.TRUE.equals(requirePolicy(tenantId, actorId, consumer).getEnabled())) {
			throw invalid("原工作简报策略已停用");
		}
		if (release.runtimeKind() != RuntimeKind.PROMPT || !"PROMPT".equals(release.executorRef().executorKey())) {
			throw invalid("AGENT_SKILL_UNSUPPORTED");
		}
		WorkBriefSkillContractValidator.validate(json, release);
		// The managed runner must be present before activation. Legacy generation is not
		// a substitute.
		if (executionServices.getIfAvailable() == null || !executionServices.getObject().available(consumer, release)) {
			throw invalid("EXECUTOR_UNAVAILABLE");
		}
	}

	@Override
	public void authorizeExecution(Identity identity, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		// Background delegation requires a separately verified service identity; not
		// inferred from management permission.
		if (!identity.actorId().equals(identity.subjectId()) || !binding.enabled()) {
			throw invalid("CONSUMER_NOT_AUTHORIZED");
		}
		var policy = requirePolicy(identity.tenantId(), identity.actorId(), consumer, false);
		if (!Boolean.TRUE.equals(policy.getEnabled()))
			throw invalid("原工作简报策略已停用");
		validateConfiguration(policy, binding);
		if (release.runtimeKind() != RuntimeKind.PROMPT || !"PROMPT".equals(release.executorRef().executorKey())) {
			throw invalid("AGENT_SKILL_UNSUPPORTED");
		}
		WorkBriefSkillContractValidator.validate(json, release);
	}

	@Override
	public void bindingActivated(String tenantId, String actorId, Consumer consumer, BindingConfiguration binding) {
		authorizeBinding(tenantId, actorId, consumer, binding);
		if (policies.markManaged(positiveId(tenantId), positiveId(consumer.id())) != 1) {
			throw invalid("简报策略迁移标记保存失败");
		}
	}

	private WorkBriefPolicyEntity requirePolicy(String tenantId, String actorId, Consumer consumer) {
		return requirePolicy(tenantId, actorId, consumer, true);
	}

	private WorkBriefPolicyEntity requirePolicy(String tenantId, String actorId, Consumer consumer,
			boolean management) {
		var auth = SecurityContextHolder.getContext().getAuthentication();
		var user = SecurityUtils.getUser();
		if (auth == null || !auth.isAuthenticated() || user == null || user.getId() == null
				|| !user.getId().toString().equals(actorId)
				|| !String.valueOf(TenantContextHolder.getTenantId()).equals(tenantId)
				|| (management && auth.getAuthorities()
					.stream()
					.noneMatch(a -> "knowledge_skill_enable".equals(a.getAuthority())))) {
			throw invalid("CONSUMER_NOT_AUTHORIZED");
		}
		long tenant = positiveId(tenantId);
		long actor = positiveId(actorId);
		long policyId = positiveId(consumer.id());
		if (!consumerType().equals(consumer.type()) || !"WORK_BRIEF".equals(consumer.slotCode())
				|| !"default".equals(consumer.bindingKey()) || !users.isAssignableUser(tenant, actor)) {
			throw invalid("CONSUMER_NOT_AUTHORIZED");
		}
		var policy = policies.selectOne(Wrappers.<WorkBriefPolicyEntity>lambdaQuery()
			.eq(WorkBriefPolicyEntity::getTenantId, tenant)
			.eq(WorkBriefPolicyEntity::getId, policyId));
		if (policy == null || !Long.valueOf(tenant).equals(policy.getTenantId()) || policy.getOrgId() == null
				|| (policy.getOrgId() != 0L && !users.isOrganizationMember(tenant, actor, policy.getOrgId()))) {
			throw invalid("CONSUMER_NOT_AUTHORIZED");
		}
		return policy;
	}

	private long positiveId(String value) {
		try {
			long parsed = Long.parseLong(value);
			if (parsed <= 0 || !Long.toString(parsed).equals(value))
				throw invalid("无效的简报消费者身份");
			return parsed;
		}
		catch (NumberFormatException ex) {
			throw invalid("无效的简报消费者身份");
		}
	}

	private IllegalArgumentException invalid(String reason) {
		return new IllegalArgumentException(reason);
	}

}
