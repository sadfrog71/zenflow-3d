package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import java.util.*;

/**
 * Owned audit facts only: no raw SQL, free-text values, provider reply or exception
 * message.
 */
final class DataQuerySqlDiagnostics {

	private static final Set<String> FIELDS = Set.of("company_id", "patrol_dept_id", "patrol_dept_name", "start_date",
			"del_flag", "id", "record_code", "record_id");

	static Map<String, Object> describe(DataQuerySqlGuard.Prepared prepared) {
		var predicates = new ArrayList<Object>();
		var groups = new ArrayList<Object>();
		var subqueries = new ArrayList<String>();
		try {
			var statement = SQLUtils.parseSingleStatement(prepared.sql(), DbType.mysql);
			statement.accept(new MySqlASTVisitorAdapter() {
				@Override
				public boolean visit(SQLBinaryOpExpr e) {
					if (predicates.size() < 32)
						predicates.add(Map.of("operator", e.getOperator().name(), "left",
								expression(e.getLeft(), prepared), "right", expression(e.getRight(), prepared)));
					return true;
				}

				@Override
				public boolean visit(SQLSelectGroupByClause e) {
					for (var item : e.getItems())
						if (groups.size() < 16)
							groups.add(expression(item, prepared));
					return true;
				}

				@Override
				public boolean visit(SQLInSubQueryExpr e) {
					if (subqueries.size() < 8)
						subqueries.add(e.isNot() ? "NOT_IN_SUBQUERY" : "IN_SUBQUERY");
					return true;
				}
			});
			return Map.of("version", "safe-sql-facts-v1", "tables", new TreeSet<>(prepared.tables()), "predicates",
					predicates, "groupBy", groups, "subqueries", subqueries);
		}
		catch (RuntimeException unsupported) {
			return Map.of("version", "safe-sql-facts-v1", "status", "FACTS_UNAVAILABLE");
		}
	}

	private static Object expression(SQLExpr e, DataQuerySqlGuard.Prepared p) {
		String field = e instanceof SQLIdentifierExpr i ? i.getName()
				: e instanceof SQLPropertyExpr prop ? prop.getName() : null;
		if (field != null) {
			field = field.replace("`", "").toLowerCase(Locale.ROOT);
			return FIELDS.contains(field) ? Map.of("field", field) : Map.of("identifier", "REDACTED");
		}
		if (e instanceof SQLVariantRefExpr v) {
			int index = v.getIndex();
			if (index < 0 || index >= p.parameters().size())
				return Map.of("parameter", "UNKNOWN");
			Object value = p.parameters().get(index);
			String text = String.valueOf(value);
			var fact = new LinkedHashMap<String, Object>();
			fact.put("parameter", index + 1);
			fact.put("type", value instanceof Number ? "NUMBER" : "TEXT");
			fact.put("length", text.length());
			fact.put("digest", DataQueryMetadataRepository.fingerprint(text));
			// Only canonical calendar bounds are meaningful and non-secret in this
			// diagnostic.
			if (text.matches("20[0-9]{2}-(0[1-9]|1[0-2])(-[0-3][0-9]([ T][0-2][0-9]:[0-5][0-9]:[0-5][0-9])?)?"))
				fact.put("calendarValue", text);
			return fact;
		}
		if (e instanceof SQLMethodInvokeExpr method)
			return Map.of("expression", "FUNCTION", "name",
					Set.of("date_format", "year", "month", "cast", "date")
						.contains(method.getMethodName().toLowerCase(Locale.ROOT))
								? method.getMethodName().toLowerCase(Locale.ROOT) : "OTHER");
		if (e instanceof SQLNullExpr)
			return Map.of("literal", "NULL");
		return Map.of("expression", e == null ? "NONE" : e.getClass().getSimpleName());
	}

}
