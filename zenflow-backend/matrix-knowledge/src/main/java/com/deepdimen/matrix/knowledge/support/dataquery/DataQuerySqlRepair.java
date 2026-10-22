package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;

/** A deliberately narrow, provable repair. The model never supplies executable SQL. */
public final class DataQuerySqlRepair {

	public static final String KIND = "TABLE_ALIAS_REFERENCE";

	public static final String VERSION = "table-alias-repair-v1";

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final ThreadPoolExecutor ADVISORS = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS,
			new SynchronousQueue<>(), task -> {
				Thread thread = new Thread(task, "dataquery-repair-advisor");
				thread.setDaemon(true);
				return thread;
			}, new ThreadPoolExecutor.AbortPolicy());

	@FunctionalInterface
	public interface Advisor {

		String suggest(String facts);

	}

	public record Diagnosis(String table, String alias, String correctedSql, String proofDigest, int references) {
		@Override
		public String toString() {
			return "SqlRepairDiagnosis[REDACTED]";
		}

		public String facts() {
			// Only server-derived structural facts: no SQL literals, source rows, driver
			// text or credentials.
			return "数据库返回字段引用不存在。后端识别到单表查询已声明别名，但字段仍由原表名限定。" + "仅允许建议 TABLE_ALIAS_REFERENCE；不得改字段、条件、口径、关联或权限。"
					+ "如同意，仅返回JSON：{\"repairKind\":\"TABLE_ALIAS_REFERENCE\",\"table\":\"" + table + "\",\"alias\":\""
					+ alias + "\"}。不确定则返回{}。";
		}

		public boolean accepts(String answer) {
			if (answer == null || answer.length() > 2048)
				return false;
			try {
				var root = JSON.readTree(answer);
				return root.isObject() && root.size() == 3 && KIND.equals(root.path("repairKind").asText())
						&& table.equals(root.path("table").asText()) && alias.equals(root.path("alias").asText());
			}
			catch (Exception invalid) {
				return false;
			}
		}
	}

	/** Nothing outside a single unambiguous physical table is automatically rewritten. */
	public Optional<Diagnosis> diagnose(String sql, DataQueryMetadataRepository.DatasetInventory inventory) {
		try {
			var guard = new DataQuerySqlGuard();
			var before = guard.prepare(sql, inventory.tables());
			var statements = SQLUtils.parseStatements(sql, DbType.mysql);
			var statement = (SQLSelectStatement) statements.get(0);
			var select = statement.getSelect();
			if (select.getWithSubQuery() != null || !(select.getQuery() instanceof MySqlSelectQueryBlock block)
					|| !(block.getFrom() instanceof SQLExprTableSource source))
				return Optional.empty();
			String table;
			if (source.getExpr() instanceof SQLIdentifierExpr name)
				table = clean(name.getName());
			else if (source.getExpr() instanceof SQLPropertyExpr name)
				table = clean(name.getName());
			else
				return Optional.empty();
			String alias = clean(source.getAlias());
			if (table == null || alias == null || table.equalsIgnoreCase(alias))
				return Optional.empty();
			Set<String> fields = new HashSet<>();
			for (var entry : JSON.readTree(inventory.schema())) {
				if (table.equals(entry.path("table_name").asText())) {
					for (var field : entry.path("fields"))
						fields.add(field.path("field_name").asText().toLowerCase(Locale.ROOT));
				}
			}
			if (fields.isEmpty())
				return Optional.empty();
			int[] changed = { 0 };
			int[] selects = { 0 };
			statement.accept(new MySqlASTVisitorAdapter() {
				@Override
				public boolean visit(SQLSelect node) {
					if (++selects[0] != 1)
						throw new IllegalArgumentException();
					return true;
				}

				@Override
				public boolean visit(SQLPropertyExpr expr) {
					if (expr.getParent() instanceof SQLExprTableSource)
						return false;
					if (!(expr.getOwner() instanceof SQLIdentifierExpr owner))
						throw new IllegalArgumentException();
					String qualifier = clean(owner.getName());
					if (table.equals(qualifier)) {
						String field = clean(expr.getName());
						if (field == null || !fields.contains(field.toLowerCase(Locale.ROOT)))
							throw new IllegalArgumentException();
						expr.setOwner(new SQLIdentifierExpr(alias));
						changed[0]++;
					}
					else if (!alias.equals(qualifier))
						throw new IllegalArgumentException();
					return false;
				}
			});
			if (changed[0] == 0)
				return Optional.empty();
			String corrected = SQLUtils.toSQLString(statement, DbType.mysql);
			var after = guard.prepare(corrected, inventory.tables());
			if (!before.parameters().equals(after.parameters()) || !before.tables().equals(after.tables()))
				return Optional.empty();
			return Optional.of(new Diagnosis(table, alias, corrected,
					DataQueryMetadataRepository.fingerprint(VERSION, before.sql(), after.sql(), inventory.digest()),
					changed[0]));
		}
		catch (Exception unsupported) {
			return Optional.empty();
		}
	}

	public static boolean suggestedByAdvisor(Advisor advisor, Diagnosis diagnosis) {
		return suggestedByAdvisor(advisor, diagnosis, 15000);
	}

	static boolean suggestedByAdvisor(Advisor advisor, Diagnosis diagnosis, long timeoutMillis) {
		if (advisor == null)
			return false;
		Future<String> task = null;
		try {
			task = ADVISORS.submit(() -> advisor.suggest(diagnosis.facts()));
			return diagnosis.accepts(task.get(timeoutMillis, TimeUnit.MILLISECONDS));
		}
		catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return false;
		}
		catch (Exception unavailable) {
			return false;
		}
		finally {
			if (task != null && !task.isDone())
				task.cancel(true);
		}
	}

	private static String clean(String value) {
		if (value == null)
			return null;
		if (value.startsWith("`") && value.endsWith("`"))
			value = value.substring(1, value.length() - 1);
		return value.matches("[A-Za-z_][A-Za-z0-9_]{0,63}") ? value : null;
	}

}
