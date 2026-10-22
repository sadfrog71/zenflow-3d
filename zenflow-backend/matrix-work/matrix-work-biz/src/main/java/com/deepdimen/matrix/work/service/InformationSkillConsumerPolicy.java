package com.deepdimen.matrix.work.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.skill.api.*;
import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.deepdimen.matrix.work.api.entity.InformationPolicy;
import com.deepdimen.matrix.work.mapper.InformationPolicyMapper;
import com.fasterxml.jackson.databind.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
@RequiredArgsConstructor
public class InformationSkillConsumerPolicy implements SkillConsumerPolicy {

	private final InformationPolicyMapper policies;

	private final TenantUserDirectory users;

	private final InformationSkillCollectionScope scope;

	private final InformationSkillResultStore results;

	private final ObjectProvider<SkillExecutionService> executors;

	private final ObjectMapper json;

	public String consumerType() {
		return "INFORMATION";
	}

	public List<BindingTarget> bindingTargets(String tenant, String actor, String before) {
		long tenantId = permissions(tenant, actor, true);
		var query = Wrappers.<InformationPolicy>query().eq("tenant_id", tenantId).orderByDesc("id").last("LIMIT 50");
		if (before != null)
			query.lt("id", positive(before));
		return policies.selectList(query)
			.stream()
			.flatMap(p -> List.of("ANALYZE", "REVIEW")
				.stream()
				.map(slot -> new BindingTarget(new Consumer(consumerType(), p.getId().toString(), slot, "default"),
						p.getName() + " · " + ("ANALYZE".equals(slot) ? "资讯分析" : "独立复核"),
						Integer.valueOf(1).equals(p.getEnabled()))))
			.toList();
	}

	public void authorizeBinding(String tenant, String actor, Consumer consumer, BindingConfiguration binding) {
		permissions(tenant, actor, true);
		policy(tenant, consumer);
		configuration(binding);
	}

	public void validateActivation(String tenant, String actor, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		authorizeBinding(tenant, actor, consumer, binding);
		compatible(policy(tenant, consumer), consumer, release);
		executor(consumer, release);
	}

	public boolean permitsBackgroundExecution(Identity identity, Consumer consumer, String businessRunId) {
		if (SecurityContextHolder.getContext().getAuthentication() != null)
			return false;
		try {
			executionContext(identity, consumer, businessRunId);
			return true;
		}
		catch (IllegalArgumentException denied) {
			return false;
		}
	}

	public void authorizeExecution(Identity identity, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		String prefix = "work-information:";
		if (identity.correlationId() == null || !identity.correlationId().startsWith(prefix))
			throw denied();
		executionContext(identity, consumer, identity.correlationId().substring(prefix.length()));
		var policy = policy(identity.tenantId(), consumer);
		if (!"MANAGED".equals(policy.getSkillManagementMode()))
			throw denied();
		active(binding, release);
		compatible(policy, consumer, release);
	}

	public void authorizeInspection(Identity identity, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		permissions(identity.tenantId(), identity.actorId(), false);
		if (!identity.actorId().equals(identity.subjectId()))
			throw denied();
		var policy = policy(identity.tenantId(), consumer);
		if (!"MANAGED".equals(policy.getSkillManagementMode()))
			throw denied();
		active(binding, release);
		compatible(policy, consumer, release);
	}

	public void bindingActivated(String tenant, String actor, Consumer consumer, BindingConfiguration binding,
			ResolvedSkillSnapshot release) {
		if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
			throw new IllegalArgumentException("INFORMATION_BINDING_TRANSACTION_REQUIRED");
		authorizeBinding(tenant, actor, consumer, binding);
		if (!binding.enabled())
			return;
		var policy = policy(tenant, consumer);
		active(binding, release);
		compatible(policy, consumer, release);
		executor(consumer, release);
		if (policy.getSkillPolicyVersion() == null || policies.markManaged(positive(tenant), policy.getId(),
				policy.getSkillPolicyVersion(), policy.getModelName()) != 1)
			throw new IllegalArgumentException("INFORMATION_POLICY_VERSION_CONFLICT");
	}

