package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.pool.DruidDataSource;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Synthetic data, real repository/AEAD/JDBC. Does not claim live TOP business validation.
 */
class DataQueryExecutionDetailsTest {

	@Test
	void slowModelDoesNotBlockIncrementalSseDemand() throws Exception {
		var observed = new java.util.concurrent.CountDownLatch(1);
		when(f.model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class))).thenAnswer(call -> {
			if (!observed.await(2, java.util.concurrent.TimeUnit.SECONDS))
				throw new IllegalStateException("Progress stalled behind blocking model");
			return usageReply("SELECT COUNT(*) AS n FROM dev_device", new dev.langchain4j.model.output.TokenUsage(1, 1, 2));
		});
		var events = f.rule.processStreaming(f.message(9298, "设备数"))
			.publishOn(reactor.core.scheduler.Schedulers.parallel(), 1)
			.doOnNext(event -> {
				if (event.getQueryProgress() != null && "MODEL_GENERATE".equals(event.getQueryProgress().get("phase")))
					observed.countDown();
			}).collectList().block(java.time.Duration.ofSeconds(10));
		assertThat(observed.getCount()).isZero();
		assertThat(events.get(events.size()-1).getDataQuery()).containsEntry("status", "SUCCEEDED");
	}


	@Test
	void streamingEmitsStagesBeforeAnswerAndPreservesRequestIdentity() throws Exception {
		when(f.model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
			.thenReturn(usageReply("SELECT COUNT(*) AS n FROM dev_device", new dev.langchain4j.model.output.TokenUsage(100, 12, 112)));
		var callerTenant = com.deepdimen.matrix.common.data.tenant.TenantContextHolder.getTenantId();
		var events = f.rule.processStreaming(f.message(9299, "设备数")).collectList().block(java.time.Duration.ofSeconds(10));
		assertThat(events.get(0).getQueryProgress()).containsEntry("phase", "AUTHORIZE_DATASET");
		assertThat(events).anyMatch(e -> e.getQueryProgress() != null && "MODEL_GENERATE".equals(e.getQueryProgress().get("phase")));
		assertThat(events.get(events.size()-1).getDataQuery()).containsEntry("status", "SUCCEEDED");
		assertThat(com.deepdimen.matrix.common.data.tenant.TenantContextHolder.getTenantId()).isEqualTo(callerTenant);
	}


	DataQueryAiChatTest f;

	DataQueryAiChatService chat;

	DataQueryExecutionDetails details;

	AtomicInteger connections;

	List<DruidDataSource> pools;

	@BeforeEach
	void setup() throws Exception {
		f = new DataQueryAiChatTest();
		f.setup();
		try (var c = f.source.getConnection()) {
			org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,
					new org.springframework.core.io.FileSystemResource("../docs/dataquery/investigation-schema.sql"));
		}
		f.metadata.investigations(
				new DataQueryQueryEvidence(f.source, f.json, f.clock, Base64.getEncoder().encodeToString(new byte[32])),
				null);
		connections = new AtomicInteger();
		pools = new ArrayList<>();
		chat = new DataQueryAiChatService(f.metadata, new DataQuerySqlExecutor(f.metadata, credentials -> {
			connections.incrementAndGet();
			var pool = new DruidDataSource();
			pool.setUrl(f.source.getUrl());
			pool.setUsername("sa");
			pool.setPassword("");
			pool.setInitialSize(0);
			pool.setMaxActive(1);
			pool.setMinIdle(0);
			pool.setMaxWait(1000);
			pools.add(pool);
			return pool;
		}));
		details = new DataQueryExecutionDetails(f.metadata, f.json);
	}

	@AfterEach
	void close() {
		pools.forEach(DruidDataSource::close);
		f.cleanup();
	}

	DataQueryAiChatService.Context context(long id) {
		return chat.begin(f.owner, "reader", f.message(id, "合成设备查询"));
	}

	void fails(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOf(DataQueryException.class).hasMessage(code);
	}

	String successful(long id) {
		var c = context(id);
		assertThat(chat.execute(c, "SELECT COUNT(*) AS n FROM dev_device WHERE company_id='private-filter'")
			.getDataQuery()).containsEntry("status", "SUCCEEDED");
		return c.queryId();
	}

	@Test
	void actualSqlAndBindingsAreOwnedReauthorizedAndReadWithoutExecution() throws Exception {
		String id = successful(980);
		int reads = connections.get();
		int audits = f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_query", Integer.class);
		var result = details.read(f.owner, id);
		assertThat(result.sql().status()).isEqualTo("AVAILABLE");
		assertThat(result.sql().submission()).isEqualTo("JDBC_INVOKED");
		assertThat(result.sql().sql()).contains("?").doesNotContain("private-filter");
		assertThat(result.sql().parameterTypes()).containsExactly("TEXT_OR_OTHER");
		assertThat(result.steps()).extracting(DataQueryExecutionTrace.Step::phase)
			.containsSubsequence("AUTHORIZE_DATASET", "LOAD_METADATA", "MATCH_REFERENCES", "VALIDATE_AND_REWRITE_SQL",
					"EXECUTE_QUERY", "ACQUIRE_CONNECTION", "BIND_STATEMENT", "JDBC_QUERY", "READ_RESULT",
					"PACKAGE_RESULT");
		assertThat(result.steps()).allSatisfy(step -> {
			assertThat(step.status()).isEqualTo("SUCCEEDED");
			assertThat(step.durationMs()).isNotNegative();
		});
		assertThat(result.sql().parameters())
			.containsExactly(new DataQueryExecutionDetails.BoundParameter(1, "TEXT_OR_OTHER", "private-filter"));
		assertThat(f.json.writeValueAsString(result)).contains("private-filter")
			.doesNotContain("synthetic-only", f.url);
		assertThat(result.toString()).doesNotContain("private-filter");
		assertThat(result.sql().parameters().get(0).toString()).doesNotContain("private-filter");
		details.read(f.owner, id);
		assertThat(connections.get()).isEqualTo(reads);
		assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_query", Integer.class)).isEqualTo(audits);
		assertThat(pools).allMatch(DruidDataSource::isClosed);
	}

	@Test
	void bindingValuesPreserveOrderEmptyStringsLargeNumbersAndAreAbsentWhenEvidenceExpires() throws Exception {
		var c = context(986);
		var prepared = new DataQuerySqlGuard().prepare(
				"SELECT company_id FROM dev_device WHERE company_id != '' AND company_id != ' ' AND company_id != '<script>alert(1)</script>' LIMIT 123456789012345678",
				Set.of("dev_device"));
		f.metadata.recordSqlDigest(c.queryId(), f.owner, prepared);
		f.metadata.captureQueryEvidence(c, prepared);
		var result = details.read(f.owner, c.queryId());
		assertThat(result.sql().parameters()).extracting(DataQueryExecutionDetails.BoundParameter::position)
			.containsExactly(1, 2, 3, 4);
		assertThat(result.sql().parameters()).extracting(DataQueryExecutionDetails.BoundParameter::value)
			.containsExactly("", " ", "<script>alert(1)</script>", "123456789012345678");
		assertThat(result.sql().sql()).doesNotContain("alert", "123456789012345678");
		fails("QUERY_NOT_FOUND", () -> details.read(new Identity("1", "3"), c.queryId()));
		f.jdbc.update("UPDATE ai_dataquery_query_evidence SET expires_at=? WHERE query_id=?",
				java.time.LocalDateTime.of(2000, 1, 1, 0, 0), c.queryId());
		assertThat(details.read(f.owner, c.queryId()).sql().parameters()).isEmpty();
		assertThat(connections).hasValue(0);
	}

	@Test
	void nationalAndHexValuesFromTheExistingCompilerAreWithheldAtTheDisplayBoundary() throws Exception {
		long message = 1100;
		for (String literal : List.of("N'private-filter'", "0x707269766174652D66696C746572")) {
			var c = context(message++);
			var prepared = new DataQuerySqlGuard()
				.prepare("SELECT company_id FROM dev_device WHERE company_id = " + literal, Set.of("dev_device"));
			assertThat(prepared.parameters()).isEmpty();
			f.metadata.recordSqlDigest(c.queryId(), f.owner, prepared);
			f.metadata.captureQueryEvidence(c, prepared);
			var result = details.read(f.owner, c.queryId());
			assertThat(result.sql().status()).isEqualTo("WITHHELD_UNBOUND_LITERALS");
			assertThat(result.sql().sql()).isNull();
			assertThat(result.sql().submission()).isEqualTo("PREPARED_ONLY");
			assertThat(result.sql().digest()).isEqualTo(DataQueryMetadataRepository.fingerprint(prepared.sql()));
			assertThat(result.steps()).isNotEmpty();
			assertThat(f.json.writeValueAsString(result)).doesNotContain("private-filter", "707269766174652");
		}
		assertThat(connections).hasValue(0);
	}

	@Test
	void otherUnboundHistoricalLiteralsAreNotDisplayedOrRewritten() throws Exception {
		long message = 1200;
		for (String literal : List.of("'private-filter'", "_utf8mb4'private-filter'", "X'70726976617465'",
				"B'01011011'", "DATE '2026-08-03'", "TIMESTAMP '2026-08-03 01:02:03'", "9876543210", "true")) {
			var c = context(message++);
			// Synthetic historical evidence, not a claim that each literal is currently
			// admitted by the query guard or has been executed against a source.
			var prepared = new DataQuerySqlGuard.Prepared(
					"SELECT company_id FROM dev_device WHERE company_id = " + literal, List.of(), Set.of("dev_device"));
			f.metadata.recordSqlDigest(c.queryId(), f.owner, prepared);
			f.metadata.captureQueryEvidence(c, prepared);
			String encrypted = f.jdbc.queryForObject(
					"SELECT encrypted_snapshot FROM ai_dataquery_query_evidence WHERE query_id=?", String.class,
					c.queryId());
			int audits = f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_query", Integer.class);
			var result = details.read(f.owner, c.queryId());
			assertThat(result.sql().status()).as(literal).isEqualTo("WITHHELD_UNBOUND_LITERALS");
			assertThat(result.sql().sql()).isNull();
			assertThat(f.json.writeValueAsString(result)).doesNotContain(literal, "private-filter");
			assertThat(f.metadata.exampleSource(f.owner, c.queryId()).query().sql()).isEqualTo(prepared.sql());
			assertThat(
					f.jdbc.queryForObject("SELECT encrypted_snapshot FROM ai_dataquery_query_evidence WHERE query_id=?",
							String.class, c.queryId()))
				.isEqualTo(encrypted);
			assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_query", Integer.class))
				.isEqualTo(audits);
		}
		assertThat(connections).hasValue(0);
	}

	@Test
	void aHavingValueIsNotExemptedAsAGroupByOrdinal() throws Exception {
		var c = context(1400);
		var prepared = new DataQuerySqlGuard()
			.prepare("SELECT COUNT(*) FROM dev_device GROUP BY company_id HAVING 9876543210", Set.of("dev_device"));
		assertThat(prepared.sql()).contains("9876543210");
		f.metadata.recordSqlDigest(c.queryId(), f.owner, prepared);
		f.metadata.captureQueryEvidence(c, prepared);
		var result = details.read(f.owner, c.queryId());
		assertThat(result.sql().status()).isEqualTo("WITHHELD_UNBOUND_LITERALS");
		assertThat(result.sql().sql()).isNull();
		assertThat(f.json.writeValueAsString(result)).doesNotContain("9876543210");
		assertThat(connections).hasValue(0);
	}

	@Test
	void structuralSqlIntegersAreNotConfusedWithUnboundBusinessValues() {
		var c = context(1300);
		var prepared = new DataQuerySqlGuard.Prepared(
				"SELECT CAST(company_id AS CHAR(32)) FROM dev_device WHERE company_id = ? GROUP BY 1 ORDER BY 1 LIMIT 5",
				List.of("private-filter"), Set.of("dev_device"));
		f.metadata.recordSqlDigest(c.queryId(), f.owner, prepared);
		f.metadata.captureQueryEvidence(c, prepared);
		var result = details.read(f.owner, c.queryId());
		assertThat(result.sql().status()).isEqualTo("AVAILABLE");
		assertThat(result.sql().sql()).isEqualTo(prepared.sql());
		assertThat(result.sql().parameterTypes()).containsExactly("TEXT_OR_OTHER");
		assertThat(connections).hasValue(0);
	}

	@Test
	void missingIdentityWrongOwnerAndTenantCannotReadSql() {
		String id = successful(981);
		fails("QUERY_NOT_FOUND", () -> details.read(new Identity("1", "3"), id));
		fails("QUERY_NOT_FOUND", () -> details.read(new Identity("2", "2"), id));
		f.login(1, 3, "editor");
		var controller = new DataQueryExecutionController(f.metadata, f.json);
		fails("QUERY_NOT_FOUND", () -> controller.execution(id));
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		fails("UNAUTHENTICATED", () -> controller.execution(id));
	}

	@Test
	void currentRevocationOrSourceChangesDenyOldDetails() {
		String id = successful(982);
		f.jdbc.update("UPDATE ai_dataquery_access SET targets_json='[]'");
		fails("DATASET_NOT_AUTHORIZED", () -> details.read(f.owner, id));
		f.jdbc.update(
				"UPDATE ai_dataquery_access SET targets_json='[{\"type\":\"user\",\"targetId\":\"2\"}]',access_revision='changed'");
		fails("AUTHORIZATION_CHANGED", () -> details.read(f.owner, id));
		f.jdbc.update("UPDATE ai_datasource SET url='jdbc:mysql://changed.invalid/top_device_biz'");
		fails("DATASOURCE_BINDING_CHANGED", () -> details.read(f.owner, id));
	}

	@Test
	void connectionFailureDoesNotPretendSqlWasExecuted() {
		var blocked = new DataQueryAiChatService(f.metadata, new DataQuerySqlExecutor(f.metadata, creds -> {
			throw new DataQueryException("DATASOURCE_RESOURCE_LIMIT");
		}));
		var c = context(983);
		assertThat(blocked.execute(c, "SELECT COUNT(*) FROM dev_device").getDataQuery()).containsEntry("code",
				"DATASOURCE_RESOURCE_LIMIT");
		var result = details.read(f.owner, c.queryId());
		assertThat(result.sql().submission()).isEqualTo("PREPARED_ONLY");
		assertThat(result.steps()).noneMatch(step -> "JDBC_QUERY".equals(step.phase()));
		assertThat(result.steps())
			.anyMatch(step -> "ACQUIRE_CONNECTION".equals(step.phase()) && "FAILED".equals(step.status()));
	}

	@Test
	void sqlRejectedBeforePreparationHasNoSqlOrDatabaseStep() {
		var c = context(984);
		chat.execute(c, "DELETE FROM dev_device");
		var result = details.read(f.owner, c.queryId());
		assertThat(result.sql().status()).isEqualTo("NOT_RECORDED");
		assertThat(result.steps())
			.anyMatch(s -> s.phase().equals("VALIDATE_AND_REWRITE_SQL") && s.status().equals("FAILED"));
		assertThat(result.steps()).noneMatch(s -> s.phase().equals("EXECUTE_QUERY"));
		assertThat(connections).hasValue(0);
	}

	@Test
	void expiredEvidenceNeverReexecutesAndLegacyTraceIsNotInvented() throws Exception {
		String id = successful(985);
		var candidate = (com.fasterxml.jackson.databind.node.ObjectNode) f.json
			.readTree(f.metadata.findOwnedQuery(f.owner, id).orElseThrow().candidateJson());
		candidate.remove("executionTrace");
		f.jdbc.update("UPDATE ai_dataquery_query SET candidate_json=? WHERE query_id=?",
				f.json.writeValueAsString(candidate), id);
		var legacy = details.read(f.owner, id);
		assertThat(legacy.traceStatus()).isEqualTo("NOT_RECORDED");
		assertThat(legacy.steps()).isEmpty();
		assertThat(legacy.sql().submission()).isEqualTo("UNKNOWN_LEGACY");
		f.jdbc.update("UPDATE ai_dataquery_query_evidence SET expires_at='2026-01-01 00:00:00'");
		assertThat(details.read(f.owner, id).sql().status()).isEqualTo("EXPIRED_OR_UNAVAILABLE");
		assertThat(details.read(f.owner, id).sql().sql()).isNull();
		assertThat(connections).hasValue(1);
	}

	@Test
	void mismatchedDigestOrSelectedMetadataWithholdsSql() throws Exception {
		String id = successful(986);
		var candidate = (com.fasterxml.jackson.databind.node.ObjectNode) f.json
			.readTree(f.metadata.findOwnedQuery(f.owner, id).orElseThrow().candidateJson());
		candidate.put("sqlDigest", "0".repeat(64));
		f.jdbc.update("UPDATE ai_dataquery_query SET candidate_json=? WHERE query_id=?", candidate.toString(), id);
		fails("QUERY_DETAILS_UNAVAILABLE", () -> details.read(f.owner, id));
		f.jdbc.update("UPDATE ai_data_field SET virtual_comment='changed'");
		fails("AUTHORIZATION_CHANGED", () -> details.read(f.owner, id));
	}

	@Test
	void jdbcInvocationFailureRecordsOnlySafeErrorCode() throws Exception {
		var connection = mock(Connection.class);
		var statement = mock(PreparedStatement.class);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(statement.executeQuery()).thenThrow(new SQLException("driver-secret-do-not-store", "42000", 1064));
		var trace = new DataQueryExecutionTrace();
		var sql = new DataQuerySqlGuard().prepare("SELECT COUNT(*) FROM dev_device", Set.of("dev_device"));
		assertThatThrownBy(() -> DataQuerySqlExecutor.read(connection, sql, 5, trace, 1))
			.isInstanceOf(SQLException.class);
		assertThat(trace.snapshot().steps()).anyMatch(
				s -> s.phase().equals("JDBC_QUERY") && s.status().equals("FAILED") && s.code().equals("QUERY_FAILED"));
		assertThat(f.json.writeValueAsString(trace.snapshot())).doesNotContain("driver-secret-do-not-store", "SELECT");
	}

	@Test
	void jdbcTimeoutUsesTheSameSafeCodeAsTheQueryOutcome() throws Exception {
		var connection = mock(Connection.class);
		var statement = mock(PreparedStatement.class);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		var timeout = new SQLTimeoutException("timeout-driver-secret", "HYT00", 0);
		when(statement.executeQuery()).thenThrow(timeout);
		var trace = new DataQueryExecutionTrace();
		var sql = new DataQuerySqlGuard().prepare("SELECT COUNT(*) FROM dev_device", Set.of("dev_device"));
		assertThatThrownBy(() -> DataQuerySqlExecutor.read(connection, sql, 5, trace, 1)).isSameAs(timeout);
		assertThat(trace.snapshot().steps()).anyMatch(
				s -> s.phase().equals("JDBC_QUERY") && s.status().equals("FAILED") && s.code().equals("QUERY_TIMEOUT"));
		assertThat(trace.snapshot().steps()).noneMatch(s -> s.phase().equals("READ_RESULT"));
		assertThat(DataQuerySqlExecutor.failure(timeout).getMessage()).isEqualTo("QUERY_TIMEOUT");
		assertThat(f.json.writeValueAsString(trace.snapshot())).doesNotContain("timeout-driver-secret");
		verify(statement).close();
	}

	@Test
	void resultReadTimeoutKeepsTheSuccessfulJdbcStepAndSafeFailureCode() throws Exception {
		var connection = mock(Connection.class);
		var statement = mock(PreparedStatement.class);
		var result = mock(ResultSet.class);
		var meta = mock(ResultSetMetaData.class);
		when(connection.prepareStatement(anyString())).thenReturn(statement);
		when(statement.executeQuery()).thenReturn(result);
		when(result.getMetaData()).thenReturn(meta);
		when(meta.getColumnCount()).thenReturn(1);
		when(meta.getColumnLabel(1)).thenReturn("n");
		when(meta.getColumnType(1)).thenReturn(Types.BIGINT);
		var timeout = new SQLTimeoutException("read-driver-secret", "HYT00", 0);
		when(result.next()).thenThrow(timeout);
		var trace = new DataQueryExecutionTrace();
		var sql = new DataQuerySqlGuard().prepare("SELECT COUNT(*) FROM dev_device", Set.of("dev_device"));
		assertThatThrownBy(() -> DataQuerySqlExecutor.read(connection, sql, 5, trace, 2)).isSameAs(timeout);
		assertThat(trace.snapshot().steps())
			.anyMatch(s -> s.phase().equals("JDBC_QUERY") && s.status().equals("SUCCEEDED") && s.attempt() == 2);
		assertThat(trace.snapshot().steps()).anyMatch(s -> s.phase().equals("READ_RESULT")
				&& s.status().equals("FAILED") && s.code().equals("QUERY_TIMEOUT"));
		assertThat(f.json.writeValueAsString(trace.snapshot())).doesNotContain("read-driver-secret");
		verify(result).close();
		verify(statement).close();
	}

	@Test
	void resultBudgetFailureIsNotMislabelledAsConnectionOrSqlPreparationFailure() {
		f.jdbc.update("INSERT INTO dev_device SELECT 'budget-row' FROM SYSTEM_RANGE(1,1001)");
		var c = context(987);
		chat.execute(c, "SELECT company_id FROM dev_device UNION ALL SELECT company_id FROM dev_device LIMIT 1001");
		var result = details.read(f.owner, c.queryId());
		assertThat(result.resultCode()).isEqualTo("RESULT_LIMIT_EXCEEDED");
		assertThat(result.sql().submission()).isEqualTo("JDBC_INVOKED");
		assertThat(result.steps()).anyMatch(s -> s.phase().equals("READ_RESULT") && s.status().equals("FAILED"));
	}

	@Test
	void repairAttemptsAndFinalSqlAreNotMixed() {
		var c = context(988);
		var result = chat.execute(c, "SELECT COUNT(*) AS n FROM dev_device d WHERE dev_device.company_id='A'",
				facts -> "{\"repairKind\":\"TABLE_ALIAS_REFERENCE\",\"table\":\"dev_device\",\"alias\":\"d\"}");
		assertThat(result.getDataQuery()).containsEntry("status", "SUCCEEDED");
		var detail = details.read(f.owner, c.queryId());
		assertThat(detail.sql().attempt()).isEqualTo(2);
		assertThat(detail.sql().sql()).contains("d.company_id").doesNotContain("dev_device.company_id");
		assertThat(detail.steps()).filteredOn(s -> s.phase().equals("EXECUTE_QUERY"))
			.extracting(DataQueryExecutionTrace.Step::status)
			.containsExactly("FAILED", "SUCCEEDED");
	}

	@Test
	void actualModelBoundaryAndFailedDownstreamAreRecordedWithoutModelText() throws Exception {
		f.modelSql("SELECT COUNT(*) FROM dev_device", 0);
		var reply = f.rule.process(f.message(989, "设备总数")).blockFirst();
		String id = reply.getDataQuery().get("queryId").toString();
		assertThat(details.read(f.owner, id).steps())
			.anyMatch(s -> s.phase().equals("MODEL_GENERATE") && s.status().equals("SUCCEEDED"));
		var c = context(990);
		assertThatThrownBy(() -> chat.modelCall(c, false, () -> {
			throw new IllegalStateException("provider-secret");
		})).isInstanceOf(IllegalStateException.class);
		chat.fail(c, "AI_QUERY_FAILED");
		assertThat(details.read(f.owner, c.queryId()).steps())
			.anyMatch(s -> s.phase().equals("MODEL_GENERATE") && s.status().equals("FAILED"));
		assertThat(f.metadata.findOwnedQuery(f.owner, c.queryId()).orElseThrow().candidateJson())
			.doesNotContain("provider-secret");
	}

	@Test
	void detailClarificationRecordsItsRealStageWithoutInvokingModelOrDatabase() {
		var c = chat.begin(f.owner, "reader", f.message(991, "看刚才第一名的明细"));
		assertThat(chat.tryDrilldown(c)).isPresent();
		var detail = details.read(f.owner, c.queryId());
		assertThat(detail.steps()).anyMatch(s -> s.phase().equals("RESOLVE_DETAIL") && s.status().equals("FAILED"));
		assertThat(detail.steps()).noneMatch(s -> s.phase().equals("MODEL_GENERATE") || s.phase().equals("JDBC_QUERY"));
		assertThat(connections).hasValue(0);
	}

	@Test
	void durationsRemainNumericUnderRealMatrixSerializationAndResponsesAreNotCached() throws Exception {
		String id = successful(992);
		f.login(1, 2, "reader");
		var response = new DataQueryExecutionController(f.metadata, f.json).execution(id);
		assertThat(response.getHeaders().getCacheControl()).contains("no-store");
		var builder = new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
		new com.deepdimen.matrix.common.core.config.JacksonConfiguration().customizer().customize(builder);
		var mapper = builder.build();
		var doc = mapper.readTree(mapper.writeValueAsString(response.getBody()));
		assertThat(doc.at("/steps/0/durationMs").isIntegralNumber()).isTrue();
		assertThat(doc.at("/queryId").asText()).isEqualTo(id);
	}

	@Test
	void successfulDownstreamDetailsRecordPreparationAndPackagingWithoutInventingJdbcForATestAdapter() {
		String prior = successful(993);
		var group = new DataQueryDrilldownTest().ranking();
		var ranking = new DataQueryDepartmentDimension.Ranking(group.captured(), group.first(), prior, group.status());
		f.metadata.recordEvidence(prior, f.owner, "department", ranking);
		// Explicitly synthetic row adapter: verifies the service path, not a MySQL
		// dialect execution.
		var adapter = new DataQueryAiChatService(f.metadata,
				(identity, scope, sql) -> new DataQuerySqlExecutor.Rows(
						List.of("record_id", "record_name", "start_date", "company_id", "patrol_dept_id",
								"patrol_dept_name"),
						List.of(List.of("1", "synthetic-record", "2026-08-01 00:00:00", "C", "D", "Same name"))));
		var c = adapter.begin(f.owner, "reader", f.message(994, "看刚才第一名的明细"));
		assertThat(adapter.tryDrilldown(c).orElseThrow().getDataQuery()).containsEntry("status", "SUCCEEDED");
		var detail = details.read(f.owner, c.queryId());
		assertThat(detail.sql().sql()).contains("ORDER BY start_date ASC, id ASC");
		assertThat(detail.sql().submission()).isEqualTo("PREPARED_ONLY");
		assertThat(detail.steps()).extracting(DataQueryExecutionTrace.Step::phase)
			.containsSubsequence("RESOLVE_DETAIL", "EXECUTE_QUERY", "PACKAGE_RESULT");
		assertThat(detail.steps()).noneMatch(step -> "MODEL_GENERATE".equals(step.phase()));
	}

	@Test
	void reportedModelTokensReachOwnedExecutionDetailsAndRereadsDoNotCallTheModel() throws Exception {
		when(f.model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
			.thenReturn(usageReply("SELECT COUNT(*) AS n FROM dev_device", new dev.langchain4j.model.output.TokenUsage(100, 12, 112)));
		var answer = f.rule.process(f.message(9201, "设备数")).blockFirst();
		String id = answer.getDataQuery().get("queryId").toString();
		var detail = details.read(f.owner, id);
		var usage = usageFor(detail, "MODEL_GENERATE");
		assertThat(usage.path("status").asText()).isEqualTo("REPORTED");
		assertThat(usage.path("inputTokens").asInt(-1)).isEqualTo(100);
		assertThat(usage.path("outputTokens").asInt(-1)).isEqualTo(12);
		assertThat(usage.path("totalTokens").asInt(-1)).isEqualTo(112);
		String persisted = f.metadata.findOwnedQuery(f.owner, id).orElseThrow().candidateJson();
		details.read(f.owner, id);
		details.read(f.owner, id);
		assertThat(f.metadata.findOwnedQuery(f.owner, id).orElseThrow().candidateJson()).isEqualTo(persisted);
		verify(f.model, times(1)).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class));
	}

	@Test
	void technicalRepairKeepsItsOwnReturnedUsageInsteadOfDiscardingItWithTheResponseText() throws Exception {
		var repair = dev.langchain4j.model.chat.response.ChatResponse.builder()
			.aiMessage(dev.langchain4j.data.message.AiMessage
				.from("{\"repairKind\":\"TABLE_ALIAS_REFERENCE\",\"table\":\"dev_device\",\"alias\":\"d\"}"))
			.tokenUsage(new dev.langchain4j.model.output.TokenUsage(50, 5, 55))
			.build();
		when(f.model.chat(any(dev.langchain4j.model.chat.request.ChatRequest.class)))
			.thenReturn(usageReply("SELECT COUNT(*) AS n FROM dev_device d WHERE dev_device.company_id='A'",
					new dev.langchain4j.model.output.TokenUsage(100, 12, 112)), repair);
		var answer = f.rule.process(f.message(9202, "A设备数")).blockFirst();
		var detail = details.read(f.owner, answer.getDataQuery().get("queryId").toString());
		assertThat(usageFor(detail, "MODEL_GENERATE").path("totalTokens").asInt(-1)).isEqualTo(112);
		assertThat(usageFor(detail, "MODEL_TECHNICAL_REPAIR").path("totalTokens").asInt(-1)).isEqualTo(55);
		verify(f.model, times(2)).chat(any(dev.langchain4j.model.chat.request.ChatRequest.class));
	}

	@Test
	void aSuccessfulModelWithoutUsageIsExplicitlyUnknownAndNeverZeroOrAnEstimate() throws Exception {
		f.modelSql("SELECT COUNT(*) AS n FROM dev_device", 0);
		var answer = f.rule.process(f.message(9203, "设备数")).blockFirst();
		var detail = details.read(f.owner, answer.getDataQuery().get("queryId").toString());
		var usage = usageFor(detail, "MODEL_GENERATE");
		assertThat(usage.path("status").asText()).isEqualTo("NOT_RETURNED");
		assertThat(usage.path("totalTokens").isNull()).isTrue();
		assertThat(detail.steps()).filteredOn(s -> s.phase().equals("MODEL_GENERATE"))
			.allSatisfy(s -> assertThat(s.durationMs()).isNotNegative());
	}

	@Test
	void oldQueriesWithoutTraceStillExposeTheirOwnedAuditDurationWithoutFabricatingTokens() throws Exception {
		String id = successful(9204);
		var row = f.metadata.findOwnedQuery(f.owner, id).orElseThrow();
		var candidate = (com.fasterxml.jackson.databind.node.ObjectNode) f.json.readTree(row.candidateJson());
		candidate.remove("executionTrace");
		f.jdbc.update(
				"UPDATE ai_dataquery_query SET candidate_json=?,started_at='2026-09-16 01:00:00',finished_at='2026-09-16 01:00:03.500000' WHERE query_id=?",
				candidate.toString(), id);
		int reads = connections.get();
		var result = details.read(f.owner, id);
		var doc = f.json.valueToTree(result);
		assertThat(result.traceStatus()).isEqualTo("NOT_RECORDED");
		assertThat(result.steps()).isEmpty();
		assertThat(doc.at("/timing/basis").asText()).isEqualTo("QUERY_AUDIT");
		assertThat(doc.at("/timing/status").asText()).isEqualTo("RECORDED");
		assertThat(doc.at("/timing/durationMs").asInt(-1)).isEqualTo(3500);
		assertThat(connections.get()).isEqualTo(reads);
	}

	@Test
	void responseUsagePreservesZeroPartialAndInvalidWithoutEstimation() throws Exception {
		var zero = context(9210);
		chat.modelCall(zero, false,
				() -> dev.langchain4j.model.chat.response.ChatResponse.builder()
					.aiMessage(dev.langchain4j.data.message.AiMessage.from("not stored"))
					.tokenUsage(new dev.langchain4j.model.output.TokenUsage(0, 0, 0))
					.build());
		var zeroUsage = usageFor(details.read(f.owner, zero.queryId()), "MODEL_GENERATE");
		assertThat(zeroUsage.path("status").asText()).isEqualTo("REPORTED");
		assertThat(zeroUsage.path("totalTokens").asInt(-1)).isZero();
		var partial = context(9211);
		chat.modelCall(partial, false,
				() -> dev.langchain4j.model.chat.response.ChatResponse.builder()
					.aiMessage(dev.langchain4j.data.message.AiMessage.from("not stored"))
					.tokenUsage(new dev.langchain4j.model.output.TokenUsage(100, 12, null))
					.build());
		var partialUsage = usageFor(details.read(f.owner, partial.queryId()), "MODEL_GENERATE");
		assertThat(partialUsage.path("status").asText()).isEqualTo("PARTIAL");
		assertThat(partialUsage.path("inputTokens").asInt(-1)).isEqualTo(100);
		assertThat(partialUsage.path("totalTokens").isNull()).isTrue();
		var invalid = context(9212);
		chat.modelCall(invalid, false,
				() -> dev.langchain4j.model.chat.response.ChatResponse.builder()
					.aiMessage(dev.langchain4j.data.message.AiMessage.from("not stored"))
					.tokenUsage(new dev.langchain4j.model.output.TokenUsage(-1, 12, 11))
					.build());
		var invalidUsage = usageFor(details.read(f.owner, invalid.queryId()), "MODEL_GENERATE");
		assertThat(invalidUsage.path("status").asText()).isEqualTo("INVALID");
		assertThat(invalidUsage.path("inputTokens").isNull()).isTrue();
		assertThat(invalidUsage.path("outputTokens").isNull()).isTrue();
		assertThat(invalidUsage.path("totalTokens").isNull()).isTrue();
	}

	@Test
	void returnedTokenTotalsAreNotRecomputedAndRemainNumbersUnderMatrixSerialization() throws Exception {
		var c = context(9213);
		var response = usageReply("SELECT COUNT(*) AS n FROM dev_device",
				new dev.langchain4j.model.output.TokenUsage(100, 12, 120));
		chat.modelCall(c, false, () -> response);
		chat.execute(c, "SELECT COUNT(*) AS n FROM dev_device");
		var builder = new org.springframework.http.converter.json.Jackson2ObjectMapperBuilder();
		new com.deepdimen.matrix.common.core.config.JacksonConfiguration().customizer().customize(builder);
		var mapper = builder.build();
		var doc = mapper.readTree(mapper.writeValueAsString(details.read(f.owner, c.queryId())));
		var usage = java.util.stream.StreamSupport.stream(doc.path("steps").spliterator(), false)
			.filter(step -> "MODEL_GENERATE".equals(step.path("phase").asText()))
			.findFirst()
			.orElseThrow()
			.path("usage");
		for (String name : List.of("inputTokens", "outputTokens", "totalTokens"))
			assertThat(usage.path(name).isIntegralNumber()).isTrue();
		assertThat(usage.path("totalTokens").asInt()).isEqualTo(120);
		assertThat(doc.at("/timing/durationMs").isIntegralNumber()).isTrue();
	}

	@Test
	void pendingAndFailedCallsDoNotFabricateUsageOrCompletedTiming() throws Exception {
		var c = context(9214);
		var response = usageReply("SELECT COUNT(*) AS n FROM dev_device",
				new dev.langchain4j.model.output.TokenUsage(100, 12, 112));
		chat.modelCall(c, false, () -> {
			var pending = details.read(f.owner, c.queryId());
			assertThat(usageFor(pending, "MODEL_GENERATE").path("status").asText()).isEqualTo("PENDING");
			assertThat(pending.timing().status()).isEqualTo("IN_PROGRESS");
			assertThat(pending.timing().durationMs()).isNull();
			return response;
		});
		assertThatThrownBy(() -> chat.modelCall(c, true, () -> {
			throw new IllegalStateException("private-provider-error");
		})).isInstanceOf(IllegalStateException.class);
		chat.fail(c, "AI_QUERY_FAILED");
		var failed = details.read(f.owner, c.queryId());
		assertThat(usageFor(failed, "MODEL_GENERATE").path("totalTokens").asInt(-1)).isEqualTo(112);
		assertThat(usageFor(failed, "MODEL_TECHNICAL_REPAIR").path("status").asText()).isEqualTo("CALL_FAILED");
		assertThat(usageFor(failed, "MODEL_TECHNICAL_REPAIR").path("totalTokens").isNull()).isTrue();
		assertThat(f.json.writeValueAsString(failed)).doesNotContain("private-provider-error", "not stored");
		assertThat(failed.timing().status()).isEqualTo("RECORDED");
	}

	@Test
	void legacyV1StepsWithoutUsageStillDeserializeAndReadsDoNotWriteAudits() throws Exception {
		var c = context(9215);
		chat.modelCall(c, false,
				() -> dev.langchain4j.model.chat.response.ChatResponse.builder()
					.aiMessage(dev.langchain4j.data.message.AiMessage.from("not stored"))
					.tokenUsage(new dev.langchain4j.model.output.TokenUsage(100, 12, 112))
					.build());
		chat.execute(c, "SELECT COUNT(*) AS n FROM dev_device");
		var candidate = f.json.readTree(f.metadata.findOwnedQuery(f.owner, c.queryId()).orElseThrow().candidateJson());
		for (var step : candidate.at("/executionTrace/steps"))
			((com.fasterxml.jackson.databind.node.ObjectNode) step).remove("usage");
		f.jdbc.update("UPDATE ai_dataquery_query SET candidate_json=? WHERE query_id=?", candidate.toString(),
				c.queryId());
		var before = f.jdbc.queryForList("SELECT * FROM ai_dataquery_query");
		int reads = connections.get();
		var old = details.read(f.owner, c.queryId());
		details.read(f.owner, c.queryId());
		assertThat(old.traceStatus()).isEqualTo("RECORDED");
		assertThat(old.steps()).allSatisfy(step -> assertThat(step.usage()).isNull());
		assertThat(old.timing().durationMs()).isNotNegative();
		assertThat(f.jdbc.queryForList("SELECT * FROM ai_dataquery_query")).isEqualTo(before);
		assertThat(connections.get()).isEqualTo(reads);
	}

	@Test
	void inconsistentOrOversizedAuditTimeDoesNotAppearAsZeroOrACompletedQuery() {
		String id = successful(9216);
		f.jdbc.update(
				"UPDATE ai_dataquery_query SET started_at='2026-09-16 01:00:00',finished_at='2026-09-16 00:59:59' WHERE query_id=?",
				id);
		assertThat(details.read(f.owner, id).timing())
			.isEqualTo(new DataQueryExecutionDetails.Timing("QUERY_AUDIT", "UNAVAILABLE", null));
		f.jdbc.update("UPDATE ai_dataquery_query SET finished_at='2026-12-01 00:00:00' WHERE query_id=?", id);
		assertThat(details.read(f.owner, id).timing().durationMs()).isNull();
		f.jdbc.update("UPDATE ai_dataquery_query SET finished_at=NULL WHERE query_id=?", id);
		assertThat(details.read(f.owner, id).timing().status()).isEqualTo("UNAVAILABLE");
		f.jdbc.update("UPDATE ai_dataquery_query SET finished_at=started_at WHERE query_id=?", id);
		assertThat(details.read(f.owner, id).timing().durationMs()).isZero();
	}

	private dev.langchain4j.model.chat.response.ChatResponse usageReply(String sql,
			dev.langchain4j.model.output.TokenUsage usage) throws Exception {
		String args = f.json
			.writeValueAsString(Map.of("sql", sql, "chartOutput", false, "chartType", 0, "messageKey", "untrusted"));
		return dev.langchain4j.model.chat.response.ChatResponse.builder()
			.aiMessage(dev.langchain4j.data.message.AiMessage.from(args))
			.tokenUsage(usage)
			.build();
	}

	private com.fasterxml.jackson.databind.JsonNode usageFor(DataQueryExecutionDetails.Detail detail, String phase) {
		var step = detail.steps().stream().filter(s -> phase.equals(s.phase())).findFirst().orElseThrow();
		return f.json.valueToTree(step).path("usage");
	}

}
