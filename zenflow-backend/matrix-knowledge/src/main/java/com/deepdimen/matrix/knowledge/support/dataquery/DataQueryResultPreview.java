package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLLimit;
import com.alibaba.druid.sql.ast.SQLSetQuantifier;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLAggregateExpr;
import com.alibaba.druid.sql.ast.expr.SQLIntegerExpr;
import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import java.util.ArrayList;
import java.util.Map;

/** Limit final output only: grouped counts still use every fact in the original scope. */
final class DataQueryResultPreview {

	static final int LIMIT = 10;
	static final int MAX_LIMIT = 500;

	record Plan(DataQuerySqlGuard.Prepared query, boolean active, boolean orderSpecified, int limit, String kind,
			boolean explicitLimit) {

		Map<String, Object> evidence(DataQuerySqlExecutor.Rows rows) {
			return active ? Map.of("version", "result-preview-v2", "limit", limit, "hasMore",
					rows.values().size() > limit, "totalKnown", false, "orderSpecified", orderSpecified, "kind", kind,
					"explicitLimit", explicitLimit, "aggregateInputLimited", false) : Map.of();
		}

		DataQuerySqlExecutor.Rows display(DataQuerySqlExecutor.Rows rows) {
			return active && rows.values().size() > limit
					? new DataQuerySqlExecutor.Rows(rows.columns(), rows.values().subList(0, limit)) : rows;
		}
	}

	private record Shape(SQLSelectStatement statement, MySqlSelectQueryBlock block, boolean aggregate) {
	}

	private static Shape shape(DataQuerySqlGuard.Prepared query) {
		var statements = SQLUtils.parseStatements(query.sql(), DbType.mysql);
		if (statements.size() != 1 || !(statements.get(0) instanceof SQLSelectStatement statement)
				|| statement.getSelect().getWithSubQuery() != null
				|| !(statement.getSelect().getQuery() instanceof MySqlSelectQueryBlock block)
				|| (block.getDistionOption() != 0 && block.getDistionOption() != SQLSetQuantifier.ALL)
				|| (block.getWindows() != null && !block.getWindows().isEmpty()))
			return null;
		int[] blocks = { 0 };
		boolean[] aggregate = { false };
		statement.accept(new MySqlASTVisitorAdapter() {
			@Override
			public boolean visit(MySqlSelectQueryBlock nested) {
				blocks[0]++;
				return true;
			}

			@Override
			public boolean visit(SQLAggregateExpr expression) {
				if (expression.getOver() != null)
					blocks[0] += 2;
				aggregate[0] = true;
				return true;
			}
		});
		return blocks[0] == 1 ? new Shape(statement, block, aggregate[0]) : null;
	}

	static boolean simpleAggregation(DataQuerySqlGuard.Prepared query) {
		var shape = shape(query);
		return shape != null && shape.aggregate() && shape.block().getFrom() instanceof SQLExprTableSource;
	}

	static Plan prepare(DataQuerySqlGuard.Prepared query) {
		var shape = shape(query);
		if (shape == null || (shape.aggregate() && shape.block().getGroupBy() == null))
			return new Plan(query, false, false, LIMIT, "OTHER", false);
		var block = shape.block();
		boolean explicit = block.getLimit() != null;
		var parameters = new ArrayList<>(query.parameters());
		int limit = LIMIT;
		if (explicit) {
			SQLExpr count = block.getLimit().getRowCount();
			Object value = count instanceof SQLVariantRefExpr variant ? parameters.get(variant.getIndex())
					: count instanceof SQLIntegerExpr integer ? integer.getNumber() : null;
			long requested;
			try {
				requested = Long.parseLong(String.valueOf(value));
			}
			catch (NumberFormatException unsupported) {
				return new Plan(query, false, false, LIMIT, "OTHER", true);
			}
			if (requested < 1)
				return new Plan(query, false, false, LIMIT, "OTHER", true);
			limit = (int) Math.min(MAX_LIMIT, requested);
			if (count instanceof SQLVariantRefExpr variant)
				parameters.set(variant.getIndex(), limit + 1);
			else
				block.getLimit().setRowCount(new SQLIntegerExpr(limit + 1));
		}
		else
			block.setLimit(new SQLLimit(limit + 1));
		var bounded = new DataQuerySqlGuard.Prepared(SQLUtils.toSQLString(shape.statement(), DbType.mysql), parameters,
				query.tables());
		return new Plan(bounded, true, block.getOrderBy() != null, limit,
				block.getGroupBy() == null ? "DETAIL" : "GROUPS", explicit);
	}

	static String notice(Map<String, Object> evidence) {
		if (!Boolean.TRUE.equals(evidence.get("hasMore")))
			return "";
		Object limit = evidence.getOrDefault("limit", LIMIT);
		if ("GROUPS".equals(evidence.get("kind")))
			return "各组数值按原筛选范围完整计算，先展示" + limit + "个分组，还有更多分组；没有先截取部分明细再统计。"
					+ (Boolean.TRUE.equals(evidence.get("orderSpecified")) ? "沿用本次查询的排序。" : "未指定排序，不代表全量排名。")
					+ "如需更多，可增加展示条数。";
		String ordering = Boolean.TRUE.equals(evidence.get("orderSpecified")) ? "沿用本次查询的排序。"
				: "本次未指定排序，这" + limit + "条不代表最低、最新或排名。";
		return "先展示" + limit + "条，还有更多匹配记录；这不是完整清单，也未统计全部匹配条数。" + ordering + "如需更多，可指定条数或排序；系统不会自行增加日期或业务筛选。";
	}

	private DataQueryResultPreview() {
	}

}
