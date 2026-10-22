package com.deepdimen.matrix.knowledge.support.dataquery;

import java.lang.reflect.Proxy;
import java.sql.*;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

/**
 * Local behavioral checks with fake ports/JDBC spies. Never claims real TOP data
 * validation.
 */
public final class ControlledDataQueryChecks {

	private static int checks;

	private static final Identity OWNER = new Identity("tenant-A", "user-A");

	private static final PatrolQueryCompiler.Candidate PLAN = new PatrolQueryCompiler.Candidate("top.patrol",
			"valid_patrol_count", "department", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), "Asia/Shanghai",
			"DESC");

	private static class MutableClock extends Clock {

		Instant now = Instant.parse("2026-09-11T01:00:00Z");

		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		public Clock withZone(ZoneId zone) {
			return this;
		}

		public Instant instant() {
			return now;
		}

	}

	private static class Fixture implements QueryAudit {

		final MutableClock clock = new MutableClock();

		Authorization scope = new Authorization("scope-1");

		PublishedRule rule = new PublishedRule("rule-1", DEFINITION, true);

		final AtomicInteger executed = new AtomicInteger();

		boolean failStart, failFinish;

		String lastCode, startedId, finishedId;

		QueryExecutor executor = query -> {
			executed.incrementAndGet();
			return List.of();
		};

		ControlledQueryService service = new ControlledQueryService(i -> scope, i -> rule, q -> executor.execute(q),
				this, clock, 2, Duration.ofMinutes(5));

		public void started(String id, Identity i, String version, String revision, PatrolQueryCompiler.Candidate c) {
			if (failStart)
				throw new IllegalStateException("secret");
			startedId = id;
		}

		public void finished(String id, Identity identity, String code, int groups) {
			if (failFinish)
				throw new IllegalStateException("secret");
			finishedId = id;
			lastCode = code;
		}

		String handle() {
			return service.validate(OWNER, PLAN).planHandle();
		}

	}

	public static void main(String[] args) throws Exception {
		Fixture f = new Fixture();
		String handle = f.handle();
		check(handle.length() == 43, "256-bit opaque handle");
		reject(() -> f.service.execute(new Identity("tenant-B", "user-A"), handle), "INVALID_PLAN_HANDLE");
		reject(() -> f.service.execute(new Identity("tenant-A", "user-B"), handle), "INVALID_PLAN_HANDLE");
		var result = f.service.execute(OWNER, handle);
		check(result.rows().isEmpty(), "authorized empty result succeeds");
		check(result.queryId().equals(f.startedId) && result.queryId().equals(f.finishedId), "audit correlation");
		check(result.ruleVersion().equals("rule-1") && "OK".equals(f.lastCode), "result rule provenance");
		reject(() -> f.service.execute(OWNER, handle), "INVALID_PLAN_HANDLE");
		check(f.executed.get() == 1, "exactly one execution");
		reject(() -> f.service.validate(null, PLAN), "UNAUTHENTICATED");
		Fixture denied = new Fixture();
		denied.scope = null;
		reject(denied::handle, "DATASET_NOT_AUTHORIZED");
		check(denied.executed.get() == 0, "deny before executor");
		Fixture unpublished = new Fixture();
		unpublished.rule = new PublishedRule("draft", DEFINITION, false);
		reject(unpublished::handle, "METRIC_DEFINITION_UNCONFIRMED");
		Fixture changed = new Fixture();
		String old = changed.handle();
		changed.scope = new Authorization("changed-membership");
		reject(() -> changed.service.execute(OWNER, old), "AUTHORIZATION_CHANGED");
		check(changed.executed.get() == 0, "compare actual grants even if revision unchanged");
		Fixture version = new Fixture();
		String oldRule = version.handle();
		version.rule = new PublishedRule("rule-2", DEFINITION, true);
		reject(() -> version.service.execute(OWNER, oldRule), "RULE_CHANGED");
		check(version.service.execute(OWNER, version.handle()).ruleVersion().equals("rule-2"),
				"new plan sees new rule");
		Fixture expiry = new Fixture();
		String expired = expiry.handle();
		expiry.clock.now = expiry.clock.now.plusSeconds(300);
		reject(() -> expiry.service.execute(OWNER, expired), "PLAN_EXPIRED");
		Fixture cap = new Fixture();
		cap.handle();
		cap.handle();
		reject(cap::handle, "PLAN_CAPACITY_EXCEEDED");
		cap.clock.now = cap.clock.now.plusSeconds(300);
		check(cap.handle() != null, "expired slots reclaimed");
		Fixture audit = new Fixture();
		audit.failStart = true;
		reject(() -> audit.service.execute(OWNER, audit.handle()), "AUDIT_UNAVAILABLE");
		check(audit.executed.get() == 0, "audit start failure prevents query");
		Fixture finish = new Fixture();
		finish.failFinish = true;
		reject(() -> finish.service.execute(OWNER, finish.handle()), "AUDIT_UNAVAILABLE");
		Fixture failure = new Fixture();
		failure.executor = q -> {
			throw new IllegalStateException("password=secret");
		};
		reject(() -> failure.service.execute(OWNER, failure.handle()), "QUERY_FAILED");
		check("QUERY_FAILED".equals(failure.lastCode), "safe failure audit");
		Fixture race = new Fixture();
		String shared = race.handle();
		ExecutorService threads = Executors.newFixedThreadPool(2);
		try {
			Callable<Boolean> action = () -> {
				try {
					race.service.execute(OWNER, shared);
					return true;
				}
				catch (DataQueryException e) {
					return false;
				}
			};
			var results = threads.invokeAll(List.of(action, action));
			check(results.stream().filter(x -> {
				try {
					return x.get();
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			}).count() == 1, "concurrent handle claim is single-use");
			check(race.executed.get() == 1, "race executes once");
		}
		finally {
			threads.shutdownNow();
		}
		jdbcChecks();
		System.out.println("PASS: " + checks + " service/JDBC checks (synthetic ports, no TOP connection)");
	}

	/** Spy validates driver interactions and cleanup, not SQL semantics. */
	private static class JdbcSpy {

		int rows, index, timeout, maxRows, closedConnections, closedStatements, closedResults;

		boolean readOnly, timeoutFailure;

		Map<Integer, Object> bound = new HashMap<>();

		JdbcPatrolQueryExecutor reentrant;

		boolean busyObserved;

		final PatrolQueryCompiler.CompiledQuery query = new PatrolQueryCompiler().compile(PLAN);

		@SuppressWarnings("unchecked")
		<T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
			return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
		}

		DataSource source() {
			return proxy(DataSource.class, (p, m, a) -> {
				if (m.getName().equals("getConnection"))
					return connection();
				throw new UnsupportedOperationException(m.getName());
			});
		}

		Connection connection() {
			return proxy(Connection.class, (p, m, a) -> switch (m.getName()) {
				case "isReadOnly" -> readOnly;
				case "setReadOnly" -> {
					readOnly = (boolean) a[0];
					yield null;
				}
				case "prepareStatement" -> {
					check(readOnly, "read-only before preparing");
					yield statement();
				}
				case "close" -> {
					closedConnections++;
					yield null;
				}
				default -> throw new UnsupportedOperationException(m.getName());
			});
		}

		PreparedStatement statement() {
			return proxy(PreparedStatement.class, (p, m, a) -> switch (m.getName()) {
				case "setQueryTimeout" -> {
					timeout = (int) a[0];
					yield null;
				}
				case "setMaxRows" -> {
					maxRows = (int) a[0];
					yield null;
				}
				case "setFetchSize" -> null;
				case "setObject" -> {
					bound.put((int) a[0], a[1]);
					yield null;
				}
				case "executeQuery" -> {
					if (reentrant != null) {
						reject(() -> reentrant.execute(query), "QUERY_BUSY");
						busyObserved = true;
					}
					if (timeoutFailure)
						throw new SQLTimeoutException("secret driver details");
					index = 0;
					yield result();
				}
				case "close" -> {
					closedStatements++;
					yield null;
				}
				default -> throw new UnsupportedOperationException(m.getName());
			});
		}

		ResultSet result() {
			return proxy(ResultSet.class, (p, m, a) -> switch (m.getName()) {
				case "next" -> ++index <= rows;
				case "getString" -> "department_name".equals(a[0]) ? null : "synthetic-id";
				case "getInt" -> 2;
				case "getLong" -> 7L;
				case "close" -> {
					closedResults++;
					yield null;
				}
				default -> throw new UnsupportedOperationException(m.getName());
			});
		}

	}

	private static void jdbcChecks() {
		JdbcSpy spy = new JdbcSpy();
		spy.rows = 1;
		JdbcPatrolQueryExecutor jdbc = new JdbcPatrolQueryExecutor(spy.source(), 1);
		spy.reentrant = jdbc;
		var rows = jdbc.execute(spy.query);
		check(spy.busyObserved, "bounded query concurrency");
		check(rows.size() == 1 && rows.get(0).departmentName() == null, "conflict remains null");
		check(spy.timeout == 30 && spy.maxRows == 1001, "statement budgets");
		check(spy.bound.size() == 3 && spy.bound.get(1).equals(LocalDateTime.of(2026, 8, 1, 0, 0)),
				"typed local time bindings");
		check(!spy.readOnly && spy.closedConnections == 1 && spy.closedStatements == 1 && spy.closedResults == 1,
				"cleanup and pool state restore");
		spy.reentrant = null;
		spy.rows = 1001;
		reject(() -> jdbc.execute(spy.query), "RESULT_LIMIT_EXCEEDED");
		check(spy.closedResults == 2 && spy.closedConnections == 2, "overflow cleans resources");
		spy.timeoutFailure = true;
		reject(() -> jdbc.execute(spy.query), "QUERY_TIMEOUT");
		check(spy.closedConnections == 3 && spy.closedStatements == 3, "timeout cleans resources");
		spy.timeoutFailure = false;
		spy.rows = 0;
		check(jdbc.execute(spy.query).isEmpty(), "permit released after all failure paths");
	}

	private static void check(boolean pass, String message) {
		if (!pass)
			throw new AssertionError(message);
		checks++;
	}

	private static void reject(Runnable action, String code) {
		try {
			action.run();
			throw new AssertionError("Expected " + code);
		}
		catch (DataQueryException e) {
			check(code.equals(e.getMessage()), "Expected " + code + " got " + e.getMessage());
		}
	}

}
