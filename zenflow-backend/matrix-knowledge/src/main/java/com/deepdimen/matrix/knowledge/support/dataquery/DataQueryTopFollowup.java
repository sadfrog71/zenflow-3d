package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.*;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import java.util.*;
import java.util.regex.Pattern;

/** Narrow presentation follow-ups reuse an owned plan, never a model-invented WHERE. */
final class DataQueryTopFollowup {

	private static final Pattern TOP = Pattern
		.compile("^(.*?)(?:(最多|最高|最少|最低)的?)?(?:前|top)([0-9]{1,6}|[一二三四五六七八九十百两]{1,4})(?:个|条|名|行)?(?:(部门|设备|记录|结果))?$");

	record Request(int limit, String prefix, String direction, boolean namedEntity) {
		boolean shortRequest() {
			return prefix.matches("(?:那|再|改为|改成|换成|换为|只|仅|就|看|要|显示|展示|列出|一下|给我)*")
					&& (!namedEntity || prefix.matches(".*(?:只看|只要|仅看|仅要|改为|改成|换成|换为|再看).*"));
		}

		boolean matches(String previous) {
			var prior = parse(previous);
			return shortRequest() || base(prefix).equals(base(prior == null ? normalize(previous) : prior.prefix()));
		}
	}

	record Source(String queryId, DataQuerySqlGuard.Prepared query, Request request) {
		@Override
		public String toString() {
			return "TopFollowup[REDACTED]";
		}

		Map<String, Object> evidence() {
			return Map.of("sourceQueryId", queryId, "kind", "TOP_N", "limit", request.limit(), "scopePreserved", true);
		}
	}

	static Request parse(String question) {
		var match = TOP.matcher(normalize(question));
		if (!match.matches())
			return null;
		int n = number(match.group(3));
		if (n < 1 || n > 100000)
			return null;
		String order = match.group(2);
		return new Request(n, match.group(1),
				order == null ? "KEEP" : Set.of("最少", "最低").contains(order) ? "ASC" : "DESC", match.group(4) != null);
	}

	private static String normalize(String question) {
		return question == null ? "" : question.toLowerCase(Locale.ROOT).replaceAll("[\\s，。？！?!,；;：:]", "");
	}

	private static String base(String text) {
		return text.replaceAll("(?:分别是多少|分别有多少|是多少|有多少|分别|多少)$", "");
	}

	private static int number(String text) {
		try {
			return Integer.parseInt(text);
		}
		catch (NumberFormatException ignored) {
		}
		int total = 0, digit = 0;
		for (char c : text.toCharArray()) {
			int n = "一二三四五六七八九".indexOf(c) + 1;
			if (c == '两')
				n = 2;
			if (n > 0) {
				digit = n;
				continue;
			}
			if (c != '十' && c != '百')
				return -1;
			total += (digit == 0 ? 1 : digit) * (c == '十' ? 10 : 100);
			digit = 0;
		}
		return total + digit;
	}

	static DataQuerySqlGuard.Prepared apply(Source source) {
		var original = source.query();
		var parsed = SQLUtils.parseSingleStatement(original.sql(), DbType.mysql);
		if (!(parsed instanceof SQLSelectStatement statement) || statement.getSelect().getWithSubQuery() != null
				|| !(statement.getSelect().getQuery() instanceof MySqlSelectQueryBlock block))
			throw unavailable();
		// Do not reinterpret offsets or ranking expressions from arbitrary nested SQL.
		int[] blocks = { 0 };
		statement.accept(new MySqlASTVisitorAdapter() {
			@Override
			public boolean visit(MySqlSelectQueryBlock b) {
				blocks[0]++;
				return true;
			}
		});
		if (blocks[0] != 1 || (block.getWindows() != null && !block.getWindows().isEmpty()))
			throw unavailable();
		var parameters = new ArrayList<>(original.parameters());
		var limit = block.getLimit();
		if (limit == null)
			block.setLimit(new SQLLimit(source.request().limit()));
		else {
			if (limit.getRowCount() instanceof SQLVariantRefExpr v)
				parameters.set(v.getIndex(), source.request().limit());
			else if (limit.getRowCount() instanceof SQLIntegerExpr)
				limit.setRowCount(new SQLIntegerExpr(source.request().limit()));
			else
				throw unavailable();
			if (limit.getOffset() instanceof SQLVariantRefExpr v)
				parameters.set(v.getIndex(), 0);
			else if (limit.getOffset() != null)
				limit.setOffset(new SQLIntegerExpr(0));
		}
		if (!"KEEP".equals(source.request().direction())) {
			// Most/least is only deterministic when exactly one aggregate measure is
			// selected.
			var measures = block.getSelectList()
				.stream()
				.filter(item -> item.getExpr() instanceof SQLAggregateExpr)
				.toList();
			if (measures.size() != 1)
				throw unavailable();
			var item = measures.get(0);
			SQLExpr expr = item.getAlias() != null ? new SQLIdentifierExpr(item.getAlias()) : item.getExpr().clone();
			if (hasBinding(expr) || (block.getOrderBy() != null && hasBinding(block.getOrderBy())))
				throw unavailable();
			var order = new SQLOrderBy();
			order.addItem(expr, SQLOrderingSpecification.valueOf(source.request().direction()));
			// Original secondary sorting remains as a tie-breaker; it cannot add filters.
			if (block.getOrderBy() != null)
				for (var previous : block.getOrderBy().getItems())
					order.addItem(previous.clone());
			block.setOrderBy(order);
		}
		return new DataQuerySqlGuard.Prepared(SQLUtils.toSQLString(statement, DbType.mysql), parameters,
				original.tables());
	}

	private static boolean hasBinding(SQLObject object) {
		boolean[] found = { false };
		object.accept(new MySqlASTVisitorAdapter() {
			@Override
			public boolean visit(SQLVariantRefExpr v) {
				found[0] = true;
				return false;
			}
		});
		return found[0];
	}

	private static DataQueryException unavailable() {
		return new DataQueryException("FOLLOWUP_CONTEXT_UNAVAILABLE");
	}

	private DataQueryTopFollowup() {
	}

}
