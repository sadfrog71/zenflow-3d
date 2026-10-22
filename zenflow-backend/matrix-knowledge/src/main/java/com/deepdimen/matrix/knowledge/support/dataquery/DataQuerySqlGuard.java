package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import com.alibaba.druid.wall.WallConfig;
import com.alibaba.druid.wall.spi.MySqlWallProvider;
import java.util.*;
import java.util.regex.Pattern;

/** A dataset-scoped read admission check, NOT a list of permitted business questions. */
public final class DataQuerySqlGuard {

	public record Prepared(String sql, List<Object> parameters, Set<String> tables) {
		public Prepared {
			parameters = List.copyOf(parameters);
			tables = Set.copyOf(tables);
		}

		@Override
		public String toString() {
			return "Prepared[SQL and parameters withheld]";
		}
	}

	private static final Set<String> FUNCTIONS = Set.of("count", "sum", "min", "max", "avg", "group_concat", "coalesce",
			"ifnull", "nullif", "if", "concat", "concat_ws", "substring", "substr", "left", "right", "trim", "ltrim",
			"rtrim", "replace", "lower", "upper", "length", "char_length", "date", "date_format", "year", "month",
			"day", "dayofmonth", "week", "quarter", "time", "timestampdiff", "datediff", "date_add", "date_sub",
			"adddate", "subdate", "round", "ceil", "ceiling", "floor", "abs", "cast", "convert", "now", "curdate",
			"current_date", "current_timestamp", "row_number", "rank", "dense_rank", "lag", "lead", "first_value",
			"last_value");

	public Prepared prepare(String sql, Set<String> allowedTables) {
		return prepare(sql, allowedTables, "top_device_biz");
	}

