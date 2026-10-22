package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class DataQueryResultPreviewTest {

	private final DataQuerySqlGuard guard = new DataQuerySqlGuard();

	private final Set<String> tables = Set.of("dev_device", "dev_patrol_record");

	@Test
	void keepsBindingsFiltersJoinsOrderingAndOnlyAddsAResultProbe() {
		var prepared = guard.prepare("SELECT r.id,ROUND(r.coverage,2) AS coverage FROM dev_patrol_record r "
				+ "LEFT JOIN dev_device d ON r.company_id=d.company_id WHERE r.coverage<80 "
				+ "AND r.company_id='approved-company' ORDER BY r.coverage ASC,r.id ASC", tables);
		var plan = DataQueryResultPreview.prepare(prepared);
		assertThat(plan.active()).isTrue();
		assertThat(plan.orderSpecified()).isTrue();
		assertThat(plan.query().parameters()).isEqualTo(prepared.parameters());
		assertThat(plan.query().tables()).isEqualTo(prepared.tables());
		assertThat(plan.query().sql()).contains("LIMIT 11").doesNotContain("approved-company");
		var statement = (SQLSelectStatement) SQLUtils.parseSingleStatement(plan.query().sql(), DbType.mysql);
		((MySqlSelectQueryBlock) statement.getSelect().getQuery()).setLimit(null);
		assertThat(SQLUtils.toSQLString(statement, DbType.mysql)).isEqualTo(prepared.sql());
		assertThat(prepared.sql()).doesNotContain("LIMIT");
	}

	@Test
	void neverLimitsScalarAggregateFactsOrComplexQueryShapes() {
		for (String sql : List.of("SELECT COUNT(*) FROM dev_device", "SELECT SUM(coverage) FROM dev_patrol_record",
				"SELECT SUM(coverage)/COUNT(*) FROM dev_patrol_record", "SELECT DISTINCT company_id FROM dev_device",
				"SELECT DISTINCTROW company_id FROM dev_device",
				"SELECT company_id,ROW_NUMBER() OVER (ORDER BY company_id) FROM dev_device",
				"SELECT company_id FROM dev_device UNION ALL SELECT company_id FROM dev_patrol_record",
				"WITH records AS (SELECT company_id FROM dev_device) SELECT company_id FROM records",
				"SELECT company_id FROM (SELECT company_id FROM dev_device) records",
				"SELECT company_id FROM dev_device WHERE EXISTS (SELECT 1 FROM dev_patrol_record)")) {
			var query = guard.prepare(sql, tables);
			var plan = DataQueryResultPreview.prepare(query);
			assertThat(plan.active()).as(sql).isFalse();
			assertThat(plan.query()).isSameAs(query);
		}
	}

	@Test
	void onlyTheExtraRowProvesMoreResultsAndIsNeverShown() {
		var plan = DataQueryResultPreview.prepare(guard.prepare("SELECT company_id FROM dev_device", tables));
		for (int size : List.of(0, 2, 10, 11)) {
			var values = IntStream.range(0, size).mapToObj(i -> List.<Object>of("record-" + i)).toList();
			var rows = new DataQuerySqlExecutor.Rows(List.of("company_id"), values);
			var evidence = plan.evidence(rows);
			assertThat(evidence).containsEntry("hasMore", size > 10)
				.containsEntry("totalKnown", false)
				.containsEntry("orderSpecified", false);
			assertThat(plan.display(rows).values()).containsExactlyElementsOf(values.subList(0, Math.min(10, size)));
			assertThat(plan.display(rows).columns()).isEqualTo(rows.columns());
			if (size > 10)
				assertThat(DataQueryResultPreview.notice(evidence)).contains("先展示10条", "还有更多", "不是完整清单", "不代表最低");
			else
				assertThat(DataQueryResultPreview.notice(evidence)).isEmpty();
		}
	}

	@Test
	void probesExplicitLimitsAndPreservesTheRequestedPageAndFilterParameters() {
		var query = guard.prepare(
				"SELECT company_id FROM dev_device WHERE company_id!='X' ORDER BY company_id LIMIT 20 OFFSET 10",
				tables);
		var plan = DataQueryResultPreview.prepare(query);
		assertThat(plan.active()).isTrue();
		assertThat(plan.limit()).isEqualTo(20);
		assertThat(plan.query().parameters()).contains("X", 10, 21).doesNotContain(20);
		var rows = new DataQuerySqlExecutor.Rows(List.of("company_id"),
				IntStream.range(0, 21).mapToObj(i -> List.<Object>of(i)).toList());
		assertThat(plan.display(rows).values()).hasSize(20);
		assertThat(plan.evidence(rows)).containsEntry("hasMore", true).containsEntry("explicitLimit", true);
		assertThat(DataQueryResultPreview.notice(plan.evidence(rows))).contains("先展示20条");
	}

	@Test
	void groupedPreviewLimitsOnlyTheFinalGroupsNeverTheInputFacts() {
		var query = guard.prepare(
				"SELECT company_id,COUNT(*) AS n FROM dev_device WHERE company_id!='X' GROUP BY company_id ORDER BY n DESC",
				tables);
		var plan = DataQueryResultPreview.prepare(query);
		assertThat(plan.active()).isTrue();
		assertThat(plan.kind()).isEqualTo("GROUPS");
		assertThat(plan.query().sql()).endsWith("LIMIT 11");
		assertThat(plan.query().parameters()).isEqualTo(query.parameters());
		assertThat(DataQueryResultPreview.simpleAggregation(query)).isTrue();
		var rows = new DataQuerySqlExecutor.Rows(List.of("company_id", "n"),
				IntStream.range(0, 11).mapToObj(i -> List.<Object>of("company-" + i, 10000 - i)).toList());
		assertThat(plan.display(rows).values()).hasSize(10);
		assertThat(DataQueryResultPreview.notice(plan.evidence(rows))).contains("原筛选范围完整计算", "10个分组", "没有先截取部分明细");
	}

	@Test
	void largeExplicitOutputIsBoundedWithoutClaimingTheRemainingRowsWereCounted() {
		var plan = DataQueryResultPreview
			.prepare(guard.prepare("SELECT company_id FROM dev_device LIMIT 10000", tables));
		assertThat(plan.limit()).isEqualTo(500);
		assertThat(plan.query().parameters()).containsExactly(501);
		assertThat(DataQueryResultPreview.simpleAggregation(guard.prepare(
				"SELECT d.company_id,COUNT(*) FROM dev_device d JOIN dev_patrol_record p ON p.company_id=d.company_id GROUP BY d.company_id",
				tables)))
			.isFalse();
	}

}
