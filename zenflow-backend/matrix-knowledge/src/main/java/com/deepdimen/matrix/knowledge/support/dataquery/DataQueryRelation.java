package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Finite parent-entity existence semantics. No arbitrary SQL substitution or inferred
 * relationships.
 */
public final class DataQueryRelation {

	public static final String KIND = "PARENT_CHILD_EXISTENCE_V1";

	public record Definition(String kind, long dataId, String parentTable, String parentKey, String previousKey,
			String childTable, String childReference, String childKey, String timeField, String conversion,
			String cardinality, String nullPolicy, String grain, String inventoryDigest,
			String bindingRevision) implements DataQueryCorrectionController.StrictRequest {
		public static Definition patrol(long dataId, String inventory, String binding) {
			return new Definition(KIND, dataId, "dev_patrol_record", "id", "record_code", "dev_patrol_record_device",
					"record_id", "id", "start_date", "CANONICAL_TEXT", "ONE_TO_MANY", "UNMATCHED", "PARENT_ENTITY",
					inventory, binding);
		}

		public void validate() {
			if (!KIND.equals(kind) || dataId <= 0 || !"CANONICAL_TEXT".equals(conversion)
					|| !"ONE_TO_MANY".equals(cardinality) || !"UNMATCHED".equals(nullPolicy)
					|| !"PARENT_ENTITY".equals(grain) || Objects.equals(parentTable, childTable)
					|| Objects.equals(parentKey, previousKey) || inventoryDigest == null || bindingRevision == null)
				throw new DataQueryException("RELATION_DEFINITION_INVALID");
			for (String name : Arrays.asList(parentTable, parentKey, previousKey, childTable, childReference, childKey,
					timeField))
				if (name == null || !name.matches("[a-zA-Z_][a-zA-Z0-9_]{0,63}"))
					throw new DataQueryException("RELATION_DEFINITION_INVALID");
		}

		String key() {
			return "relation:" + dataId + ":"
					+ DataQueryMetadataRepository.fingerprint(parentTable, childTable, childReference).substring(0, 20);
		}
	}

	public record Applied(String status, DataQuerySqlGuard.Prepared query, String originalKey, String shape) {
	}

	record Parsed(SQLSelectStatement statement, SQLSelectQueryBlock parent, SQLSelectQueryBlock child,
			SQLBinaryOpExpr edge, String parentAlias, String childAlias, boolean negative, String originalKey,
			Map<String, Object> parameters, String marker, String shape) {
	}

	static String clean(String s) {
		return s == null ? "" : s.replace("`", "");
	}

	static String table(SQLTableSource t) {
		return t instanceof SQLExprTableSource e ? clean(e.getTableName()) : "";
	}

	static String alias(SQLTableSource t) {
		return t.getAlias() == null ? table(t) : clean(t.getAlias());
	}

	static boolean column(SQLExpr e, String alias, String field) {
		return e instanceof SQLPropertyExpr p && clean(p.getOwner().toString()).equals(alias)
				&& clean(p.getName()).equals(field);
	}

	static List<SQLExpr> ands(SQLExpr e) {
		var out = new ArrayList<SQLExpr>();
		if (e instanceof SQLBinaryOpExpr b && b.getOperator() == SQLBinaryOperator.BooleanAnd) {
			out.addAll(ands(b.getLeft()));
			out.addAll(ands(b.getRight()));
		}
		else if (e != null)
			out.add(e);
		return out;
	}

	static SQLExpr and(List<SQLExpr> expressions) {
		SQLExpr out = null;
		for (var e : expressions)
			out = out == null ? e : new SQLBinaryOpExpr(out, SQLBinaryOperator.BooleanAnd, e);
		return out;
	}

	static SQLExpr relation(Definition d, String pa, String ca, String key) {
		// A byte-exact canonical decimal text identity avoids MySQL's implicit numeric
		// coercion (e.g. '01' = 1).
		return SQLUtils.toSQLExpr(
				"BINARY " + ca + "." + d.childReference() + " = BINARY CAST(" + pa + "." + key + " AS CHAR)",
				DbType.mysql);
	}

