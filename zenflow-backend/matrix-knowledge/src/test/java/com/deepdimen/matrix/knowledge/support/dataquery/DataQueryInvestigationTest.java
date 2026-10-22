package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.expr.SQLUnaryExpr;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitorAdapter;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Real H2 platform lifecycle + real SQL over manually labelled counterexamples; advisor
 * is explicitly synthetic.
 */
class DataQueryInvestigationTest {

	DataQueryAiChatTest f;

	DataQueryQueryEvidence vault;

	DataQueryRelationStore rules;

	DataQueryInvestigationService service;

	DataQueryAiChatService chat;

	DataQueryCorrectionService corrections;

	DataQueryInvestigationAccess access;

	DataQueryInvestigationService.Source source;

	AtomicInteger calls;

	AtomicBoolean revoked;

	AtomicReference<String> forced = new AtomicReference<>();

	final String original = "SELECT COUNT(*) AS n FROM dev_patrol_record p WHERE p.start_date >= '2026-08-01' AND p.start_date < '2026-09-01' AND p.del_flag='0' AND NOT EXISTS (SELECT 1 FROM dev_patrol_record_device c WHERE c.record_id=p.record_code)";

	DataQuerySecurity.ManagementActor actor(String user) {
		return new DataQuerySecurity.ManagementActor(new Identity("1", user),
				Set.of("knowledge_dataquery_edit", "knowledge_dataquery_verify", "knowledge_dataquery_review",
						"knowledge_dataquery_publish", "knowledge_dataquery_human_review"));
	}