	/**
	 * Schema is resolved from trusted dataset metadata, never accepted from a model
	 * request.
	 */
	Prepared prepare(String sql, Set<String> allowedTables, String schema) {
		if (schema == null || !schema.matches("[A-Za-z_][A-Za-z0-9_]{0,63}"))
			throw new DataQueryException("DATASET_METADATA_REQUIRED");
		if (sql == null || sql.isBlank() || sql.length() > 20000 || allowedTables == null || allowedTables.isEmpty())
			throw new DataQueryException("SQL_INVALID");
		try {
			var statements = SQLUtils.parseStatements(sql, DbType.mysql);
			if (statements.size() != 1 || !(statements.get(0) instanceof SQLSelectStatement))
				throw new DataQueryException("SQL_READ_ONLY_REQUIRED");
			var statement = statements.get(0);
			WallConfig wall = new WallConfig();
			wall.setSelectAllow(true);
			wall.setInsertAllow(false);
			wall.setUpdateAllow(false);
			wall.setDeleteAllow(false);
			wall.setReplaceAllow(false);
			wall.setMergeAllow(false);
			wall.setSelectIntoAllow(false);
			wall.setSelectIntoOutfileAllow(false);
			wall.setCallAllow(false);
			wall.setSetAllow(false);
			wall.setMultiStatementAllow(false);
			wall.setCommentAllow(false);
			wall.setHintAllow(false);
			wall.setNoneBaseStatementAllow(false);
			wall.setSelectWhereAlwayTrueCheck(false);
			wall.setSelectHavingAlwayTrueCheck(false);
			if (!new MySqlWallProvider(wall).check(sql).getViolations().isEmpty())
				throw new DataQueryException("SQL_UNSAFE");
			var allowed = new HashSet<String>();
			allowedTables.forEach(t -> allowed.add(name(t)));
			var used = new HashSet<String>();
			statement.accept(new MySqlASTVisitorAdapter() {
				@Override
				public boolean visit(SQLExprTableSource table) {
					SQLExpr expr = table.getExpr();
					String actual;
					if (expr instanceof SQLPropertyExpr qualified) {
						if (!(qualified.getOwner() instanceof SQLIdentifierExpr)
								|| !schema.equals(name(qualified.getOwner().toString())))
							throw new DataQueryException("SQL_TABLE_NOT_ALLOWED");
						actual = name(qualified.getName());
					}
					else if (expr instanceof SQLIdentifierExpr identifier)
						actual = name(identifier.getName());
					else
						throw new DataQueryException("SQL_TABLE_NOT_ALLOWED");
					if (expr instanceof SQLIdentifierExpr && isVisibleCte(table, actual))
						return true;
					if (!allowed.contains(actual))
						throw new DataQueryException("SQL_TABLE_NOT_ALLOWED");
					used.add(actual);
					return true;
				}

				@Override
				public boolean visit(SQLVariantRefExpr expr) {
					throw new DataQueryException("SQL_UNSAFE");
				}

				@Override
				public boolean visit(SQLMethodInvokeExpr expr) {
					if (expr.getOwner() != null || !FUNCTIONS.contains(expr.getMethodName().toLowerCase(Locale.ROOT)))
						throw new DataQueryException("SQL_FUNCTION_NOT_ALLOWED");
					return true;
				}

				@Override
				public boolean visit(SQLAggregateExpr expr) {
					if (!FUNCTIONS.contains(expr.getMethodName().toLowerCase(Locale.ROOT)))
						throw new DataQueryException("SQL_FUNCTION_NOT_ALLOWED");
					return true;
				}

				@Override
				public boolean visit(MySqlSelectQueryBlock block) {
					if (block.isForUpdate() || block.isForShare() || block.getInto() != null)
						throw new DataQueryException("SQL_UNSAFE");
					return true;
				}
			});
			if (used.isEmpty())
				throw new DataQueryException("SQL_TABLE_NOT_ALLOWED");
			// Bind literals, not model-written placeholders. Random markers are absent
			// from
			// the input; rendering determines JDBC parameter order, not visitor order.
			String prefix = ":dq_" + UUID.randomUUID().toString().replace("-", "") + "_";
			var values = new HashMap<String, Object>();
			statement.accept(new MySqlASTVisitorAdapter() {
				private boolean bind(SQLExpr expr, Object value) {
					String parent = expr.getParent() == null ? "" : expr.getParent().getClass().getSimpleName();
					// SQL ordinals and data-type sizes are grammar, not bindable values.
					if (parent.contains("OrderBy") || parent.contains("GroupBy") || parent.contains("DataType"))
						return true;
					String marker = prefix + values.size();
					if (!SQLUtils.replaceInParent(expr, new SQLVariantRefExpr(marker)))
						throw new DataQueryException("SQL_PARAMETER_UNSUPPORTED");
					values.put(marker, value);
					return false;
				}

				@Override
				public boolean visit(SQLCharExpr expr) {
					return bind(expr, expr.getText());
				}

				@Override
				public boolean visit(SQLIntegerExpr expr) {
					return bind(expr, expr.getNumber());
				}

				@Override
				public boolean visit(SQLNumberExpr expr) {
					return bind(expr, expr.getNumber());
				}
			});
			String rendered = SQLUtils.toSQLString(statement, DbType.mysql);
			var matcher = Pattern.compile(Pattern.quote(prefix) + "[0-9]+").matcher(rendered);
			var parameters = new ArrayList<Object>();
			var prepared = new StringBuffer();
			while (matcher.find()) {
				parameters.add(values.get(matcher.group()));
				matcher.appendReplacement(prepared, "?");
			}
			matcher.appendTail(prepared);
			return new Prepared(prepared.toString(), parameters, used);
		}
		catch (DataQueryException e) {
			throw e;
		}
		catch (RuntimeException malformed) {
			throw new DataQueryException("SQL_INVALID");
		}
	}

	private static boolean isVisibleCte(SQLExprTableSource reference, String actual) {
		for (com.alibaba.druid.sql.ast.SQLObject parent = reference.getParent(); parent != null; parent = parent
			.getParent()) {
			if (!(parent instanceof SQLSelect select) || select.getWithSubQuery() == null)
				continue;
			var with = select.getWithSubQuery();
			SQLWithSubqueryClause.Entry containing = null;
			for (com.alibaba.druid.sql.ast.SQLObject node = reference; node != null
					&& node != select; node = node.getParent())
				if (node instanceof SQLWithSubqueryClause.Entry entry && with.getEntries().contains(entry))
					containing = entry;
			for (var entry : with.getEntries()) {
				if (entry == containing) {
					if (Boolean.TRUE.equals(with.getRecursive()) && name(entry.getAlias()).equalsIgnoreCase(actual))
						return true;
					break; // Forward/self references are not in scope in a non-recursive
							// CTE.
				}
				if (name(entry.getAlias()).equalsIgnoreCase(actual))
					return true;
			}
		}
		return false;
	}

	private static String name(String name) {
		// MySQL on Linux can have case-distinct schemas/tables. Never widen exact
		// selection.
		if (name.length() >= 2 && name.startsWith("`") && name.endsWith("`"))
			return name.substring(1, name.length() - 1).replace("``", "`");
		return name;
	}

}
