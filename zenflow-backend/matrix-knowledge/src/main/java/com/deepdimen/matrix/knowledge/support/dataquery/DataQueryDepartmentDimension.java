package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import java.time.*;
import java.util.*;

/**
 * Bounded provenance for record-count drilldown; never asks the model to invent an
 * identity.
 */
final class DataQueryDepartmentDimension {

	static final List<String> DETAIL_COLUMNS = List.of("record_id", "record_name", "start_date", "company_id",
			"patrol_dept_id", "patrol_dept_name");

	static final String KIND = "RECORD_COMPANY_DEPARTMENT_V1";
	static final Set<String> KEYS = Set.of("company_id", "patrol_dept_id", "patrol_dept_name");

	record Definition(String kind, List<String> identityKeys, String nameSource, String conflictHandling,
			String missingHandling, long dataId, String inventoryDigest, String bindingRevision) {
		Definition {
			identityKeys = List.copyOf(identityKeys);
		}

		void validate() {
			if (!KIND.equals(kind) || !identityKeys.equals(List.of("company_id", "patrol_dept_id"))
					|| !"RECORD_TIME_NOT_AUTHORITY".equals(nameSource)
					|| !"KEEP_COUNT_NULL_NAME".equals(conflictHandling)
					|| !"KEEP_COUNT_AND_WARN".equals(missingHandling) || dataId <= 0 || inventoryDigest == null
					|| !inventoryDigest.matches("[0-9a-f]{64}") || bindingRevision == null || bindingRevision.isBlank())
				throw new DataQueryException("DEPARTMENT_DEFINITION_INVALID");
		}
	}

	record Scope(String from, String to, int topN, List<String> nonNullFields, boolean nonEmptyNameFilter) {
		Scope(String from, String to, int topN, List<String> nonNullFields) {
			this(from, to, topN, nonNullFields, false);
		}

		Scope {
			nonNullFields = List.copyOf(nonNullFields);
			var month = YearMonth.from(LocalDate.parse(from));
			if (!month.atDay(1).toString().equals(from) || !month.plusMonths(1).atDay(1).toString().equals(to)
					|| topN < 0 || topN > 1000 || !KEYS.containsAll(nonNullFields))
				throw new IllegalArgumentException("Invalid bounded month");
		}

		Scope at(YearMonth month, int top) {
			return new Scope(month.atDay(1).toString(), month.plusMonths(1).atDay(1).toString(), top, nonNullFields,
					nonEmptyNameFilter);
		}
	}

	record Key(String field, boolean binary) {
		Key {
			if (!KEYS.contains(field))
				throw new IllegalArgumentException();
		}
	}

	record Captured(Scope scope, List<Key> groupKeys, int visibleColumns) {
		Captured {
			groupKeys = List.copyOf(groupKeys);
		}
	}

	record Filter(Key key, String value) {
	}

	record Group(String companyId, String departmentId, List<Filter> filters, long count) {
		Group {
			filters = List.copyOf(filters);
		}

		@Override
		public String toString() {
			return "Group[REDACTED]";
		}
	}

	record Ranking(Captured captured, Group first, String sourceQueryId, String status) {
	}

	record Page(Ranking ranking, int pageSize, int pageNumber, String afterStart, String afterId, boolean hasMore,
			List<String> columns) {
		Page(Ranking ranking, int pageSize, int pageNumber, String afterStart, String afterId, boolean hasMore) {
			this(ranking, pageSize, pageNumber, afterStart, afterId, hasMore, DETAIL_COLUMNS);
		}

		Page {
			// Older saved pages have no projection; retain their full original columns.
			columns = columns == null ? DETAIL_COLUMNS : List.copyOf(columns);
			if (columns.isEmpty() || !DETAIL_COLUMNS.containsAll(columns)
					|| new HashSet<>(columns).size() != columns.size())
				throw new DataQueryException("DETAIL_SCOPE_UNAVAILABLE");
		}
	}

	record Ranked(DataQuerySqlExecutor.Rows rows, Ranking ranking) {
	}

	record Paged(DataQuerySqlExecutor.Rows rows, Page next) {
	}

