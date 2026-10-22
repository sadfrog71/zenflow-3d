package com.deepdimen.matrix.work.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import com.deepdimen.matrix.common.security.component.PermissionService;
import com.deepdimen.matrix.work.api.entity.InformationPolicy;
import com.deepdimen.matrix.work.api.entity.InformationRun;
import com.deepdimen.matrix.work.mapper.InformationPolicyMapper;
import com.deepdimen.matrix.work.mapper.InformationRunMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.Objects;

/**
 * In-process collection authority; business IDs by themselves grant no execution rights.
 */
@Component
@RequiredArgsConstructor
public final class InformationSkillCollectionScope {

	private final InformationPolicyMapper policies;

	private final InformationRunMapper runs;

	private final TenantUserDirectory users;

	private final ObjectMapper json;

	private final ThreadLocal<Lease> current = new ThreadLocal<>();

	// Only the collection pipeline in this package may establish a scope.
	Lease open(Long policyId, Long collectionRunId) {
		Long tenant = TenantContextHolder.getTenantId();
		if (current.get() != null || tenant == null || tenant <= 0 || policyId == null || policyId <= 0
				|| collectionRunId == null || collectionRunId <= 0)
			throw denied();
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String actor = actor(tenant, auth);
		var policy = policy(tenant, policyId);
		if (policy == null)
			throw denied();
		var lease = new Lease(tenant, policyId, collectionRunId, actor, auth, snapshot(policy));
		check(lease);
		current.set(lease);
		return lease;
	}

	public Context require() {
		var lease = current.get();
		if (lease == null)
			throw denied();
		return check(lease);
	}

	private Context check(Lease lease) {
		if (lease.closed || lease.thread != Thread.currentThread()
				|| !lease.tenant.equals(TenantContextHolder.getTenantId())
				|| lease.authentication != SecurityContextHolder.getContext().getAuthentication()
				|| !lease.actor.equals(actor(lease.tenant, lease.authentication)))
			throw denied();
		var policy = policy(lease.tenant, lease.policyId);
		var run = runs.selectOne(Wrappers.<InformationRun>query().eq("tenant_id", lease.tenant).eq("id", lease.runId));
		if (policy == null || !Integer.valueOf(1).equals(policy.getEnabled())
				|| !lease.policySnapshot.equals(snapshot(policy)) || run == null || !lease.runId.equals(run.getId())
				|| !lease.policyId.equals(run.getPolicyId()) || !Objects.equals(policy.getOrgId(), run.getOrgId())
				|| !"RUNNING".equals(run.getStatus()))
			throw denied();
		return new Context(lease.tenant, lease.policyId, lease.runId, policy.getOrgId(), lease.actor,
				lease.policySnapshot);
	}

	private InformationPolicy policy(Long tenant, Long id) {
		var policy = policies.selectOne(Wrappers.<InformationPolicy>query().eq("tenant_id", tenant).eq("id", id));
		return policy != null && id.equals(policy.getId()) ? policy : null;
	}

	private String snapshot(InformationPolicy policy) {
		try {
			return json.writeValueAsString(policy);
		}
		catch (Exception failure) {
			throw new IllegalArgumentException("INFORMATION_POLICY_SNAPSHOT_INVALID", failure);
		}
	}

	private String actor(Long tenant, Authentication authentication) {
		if (authentication == null)
			return "information-collector";
		var user = SecurityUtils.getUser();
		if (!authentication.isAuthenticated() || user == null || user.getId() == null
				|| !users.isAssignableUser(tenant, user.getId())
				|| !new PermissionService().hasPermission("work_information_run"))
			throw denied();
		return user.getId().toString();
	}

	public record Context(Long tenantId, Long policyId, Long collectionRunId, Long orgId, String actorId,
			String policySnapshot) {
	}

	final class Lease implements AutoCloseable {

		final Long tenant, policyId, runId;

		final String actor, policySnapshot;

		final Authentication authentication;

		final Thread thread = Thread.currentThread();

		boolean closed;

		Lease(Long tenant, Long policyId, Long runId, String actor, Authentication authentication,
				String policySnapshot) {
			this.tenant = tenant;
			this.policyId = policyId;
			this.runId = runId;
			this.actor = actor;
			this.authentication = authentication;
			this.policySnapshot = policySnapshot;
		}

		public void close() {
			if (thread != Thread.currentThread())
				throw denied();
			if (!closed) {
				if (current.get() != this)
					throw denied();
				current.remove();
				closed = true;
			}
		}

	}

	private static IllegalArgumentException denied() {
		return new IllegalArgumentException("INFORMATION_COLLECTION_NOT_AUTHORIZED");
	}

}
