package com.deepdimen.matrix.knowledge.support.dataquery;

import java.time.LocalDate;
import java.util.List;

/**
 * Prints a read-only prepared-statement smoke test with synthetic inline rows only. Never
 * connects anywhere. Caller chooses an already authorized read-only MySQL session.
 * Expected principal groups (other business-key variants and null IDs also remain):
 * company-X/department-X => count 4, variants 2, missing 2, name NULL;
 * company-Y/department-Y => count 1, variants 1, missing 0, name Gamma.
 */
public final class DataQueryMySqlFixture {

	public static void main(String[] args) {
		var candidate = new PatrolQueryCompiler.Candidate("top.patrol", "valid_patrol_count", "department",
				LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), "Asia/Shanghai", "DESC");
		var query = new PatrolQueryCompiler().compile(candidate);
		String fixture = """
				SELECT CAST('2026-08-01 00:00:00' AS DATETIME) AS start_date,
				_utf8mb4'0' COLLATE utf8mb4_general_ci AS del_flag,
				_utf8mb4'company-X' COLLATE utf8mb4_general_ci AS company_id,
				_utf8mb4'department-X' COLLATE utf8mb4_general_ci AS patrol_dept_id,
				_utf8mb4'Alpha' COLLATE utf8mb4_general_ci AS patrol_dept_name
				UNION ALL SELECT '2026-08-02','0','company-X','department-X','Beta'
				UNION ALL SELECT '2026-08-03','0','company-Y','department-Y','Gamma'
				UNION ALL SELECT '2026-08-04','0','company-X','department-Y','Cross pair remains a distinct business group'
				UNION ALL SELECT '2026-08-05','0','company-X','DEPARTMENT-X','Different case remains a distinct business group'
				UNION ALL SELECT '2026-08-06','0','COMPANY-X','department-X','Different company case remains a distinct business group'
				UNION ALL SELECT '2026-07-31','0','company-X','department-X','Previous month'
				UNION ALL SELECT '2026-09-01','0','company-X','department-X','Next month boundary'
				UNION ALL SELECT '2026-08-07','1','company-X','department-X','Deleted'
				UNION ALL SELECT '2026-08-08','0','company-X',NULL,'Missing department'
				UNION ALL SELECT '2026-08-09','0','company-X','department-X',NULL
				UNION ALL SELECT '2026-08-10','0','company-X','department-X','null'
				UNION ALL SELECT '2026-08-11','0','company-X','department-X ','Trailing space remains a distinct business group'
				""";
		if (query.sql().contains("JOIN") || query.sql().split("FROM", -1).length != 2) {
			throw new AssertionError("Fixture requires the single-table compiler");
		}
		String sql = query.sql()
			.replace("FROM top_device_biz.dev_patrol_record", "FROM (" + fixture + ") AS synthetic_patrol");
		if (sql.contains("top_device_biz") || !sql.contains("synthetic_patrol"))
			throw new AssertionError("Unsafe fixture target");
		System.out.println("SELECT 'SYNTHETIC_ONLY_NO_BUSINESS_TABLES' AS validation_scope;");
		System.out.println("SET @dq_fixture_sql='" + sql.replace("'", "''") + "';");
		System.out.println("PREPARE dq_fixture_stmt FROM @dq_fixture_sql;");
		StringBuilder names = new StringBuilder();
		for (int i = 0; i < query.parameters().size(); i++) {
			String name = "@dq_fixture_p" + i;
			if (i > 0)
				names.append(',');
			names.append(name);
			String value = query.parameters().get(i).toString();
			System.out.println("SET " + name + "='" + value.replace("'", "''") + "';");
		}
		System.out.println("EXECUTE dq_fixture_stmt USING " + names + ";");
		System.out.println("DEALLOCATE PREPARE dq_fixture_stmt;");
	}

}
