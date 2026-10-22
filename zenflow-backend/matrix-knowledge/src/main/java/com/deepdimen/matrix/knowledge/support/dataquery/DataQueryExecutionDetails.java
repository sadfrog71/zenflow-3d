package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.*;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

/**
 * Read-only query evidence. Viewing never prepares, executes or restores an old query.
 */
public final class DataQueryExecutionDetails {

	public record SqlEvidence(String status, String sql, List<String> parameterTypes, String digest, String submission,
			Integer attempt, List<BoundParameter> parameters) {
		SqlEvidence(String status, String sql, List<String> parameterTypes, String digest, String submission,
				Integer attempt) {
			this(status, sql, parameterTypes, digest, submission, attempt, List.of());
		}

		@Override
		public String toString() {
			return "SqlEvidence[REDACTED]";
		}
	}

	/**
	 * Values are returned only through the owned, reauthorized, no-store detail endpoint.
	 */
	public record BoundParameter(int position, String type, String value) {
		@Override
		public String toString() {
			return "BoundParameter[REDACTED]";
		}
	}

	private static List<BoundParameter> bindings(DataQuerySqlGuard.Prepared query) {
		var result = new ArrayList<BoundParameter>();
		for (int i = 0; i < query.parameters().size(); i++) {
			Object value = query.parameters().get(i);
			result.add(new BoundParameter(i + 1, type(value), value == null ? null : value.toString()));
		}
		return List.copyOf(result);
	}

	public record Detail(String queryId, String dataId, String status, String resultCode, String ruleVersion,
			String executionVersion, String traceStatus, List<DataQueryExecutionTrace.Step> steps, SqlEvidence sql,
			String notice, Timing timing) {
		@Override
		public String toString() {
			return "ExecutionDetail[REDACTED]";
		}
	}

	public record Timing(String basis, String status, Integer durationMs) {
	}

	private static Timing timing(DataQueryMetadataRepository.QueryRecord row) {
		if (row.startedAt() == null)
			return new Timing("QUERY_AUDIT", "UNAVAILABLE", null);
		if (row.finishedAt() == null)
			return new Timing("QUERY_AUDIT", "STARTED".equals(row.status()) ? "IN_PROGRESS" : "UNAVAILABLE", null);
		long millis = java.time.Duration.between(row.startedAt(), row.finishedAt()).toMillis();
		return millis < 0 || millis > Integer.MAX_VALUE ? new Timing("QUERY_AUDIT", "UNAVAILABLE", null)
				: new Timing("QUERY_AUDIT", "RECORDED", (int) millis);
	}

	private final DataQueryMetadataRepository metadata;

	private final ObjectMapper json;

	public DataQueryExecutionDetails(DataQueryMetadataRepository metadata, ObjectMapper json) {
		this.metadata = metadata;
		this.json = json;
	}

