package com.deepdimen.matrix.knowledge.support.dataquery;

import java.sql.SQLException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DataQuerySqlDiagnosticsTest {

	@Test
	void mysqlServerDeadlineIsTimeoutWithoutLeakingDriverTextOrTreatingOtherCancellationAsTimeout() {
		var root = new SQLException("private SQL must_not_leak", "HY000", 3024);
		for (SQLException error : List.of(root, new com.alibaba.druid.pool.DataSourceNotAvailableException(root))) {
			var result = DataQuerySqlExecutor.failure(error);
			assertThat(result.getMessage()).isEqualTo("QUERY_TIMEOUT");
			assertThat(result.databaseDiagnostic()).containsEntry("sqlState", "HY000")
				.containsEntry("vendorCode", 3024);
			assertThat(result.toString() + result.databaseDiagnostic()).doesNotContain("must_not_leak");
			assertThat(result.getCause()).isNull();
		}
		assertThat(DataQuerySqlExecutor.failure(new SQLException("timeout in text only", "HY000", 1317)).getMessage())
			.isEqualTo("QUERY_FAILED");
		assertThat(DataQuerySqlExecutor.failure(new SQLException("timeout", "42000", 3024)).getMessage())
			.isEqualTo("QUERY_FAILED");
	}

	@Test
	void recordsSafeDatabaseCodesWithoutDriverMessageOrCause() {
		var failure = DataQuerySqlExecutor
			.failure(new SQLException("jdbc:secret host=private password=DO_NOT_PERSIST", "42000", 1235));
		assertThat(failure.getMessage()).isEqualTo("QUERY_FAILED");
		assertThat(failure.databaseDiagnostic()).containsEntry("sqlState", "42000").containsEntry("vendorCode", 1235);
		assertThat(failure.toString() + failure.databaseDiagnostic()).doesNotContain("DO_NOT_PERSIST", "private");
		assertThat(failure.getCause()).isNull();
	}

	@Test
	void druidWrappedResourceErrorsKeepTheRealCodeWithoutExposingDriverMessages() {
		for (int vendor : List.of(1040, 1203, 1226)) {
			var root = new SQLException("SYNTHETIC private-user password=DO_NOT_PERSIST", "42000", vendor);
			var wrapped = new com.alibaba.druid.pool.DataSourceNotAvailableException(root);
			assertThat(wrapped.getSQLState()).isNull();
			assertThat(wrapped.getErrorCode()).isZero();
			var failure = DataQuerySqlExecutor.failure(wrapped);
			assertThat(failure.getMessage()).isEqualTo("DATASOURCE_RESOURCE_LIMIT");
			assertThat(failure.databaseDiagnostic()).containsEntry("sqlState", "42000")
				.containsEntry("vendorCode", vendor);
			assertThat(failure.getCause()).isNull();
			assertThat(failure.toString() + failure.databaseDiagnostic()).doesNotContain("private-user",
					"DO_NOT_PERSIST");
			var response = DataQueryResponses.failure(failure);
			assertThat(response.getStatusCode().value()).isEqualTo(503);
			assertThat(response.getBody().get("message").toString()).contains("配额").doesNotContain("DO_NOT_PERSIST");
		}
	}

	@Test
	void resourceClassificationDoesNotTrustDriverTextAndBoundsCyclicCauses() {
		assertThat(DataQuerySqlExecutor.failure(new SQLException("max_user_connections current value 5", "42000", 1235))
			.getMessage()).isEqualTo("QUERY_FAILED");
		var column = new SQLException("SYNTHETIC_COLUMN", "42S22", 1054);
		assertThat(DataQuerySqlExecutor.failure(column).getMessage()).isEqualTo("SQL_COLUMN_REFERENCE_INVALID");
		// A connect-time wrapper is not evidence that the user's query has a bad column.
		var wrappedColumn = DataQuerySqlExecutor
			.failure(new com.alibaba.druid.pool.DataSourceNotAvailableException(column));
		assertThat(wrappedColumn.getMessage()).isEqualTo("QUERY_FAILED");
		assertThat(wrappedColumn.databaseDiagnostic()).containsEntry("vendorCode", 1054);
		var a = new SQLException("SYNTHETIC_A");
		var b = new SQLException("SYNTHETIC_B");
		a.initCause(b);
		b.initCause(a);
		org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () -> {
			var error = DataQuerySqlExecutor.failure(a);
			assertThat(error.getMessage()).isEqualTo("QUERY_FAILED");
			assertThat(error.getCause()).isNull();
		});
	}

	@Test
	void keepsActualPredicateColumnsAndCalendarButNotFreeText() {
		var p = new DataQuerySqlGuard().prepare(
				"SELECT record_name FROM dev_patrol_record WHERE start_date >= '2026-08-01' AND start_date < '2026-09-01' AND company_id='DO_NOT_PERSIST' AND patrol_dept_id='4733' ORDER BY start_date,id LIMIT 5",
				Set.of("dev_patrol_record"));
		var facts = DataQuerySqlDiagnostics.describe(p).toString();
		assertThat(facts).contains("company_id", "patrol_dept_id", "2026-08-01", "2026-09-01")
			.doesNotContain("DO_NOT_PERSIST", "4733", "SELECT record_name");
	}

	@Test
	void tracksInSubqueriesAndKeepsResultLimitsActionable() {
		var p = new DataQuerySqlGuard().prepare(
				"SELECT id FROM dev_patrol_record WHERE patrol_dept_id IN (SELECT patrol_dept_id FROM dev_patrol_record GROUP BY patrol_dept_id LIMIT 1)",
				Set.of("dev_patrol_record"));
		assertThat(DataQuerySqlDiagnostics.describe(p).toString()).contains("IN_SUBQUERY", "groupBy");
		assertThat(DataQueryResponses.failure(new DataQueryException("RESULT_LIMIT_EXCEEDED"))
			.getBody()
			.get("message")
			.toString()).contains("上限", "分页");
	}

}