	static Applied apply(DataQuerySqlGuard.Prepared sql, Definition d) {
		d.validate();
		if (!sql.tables().containsAll(Set.of(d.parentTable(), d.childTable())))
			return new Applied("NOT_APPLICABLE", sql, null, null);
		try {
			var parsed = parse(sql, d);
			SQLUtils.replaceInParent(parsed.edge(),
					relation(d, parsed.parentAlias(), parsed.childAlias(), d.parentKey()));
			for (var item : parsed.parent().getSelectList())
				if (item.getExpr() instanceof SQLAggregateExpr count) {
					count.getArguments().clear();
					count.addArgument(new SQLPropertyExpr(new SQLIdentifierExpr(parsed.parentAlias()), d.parentKey()));
					count.setOption(SQLAggregateOption.DISTINCT);
				}
			var result = render(parsed);
			return new Applied(sql.sql().equals(result.sql()) ? "CHECKED" : "APPLIED", result, parsed.originalKey(),
					parsed.shape());
		}
		catch (DataQueryException | IllegalArgumentException e) {
			return new Applied("UNSUPPORTED_SHAPE", sql, null, null);
		}
	}

	static Parsed parse(DataQuerySqlGuard.Prepared sql, Definition d) {
		String marker = ":dqr_" + UUID.randomUUID().toString().replace("-", "") + "_";
		var params = new LinkedHashMap<String, Object>();
		var statement = (SQLSelectStatement) SQLUtils.parseSingleStatement(sql.sql(), DbType.mysql);
		statement.accept(new MySqlASTVisitorAdapter() {
			@Override
			public boolean visit(SQLVariantRefExpr e) {
				if (!"?".equals(e.getName()) || params.size() >= sql.parameters().size())
					throw new DataQueryException("RELATION_UNSUPPORTED");
				String name = marker + params.size();
				params.put(name, sql.parameters().get(params.size()));
				e.setName(name);
				return false;
			}
		});
		if (params.size() != sql.parameters().size() || statement.getSelect().getWithSubQuery() != null
				|| !(statement.getSelect().getQuery() instanceof SQLSelectQueryBlock b) || b.getGroupBy() != null
				|| b.getDistionOption() != 0)
			throw new DataQueryException("RELATION_UNSUPPORTED");
		SQLSelectQueryBlock child;
		String pa, ca, shape;
		boolean negative = false;
		SQLExistsExpr exists = null;
		if (b.getFrom() instanceof SQLExprTableSource parent && table(parent).equals(d.parentTable())) {
			pa = alias(parent);
			shape = "CORRELATED_EXISTS";
			for (var e : ands(b.getWhere()))
				if (e instanceof SQLExistsExpr x) {
					if (exists != null)
						throw new DataQueryException("RELATION_UNSUPPORTED");
					exists = x;
				}
			if (exists == null || !(exists.getSubQuery().getQuery() instanceof SQLSelectQueryBlock cb)
					|| !table(cb.getFrom()).equals(d.childTable()) || cb.getGroupBy() != null || cb.getLimit() != null
					|| exists.getSubQuery().getWithSubQuery() != null)
				throw new DataQueryException("RELATION_UNSUPPORTED");
			child = cb;
			ca = alias(child.getFrom());
			negative = exists.isNot();
		}
		else if (b.getFrom() instanceof SQLJoinTableSource join && table(join.getLeft()).equals(d.parentTable())
				&& table(join.getRight()).equals(d.childTable()) && join.getUsing().isEmpty()) {
			pa = alias(join.getLeft());
			ca = alias(join.getRight());
			shape = "JOIN_TO_EXISTS";
			boolean left = join.getJoinType() == SQLJoinTableSource.JoinType.LEFT_OUTER_JOIN;
			if (!left && join.getJoinType() != SQLJoinTableSource.JoinType.INNER_JOIN
					&& join.getJoinType() != SQLJoinTableSource.JoinType.JOIN)
				throw new DataQueryException("RELATION_UNSUPPORTED");
			var remaining = new ArrayList<SQLExpr>();
			int nullTests = 0;
			for (var e : ands(b.getWhere())) {
				if (e instanceof SQLBinaryOpExpr q && column(q.getLeft(), ca, d.childKey())
						&& q.getRight() instanceof SQLNullExpr
						&& (q.getOperator() == SQLBinaryOperator.Is || q.getOperator() == SQLBinaryOperator.IsNot)) {
					nullTests++;
					negative = q.getOperator() == SQLBinaryOperator.Is;
				}
				else
					remaining.add(e);
			}
			if (nullTests > 1 || (left && nullTests != 1) || (!left && negative))
				throw new DataQueryException("RELATION_UNSUPPORTED");
			child = new SQLSelectQueryBlock();
			child.setFrom(join.getRight());
			child.addSelectItem(new SQLIntegerExpr(1));
			child.setWhere(join.getCondition());
			exists = new SQLExistsExpr(new SQLSelect(child));
			exists.setNot(negative);
			remaining.add(exists);
			b.setFrom(join.getLeft());
			b.setWhere(and(remaining));
		}
		else
			throw new DataQueryException("RELATION_UNSUPPORTED");
		if (pa.equals(ca) || !pa.matches("[a-zA-Z_][a-zA-Z0-9_]*") || !ca.matches("[a-zA-Z_][a-zA-Z0-9_]*"))
			throw new DataQueryException("RELATION_UNSUPPORTED");
		SQLBinaryOpExpr edge = null;
		String old = null;
		for (var e : ands(child.getWhere()))
			if (e instanceof SQLBinaryOpExpr q && q.getOperator() == SQLBinaryOperator.Equality) {
				for (var key : List.of(d.previousKey(), d.parentKey())) {
					boolean direct = column(q.getLeft(), ca, d.childReference()) && column(q.getRight(), pa, key)
							|| column(q.getRight(), ca, d.childReference()) && column(q.getLeft(), pa, key);
					boolean canonical = SQLUtils.toSQLString(q, DbType.mysql)
						.equals(SQLUtils.toSQLString(relation(d, pa, ca, key), DbType.mysql));
					if (direct || canonical) {
						if (edge != null)
							throw new DataQueryException("RELATION_UNSUPPORTED");
						edge = q;
						old = key;
					}
				}
			}
		if (edge == null)
			throw new DataQueryException("RELATION_UNSUPPORTED");
		// EXISTS over an aggregate without GROUP BY returns a row even for zero children.
		// It is not this operation.
		for (var item : child.getSelectList())
			item.getExpr().accept(new MySqlASTVisitorAdapter() {
				@Override
				public boolean visit(SQLAggregateExpr a) {
					throw new DataQueryException("RELATION_UNSUPPORTED");
				}

				@Override
				public boolean visit(SQLQueryExpr q) {
					throw new DataQueryException("RELATION_UNSUPPORTED");
				}
			});

		final var selected = exists;
		final var parentAlias = pa;
		final var childAlias = ca;
		final var childBlock = child;
		final var selectedEdge = edge;
		// All surviving predicates are retained. Extra subqueries, windows and
		// unqualified/child output are unsupported.
		for (var e : ands(b.getWhere()))
			if (e != selected)
				parentOnly(e, pa);
		for (var item : b.getSelectList()) {
			var e = item.getExpr();
			if (e instanceof SQLAggregateExpr a) {
				if (a.getOver() != null || a.getOverRef() != null || a.getWithinGroup() != null || a.getFilter() != null
						|| !"count".equalsIgnoreCase(a.getMethodName()) || a.getArguments().size() != 1
						|| !(a.getArguments().get(0) instanceof SQLAllColumnExpr
								|| a.getArguments().get(0) instanceof SQLVariantRefExpr v
										&& params.get(v.getName()) != null
								|| column(a.getArguments().get(0), pa, d.parentKey())))
					throw new DataQueryException("RELATION_UNSUPPORTED");
			}
			else
				parentOnly(e, pa);
		}
		if (b.getOrderBy() != null)
			for (var item : b.getOrderBy().getItems()) {
				if (item.getExpr() instanceof SQLPropertyExpr)
					parentOnly(item.getExpr(), pa);
				else if (!(item.getExpr() instanceof SQLIdentifierExpr || item.getExpr() instanceof SQLIntegerExpr))
					throw new DataQueryException("RELATION_UNSUPPORTED");
			}
		long aggregateCount = b.getSelectList().stream().filter(i -> i.getExpr() instanceof SQLAggregateExpr).count();
		if (aggregateCount > 0 && (aggregateCount != 1 || b.getSelectList().size() != 1))
			throw new DataQueryException("RELATION_UNSUPPORTED");
		if (aggregateCount == 0) {
			if (b.getSelectList().stream().noneMatch(i -> column(i.getExpr(), pa, d.parentKey())))
				throw new DataQueryException("RELATION_UNSUPPORTED");
			if (b.getLimit() != null && (b.getOrderBy() == null
					|| b.getOrderBy().getItems().stream().noneMatch(i -> column(i.getExpr(), pa, d.parentKey()))))
				throw new DataQueryException("RELATION_UNSUPPORTED");
		}
		if (b.getSelectList().isEmpty())
			throw new DataQueryException("RELATION_UNSUPPORTED");
		for (var e : ands(child.getWhere()))
			if (e != selectedEdge)
				parentOrChild(e, parentAlias, childAlias);
		// Reject hidden nested tables/correlations not accounted for above.
		final int[] tables = { 0 };
		statement.accept(new MySqlASTVisitorAdapter() {
			@Override
			public boolean visit(SQLExprTableSource t) {
				tables[0]++;
				return true;
			}
		});
		if (tables[0] != 2)
			throw new DataQueryException("RELATION_UNSUPPORTED");
		return new Parsed(statement, b, child, edge, pa, ca, negative, old, params, marker, shape);
	}