	public Detail read(Identity identity, String queryId) {
		if (queryId == null || !queryId.matches("[0-9a-f-]{36}"))
			throw new DataQueryException("QUERY_NOT_FOUND");
		var row = metadata.findOwnedQuery(identity, queryId)
			.orElseThrow(() -> new DataQueryException("QUERY_NOT_FOUND"));
		var scope = metadata.resolve(identity);
		var inventory = metadata.inventory(identity, scope.source());
		try {
			var candidate = json.readTree(row.candidateJson());
			if (!row.scopeRevision().equals(scope.revision()))
				throw new DataQueryException("AUTHORIZATION_CHANGED");
			if (!"AI_SQL".equals(candidate.path("kind").asText())) {
				return new Detail(queryId, Long.toString(scope.source().dataId()), row.status(), row.resultCode(),
						row.ruleVersion(), null, "NOT_RECORDED", List.of(),
						new SqlEvidence("NOT_RECORDED", null, List.of(), null, "UNKNOWN", null),
						"旧计划执行路径未记录本接口所需SQL和阶段证据，不重新查询或补造执行过程。", timing(row));
			}
			if (!Long.toString(scope.source().dataId()).equals(candidate.path("dataId").asText())
					|| !inventory.digest().equals(candidate.path("inventoryDigest").asText()))
				throw new DataQueryException("AUTHORIZATION_CHANGED");
			String traceStatus = "NOT_RECORDED";
			List<DataQueryExecutionTrace.Step> steps = List.of();
			if (candidate.has("executionTrace")) {
				var trace = json.treeToValue(candidate.get("executionTrace"), DataQueryExecutionTrace.Snapshot.class);
				if (!DataQueryExecutionTrace.VERSION.equals(trace.version()) || trace.steps() == null
						|| trace.steps().size() > 64)
					throw new DataQueryException("QUERY_DETAILS_UNAVAILABLE");
				steps = trace.steps();
				traceStatus = "RECORDED";
			}
			boolean assistance = Set.of("ASSISTANCE_COMPLETED", "ASSISTANCE_CLARIFICATION")
				.contains(row.resultCode() == null ? "" : row.resultCode()) && candidate.path("assistance").isObject();
			if (assistance
					&& (candidate.has("sqlDigest") || steps.stream().anyMatch(s -> "JDBC_QUERY".equals(s.phase()))))
				throw new DataQueryException("QUERY_DETAILS_UNAVAILABLE");
			SqlEvidence sql = new SqlEvidence(assistance ? "NOT_EXECUTED" : "NOT_RECORDED", null, List.of(), null,
					assistance ? "NOT_APPLICABLE" : "UNKNOWN", null);
			if (candidate.has("sqlDigest")) {
				try {
					var snapshot = metadata.exampleSource(identity, queryId);
					String digest = DataQueryMetadataRepository.fingerprint(snapshot.query().sql());
					if (!snapshot.authorization().equals(scope)
							|| !snapshot.inventoryDigest().equals(inventory.digest())
							|| !candidate.path("sqlDigest").asText().equals(digest)
							|| !Objects.equals(snapshot.ruleVersion(), row.ruleVersion()) || !Objects
								.equals(snapshot.executionVersion(), candidate.path("executionVersion").asText()))
						throw new DataQueryException("QUERY_DETAILS_UNAVAILABLE");
					int attempt = steps.stream().mapToInt(DataQueryExecutionTrace.Step::attempt).max().orElse(0);
					var invocation = steps.stream()
						.filter(s -> s.attempt() == attempt && "JDBC_QUERY".equals(s.phase())
								&& digest.equals(s.sqlDigest()))
						.findFirst();
					String submission = invocation
						.map(s -> "RUNNING".equals(s.status()) ? "INVOKING_UNCONFIRMED" : "JDBC_INVOKED")
						.orElse("RECORDED".equals(traceStatus) ? "PREPARED_ONLY" : "UNKNOWN_LEGACY");
					boolean displayable = valuesHidden(snapshot.query().sql());
					sql = new SqlEvidence(displayable ? "AVAILABLE" : "WITHHELD_UNBOUND_LITERALS",
							displayable ? snapshot.query().sql() : null,
							snapshot.query().parameters().stream().map(DataQueryExecutionDetails::type).toList(),
							digest, submission, attempt == 0 ? null : attempt,
							displayable ? bindings(snapshot.query()) : List.of());
				}
				catch (DataQueryException unavailable) {
					if (!Set.of("SQL_EXAMPLE_SEED_UNAVAILABLE", "INVESTIGATION_EVIDENCE_UNAVAILABLE")
						.contains(unavailable.getMessage()))
						throw unavailable;
					sql = new SqlEvidence("EXPIRED_OR_UNAVAILABLE", null, List.of(), null, "UNKNOWN", null);
				}
			}
			// Recheck after decryption; a query handle is never an access grant.
			if (!scope.equals(metadata.resolve(identity))
					|| !inventory.equals(metadata.inventory(identity, scope.source())))
				throw new DataQueryException("AUTHORIZATION_CHANGED");
			return new Detail(queryId, Long.toString(scope.source().dataId()), row.status(), row.resultCode(),
					row.ruleVersion(), candidate.path("executionVersion").asText(null), traceStatus, steps, sql,
					(assistance ? "本轮为解释或澄清，未执行数据查询，未生成图表或应用SQL修正。" : "")
							+ "只读历史证据，不重新查询。仅保留最后一次准备的参数化SQL及真实绑定值；参数按问号出现顺序对应，前次尝试仅有摘要。JDBC已调用不等于业务口径正确；旧记录不补造步骤，进行中断开的记录不能据此判定已完成。阶段记录不是模型思维链；异步图表生成和网络传输不在本面板计时范围内。Token仅来自已记录模型调用的响应，不含未回传用量的SDK内部重试，也不等于供应商完整账单。",
					timing(row));
		}
		catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException invalid) {
			throw new DataQueryException("QUERY_DETAILS_UNAVAILABLE");
		}
	}

	/**
	 * Existing encrypted evidence may contain literal kinds the query compiler did not
	 * bind. Withhold that original SQL rather than rewrite it and mislabel the rewrite as
	 * execution evidence. This display-only check never changes query admission.
	 */
	private static boolean valuesHidden(String sql) {
		try {
			if (sql == null || sql.length() > 20000)
				return false;
			var statements = SQLUtils.parseStatements(sql, DbType.mysql);
			if (statements.size() != 1 || !(statements.get(0) instanceof SQLSelectStatement))
				return false;
			statements.get(0).accept(new MySqlASTVisitorAdapter() {
				@Override
				public void preVisit(SQLObject node) {
					if (!(node instanceof SQLLiteralExpr) || node instanceof SQLNullExpr)
						return;
					var parent = node.getParent();
					// Ordinals, type sizes and pagination are SQL grammar, not bound
					// business values. No text, binary, date or general numeric literal
					// is exempted by its spelling or a parent-name substring.
					if (node instanceof SQLIntegerExpr
							&& ((parent instanceof SQLSelectOrderByItem order && order.getExpr() == node)
									|| (parent instanceof SQLSelectGroupByClause group
											&& group.getItems().stream().anyMatch(item -> item == node))
									|| (parent instanceof SQLDataType type
											&& type.getArguments().stream().anyMatch(item -> item == node))
									|| (parent instanceof SQLLimit limit
											&& (limit.getOffset() == node || limit.getRowCount() == node))))
						return;
					throw new IllegalArgumentException("UNBOUND_SQL_LITERAL");
				}
			});
			return true;
		}
		catch (RuntimeException unsafeOrUnrecognized) {
			return false;
		}
	}

	private static String type(Object value) {
		if (value == null)
			return "NULL";
		if (value instanceof Number)
			return "NUMBER";
		if (value instanceof Boolean)
			return "BOOLEAN";
		if (value instanceof java.time.temporal.TemporalAccessor || value instanceof java.util.Date)
			return "TEMPORAL";
		return "TEXT_OR_OTHER";
	}

}