	static Optional<Captured> capture(DataQuerySqlGuard.Prepared sql) {
		try {
			var statement = (SQLSelectStatement) SQLUtils.parseSingleStatement(sql.sql(), DbType.mysql);
			if (statement.getSelect().getWithSubQuery() != null
					|| !(statement.getSelect().getQuery() instanceof SQLSelectQueryBlock b)
					|| !(b.getFrom() instanceof SQLExprTableSource t) || !"dev_patrol_record".equals(field(t.getExpr()))
					|| b.getGroupBy() == null || b.getGroupBy().getHaving() != null || b.getSelectList().size() > 10
					|| b.getDistionOption() != 0)
				return Optional.empty();
			var keys = new ArrayList<Key>();
			for (var e : b.getGroupBy().getItems()) {
				var k = key(e);
				if (k == null || keys.contains(k))
					return Optional.empty();
				keys.add(k);
			}
			if (keys.isEmpty() || keys.stream().noneMatch(k -> k.field.startsWith("patrol_dept_")))
				return Optional.empty();
			var counts = new HashSet<String>();
			boolean hasCount = false;
			for (int i = 0; i < b.getSelectList().size(); i++) {
				var item = b.getSelectList().get(i);
				if (isCount(item.getExpr())) {
					hasCount = true;
					counts.add(Integer.toString(i + 1));
					if (item.getAlias() != null)
						counts.add(clean(item.getAlias()));
				}
				else if (!keys.contains(key(item.getExpr())))
					return Optional.empty();
			}
			if (!hasCount || b.getOrderBy() == null || b.getOrderBy().getItems().isEmpty())
				return Optional.empty();
			var order = b.getOrderBy().getItems().get(0);
			if (!"DESC".equals(String.valueOf(order.getType()))
					|| !(isCount(order.getExpr()) || counts.contains(clean(order.getExpr().toString()))))
				return Optional.empty();
			for (var extra : b.getOrderBy().getItems().subList(1, b.getOrderBy().getItems().size()))
				if (!keys.contains(key(extra.getExpr())))
					return Optional.empty();
			var scope = captureScope(b, sql);
			return scope == null ? Optional.empty() : Optional.of(new Captured(scope, keys, b.getSelectList().size()));
		}
		catch (RuntimeException unsupported) {
			return Optional.empty();
		}
	}

	/**
	 * Presentation-only reuse of the same finite filter parser; never changes or executes
	 * SQL.
	 */
	static Optional<Scope> totalScope(DataQuerySqlGuard.Prepared sql) {
		try {
			var statement = (SQLSelectStatement) SQLUtils.parseSingleStatement(sql.sql(), DbType.mysql);
			if (statement.getSelect().getWithSubQuery() != null
					|| !(statement.getSelect().getQuery() instanceof SQLSelectQueryBlock b)
					|| !(b.getFrom() instanceof SQLExprTableSource t) || !"dev_patrol_record".equals(field(t.getExpr()))
					|| b.getGroupBy() != null || b.getDistionOption() != 0 || b.getSelectList().size() != 1
					|| !isCount(b.getSelectList().get(0).getExpr()))
				return Optional.empty();
			return Optional.ofNullable(captureScope(b, sql));
		}
		catch (RuntimeException unsupported) {
			return Optional.empty();
		}
	}

	private static Scope captureScope(SQLSelectQueryBlock b, DataQuerySqlGuard.Prepared sql) {
		var bounds = new HashMap<String, String>();
		var nonNull = new ArrayList<String>();
		if (!conditions(b.getWhere(), sql, bounds, nonNull) || !"0".equals(bounds.get("deleted")))
			return null;
		boolean nonEmptyName = "true".equals(bounds.remove("nonEmptyName"));
		if (bounds.containsKey("month")) {
			if (bounds.size() != 2)
				return null;
			var m = YearMonth.parse(bounds.get("month"));
			bounds.put("from", m.atDay(1).toString());
			bounds.put("to", m.plusMonths(1).atDay(1).toString());
		}
		else if (bounds.containsKey("year") || bounds.containsKey("monthNumber")) {
			if (bounds.size() != 3)
				return null;
			var m = YearMonth.of(Integer.parseInt(bounds.get("year")), Integer.parseInt(bounds.get("monthNumber")));
			bounds.put("from", m.atDay(1).toString());
			bounds.put("to", m.plusMonths(1).atDay(1).toString());
		}
		else if (bounds.size() != 3)
			return null;
		int top = 0;
		if (b.getLimit() != null) {
			if (b.getLimit().getOffset() != null && !"0".equals(value(b.getLimit().getOffset(), sql)))
				return null;
			top = Integer.parseInt(value(b.getLimit().getRowCount(), sql));
			if (top < 1)
				return null;
		}
		Collections.sort(nonNull);
		return new Scope(date(bounds.get("from")), date(bounds.get("to")), top, nonNull, nonEmptyName);
	}