	@BeforeEach
	void setup() throws Exception {
		f = new DataQueryAiChatTest();
		f.setup();
		try (var c = f.source.getConnection()) {
			ScriptUtils.executeSqlScript(c, new FileSystemResource("../docs/dataquery/investigation-schema.sql"));
		}
		f.jdbc.update("UPDATE ai_dataquery_access SET targets_json=?",
				"[{\"type\":\"user\",\"targetId\":\"2\"},{\"type\":\"user\",\"targetId\":\"3\"}]");
		f.jdbc.update("DELETE FROM dev_patrol_record");
		f.jdbc.execute(
				"ALTER TABLE dev_patrol_record ADD (id BIGINT PRIMARY KEY,record_code VARCHAR(30),start_date DATE,del_flag CHAR(1))");
		f.jdbc.execute("CREATE TABLE dev_patrol_record_device(id BIGINT PRIMARY KEY,record_id VARCHAR(64))");
		f.jdbc.update(
				"INSERT INTO dev_patrol_record VALUES ('A',1,'R1','2026-08-01','0'),('A',2,'R2','2026-08-02','0'),('B',3,'R3','2026-08-03','0'),('B',4,'R4','2026-08-04','1'),('A',101,'R101','2026-07-01','0'),('B',102,'R102','2026-07-02','0')");
		// Manually labelled identity relationships, not produced by the correction
		// compiler or advisor.
		f.jdbc.update(
				"INSERT INTO dev_patrol_record_device VALUES (1,'1'),(2,'1'),(3,'3'),(4,NULL),(5,'bad-ref'),(6,'01'),(7,'1 '),(8,'101'),(9,'101'),(10,'R2')");
		f.jdbc.update("UPDATE ai_data SET table_name='dev_patrol_record,dev_device,dev_patrol_record_device'");
		f.jdbc.update(
				"INSERT INTO ai_data_table VALUES(102,1,'synthetic','dev_patrol_record_device','physical','research is not approval')");
		for (String field : List.of("id", "record_code", "start_date", "del_flag"))
			f.jdbc.update("INSERT INTO ai_data_field VALUES(100,1,'synthetic',?,'varchar','physical','unchanged')",
					field);
		for (String field : List.of("id", "record_id"))
			f.jdbc.update("INSERT INTO ai_data_field VALUES(102,1,'synthetic',?,'varchar','physical','unchanged')",
					field);
		f.jdbc
			.execute("CREATE TABLE sys_menu(menu_id BIGINT,tenant_id BIGINT,permission VARCHAR(64),del_flag CHAR(1))");
		f.jdbc.execute("CREATE TABLE sys_role_menu(role_id BIGINT,menu_id BIGINT)");
		f.jdbc.update("INSERT INTO sys_role(role_id,tenant_id,role_name,del_flag) VALUES(900,1,'test-only','0')");
		f.jdbc.update("INSERT INTO sys_user_role(tenant_id,user_id,role_id) VALUES(1,2,900),(1,3,900)");
		int i = 1;
		for (String permission : actor("2").permissions()) {
			f.jdbc.update("INSERT INTO sys_menu VALUES(?,1,?,'0')", i, permission);
			f.jdbc.update("INSERT INTO sys_role_menu VALUES(900,?)", i++);
		}
		access = new DataQueryInvestigationAccess(f.source);
		revoked = new AtomicBoolean();
		calls = new AtomicInteger();
		vault = new DataQueryQueryEvidence(f.source, f.json, f.clock, Base64.getEncoder().encodeToString(new byte[32]));
		source = new DataQueryInvestigationService.Source() {
			public <T> T snapshot(Identity id, Authorization scope, Function<DataQueryRelationProbe.Reader, T> work) {
				assertThat(id).isEqualTo(f.owner);
				assertThat(scope).isEqualTo(f.metadata.resolve(id));
				try (var c = f.source.getConnection()) {
					c.setAutoCommit(false);
					c.setTransactionIsolation(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
					try {
						return work.apply(p -> {
							try {
								return DataQuerySqlExecutor.read(c, h2(p), 5);
							}
							catch (java.sql.SQLException e) {
								throw DataQuerySqlExecutor.failure(e);
							}
						});
					}
					finally {
						c.rollback();
					}
				}
				catch (java.sql.SQLException e) {
					throw DataQuerySqlExecutor.failure(e);
				}
			}
		};
		rules = new DataQueryRelationStore(f.source, f.json, f.clock, f.metadata, vault,
				(owner, snapshot, d, check) -> DataQueryInvestigationService.verifyWith(source, f.json, owner, snapshot,
						d, check, new DataQueryInvestigationService.Budget()),
				access);
		f.metadata.investigations(vault, rules);
		service = new DataQueryInvestigationService(f.source, f.json, f.clock, f.metadata, vault, rules,
				(owner, id, facts) -> {
					assertThat(facts).doesNotContain("synthetic-only", "bad-ref", "SELECT COUNT");
					if (revoked.get())
						f.jdbc.update("DELETE FROM sys_role_menu");
					if (forced.get() != null)
						return forced.get();
					return switch (calls.getAndIncrement()) {
						case 0 -> "{\"tool\":\"METADATA\"}";
						case 1 -> "{\"tool\":\"SEMANTICS\"}";
						case 2 -> "{\"tool\":\"PROBE_CANDIDATES\"}";
						case 3 -> "{\"tool\":\"TRIAL_REGRESSION\"}";
						default ->
							"{\"tool\":\"PROPOSE\",\"selectedKey\":\"id\",\"evidenceIds\":[\"PROBE_CANDIDATES\",\"TRIAL_REGRESSION\"],\"reason\":\"Synthetic advisor: plausible identity link; requires independent business confirmation\"}";
					};
				}, source, access);
		chat = new DataQueryAiChatService(f.metadata,
				(owner, scope, q) -> source.snapshot(owner, scope, reader -> reader.read(q)));
		corrections = new DataQueryCorrectionService(f.source, f.metadata, f.json, f.clock);
	}

	@AfterEach
	void cleanup() {
		if (service != null)
			service.close();
		if (f != null)
			f.cleanup();
	}

	static DataQuerySqlGuard.Prepared h2(DataQuerySqlGuard.Prepared p) {
		// H2 dialect adapter ONLY: MySQL unary BINARY -> byte-array cast; preserve
		// parameters and all query structure.
		var ast = SQLUtils.parseSingleStatement(p.sql(), DbType.mysql);
		ast.accept(new MySqlASTVisitorAdapter() {
			@Override
			public boolean visit(com.alibaba.druid.sql.ast.expr.SQLCastExpr expr) {
				// MySQL CAST AS CHAR has unbounded display width; H2 CHAR defaults to
				// width 1.
				if ("CHAR".equalsIgnoreCase(expr.getDataType().getName())
						&& expr.getDataType().getArguments().isEmpty())
					expr.getDataType().setName("VARCHAR");
				return true;
			}

			@Override
			public void endVisit(SQLUnaryExpr expr) {
				if ("BINARY".equalsIgnoreCase(expr.getOperator().name()))
					SQLUtils.replaceInParent(expr,
							SQLUtils.toSQLExpr("CAST(" + expr.getExpr() + " AS VARBINARY)", DbType.mysql));
			}
		});
		return new DataQuerySqlGuard.Prepared(SQLUtils.toSQLString(ast, DbType.mysql), p.parameters(), p.tables());
	}

	String feedback(long message) {
		var response = chat.execute(chat.begin(f.owner, "reader", f.message(message, "2026年8月没有现场检查项的巡视有多少")),
				original);
		assertThat(response.getDataQuery().get("status")).isEqualTo("SUCCEEDED");
		return corrections.feedback(f.owner, response.getDataQuery().get("queryId").toString(),
				DataQueryCorrectionService.Category.RELATION_OR_NAME_MAPPING, "这些巡视有现场项，为什么说没有？", null);
	}

	Map<String, Object> completed(String feedback) throws Exception {
		var task = service.start(actor("2"), feedback, null);
		String id = task.get("investigation_id").toString();
		for (int i = 0; i < 200; i++) {
			task = service.read(actor("2"), id);
			if (!Set.of("QUEUED", "RUNNING").contains(task.get("status")))
				return task;
			Thread.sleep(25);
		}
		throw new AssertionError("Worker did not terminate");
	}

	String proposed() throws Exception {
		var task = completed(feedback(300));
		assertThat(task.get("status")).withFailMessage(task.toString()).isEqualTo("PROPOSED");
		assertThat(calls.get()).isEqualTo(5);
		return service.prepare(actor("2"), task.get("investigation_id").toString());
	}

	@Test
	void investigationActualSqlHumanPublicationFreshParametersReuseAndDisable() throws Exception {
		String version = proposed();
		var detail = rules.inspect(actor("2"), version);
		assertThat(detail.get("state")).isEqualTo("DRAFT");
		var before = chat.execute(chat.begin(f.owner, "reader", f.message(301, "2026年8月未检查巡视数")), original);
		assertThat(before.getMessage()).isNotBlank();
		rules.edit(actor("2"), version, 1, "BUSINESS_CONFIRMATION",
				"Synthetic fixture independently labelled: child record_id denotes parent numeric id, not record_code.");
		assertThat(rules.verify(actor("2"), version, 2)).contains("\"passed\":true", "independentJoin");
		assertThatThrownBy(() -> corrections.approve(actor("2"), version, 2)).hasMessage("REVIEWER_MUST_DIFFER");
		corrections.approve(actor("3"), version, 2);
		corrections.publish(actor("3"), version, 2, "Synthetic local independent review only");
		var current = chat.begin(f.owner, "reader", f.message(302, "2026年7月未检查的巡视数量"));
		assertThat(chat.prompt(current)).contains(version);
		var response = chat.execute(current,
				original.replace("2026-08-01", "2026-07-01").replace("2026-09-01", "2026-08-01"));
		assertThat(response.getDataQuery().get("status")).isEqualTo("SUCCEEDED");
		assertThat(response.getMessage()).contains("|1|");
		assertThat(f.json.writeValueAsString(response.getDataQuery().get("explanation"))).contains(version,
				"AST_RELATION", "APPLIED");
		var untouched = chat.execute(chat.begin(f.owner, "reader", f.message(309, "设备总数")),
				"SELECT COUNT(*) AS n FROM dev_device");
		assertThat(untouched.getDataQuery().get("status")).isEqualTo("SUCCEEDED");
		assertThat(f.json.writeValueAsString(untouched.getDataQuery().get("explanation"))).contains("NOT_APPLICABLE")
			.doesNotContain("AST_RELATION");
		var detailQuery = chat.execute(chat.begin(f.owner, "reader", f.message(310, "8月没有现场项的前五条巡视")),
				original.replace("COUNT(*) AS n", "p.id").concat(" ORDER BY p.id LIMIT 5"));
		assertThat(detailQuery.getDataQuery().get("status")).isEqualTo("SUCCEEDED");
		assertThat(detailQuery.getMessage()).contains("|2|");
		String history = f.metadata.findOwnedQuery(f.owner, current.queryId()).orElseThrow().candidateJson();
		f.jdbc.update("INSERT INTO dev_patrol_record VALUES('A',103,'R103','2026-07-03','0')");
		var newQuery = chat.begin(f.owner, "reader", f.message(303, "换个说法，7月无现场项巡视多少"));
		var fresh = chat.execute(newQuery,
				original.replace("2026-08-01", "2026-07-01").replace("2026-09-01", "2026-08-01"));
		assertThat(fresh.getMessage()).isNotEqualTo(response.getMessage());
		corrections.rollback(actor("3"), version, null, "Synthetic stop consumption");
		var stopped = chat.execute(chat.begin(f.owner, "reader", f.message(304, "2026年8月未检查的巡视多少")), original);
		assertThat(f.json.writeValueAsString(stopped.getDataQuery().get("explanation"))).contains("DISABLED")
			.doesNotContain("AST_RELATION");
		assertThat(f.metadata.findOwnedQuery(f.owner, current.queryId()).orElseThrow().candidateJson())
			.isEqualTo(history);
	}

	@Test
	void encryptedEvidenceIsOwnerBoundExpiresAndDoesNotRebuildOldQueries() throws Exception {
		String feedback = feedback(305);
		var query = f.jdbc.queryForObject("SELECT query_id FROM ai_dataquery_feedback WHERE feedback_id=?",
				String.class, feedback);
		String ciphertext = f.jdbc.queryForObject(
				"SELECT encrypted_snapshot FROM ai_dataquery_query_evidence WHERE query_id=?", String.class, query);
		assertThat(ciphertext).doesNotContain("SELECT", "2026-08-01", "record_code");
		assertThatThrownBy(() -> vault.load(new Identity("9", "2"), query))
			.hasMessage("INVESTIGATION_EVIDENCE_UNAVAILABLE");
		f.jdbc.update("UPDATE ai_dataquery_query_evidence SET expires_at=?", LocalDateTime.of(2026, 1, 1, 0, 0));
		var job = completed(feedback);
		assertThat(job.get("status")).isEqualTo("NEEDS_EVIDENCE");
		assertThat(calls.get()).isZero();
		assertThatThrownBy(() -> service.prepare(actor("2"), job.get("investigation_id").toString()))
			.hasMessage("INVESTIGATION_NOT_READY");
		vault.purge();
		assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_query_evidence", Integer.class)).isZero();
	}

	@Test
	void revokedManagementPermissionStopsWorkerAfterTheModelReturns() throws Exception {
		String fb = feedback(306);
		revoked.set(true);
		var task = service.start(actor("2"), fb, null);
		String id = task.get("investigation_id").toString();
		for (int i = 0; i < 100; i++) {
			if ("FAILED".equals(f.jdbc.queryForObject(
					"SELECT status FROM ai_dataquery_investigation WHERE investigation_id=?", String.class, id)))
				break;
			Thread.sleep(25);
		}
		assertThat(f.jdbc.queryForObject("SELECT error_code FROM ai_dataquery_investigation WHERE investigation_id=?",
				String.class, id))
			.isEqualTo("MANAGEMENT_FORBIDDEN");
		assertThatThrownBy(() -> service.read(actor("2"), id)).hasMessage("MANAGEMENT_FORBIDDEN");
	}

	@Test
	void changedDraftInvalidatesValidationAndApprovalAndCrossTenantCannotInspect() throws Exception {
		String v = proposed();
		rules.edit(actor("2"), v, 1, "BUSINESS_CONFIRMATION",
				"Synthetic relationship independently confirmed in fixture");
		rules.verify(actor("2"), v, 2);
		rules.approve(actor("3"), v, 2);
		rules.edit(actor("2"), v, 2, "BUSINESS_CONFIRMATION",
				"Changed scope explanation requires revalidation and independent review");
		var row = rules.inspect(actor("2"), v);
		assertThat(row.get("state")).isEqualTo("DRAFT");
		assertThat(row.get("regression_report")).isNull();
		assertThat(row.get("approved_by")).isNull();
		assertThatThrownBy(() -> rules.publish(actor("3"), v, 3, "No previous validation reuse"))
			.hasMessage("STATE_CONFLICT");
		var cross = new DataQuerySecurity.ManagementActor(new Identity("99", "2"), actor("2").permissions());
		assertThatThrownBy(() -> rules.inspect(cross, v)).hasMessage("MANAGEMENT_FORBIDDEN");
	}

	@Test
	void modelsCannotSkipToolsOrSubmitSqlAndMissingBusinessAuthorityBlocksApproval() throws Exception {
		forced.set(
				"{\"tool\":\"PROPOSE\",\"selectedKey\":\"id\",\"evidenceIds\":[\"PROBE_CANDIDATES\",\"TRIAL_REGRESSION\"]}");
		var rejected = completed(feedback(307));
		assertThat(rejected.get("status")).isEqualTo("NEEDS_CONFIRMATION");
		assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_rule_version WHERE definition_key=?",
				Integer.class, DataQueryRelation.KIND))
			.isZero();
		forced.set(null);
		String v = proposed();
		rules.verify(actor("2"), v, 1);
		assertThatThrownBy(() -> rules.approve(actor("3"), v, 1)).hasMessage("RELATION_BUSINESS_CONFIRMATION_REQUIRED");
		f.jdbc.update("UPDATE ai_dataquery_rule_version SET validated_at=? WHERE rule_version=?",
				LocalDateTime.of(2026, 1, 1, 0, 0), v);
		assertThatThrownBy(() -> rules.approve(actor("3"), v, 1)).hasMessage("VALIDATION_EXPIRED");
	}

	@Test
	void unpublishedActivePointerAndMetadataDriftCannotBecomeExecutedExperience() throws Exception {
		String v = proposed();
		var scope = f.metadata.resolve(f.owner);
		var def = DataQueryRelation.Definition.patrol(10, f.metadata.inventory(f.owner, scope.source()).digest(),
				scope.source().revision());
		f.jdbc.update("INSERT INTO ai_dataquery_rule_active VALUES(1,?,?)", def.key(), v);
		assertThatThrownBy(() -> chat.begin(f.owner, "reader", f.message(308, "一个正常查询")))
			.hasMessage("RELATION_EXPERIENCE_INVALID");
		f.jdbc.update("DELETE FROM ai_dataquery_rule_active WHERE dataset_key=?", def.key());
		f.jdbc.update("UPDATE ai_data_field SET virtual_comment='changed metadata' WHERE table_id=102");
		assertThatThrownBy(() -> rules.verify(actor("2"), v, 1)).hasMessage("DATASET_METADATA_CHANGED");
	}

	@Test
	void aLateVerifierCannotDowngradeAConcurrentlyPublishedVersion() throws Exception {
		String v = proposed();
		rules.edit(actor("2"), v, 1, "BUSINESS_CONFIRMATION",
				"Synthetic independent business confirmation for interleaving regression");
		rules.verify(actor("2"), v, 2);
		var racing = new DataQueryRelationStore(f.source, f.json, f.clock, f.metadata, vault,
				(owner, snapshot, d, check) -> {
					var report = DataQueryInvestigationService.verifyWith(source, f.json, owner, snapshot, d, check,
							new DataQueryInvestigationService.Budget());
					rules.approve(actor("3"), v, 2);
					rules.publish(actor("3"), v, 2, "Synthetic interleaved publish");
					return report;
				}, access);
		assertThatThrownBy(() -> racing.verify(actor("2"), v, 2)).hasMessage("STATE_CONFLICT");
		assertThat(rules.inspect(actor("3"), v).get("state")).isEqualTo("PUBLISHED");
	}

	@Test
	void independentReferenceRejectsAnIncorrectCompilerResultEvenWhenBothProbeTotalsAgree() throws Exception {
		String fb = feedback(311);
		String qid = f.jdbc.queryForObject("SELECT query_id FROM ai_dataquery_feedback WHERE feedback_id=?",
				String.class, fb);
		var snapshot = vault.load(f.owner, qid);
		var d = DataQueryRelation.Definition.patrol(10, snapshot.inventoryDigest(),
				snapshot.authorization().source().revision());
		var corrupted = new DataQueryInvestigationService.Source() {
			public <T> T snapshot(Identity id, Authorization auth, Function<DataQueryRelationProbe.Reader, T> work) {
				return source.snapshot(id, auth, reader -> work.apply(query -> {
					if (query.sql().contains("COUNT(DISTINCT") && query.sql().contains("NOT EXISTS"))
						return new DataQuerySqlExecutor.Rows(List.of("N"), List.of(List.of("999")));
					return reader.read(query);
				}));
			}
		};
		var result = DataQueryInvestigationService.verifyWith(corrupted, f.json, f.owner, snapshot, d, () -> {
		}, new DataQueryInvestigationService.Budget());
		assertThat(result.get("passed")).isEqualTo(false);
		assertThat(((Map<?, ?>) result.get("actualIndependentComparison")).get("matches")).isEqualTo(false);
	}

	@Test
	void boundedJoinFixCountsParentOnceAndKeepsResourceAndFieldLimits() throws Exception {
		var scope = f.metadata.resolve(f.owner);
		var d = DataQueryRelation.Definition.patrol(10, f.metadata.inventory(f.owner, scope.source()).digest(),
				scope.source().revision());
		String sql = "SELECT COUNT(*) AS n FROM dev_patrol_record p LEFT JOIN dev_patrol_record_device c ON c.record_id=p.record_code WHERE p.start_date>='2026-08-01' AND p.start_date<'2026-09-01' AND p.del_flag='0' AND c.id IS NOT NULL";
		var input = new DataQuerySqlGuard().prepare(sql, Set.of(d.parentTable(), d.childTable()));
		var fixed = DataQueryRelation.apply(input, d);
		assertThat(fixed.status()).isEqualTo("APPLIED");
		assertThat(source.snapshot(f.owner, scope, r -> r.read(fixed.query())).values().get(0).get(0).toString())
			.isEqualTo("2");
		var begun = chat.begin(f.owner, "reader", f.message(312, "现场项数量"));
		var simulated = new DataQueryAiChatService.Context(begun.queryId(), begun.identity(), begun.username(),
				begun.messageId(), begun.question(), begun.authorization(), begun.inventory(), begun.rule(),
				List.of(new DataQueryRelationStore.Match("PUBLISHED", "rel-synthetic", d)));
		var ambiguous = rules.apply(simulated, input);
		assertThat(ambiguous.query()).isEqualTo(input);
		assertThat(f.json.writeValueAsString(ambiguous.report())).contains("NOT_APPLICABLE")
			.doesNotContain("AST_RELATION");
		var invalid = new DataQuerySqlGuard().prepare(sql.replace("COUNT(*) AS n", "c.record_id"), input.tables());
		assertThat(DataQueryRelation.apply(invalid, d).status()).isEqualTo("UNSUPPORTED_SHAPE");
		assertThat(new DataQuerySqlGuard().prepare("SELECT COUNT(*) FROM dev_device", Set.of("dev_device")).tables())
			.containsExactly("dev_device");
	}

	@Test
	void capturedTimestampJoinReachesAdvisorAndIndependentTrialWithCompanyCondition() throws Exception {
		f.jdbc.execute("ALTER TABLE dev_patrol_record ALTER COLUMN start_date TIMESTAMP");
		f.jdbc.execute("ALTER TABLE dev_patrol_record_device ADD company_id VARCHAR(64) DEFAULT 'A'");
		f.jdbc.update("UPDATE dev_patrol_record_device SET company_id='B' WHERE record_id='3'");
		f.jdbc.update("INSERT INTO dev_patrol_record_device(id,record_id,company_id) VALUES(11,'2','B')");
		f.jdbc.update(
				"INSERT INTO ai_data_field VALUES(102,1,'synthetic','company_id','varchar','physical','unchanged')");
		var captured = DataQueryRelationTest.observedMonthlyQuery("2026-08-01 00:00:00", "2026-09-01 00:00:00");
		var context = chat.begin(f.owner, "reader", f.message(320, "2026年8月没有现场检查项的巡视记录有多少"));
		var snapshot = new DataQueryQueryEvidence.Snapshot(captured, context.authorization(),
				context.inventory().digest(), context.ruleVersion(), DataQueryAiChatService.EXECUTION_VERSION,
				"CAPTURED_FOR_EXECUTION_NOT_USER_CONFIRMATION", context.relations());
		var response = chat.execute(context,
				"SELECT COUNT(DISTINCT r.id) AS record_count FROM dev_patrol_record r LEFT JOIN dev_patrol_record_device d ON r.record_code=d.record_id AND r.company_id=d.company_id WHERE r.del_flag='0' AND r.start_date >= '2026-08-01 00:00:00' AND r.start_date < '2026-09-01 00:00:00' AND d.id IS NULL");
		assertThat(response.getDataQuery().get("status")).isEqualTo("SUCCEEDED");
		String feedback = corrections.feedback(f.owner, context.queryId(),
				DataQueryCorrectionService.Category.RELATION_OR_NAME_MAPPING, "核查现场项关联和公司条件", null);
		var task = completed(feedback);
		assertThat(task.get("status")).withFailMessage(task.toString()).isEqualTo("PROPOSED");
		assertThat(calls.get()).isEqualTo(5);
		assertThat(((Map<?, ?>) task.get("evidence")).keySet().stream().map(String::valueOf).toList())
			.contains("METADATA", "SEMANTICS", "PROBE_CANDIDATES", "TRIAL_REGRESSION");
		assertThat(vault.load(f.owner, context.queryId()).query()).isEqualTo(snapshot.query());
		var corrected = DataQueryRelation
			.apply(captured,
					DataQueryRelation.Definition.patrol(context.authorization().source().dataId(),
							context.inventory().digest(), context.authorization().source().revision()))
			.query();
		assertThat(corrected.sql()).contains("r.company_id = d.company_id");
		var rows = source.snapshot(f.owner, context.authorization(), reader -> reader.read(corrected));
		assertThat(rows.values().get(0).get(0).toString()).isEqualTo("1");
		assertThat(task.get("draft_version")).isNull();
	}

	@Test
	void nonMidnightRangeIsASystemScopeFailureNotMissingBusinessEvidence() throws Exception {
		f.jdbc.execute("ALTER TABLE dev_patrol_record ALTER COLUMN start_date TIMESTAMP");
		var context = chat.begin(f.owner, "reader", f.message(321, "核查未覆盖完整月份的查询"));
		var response = chat.execute(context, original.replace("2026-08-01'", "2026-08-01 01:00:00'"));
		assertThat(response.getDataQuery().get("status")).isEqualTo("SUCCEEDED");
		String feedback = corrections.feedback(f.owner, context.queryId(),
				DataQueryCorrectionService.Category.RELATION_OR_NAME_MAPPING, "调查范围", null);
		var task = completed(feedback);
		assertThat(task.get("status")).isEqualTo("FAILED");
		assertThat(task.get("error_code")).isEqualTo("INVESTIGATION_SCOPE_UNSUPPORTED");
		assertThat(calls.get()).isZero();
		assertThat(vault.load(f.owner, context.queryId())).isNotNull();
		assertThat(task.get("draft_version")).isNull();
	}

}
