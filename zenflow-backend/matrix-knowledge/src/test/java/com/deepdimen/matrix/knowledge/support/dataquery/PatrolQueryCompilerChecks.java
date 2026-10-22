package com.deepdimen.matrix.knowledge.support.dataquery;

import java.time.LocalDate;
import java.util.List;

/**
 * Dependency-free compiler checks; run with Java 17 or later. No database or application
 * boot.
 */
public final class PatrolQueryCompilerChecks {

	private static int checks;

	public static void main(String[] args) {
		var compiler = new PatrolQueryCompiler();
		var plan = new PatrolQueryCompiler.Candidate("top.patrol", "valid_patrol_count", "department",
				LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), "Asia/Shanghai", "DESC");
		var query = compiler.compile(plan);
		check(query.parameters().size() == 3, "only time and deletion are bound; no source permission mapping");
		check(query.sql().chars().filter(c -> c == '?').count() == 3, "placeholder count");
		check(query.sql().contains("GROUP BY BINARY company_id, BINARY patrol_dept_id"), "compound grouping");
		check(query.sql().contains("start_date >= ? AND start_date < ?"), "half-open interval");
		check(!query.sql().contains("JOIN"), "no uncontrolled join");
		check(query.sql().contains("ELSE NULL END AS department_name"), "no guessed conflict name");
		check(query.sql().contains("LIMIT 1001"), "overflow sentinel");
		check(!query.sql().contains("company_id =") && !query.sql().contains("patrol_dept_id ="),
				"business group keys are not authorization predicates");
		reject(() -> compiler.compile(null), "UNSUPPORTED_PLAN");
		reject(() -> compiler.compile(new PatrolQueryCompiler.Candidate("top.patrol", "completed", "department",
				plan.from(), plan.to(), "Asia/Shanghai", "DESC")), "UNSUPPORTED_PLAN");
		reject(() -> compiler.compile(new PatrolQueryCompiler.Candidate("top.patrol", "valid_patrol_count",
				"department", plan.to(), plan.from(), "Asia/Shanghai", "DESC")), "INVALID_TIME_RANGE");
		try {
			query.parameters().add("tamper");
			throw new AssertionError("mutable parameters");
		}
		catch (UnsupportedOperationException expected) {
			checks++;
		}
		System.out.println("PASS: " + checks + " compiler checks; no database execution");
	}

	private static void check(boolean condition, String label) {
		if (!condition) {
			throw new AssertionError(label);
		}
		checks++;
	}

	private static void reject(Runnable action, String code) {
		try {
			action.run();
			throw new AssertionError("expected " + code);
		}
		catch (IllegalArgumentException expected) {
			check(code.equals(expected.getMessage()), "stable rejection code");
		}
	}

}