	private static void parentOnly(SQLExpr expr, String pa) {
		parentOrChild(expr, pa, pa);
	}

	private static void parentOrChild(SQLExpr expr, String pa, String ca) {
		expr.accept(new MySqlASTVisitorAdapter() {
			@Override
			public boolean visit(SQLPropertyExpr p) {
				if (!pa.equals(clean(p.getOwner().toString())) && !ca.equals(clean(p.getOwner().toString())))
					throw new DataQueryException("RELATION_UNSUPPORTED");
				return false;
			}

			@Override
			public boolean visit(SQLIdentifierExpr e) {
				throw new DataQueryException("RELATION_UNSUPPORTED");
			}

			@Override
			public boolean visit(SQLAllColumnExpr e) {
				throw new DataQueryException("RELATION_UNSUPPORTED");
			}

			@Override
			public boolean visit(SQLExistsExpr e) {
				throw new DataQueryException("RELATION_UNSUPPORTED");
			}

			@Override
			public boolean visit(SQLQueryExpr e) {
				throw new DataQueryException("RELATION_UNSUPPORTED");
			}
		});
	}

	static DataQuerySqlGuard.Prepared render(Parsed p) {
		String sql = SQLUtils.toSQLString(p.statement(), DbType.mysql);
		var matcher = Pattern.compile(Pattern.quote(p.marker()) + "[0-9]+").matcher(sql);
		var text = new StringBuffer();
		var parameters = new ArrayList<Object>();
		while (matcher.find()) {
			parameters.add(p.parameters().get(matcher.group()));
			matcher.appendReplacement(text, "?");
		}
		matcher.appendTail(text);
		return new DataQuerySqlGuard.Prepared(text.toString(), parameters,
				Set.of(table(p.parent().getFrom()), table(p.child().getFrom())));
	}

}
