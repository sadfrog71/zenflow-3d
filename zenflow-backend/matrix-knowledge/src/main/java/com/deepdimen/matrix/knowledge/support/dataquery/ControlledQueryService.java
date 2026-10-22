package com.deepdimen.matrix.knowledge.support.dataquery;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Process-local orchestration. All ports are trusted server dependencies, NOT request
 * DTOs. No permissive defaults: missing grants or an unpublished definition prevent
 * execution. Restart invalidates handles. Wire one instance per process; multi-instance
 * routing needs a shared atomic store. Audit must be durable before enabling a real
 * endpoint.
 */
public final class ControlledQueryService {

	public static final String DEFINITION = "patrol-start-date-not-deleted-v1";

	public record Identity(String tenantId, String userId) {
		public Identity {
			if (tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank()) {
				throw new DataQueryException("UNAUTHENTICATED");
			}
		}

	}

	public record SourceBinding(long dataId, long datasourceId, String revision, String connectionFingerprint) {
	}

	public record Authorization(String revision, SourceBinding source) {
		public Authorization(String revision) {
			this(revision, null);
		}

		public Authorization {
			if (revision == null || revision.isBlank())
				throw new DataQueryException("DATASET_NOT_AUTHORIZED");
		}
	}

	public record PublishedRule(String version, String definition, boolean published, List<String> intentTerms,
			DataQueryDepartmentDimension.Definition department) {
		public PublishedRule(String version, String definition, boolean published, List<String> intentTerms) {
			this(version, definition, published, intentTerms, null);
		}

		public PublishedRule(String version, String definition, boolean published) {
			this(version, definition, published, List.of("巡视"));
		}

		public PublishedRule {
			intentTerms = List.copyOf(intentTerms);
		}
	}

	@FunctionalInterface
	public interface AuthorizationResolver {

		Authorization resolve(Identity identity);

	}

	@FunctionalInterface
	public interface RuleProvider {

		PublishedRule current(Identity identity);

	}

	@FunctionalInterface
	public interface QueryExecutor {

		List<PatrolRow> execute(QueryExecution execution);

	}

	public record QueryExecution(Identity identity, Authorization authorization,
			PatrolQueryCompiler.CompiledQuery query) {
	}

	/**
	 * Internal rows contain physical group keys. HTTP mapping must not serialize this
	 * type.
	 */
	public record PatrolRow(String companyId, String departmentId, String departmentName, long count,
			int nameVariantCount, long missingNameCount) {
	}

	public record QueryOrigin(String question, Long chatRecordId) {
		public QueryOrigin {
			if (question == null || question.isBlank() || question.length() > 2000)
				throw new DataQueryException("INVALID_REQUEST");
		}
	}

	public interface QueryAudit {

		void started(String queryId, Identity identity, String ruleVersion, String scopeRevision,
				PatrolQueryCompiler.Candidate candidate);

		void finished(String queryId, Identity identity, String code, int groupCount);

		default void origin(String queryId, Identity identity, QueryOrigin origin) {
			throw new DataQueryException("AUDIT_UNAVAILABLE");
		}

	}

	public record ValidatedPlan(String planHandle, Instant expiresAt, String ruleVersion) {
	}

	public record QueryResult(String queryId, String ruleVersion, PatrolQueryCompiler.Candidate definition,
			List<PatrolRow> rows) {
		public QueryResult {
			rows = List.copyOf(rows);
		}
	}

	private record StoredPlan(Identity identity, Authorization authorization, PublishedRule rule,
			PatrolQueryCompiler.Candidate candidate, Instant expiresAt) {
	}

	private final Map<String, StoredPlan> plans = new HashMap<>();

	private final SecureRandom random = new SecureRandom();

	private final PatrolQueryCompiler compiler = new PatrolQueryCompiler();

	private final AuthorizationResolver authorization;

	private final RuleProvider rules;

	private final QueryExecutor executor;

	private final QueryAudit audit;

	private final Clock clock;

	private final int capacity;

	private final Duration ttl;

	public ControlledQueryService(AuthorizationResolver authorization, RuleProvider rules, QueryExecutor executor,
			QueryAudit audit, Clock clock, int capacity, Duration ttl) {
		this.authorization = Objects.requireNonNull(authorization);
		this.rules = Objects.requireNonNull(rules);
		this.executor = Objects.requireNonNull(executor);
		this.audit = Objects.requireNonNull(audit);
		this.clock = Objects.requireNonNull(clock);
		if (capacity < 1 || capacity > 10000 || ttl == null || ttl.isNegative() || ttl.isZero()
				|| ttl.compareTo(Duration.ofMinutes(5)) > 0) {
			throw new IllegalArgumentException("Invalid plan budget");
		}
		this.capacity = capacity;
		this.ttl = ttl;
	}