	/** Safe presentation of visible COUNT bases, not a new execution plan. */
	static String countBasis(DataQuerySqlGuard.Prepared sql, int visibleColumns) {
		try {
			var statement = (SQLSelectStatement) SQLUtils.parseSingleStatement(sql.sql(), DbType.mysql);
			var b = (SQLSelectQueryBlock) statement.getSelect().getQuery();
			var bases = new HashSet<String>();
			for (var item : b.getSelectList().subList(0, Math.min(visibleColumns, b.getSelectList().size())))
				if (isCount(item.getExpr()))
					bases.add("id".equals(field(((SQLAggregateExpr) item.getExpr()).getArguments().get(0)))
							? "NON_NULL_RECORD_ID" : "ALL_ROWS");
			return bases.size() == 1 ? bases.iterator().next() : "MIXED_OR_UNKNOWN";
		}
		catch (RuntimeException unsupported) {
			return "MIXED_OR_UNKNOWN";
		}
	}

	private static boolean conditions(SQLExpr e, DataQuerySqlGuard.Prepared p, Map<String, String> b, List<String> n) {
		if (!(e instanceof SQLBinaryOpExpr op))
			return false;
		if (op.getOperator() == SQLBinaryOperator.BooleanAnd)
			return conditions(op.getLeft(), p, b, n) && conditions(op.getRight(), p, b, n);
		String f = field(op.getLeft()), v = value(op.getRight(), p);
		if (KEYS.contains(f) && op.getOperator() == SQLBinaryOperator.IsNot && op.getRight() instanceof SQLNullExpr) {
			if (n.contains(f))
				return false;
			n.add(f);
			return true;
		}
		// Preserve only the literal empty-name exclusion observed in the real failed Top5
		// query.
		// No arbitrary inequalities, trimming, other columns or source collation changes.
		if (f.equals("patrol_dept_name") && (op.getOperator() == SQLBinaryOperator.NotEqual
				|| op.getOperator() == SQLBinaryOperator.LessThanOrGreater) && "".equals(v))
			return put(b, "nonEmptyName", "true");
		if (f.equals("del_flag") && op.getOperator() == SQLBinaryOperator.Equality)
			return put(b, "deleted", v);
		if (f.equals("start_date") && op.getOperator() == SQLBinaryOperator.GreaterThanOrEqual)
			return put(b, "from", v);
		if (f.equals("start_date") && op.getOperator() == SQLBinaryOperator.LessThan)
			return put(b, "to", v);
		if (op.getOperator() == SQLBinaryOperator.Equality && op.getLeft() instanceof SQLMethodInvokeExpr m
				&& !m.getArguments().isEmpty() && field(m.getArguments().get(0)).equals("start_date")) {
			if (m.getMethodName().equalsIgnoreCase("date_format") && m.getArguments().size() == 2
					&& "%Y-%m".equals(value(m.getArguments().get(1), p)))
				return put(b, "month", v);
			if (m.getMethodName().equalsIgnoreCase("year") && m.getArguments().size() == 1)
				return put(b, "year", v);
			if (m.getMethodName().equalsIgnoreCase("month") && m.getArguments().size() == 1)
				return put(b, "monthNumber", v);
		}
		return false;
	}

	private static boolean put(Map<String, String> b, String key, String value) {
		return value != null && b.putIfAbsent(key, value) == null;
	}

