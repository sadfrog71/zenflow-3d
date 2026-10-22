package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidPooledConnection;
import com.deepdimen.matrix.knowledge.dto.AiMessageResultDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.StringReader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real H2 audit/evidence storage; a counted JDBC double never connects to a server. */
class DataQueryExecutionTracePersistenceTest {

	private static final String SQL = "SELECT COUNT(*) AS n FROM dev_device";

	private static final String WITHHELD = "withheld-trace-result";

	DataQueryAiChatTest f;

	DataQueryAiChatService chat;

	PreparedStatement statement;

	AtomicInteger jdbcCalls;

	@BeforeEach
	void setup() throws Exception {
		f = new DataQueryAiChatTest();
		f.setup();
		try (var connection = f.source.getConnection()) {
			org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
					new org.springframework.core.io.FileSystemResource("../docs/dataquery/investigation-schema.sql"));
		}
		f.metadata.investigations(
				new DataQueryQueryEvidence(f.source, f.json, f.clock, Base64.getEncoder().encodeToString(new byte[32])),
				null);
		var pool = mock(DruidDataSource.class);
		var connection = mock(DruidPooledConnection.class);
		statement = mock(PreparedStatement.class);
		jdbcCalls = new AtomicInteger();
		when(pool.getConnection()).thenReturn(connection);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		chat = new DataQueryAiChatService(f.metadata, new DataQuerySqlExecutor(f.metadata, credentials -> pool));
	}

	@AfterEach
	void cleanup() {
		f.cleanup();
	}

	@Test
	void failedStartRollsBackTheStepAndPreservesThePersistenceException() {
		var trace = new DataQueryExecutionTrace();
		var error = new DataQueryException("AUDIT_UNAVAILABLE");
		var rejectOnce = new AtomicBoolean(true);
		trace.attach(snapshot -> {
			if (hasStep(snapshot, "JDBC_QUERY", "RUNNING") && rejectOnce.getAndSet(false))
				throw error;
		});
		assertThatThrownBy(() -> trace.start("JDBC_QUERY", 1, "a".repeat(64))).isSameAs(error);
		assertThat(trace.snapshot().steps()).isEmpty();
		trace.failOpen("QUERY_FAILED");
		assertThat(trace.snapshot().steps()).hasSize(1);
		assertThat(trace.snapshot().steps().get(0)).satisfies(step -> {
			assertThat(step.sequence()).isEqualTo(1);
			assertThat(step.phase()).isEqualTo("QUERY_FAILURE");
			assertThat(step.status()).isEqualTo("FAILED");
		});
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void jdbcStartWriteFailureNeverClaimsInvocationOrRetries(boolean drilldown) throws Exception {
		var context = context(drilldown);
		rows(drilldown);
		var rejected = new AtomicBoolean();
		traceSink(context, snapshot -> {
			if (hasStep(snapshot, "JDBC_QUERY", "RUNNING") && rejected.compareAndSet(false, true))
				throw new DataQueryException("AUDIT_UNAVAILABLE");
		});
		var reply = execute(context, drilldown);
		assertThat(rejected).isTrue();
		assertFailureStored(context, reply, "AUDIT_UNAVAILABLE");
		assertThat(jdbcCalls).hasValue(0);
		verify(statement, never()).executeQuery();
		verify(statement).close();
		var detail = new DataQueryExecutionDetails(f.metadata, f.json).read(f.owner, context.queryId());
		assertThat(detail.sql().submission()).isEqualTo("PREPARED_ONLY");
		assertThat(detail.steps()).noneMatch(step -> "JDBC_QUERY".equals(step.phase()));
		assertThat(detail.steps()).noneMatch(step -> "RUNNING".equals(step.status()));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void packagingAndTerminalTraceWriteFailuresStillReplaceTheSuccessfulOutcome(boolean drilldown) throws Exception {
		var context = context(drilldown);
		rows(drilldown);
		var rejected = new AtomicInteger();
		traceSink(context, snapshot -> {
			if (hasStep(snapshot, "PACKAGE_RESULT", "SUCCEEDED")) {
				if (rejected.getAndIncrement() == 0) {
					assertThat(f.metadata.findOwnedQuery(f.owner, context.queryId()).orElseThrow().resultCode())
						.isEqualTo("OK");
					assertThat(answer(context)).contains(WITHHELD);
					assertThat(
							f.metadata.storedExplanation(f.owner, context.queryId()).path("executionStatus").asText())
						.isEqualTo("SUCCEEDED");
				}
				throw new DataQueryException("AUDIT_UNAVAILABLE");
			}
		});
		var reply = execute(context, drilldown);
		assertThat(rejected).hasValue(2);
		assertFailureStored(context, reply, "AUDIT_UNAVAILABLE");
		assertThat(jdbcCalls).hasValue(1);
		verify(statement).executeQuery();
		verify(statement).close();
		var detail = new DataQueryExecutionDetails(f.metadata, f.json).read(f.owner, context.queryId());
		assertThat(detail.resultCode()).isEqualTo(reply.getDataQuery().get("code"));
		assertThat(detail.sql().submission()).isEqualTo("JDBC_INVOKED");
	}

	@Test
	void terminalTraceFailureDoesNotReplaceTheOriginalBusinessFailureCode() throws Exception {
		var context = context(false);
		rejectTerminalTrace(context);
		var reply = chat.fail(context, "QUERY_TIMEOUT");
		assertFailureStored(context, reply, "QUERY_TIMEOUT");
		assertThat(jdbcCalls).hasValue(0);
		verify(statement, never()).executeQuery();
	}

	@ParameterizedTest
	@ValueSource(strings = { "query", "explanation", "answer" })
	void primaryAuditFailuresAreStillReportedWhenTerminalTraceAlsoFails(String operation) throws Exception {
		var context = context(false);
		rejectTerminalTrace(context);
		var jdbc = spy((org.springframework.jdbc.core.JdbcTemplate) org.springframework.test.util.ReflectionTestUtils
			.getField(f.metadata, "jdbc"));
		org.springframework.test.util.ReflectionTestUtils.setField(f.metadata, "jdbc", jdbc);
		String update = switch (operation) {
			case "query" -> "UPDATE ai_dataquery_query SET status='FINISHED',result_code=?";
			case "explanation" ->
				"UPDATE ai_dataquery_query SET candidate_json=? WHERE query_id=? AND tenant_id=? AND user_id=? AND dataset_key=?";
			case "answer" -> "UPDATE ai_chat_record SET answer_text=?";
			default -> throw new AssertionError(operation);
		};
		doThrow(new IllegalStateException("synthetic-primary-audit-failure")).when(jdbc)
			.update(startsWith(update), any(), any(), any(), any(), any());
		var reply = chat.fail(context, "QUERY_TIMEOUT");
		assertThat(reply.getDataQuery()).containsEntry("status", "ERROR").containsEntry("code", "AUDIT_UNAVAILABLE");
		assertThat(reply.getDataQuery()).doesNotContainKeys("rows", "columns");
		verify(jdbc).update(startsWith(update), any(), any(), any(), any(), any());
		assertThat(jdbcCalls).hasValue(0);
	}

	private DataQueryAiChatService.Context context(boolean drilldown) {
		if (drilldown) {
			var prior = f.service.begin(f.owner, "reader", f.message(1900, "synthetic ranking"));
			assertThat(f.service.execute(prior, SQL).getDataQuery()).containsEntry("status", "SUCCEEDED");
			var group = new DataQueryDrilldownTest().ranking();
			f.metadata.recordEvidence(prior.queryId(), f.owner, "department", new DataQueryDepartmentDimension.Ranking(
					group.captured(), group.first(), prior.queryId(), group.status()));
		}
		return chat.begin(f.owner, "reader", f.message(1901, drilldown ? "看刚才第一名的明细" : "synthetic count"));
	}

	private void rows(boolean drilldown) throws Exception {
		var columns = drilldown
				? List.of("record_id", "record_name", "start_date", "company_id", "patrol_dept_id", "patrol_dept_name")
				: List.of("n");
		var values = drilldown ? List.of("1", WITHHELD, "2026-08-01 00:00:00", "C", "D", "Same name")
				: List.of(WITHHELD);
		var result = mock(ResultSet.class);
		var metadata = mock(ResultSetMetaData.class);
		when(statement.executeQuery()).thenAnswer(invocation -> {
			jdbcCalls.incrementAndGet();
			return result;
		});
		when(result.getMetaData()).thenReturn(metadata);
		when(result.next()).thenReturn(true, false);
		when(metadata.getColumnCount()).thenReturn(columns.size());
		for (int i = 0; i < columns.size(); i++) {
			String value = values.get(i);
			when(metadata.getColumnLabel(i + 1)).thenReturn(columns.get(i));
			when(metadata.getColumnType(i + 1)).thenReturn(Types.VARCHAR);
			when(result.getCharacterStream(i + 1)).thenAnswer(invocation -> new StringReader(value));
		}
	}

	private AiMessageResultDTO execute(DataQueryAiChatService.Context context, boolean drilldown) {
		return drilldown ? chat.tryDrilldown(context).orElseThrow() : chat.execute(context, SQL);
	}

	private void traceSink(DataQueryAiChatService.Context context,
			Consumer<DataQueryExecutionTrace.Snapshot> beforeSave) {
		context.trace().attach(snapshot -> {
			beforeSave.accept(snapshot);
			f.metadata.recordEvidence(context.queryId(), f.owner, "executionTrace", snapshot);
		});
	}

	private void rejectTerminalTrace(DataQueryAiChatService.Context context) {
		traceSink(context, snapshot -> {
			if (hasStep(snapshot, "QUERY_FAILURE", "RUNNING"))
				throw new DataQueryException("AUDIT_UNAVAILABLE");
		});
	}

	private static boolean hasStep(DataQueryExecutionTrace.Snapshot snapshot, String phase, String status) {
		return snapshot.steps().stream().anyMatch(step -> phase.equals(step.phase()) && status.equals(step.status()));
	}

	private String answer(DataQueryAiChatService.Context context) {
		return f.jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=?", String.class,
				context.messageId());
	}

	private void assertFailureStored(DataQueryAiChatService.Context context, AiMessageResultDTO reply, String code)
			throws Exception {
		assertThat(reply.getDataQuery()).containsEntry("status", "ERROR").containsEntry("code", code);
		assertThat(reply.getDataQuery()).doesNotContainKeys("rows", "columns");
		assertThat(reply.getMessage()).doesNotContain(WITHHELD);
		var query = f.metadata.findOwnedQuery(f.owner, context.queryId()).orElseThrow();
		assertThat(query.status()).isEqualTo("FINISHED");
		assertThat(query.resultCode()).isEqualTo(code);
		assertThat(query.groupCount()).isZero();
		assertThat(answer(context)).isEqualTo(reply.getMessage()).doesNotContain(WITHHELD);
		var explanation = f.metadata.storedExplanation(f.owner, context.queryId());
		assertThat(explanation.path("executionStatus").asText()).isEqualTo("ERROR");
		var returnedExplanation = f.json.valueToTree(reply.getDataQuery().get("explanation"));
		assertThat(returnedExplanation).isEqualTo(explanation);
	}

}
