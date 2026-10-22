package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Independent server-built JOIN aggregate oracle vs EXISTS trial, always retaining
 * captured filters.
 */
final class DataQueryRelationProbe {

	interface Reader {

		DataQuerySqlExecutor.Rows read(DataQuerySqlGuard.Prepared query);

	}

	record Window(String from, String to) {
	}

	static Window window(DataQuerySqlGuard.Prepared sql, DataQueryRelation.Definition d) {
		var p = DataQueryRelation.parse(sql, d);
		String from = null, to = null;
		for (var e : DataQueryRelation.ands(p.parent().getWhere()))
			if (e instanceof SQLBinaryOpExpr b && DataQueryRelation.column(b.getLeft(), p.parentAlias(), d.timeField())
					&& b.getRight() instanceof SQLVariantRefExpr ref) {
				String value = String.valueOf(p.parameters().get(ref.getName()));
				if (b.getOperator() == SQLBinaryOperator.GreaterThanOrEqual) {
					if (from != null)
						throw new DataQueryException("INVESTIGATION_SCOPE_UNSUPPORTED");
					from = value;
				}
				else if (b.getOperator() == SQLBinaryOperator.LessThan) {
					if (to != null)
						throw new DataQueryException("INVESTIGATION_SCOPE_UNSUPPORTED");
					to = value;
				}
				else
					throw new DataQueryException("INVESTIGATION_SCOPE_UNSUPPORTED");
			}
		try {
			var start = midnightDate(from);
			var end = midnightDate(to);
			var month = YearMonth.from(start);
			if (!month.atDay(1).equals(start) || !month.plusMonths(1).atDay(1).equals(end))
				throw new IllegalArgumentException();
			return new Window(start.toString(), end.toString());
		}
		catch (RuntimeException e) {
			throw new DataQueryException("INVESTIGATION_SCOPE_UNSUPPORTED");
		}
	}

	private static LocalDate midnightDate(String value) {
		if (value != null && value.matches("\\d{4}-\\d{2}-\\d{2}"))
			return LocalDate.parse(value);
		if (value == null || !value.matches("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?"))
			throw new IllegalArgumentException();
		var timestamp = LocalDateTime.parse(value.replace(' ', 'T'));
		// Only equivalent midnight boundaries are accepted; never truncate a time or
		// infer a time zone.
		if (!LocalTime.MIDNIGHT.equals(timestamp.toLocalTime()))
			throw new IllegalArgumentException();
		return timestamp.toLocalDate();
	}

	static DataQuerySqlGuard.Prepared previousMonth(DataQuerySqlGuard.Prepared sql, DataQueryRelation.Definition d) {
		var w = window(sql, d);
		var p = DataQueryRelation.parse(sql, d);
		for (var e : DataQueryRelation.ands(p.parent().getWhere()))
			if (e instanceof SQLBinaryOpExpr b && DataQueryRelation.column(b.getLeft(), p.parentAlias(), d.timeField())
					&& b.getRight() instanceof SQLVariantRefExpr r) {
				// window() has already validated the whole value; retain each bound's
				// original timestamp format.
				String suffix = String.valueOf(p.parameters().get(r.getName())).substring(10);
				if (b.getOperator() == SQLBinaryOperator.GreaterThanOrEqual)
					p.parameters().put(r.getName(), LocalDate.parse(w.from()).minusMonths(1) + suffix);
				else if (b.getOperator() == SQLBinaryOperator.LessThan)
					p.parameters().put(r.getName(), w.from() + suffix);
			}
		return DataQueryRelation.render(p);
	}

