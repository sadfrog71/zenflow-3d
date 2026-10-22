package com.deepdimen.matrix.knowledge.support.dataquery;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.YearMonth;
import java.util.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

/**
 * Actual trial + independent name-level aggregation for one department-grain correction,
 * not AI approval.
 */
final class DataQueryDepartmentVerifier {

	record Pair(String company, String department) {
	}

	record Expected(Pair key, long count, Set<String> names, long missing) {
	}

	record Trial(boolean passed, boolean beforeFailed, int originalGroups, int correctedGroups, List<String> cases) {
	}

	private final DataQuerySqlExecutor executor;

	DataQueryDepartmentVerifier(DataQueryMetadataRepository metadata) {
		this.executor = new DataQuerySqlExecutor(metadata);
	}

	Trial verify(Identity who, Authorization authorization, DataQueryDepartmentDimension.Captured original) {
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
		return executor.snapshot(who, authorization, connection -> {
			var originalMonth = YearMonth.from(java.time.LocalDate.parse(original.scope().from()));
			var all = original.scope().at(originalMonth, 0);
			var oracle = oracle(connection, all, deadline);
			var baseline = baseline(connection, original, all, deadline);
			boolean beforeFailed = !baselineMatches(baseline, original.groupKeys().size() + 1, oracle);
			var cases = new ArrayList<String>();
			for (var month : List.of(originalMonth, originalMonth.minusMonths(1), originalMonth.plusMonths(1))) {
				var scope = all.at(month, 0);
				var independent = month.equals(originalMonth) ? oracle : oracle(connection, scope, deadline);
				var current = read(connection, new PatrolQueryCompiler().compileDepartment(scope), deadline);
				requireMatches(current, independent);
				for (int top : List.of(1, 5, 10)) {
					var limited = read(connection, new PatrolQueryCompiler().compileDepartment(scope.at(month, top)),
							deadline);
					requireMatches(limited, independent.subList(0, Math.min(top, independent.size())));
				}
				if (!scope.nonEmptyNameFilter()) {
					var filtered = new DataQueryDepartmentDimension.Scope(scope.from(), scope.to(), 0,
							scope.nonNullFields(), true);
					var filteredOracle = oracle(connection, filtered, deadline);
					for (int top : List.of(0, 1, 5, 10)) {
						var actual = read(connection,
								new PatrolQueryCompiler().compileDepartment(filtered.at(month, top)), deadline);
						requireMatches(actual, top == 0 ? filteredOracle
								: filteredOracle.subList(0, Math.min(top, filteredOracle.size())));
					}
					var filteredTotal = read(connection,
							new DataQuerySqlGuard.Prepared(
									"SELECT COUNT(*) FROM dev_patrol_record WHERE "
											+ DataQueryDepartmentDimension.where(filtered),
									DataQueryDepartmentDimension.parameters(filtered), Set.of("dev_patrol_record")),
							deadline);
					if (Long.parseLong(filteredTotal.values().get(0).get(0).toString()) != filteredOracle.stream()
						.mapToLong(Expected::count)
						.sum())
						throw new DataQueryException("DEPARTMENT_REGRESSION_FAILED");
					cases.add(month + ": preserved empty-name exclusion; all/Top1/Top5/Top10/name/total match");
				}
				long expectedTotal = independent.stream().mapToLong(Expected::count).sum();
				var total = read(connection,
						new DataQuerySqlGuard.Prepared(
								"SELECT COUNT(*) FROM dev_patrol_record WHERE "
										+ DataQueryDepartmentDimension.where(scope),
								DataQueryDepartmentDimension.parameters(scope), Set.of("dev_patrol_record")),
						deadline);
				if (Long.parseLong(total.values().get(0).get(0).toString()) != expectedTotal)
					throw new DataQueryException("DEPARTMENT_REGRESSION_FAILED");
				cases.add(month + ": real all/Top1/Top5/Top10; independent identity/name/total match");
			}
			return new Trial(beforeFailed, beforeFailed, baseline.values().size(), oracle.size(), List.copyOf(cases));
		});
	}

	private static DataQuerySqlExecutor.Rows read(Connection c, DataQuerySqlGuard.Prepared sql, long deadline)
			throws SQLException {
		long remaining = deadline - System.nanoTime();
		if (remaining <= 0)
			throw new DataQueryException("QUERY_TIMEOUT");
		var rows = DataQuerySqlExecutor.read(c, sql,
				(int) Math.max(1, java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(remaining)));
		if (System.nanoTime() > deadline)
			throw new DataQueryException("QUERY_TIMEOUT");
		return rows;
	}

