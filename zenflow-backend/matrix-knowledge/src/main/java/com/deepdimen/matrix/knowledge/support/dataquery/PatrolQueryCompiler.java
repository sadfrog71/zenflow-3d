package com.deepdimen.matrix.knowledge.support.dataquery;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * First-slice compiler. Not a Spring bean or an execution/authorization API. Dataset
 * access must be checked by the trusted service before compilation; source
 * company/department columns are grouping dimensions, not MATRIX authorization. The
 * research definition requires business approval before production wiring.
 */
public final class PatrolQueryCompiler {

	public record Candidate(String dataset, String metric, String dimension, LocalDate from, LocalDate to,
			String timezone, String order) {
	}

	public record CompiledQuery(String sql, List<Object> parameters) {
		public CompiledQuery {
			parameters = List.copyOf(parameters);
		}
	}

	public CompiledQuery compile(Candidate candidate) {
		if (candidate == null || !"top.patrol".equals(candidate.dataset())
				|| !"valid_patrol_count".equals(candidate.metric()) || !"department".equals(candidate.dimension())
				|| !"Asia/Shanghai".equals(candidate.timezone()) || !"DESC".equals(candidate.order())) {
			throw new IllegalArgumentException("UNSUPPORTED_PLAN");
		}
		if (candidate.from() == null || candidate.to() == null || !candidate.from().isBefore(candidate.to())
				|| ChronoUnit.DAYS.between(candidate.from(), candidate.to()) > 366) {
			throw new IllegalArgumentException("INVALID_TIME_RANGE");
		}
		List<Object> parameters = new ArrayList<>();
		parameters.add(candidate.from().atStartOfDay());
		parameters.add(candidate.to().atStartOfDay());
		parameters.add("0");
		// Binary comparison exposes case/spacing variants instead of allowing collation
		// to merge them.
		String validName = "CASE WHEN patrol_dept_name IS NOT NULL AND TRIM(patrol_dept_name) <> '' "
				+ "AND LOWER(TRIM(patrol_dept_name)) NOT IN ('null','none','undefined') "
				+ "THEN BINARY patrol_dept_name END";
		String variants = "COUNT(DISTINCT " + validName + ")";
		String sql = "SELECT BINARY company_id AS company_id, BINARY patrol_dept_id AS patrol_dept_id, COUNT(*) AS valid_patrol_count, "
				+ variants + " AS name_variant_count, " + "SUM(CASE WHEN (" + validName
				+ ") IS NULL THEN 1 ELSE 0 END) AS missing_name_count, " + "CASE WHEN " + variants + " = 1 THEN MIN("
				+ validName + ") ELSE NULL END AS department_name " + "FROM top_device_biz.dev_patrol_record "
				+ "WHERE start_date >= ? AND start_date < ? AND del_flag = ? "
				+ "GROUP BY BINARY company_id, BINARY patrol_dept_id "
				+ "ORDER BY valid_patrol_count DESC, company_id ASC, patrol_dept_id ASC LIMIT 1001";
		// Executor must reject >1000 groups rather than silently truncate results.
		return new CompiledQuery(sql, parameters);
	}

	/**
	 * The same record-count definition, activated only by a published bounded correction.
	 */
	DataQuerySqlGuard.Prepared compileDepartment(DataQueryDepartmentDimension.Scope scope) {
		String name = "CASE WHEN patrol_dept_name IS NOT NULL AND TRIM(patrol_dept_name) <> '' "
				+ "AND LOWER(TRIM(patrol_dept_name)) NOT IN ('null','none','undefined') THEN BINARY patrol_dept_name END";
		String variants = "COUNT(DISTINCT " + name + ")";
		String sql = "SELECT CONVERT(MIN(BINARY company_id) USING utf8mb4) AS company_id, "
				+ "CONVERT(MIN(BINARY patrol_dept_id) USING utf8mb4) AS patrol_dept_id, COUNT(*) AS record_count, "
				+ variants + " AS name_variant_count, SUM(CASE WHEN (" + name
				+ ") IS NULL THEN 1 ELSE 0 END) AS missing_name_count, " + "CASE WHEN " + variants
				+ "=1 THEN CONVERT(MIN(" + name + ") USING utf8mb4) ELSE NULL END AS department_name "
				+ "FROM dev_patrol_record WHERE " + DataQueryDepartmentDimension.where(scope)
				+ " GROUP BY BINARY company_id, BINARY patrol_dept_id ORDER BY record_count DESC, MIN(BINARY company_id) ASC, MIN(BINARY patrol_dept_id) ASC"
				+ (scope.topN() > 0 ? " LIMIT " + scope.topN() : "");
		return new DataQuerySqlGuard.Prepared(sql, DataQueryDepartmentDimension.parameters(scope),
				java.util.Set.of("dev_patrol_record"));
	}

}