	static Map<String, Object> probe(DataQuerySqlGuard.Prepared source, DataQueryRelation.Definition d,
			String candidate, Reader reader) {
		if (!List.of(d.parentKey(), d.previousKey()).contains(candidate))
			throw new DataQueryException("INVESTIGATION_TOOL_REJECTED");
		var p = DataQueryRelation.parse(source, d);
		var w = window(source, d);
		var filters = DataQueryRelation.ands(p.parent().getWhere())
			.stream()
			.filter(e -> !(e instanceof SQLExistsExpr))
			.toList();
		var childFilters = DataQueryRelation.ands(p.child().getWhere()).stream().filter(e -> e != p.edge()).toList();
		String where = filters.isEmpty() ? "1=1" : SQLUtils.toSQLString(DataQueryRelation.and(filters), DbType.mysql);
		String extra = childFilters.isEmpty() ? ""
				: " AND (" + SQLUtils.toSQLString(DataQueryRelation.and(childFilters), DbType.mysql) + ")";
		String pa = p.parentAlias(), ca = p.childAlias();
		String edge = SQLUtils.toSQLString(DataQueryRelation.relation(d, pa, ca, candidate), DbType.mysql);
		String parent = d.parentTable() + " " + pa;
		String child = d.childTable() + " " + ca;
		// Probe uniqueness in the actual parent scope; it is not evidence about rows
		// outside that scope.
		String stats = "SELECT COUNT(*) AS parent_rows, COUNT(DISTINCT " + pa + "." + d.parentKey()
				+ ") AS parent_ids, COUNT(DISTINCT BINARY CAST(" + pa + "." + candidate
				+ " AS CHAR)) AS candidate_keys, COUNT(" + pa + "." + candidate + ") AS non_null_keys FROM " + parent
				+ " WHERE " + where;
		var summary = numbers(reader.read(compiled(stats, p, d)));
		String trial = "SELECT COUNT(*) AS parent_count FROM " + parent + " WHERE (" + where + ") AND "
				+ (p.negative() ? "NOT " : "") + "EXISTS (SELECT 1 FROM " + child + " WHERE " + edge + extra + ")";
		var trialResult = numbers(reader.read(compiled(trial, p, d)));
		// Separate formulation, no use of the correction compiler's result as the
		// reference answer.
		String oracle = "SELECT COUNT(DISTINCT " + pa + "." + d.parentKey() + ") AS total, COUNT(DISTINCT CASE WHEN "
				+ ca + "." + d.childKey() + " IS NOT NULL THEN " + pa + "." + d.parentKey()
				+ " END) AS with_items, COUNT(DISTINCT CASE WHEN " + ca + "." + d.childKey() + " IS NULL THEN " + pa
				+ "." + d.parentKey() + " END) AS without_items, COUNT(" + ca + "." + d.childKey()
				+ ") AS matched_child_rows FROM " + parent + " LEFT JOIN " + child + " ON (" + edge + extra + ") WHERE "
				+ where;
		var reference = numbers(reader.read(compiled(oracle, p, d)));
		long expected = reference.get(p.negative() ? "without_items" : "with_items");
		boolean agrees = expected == trialResult.get("parent_count")
				&& summary.get("parent_rows").equals(summary.get("parent_ids"));
		return Map.of("candidateKey", candidate, "window", Map.of("from", w.from(), "to", w.to()), "parent", summary,
				"existsTrial", trialResult, "independentJoin", reference, "agrees", agrees, "scope",
				"ORIGINAL_AUTHORIZATION_AND_CAPTURED_PREDICATES", "unverified",
				List.of("Business ownership of the relation requires independent confirmation",
						"Orphan child rows cannot be assigned to the month without an approved child time field",
						"Uniqueness outside these month predicates is not established"));
	}

	static DataQuerySqlGuard.Prepared referenceQuery(DataQuerySqlGuard.Prepared source,
			DataQueryRelation.Definition d) {
		var p = DataQueryRelation.parse(source, d);
		var filters = new ArrayList<>(DataQueryRelation.ands(p.parent().getWhere())
			.stream()
			.filter(e -> !(e instanceof SQLExistsExpr))
			.toList());
		var joinFilters = new ArrayList<>(
				DataQueryRelation.ands(p.child().getWhere()).stream().filter(e -> e != p.edge()).toList());
		joinFilters.add(DataQueryRelation.relation(d, p.parentAlias(), p.childAlias(), d.parentKey()));
		var join = new SQLJoinTableSource();
		join.setLeft(p.parent().getFrom());
		join.setRight(p.child().getFrom());
		join.setJoinType(SQLJoinTableSource.JoinType.LEFT_OUTER_JOIN);
		join.setCondition(DataQueryRelation.and(joinFilters));
		p.parent().setFrom(join);
		filters.add(SQLUtils.toSQLExpr(
				p.childAlias() + "." + d.childKey() + (p.negative() ? " IS NULL" : " IS NOT NULL"), DbType.mysql));
		p.parent().setWhere(DataQueryRelation.and(filters));
		boolean aggregate = p.parent().getSelectList().size() == 1
				&& p.parent().getSelectList().get(0).getExpr() instanceof SQLAggregateExpr;
		if (aggregate) {
			var item = p.parent().getSelectList().get(0);
			item.setExpr(
					SQLUtils.toSQLExpr("COUNT(DISTINCT " + p.parentAlias() + "." + d.parentKey() + ")", DbType.mysql));
		}
		else
			p.parent().setDistionOption(com.alibaba.druid.sql.ast.SQLSetQuantifier.DISTINCT);
		return compiled(SQLUtils.toSQLString(p.statement(), DbType.mysql), p, d);
	}

	static DataQuerySqlGuard.Prepared compiled(String sql, DataQueryRelation.Parsed p, DataQueryRelation.Definition d) {
		var matcher = Pattern.compile(Pattern.quote(p.marker()) + "[0-9]+").matcher(sql);
		var rendered = new StringBuffer();
		var parameters = new ArrayList<Object>();
		while (matcher.find()) {
			parameters.add(p.parameters().get(matcher.group()));
			matcher.appendReplacement(rendered, "?");
		}
		matcher.appendTail(rendered);
		return new DataQuerySqlGuard.Prepared(rendered.toString(), parameters, Set.of(d.parentTable(), d.childTable()));
	}

	private static Map<String, Long> numbers(DataQuerySqlExecutor.Rows rows) {
		if (rows.values().size() != 1)
			throw new DataQueryException("INVESTIGATION_REFERENCE_FAILED");
		var result = new LinkedHashMap<String, Long>();
		for (int i = 0; i < rows.columns().size(); i++) {
			Object value = rows.values().get(0).get(i);
			try {
				long n = Long.parseLong(String.valueOf(value));
				if (n < 0)
					throw new NumberFormatException();
				result.put(rows.columns().get(i).toLowerCase(Locale.ROOT), n);
			}
			catch (NumberFormatException e) {
				throw new DataQueryException("INVESTIGATION_REFERENCE_FAILED");
			}
		}
		return result;
	}

}