	private static DataQuerySqlExecutor.Rows baseline(Connection c, DataQueryDepartmentDimension.Captured original,
			DataQueryDepartmentDimension.Scope scope, long deadline) throws SQLException {
		var keys = original.groupKeys();
		String select = keys.stream()
			.map(k -> "MIN(" + k.field() + ") AS " + k.field())
			.collect(java.util.stream.Collectors.joining(","));
		String group = keys.stream()
			.map(k -> (k.binary() ? "BINARY " : "") + k.field())
			.collect(java.util.stream.Collectors.joining(","));
		var p = new DataQuerySqlGuard.Prepared(
				"SELECT " + select + ",COUNT(*) AS record_count FROM dev_patrol_record WHERE "
						+ DataQueryDepartmentDimension.where(scope) + " GROUP BY " + group
						+ " ORDER BY record_count DESC",
				DataQueryDepartmentDimension.parameters(scope), Set.of("dev_patrol_record"));
		var captured = new DataQueryDepartmentDimension.Captured(scope, keys, keys.size() + 1);
		return read(c, DataQueryDepartmentDimension.instrument(p, captured), deadline);
	}

	/**
	 * Independent query groups by raw names; Java merges only the actual binary
	 * company/department identity.
	 */
	private static List<Expected> oracle(Connection c, DataQueryDepartmentDimension.Scope scope, long deadline)
			throws SQLException {
		var p = new DataQuerySqlGuard.Prepared(
				"SELECT CONVERT(MIN(BINARY company_id) USING utf8mb4), CONVERT(MIN(BINARY patrol_dept_id) USING utf8mb4), CONVERT(MIN(BINARY patrol_dept_name) USING utf8mb4), COUNT(*) FROM dev_patrol_record WHERE "
						+ DataQueryDepartmentDimension.where(scope)
						+ " GROUP BY BINARY company_id,BINARY patrol_dept_id,BINARY patrol_dept_name",
				DataQueryDepartmentDimension.parameters(scope), Set.of("dev_patrol_record"));
		return mergeNames(read(c, p, deadline));
	}

	static List<Expected> mergeNames(DataQuerySqlExecutor.Rows raw) {
		var merged = new HashMap<Pair, Expected>();
		for (var row : raw.values()) {
			var key = new Pair(Objects.toString(row.get(0), null), Objects.toString(row.get(1), null));
			long n = Long.parseLong(row.get(3).toString());
			String name = Objects.toString(row.get(2), null),
					trimmed = name == null ? "" : name.replaceAll("^ +| +$", "");
			boolean missing = trimmed.isEmpty()
					|| Set.of("null", "none", "undefined").contains(trimmed.toLowerCase(Locale.ROOT));
			var before = merged.getOrDefault(key, new Expected(key, 0, new HashSet<>(), 0));
			var names = new HashSet<>(before.names());
			if (!missing)
				names.add(name);
			merged.put(key,
					new Expected(key, before.count() + n, Set.copyOf(names), before.missing() + (missing ? n : 0)));
		}
		var result = new ArrayList<>(merged.values());
		result.sort(Comparator.comparingLong(Expected::count)
			.reversed()
			.thenComparing(x -> x.key().company(), DataQueryDepartmentVerifier::binaryCompare)
			.thenComparing(x -> x.key().department(), DataQueryDepartmentVerifier::binaryCompare));
		return List.copyOf(result);
	}

	private static int binaryCompare(String a, String b) {
		if (a == null)
			return b == null ? 0 : -1;
		if (b == null)
			return 1;
		return Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
	}

	static boolean baselineMatches(DataQuerySqlExecutor.Rows rows, int width, List<Expected> expected) {
		var actual = new HashMap<Pair, Long>();
		for (var row : rows.values()) {
			if (!"1".equals(row.get(width + 1).toString()) || !"1".equals(row.get(width + 3).toString()))
				return false;
			var key = new Pair(Objects.toString(row.get(width), null), Objects.toString(row.get(width + 2), null));
			if (actual.put(key, Long.parseLong(row.get(width + 4).toString())) != null)
				return false;
		}
		return actual.size() == expected.size()
				&& expected.stream().allMatch(e -> Objects.equals(actual.get(e.key()), e.count()));
	}

	static void requireMatches(DataQuerySqlExecutor.Rows rows, List<Expected> expected) {
		if (rows.values().size() != expected.size())
			throw new DataQueryException("DEPARTMENT_REGRESSION_FAILED");
		for (int i = 0; i < expected.size(); i++) {
			var row = rows.values().get(i);
			var e = expected.get(i);
			String name = e.names().size() == 1 ? e.names().iterator().next() : null;
			if (!Objects.equals(row.get(0), e.key().company()) || !Objects.equals(row.get(1), e.key().department())
					|| Long.parseLong(row.get(2).toString()) != e.count()
					|| Long.parseLong(row.get(3).toString()) != e.names().size()
					|| Long.parseLong(row.get(4).toString()) != e.missing() || !Objects.equals(row.get(5), name))
				throw new DataQueryException("DEPARTMENT_REGRESSION_FAILED");
		}
	}

}