	private static String date(String s) {
		if (s == null)
			throw new IllegalArgumentException();
		return s.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}[ T]00:00:00") ? s.substring(0, 10) : s;
	}

	private static String value(SQLExpr e, DataQuerySqlGuard.Prepared p) {
		if (e instanceof SQLVariantRefExpr v)
			return Objects.toString(p.parameters().get(v.getIndex()), null);
		if (e instanceof SQLCharExpr c)
			return c.getText();
		if (e instanceof SQLIntegerExpr i)
			return i.getNumber().toString();
		return null;
	}

	private static boolean isCount(SQLExpr e) {
		return e instanceof SQLAggregateExpr a && a.getOption() == null && a.getMethodName().equalsIgnoreCase("count")
				&& a.getArguments().size() == 1
				&& (a.getArguments().get(0) instanceof SQLAllColumnExpr || "id".equals(field(a.getArguments().get(0))));
	}

	private static Key key(SQLExpr e) {
		if (e instanceof SQLUnaryExpr u && "BINARY".equalsIgnoreCase(u.getOperator().toString())) {
			String f = field(u.getExpr());
			return KEYS.contains(f) ? new Key(f, true) : null;
		}
		String f = field(e);
		return KEYS.contains(f) ? new Key(f, false) : null;
	}

	static String field(SQLExpr e) {
		if (e instanceof SQLPropertyExpr p)
			return clean(p.getName());
		if (e instanceof SQLIdentifierExpr i)
			return clean(i.getName());
		return "";
	}

	private static String clean(String s) {
		return s.replace("`", "").toLowerCase(Locale.ROOT);
	}

	/**
	 * Same WHERE/GROUP/ORDER/LIMIT and same execution snapshot, with hidden physical-key
	 * evidence.
	 */
	static DataQuerySqlGuard.Prepared instrument(DataQuerySqlGuard.Prepared p, Captured capture) {
		var statement = (SQLSelectStatement) SQLUtils.parseSingleStatement(p.sql(), DbType.mysql);
		var b = (SQLSelectQueryBlock) statement.getSelect().getQuery();
		String alias = ((SQLExprTableSource) b.getFrom()).getAlias();
		String prefix = alias == null ? "" : "`" + alias.replace("`", "") + "`.";
		int i = 0;
		for (String f : List.of("company_id", "patrol_dept_id")) {
			b.addSelectItem(SQLUtils.toSQLExpr("MIN(" + prefix + f + ")", DbType.mysql), "__dq_key_" + i++);
			b.addSelectItem(SQLUtils
				.toSQLExpr("COUNT(DISTINCT BINARY " + prefix + f + ") + MAX(" + prefix + f + " IS NULL)", DbType.mysql),
					"__dq_key_" + i++);
		}
		b.addSelectItem(SQLUtils.toSQLExpr("COUNT(*)", DbType.mysql), "__dq_count");
		for (Key k : capture.groupKeys)
			b.addSelectItem(SQLUtils.toSQLExpr("MIN(" + prefix + k.field + ")", DbType.mysql), "__dq_group_" + i++);
		return new DataQuerySqlGuard.Prepared(SQLUtils.toSQLString(statement, DbType.mysql), p.parameters(),
				p.tables());
	}

	static Ranked materialize(String queryId, Captured c, DataQuerySqlExecutor.Rows result) {
		int width = c.visibleColumns;
		if (result.columns().size() != width + 5 + c.groupKeys.size())
			throw new DataQueryException("DETAIL_EVIDENCE_INVALID");
		var rows = new DataQuerySqlExecutor.Rows(result.columns().subList(0, width),
				result.values().stream().map(r -> r.subList(0, width)).toList());
		Group group = null;
		String status = rows.values().isEmpty() ? "EMPTY" : "AMBIGUOUS_GRAIN";
		if (!rows.values().isEmpty()) {
			var first = result.values().get(0);
			if ("1".equals(String.valueOf(first.get(width + 1))) && "1".equals(String.valueOf(first.get(width + 3)))) {
				var filters = new ArrayList<Filter>();
				for (int i = 0; i < c.groupKeys.size(); i++)
					filters.add(new Filter(c.groupKeys.get(i), Objects.toString(first.get(width + 5 + i), null)));
				group = new Group(Objects.toString(first.get(width), null),
						Objects.toString(first.get(width + 2), null), filters,
						Long.parseLong(first.get(width + 4).toString()));
				status = "VERIFIED_KEYS";
			}
		}
		return new Ranked(rows, new Ranking(c, group, queryId, status));
	}

	record Corrected(Ranked ranked, List<Map<String, Object>> quality) {
	}

	static Corrected corrected(String queryId, Scope scope, DataQuerySqlExecutor.Rows result) {
		if (result.columns().size() != 6)
			throw new DataQueryException("DEPARTMENT_REGRESSION_FAILED");
		var values = new ArrayList<List<Object>>();
		var quality = new ArrayList<Map<String, Object>>();
		Group first = null;
		for (var row : result.values()) {
			long variants = Long.parseLong(row.get(3).toString()), missing = Long.parseLong(row.get(4).toString());
			values.add(Arrays.asList(row.get(5), row.get(2)));
			quality.add(Map.of("resultRow", values.size(), "nameVariants", variants, "missingNameRecords", missing,
					"nameStatus",
					variants > 1 ? "CONFLICT"
							: variants == 0 ? "MISSING" : missing > 0 ? "SINGLE_NAME_WITH_GAPS" : "SINGLE_NAME",
					"missingIdentity", row.get(0) == null || row.get(1) == null));
			if (values.size() == 1)
				first = new Group(Objects.toString(row.get(0), null), Objects.toString(row.get(1), null), List.of(),
						Long.parseLong(row.get(2).toString()));
		}
		var captured = new Captured(scope, List.of(new Key("company_id", true), new Key("patrol_dept_id", true)), 2);
		return new Corrected(
				new Ranked(new DataQuerySqlExecutor.Rows(List.of("部门（记录时名称）", "巡视记录数"), values),
						new Ranking(captured, first, queryId, first == null ? "EMPTY" : "VERIFIED_KEYS")),
				List.copyOf(quality));
	}

	static String where(Scope scope) {
		return "start_date >= ? AND start_date < ? AND del_flag = ?"
				+ scope.nonNullFields.stream().map(f -> " AND " + f + " IS NOT NULL").reduce("", String::concat)
				+ (scope.nonEmptyNameFilter ? " AND patrol_dept_name <> ?" : "");
	}

	static List<Object> parameters(Scope scope) {
		var args = new ArrayList<Object>(List.of(scope.from + " 00:00:00", scope.to + " 00:00:00", "0"));
		if (scope.nonEmptyNameFilter)
			args.add("");
		return args;
	}

	static DataQuerySqlGuard.Prepared details(Page page) {
		if (page.ranking == null || page.ranking.first == null || !"VERIFIED_KEYS".equals(page.ranking.status)
				|| page.pageSize < 1 || page.pageSize > 100 || page.pageNumber < 1)
			throw new DataQueryException("DETAIL_SCOPE_UNAVAILABLE");
		Scope scope = page.ranking.captured.scope;
		Group group = page.ranking.first;
		var args = parameters(scope);
		var where = new StringBuilder(where(scope));
		addFilter(where, args, new Filter(new Key("company_id", true), group.companyId));
		addFilter(where, args, new Filter(new Key("patrol_dept_id", true), group.departmentId));
		for (Filter f : group.filters)
			addFilter(where, args, f);
		if (page.afterStart != null) {
			if (page.afterId == null || !page.afterId.matches("[0-9]{1,20}"))
				throw new DataQueryException("DETAIL_SCOPE_UNAVAILABLE");
			LocalDateTime.parse(page.afterStart.replace(' ', 'T'));
			where.append(" AND (start_date > ? OR (start_date = ? AND id > ?))");
			args.add(page.afterStart);
			args.add(page.afterStart);
			args.add(page.afterId);
		}
		return new DataQuerySqlGuard.Prepared(
				"SELECT CAST(id AS CHAR) AS record_id, record_name, CAST(start_date AS CHAR) AS start_date, company_id, patrol_dept_id, patrol_dept_name FROM dev_patrol_record WHERE "
						+ where + " ORDER BY start_date ASC, id ASC LIMIT " + (page.pageSize + 1),
				args, Set.of("dev_patrol_record"));
	}

	private static void addFilter(StringBuilder w, List<Object> args, Filter f) {
		if (f.value == null) {
			w.append(" AND ").append(f.key.field).append(" IS NULL");
			return;
		}
		w.append(" AND ").append(f.key.field).append(" = ?");
		args.add(f.value);
		if (f.key.binary) {
			w.append(" AND BINARY ").append(f.key.field).append(" = BINARY ?");
			args.add(f.value);
		}
	}

	static Paged page(Page request, DataQuerySqlExecutor.Rows result) {
		boolean more = result.values().size() > request.pageSize;
		var rows = new DataQuerySqlExecutor.Rows(result.columns(),
				result.values().subList(0, Math.min(request.pageSize, result.values().size())));
		String date = null, id = null;
		if (!rows.values().isEmpty()) {
			var last = rows.values().get(rows.values().size() - 1);
			id = last.get(0).toString();
			date = last.get(2).toString();
		}
		var displayed = new DataQuerySqlExecutor.Rows(request.columns, rows.values()
			.stream()
			.map(row -> request.columns.stream().map(column -> row.get(DETAIL_COLUMNS.indexOf(column))).toList())
			.toList());
		return new Paged(displayed,
				new Page(request.ranking, request.pageSize, request.pageNumber, date, id, more, request.columns));
	}

}