	private void executionContext(Identity identity, Consumer consumer, String businessId) {
		var context = scope.require();
		if (!context.tenantId().toString().equals(identity.tenantId()) || !context.actorId().equals(identity.actorId())
				|| !identity.actorId().equals(identity.subjectId())
				|| !context.policyId().toString().equals(consumer.id())
				|| !("work-information:" + positive(businessId)).equals(identity.correlationId()))
			throw denied();
		// Exact stored input is also checked at the execution/result-store boundary.
		results.authorizeInvocation(identity, consumer, businessId);
	}

	private InformationPolicy policy(String tenant, Consumer consumer) {
		if (!consumerType().equals(consumer.type()) || !List.of("ANALYZE", "REVIEW").contains(consumer.slotCode())
				|| !"default".equals(consumer.bindingKey()))
			throw denied();
		long id = positive(consumer.id());
		var p = policies.selectOne(Wrappers.<InformationPolicy>query().eq("tenant_id", positive(tenant)).eq("id", id));
		if (p == null || !Long.valueOf(id).equals(p.getId())
				|| !("LEGACY".equals(p.getSkillManagementMode()) || "MANAGED".equals(p.getSkillManagementMode())))
			throw denied();
		return p;
	}

	private void compatible(InformationPolicy policy, Consumer consumer, ResolvedSkillSnapshot release) {
		if (!Integer.valueOf(1).equals(policy.getEnabled()) || policy.getModelName() == null
				|| policy.getModelName().isBlank() || release.runtimeKind() != RuntimeKind.PROMPT
				|| !"PROMPT".equals(release.executorRef().executorKey())
				|| !policy.getModelName().equals(release.executorRef().resourceId())
				|| !release.resourceRefs().isEmpty() || !release.allowedParameters().isEmpty()
				|| !InformationSkillInstructions.source(consumer.slotCode())
					.instruction()
					.equals(release.instructionSnapshot()))
			throw new IllegalArgumentException("INFORMATION_RELEASE_INCOMPATIBLE");
		InformationSkillContract.validate(json, consumer.slotCode(), release);
	}

	private void executor(Consumer consumer, ResolvedSkillSnapshot release) {
		var executor = executors.getIfAvailable();
		if (executor == null || !executor.available(consumer, release))
			throw new IllegalArgumentException("EXECUTOR_UNAVAILABLE");
	}

	private void active(BindingConfiguration binding, ResolvedSkillSnapshot release) {
		configuration(binding);
		if (!binding.enabled() || !binding.releaseId().equals(release.releaseId())
				|| !binding.revisionId().equals(release.bindingRevisionId()))
			throw denied();
	}

	private void configuration(BindingConfiguration binding) {
		try {
			var r = json.reader()
				.with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
			if (!"EXPLICIT".equals(binding.activationMode())
					|| !r.readTree(binding.audienceJson()).equals(json.readTree("{\"scope\":\"POLICY\"}"))
					|| !r.readTree(binding.parameterJson()).equals(json.readTree("{}"))
					|| !r.readTree(binding.fallbackPolicyJson()).equals(json.readTree("{\"mode\":\"NONE\"}")))
				throw denied();
		}
		catch (java.io.IOException | NullPointerException failure) {
			throw denied();
		}
	}

	private long permissions(String tenant, String actor, boolean management) {
		long tenantId = positive(tenant), actorId = positive(actor);
		var auth = SecurityContextHolder.getContext().getAuthentication();
		var user = SecurityUtils.getUser();
		if (auth == null || !auth.isAuthenticated() || user == null || !Long.valueOf(actorId).equals(user.getId())
				|| !Long.valueOf(tenantId).equals(TenantContextHolder.getTenantId())
				|| !users.isAssignableUser(tenantId, actorId))
			throw denied();
		var authorities = auth.getAuthorities()
			.stream()
			.map(a -> a.getAuthority())
			.collect(java.util.stream.Collectors.toSet());
		if (!authorities.containsAll(management ? Set.of("knowledge_skill_enable", "work_information_policy")
				: Set.of("work_information_run")))
			throw denied();
		return tenantId;
	}

	private static long positive(String value) {
		try {
			long n = Long.parseLong(value);
			if (n <= 0 || !Long.toString(n).equals(value))
				throw denied();
			return n;
		}
		catch (RuntimeException ex) {
			throw denied();
		}
	}

	private static IllegalArgumentException denied() {
		return new IllegalArgumentException("CONSUMER_NOT_AUTHORIZED");
	}

}
