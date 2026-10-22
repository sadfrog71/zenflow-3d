package com.deepdimen.matrix.work.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.work.entity.WorkTaskDispatchRunEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskDispatchRunMapper;
import java.time.*;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Thread-local evidence only; every use rechecks the durable lease and creator
 * permission.
 */
@Component
final class TaskDispatchWorkerScope {

	private final WorkTaskDispatchRunMapper runs;

	private final TaskDispatchCreatorAuthorization creators;

	private final Clock clock;

	private final ThreadLocal<Lease> current = new ThreadLocal<>();

	@Autowired
	TaskDispatchWorkerScope(WorkTaskDispatchRunMapper runs, TaskDispatchCreatorAuthorization creators) {
		this(runs, creators, Clock.systemUTC());
	}

	TaskDispatchWorkerScope(WorkTaskDispatchRunMapper runs, TaskDispatchCreatorAuthorization creators, Clock clock) {
		this.runs = runs;
		this.creators = creators;
		this.clock = clock;
	}

	Lease open(Long tenant, Long runId, String owner) {
		if (current.get() != null || tenant == null || tenant <= 0 || runId == null || runId <= 0 || owner == null
				|| owner.isBlank())
			throw denied();
		Lease lease = new Lease(tenant, runId, owner, Thread.currentThread());
		check(lease);
		current.set(lease);
		return lease;
	}

	WorkTaskDispatchRunEntity require() {
		Lease lease = current.get();
		if (lease == null)
			throw denied();
		return check(lease);
	}

	private WorkTaskDispatchRunEntity check(Lease lease) {
		if (lease.thread != Thread.currentThread() || lease.closed
				|| !lease.tenant.equals(TenantContextHolder.getTenantId())
				|| SecurityContextHolder.getContext().getAuthentication() != null)
			throw denied();
		var run = runs.selectOne(Wrappers.<WorkTaskDispatchRunEntity>lambdaQuery()
			.eq(WorkTaskDispatchRunEntity::getTenantId, lease.tenant)
			.eq(WorkTaskDispatchRunEntity::getId, lease.runId));
		if (run == null || !Objects.equals(run.getId(), lease.runId) || !Objects.equals(run.getTenantId(), lease.tenant)
				|| !"RUNNING".equals(run.getStatus()) || !lease.owner.equals(run.getLeaseOwner())
				|| run.getLeaseUntil() == null
				|| !run.getLeaseUntil().isAfter(LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai")))
				|| !creators.authorized(lease.tenant, run.getCreatorId()))
			throw denied();
		return run;
	}

	final class Lease implements AutoCloseable {

		private final Long tenant, runId;

		private final String owner;

		private final Thread thread;

		private boolean closed;

		private Lease(Long tenant, Long runId, String owner, Thread thread) {
			this.tenant = tenant;
			this.runId = runId;
			this.owner = owner;
			this.thread = thread;
		}

		@Override
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

	private IllegalArgumentException denied() {
		return new IllegalArgumentException("TASK_WORKER_LEASE_NOT_AUTHORIZED");
	}

}