	public ValidatedPlan validate(Identity identity, PatrolQueryCompiler.Candidate candidate) {
		requireIdentity(identity);
		Authorization scope = scope(identity);
		PublishedRule rule = rule(identity);
		compile(candidate, scope);
		synchronized (plans) {
			Instant now = clock.instant();
			plans.values().removeIf(plan -> !now.isBefore(plan.expiresAt()));
			if (plans.size() >= capacity) {
				throw new DataQueryException("PLAN_CAPACITY_EXCEEDED");
			}
			String handle;
			do {
				byte[] bytes = new byte[32];
				random.nextBytes(bytes);
				handle = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
			}
			while (plans.containsKey(handle));
			Instant expiresAt = now.plus(ttl);
			plans.put(handle, new StoredPlan(identity, scope, rule, candidate, expiresAt));
			return new ValidatedPlan(handle, expiresAt, rule.version());
		}
	}

	public QueryResult execute(Identity identity, String handle) {
		return execute(identity, handle, null);
	}

	public QueryResult execute(Identity identity, String handle, QueryOrigin origin) {
		requireIdentity(identity);
		StoredPlan plan;
		synchronized (plans) {
			plan = plans.get(handle);
			// Another identity must neither use nor consume the owner's handle.
			if (plan == null || !plan.identity().equals(identity)) {
				throw new DataQueryException("INVALID_PLAN_HANDLE");
			}
			plans.remove(handle); // atomic single-use claim, including failed attempts
		}
		if (!clock.instant().isBefore(plan.expiresAt())) {
			throw new DataQueryException("PLAN_EXPIRED");
		}
		Authorization currentScope = scope(identity);
		if (!currentScope.equals(plan.authorization())) {
			throw new DataQueryException("AUTHORIZATION_CHANGED");
		}
		PublishedRule currentRule = rule(identity);
		if (!currentRule.equals(plan.rule())) {
			throw new DataQueryException("RULE_CHANGED");
		}
		var query = compile(plan.candidate(), currentScope);
		String queryId = UUID.randomUUID().toString();
		try {
			audit.started(queryId, identity, currentRule.version(), currentScope.revision(), plan.candidate());
			if (origin != null)
				audit.origin(queryId, identity, origin);
		}
		catch (RuntimeException e) {
			throw new DataQueryException("AUDIT_UNAVAILABLE");
		}
		List<PatrolRow> rows;
		try {
			rows = List.copyOf(executor.execute(new QueryExecution(identity, currentScope, query)));
			if (rows.size() > 1000) {
				throw new DataQueryException("RESULT_LIMIT_EXCEEDED");
			}
		}
		catch (RuntimeException e) {
			String code = e instanceof DataQueryException ? e.getMessage() : "QUERY_FAILED";
			finish(queryId, identity, code, 0);
			throw new DataQueryException(code, queryId, currentRule.version());
		}
		finish(queryId, identity, "OK", rows.size());
		return new QueryResult(queryId, currentRule.version(), plan.candidate(), rows);
	}

	private void finish(String queryId, Identity identity, String code, int groups) {
		try {
			audit.finished(queryId, identity, code, groups);
		}
		catch (RuntimeException e) {
			throw new DataQueryException("AUDIT_UNAVAILABLE");
		}
	}

	private Authorization scope(Identity identity) {
		Authorization scope = authorization.resolve(identity);
		if (scope == null) {
			throw new DataQueryException("DATASET_NOT_AUTHORIZED");
		}
		return scope;
	}

	private PublishedRule rule(Identity identity) {
		PublishedRule rule = rules.current(identity);
		if (rule == null || !rule.published() || rule.version() == null || rule.version().isBlank()
				|| !DEFINITION.equals(rule.definition())) {
			throw new DataQueryException("METRIC_DEFINITION_UNCONFIRMED");
		}
		return rule;
	}

	private PatrolQueryCompiler.CompiledQuery compile(PatrolQueryCompiler.Candidate candidate, Authorization scope) {
		try {
			return compiler.compile(candidate);
		}
		catch (IllegalArgumentException e) {
			throw new DataQueryException(e.getMessage());
		}
	}

	private static void requireIdentity(Identity identity) {
		if (identity == null) {
			throw new DataQueryException("UNAUTHENTICATED");
		}
	}

}
