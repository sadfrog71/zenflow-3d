package com.deepdimen.matrix.knowledge.support.dataquery;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.DataQuerySqlExamples.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.DataQuerySecurity.ManagementActor;

/** Real H2 metadata/lifecycle and bound JDBC reads. No real TOP data or model claims. */
class DataQuerySqlExamplesTest {

	DataQueryAiChatTest f;

	DataQuerySqlExamples store;

	final Set<String> permissions = Set.of("knowledge_aiData_view", "knowledge_dataquery_edit",
			"knowledge_dataquery_verify", "knowledge_dataquery_review", "knowledge_dataquery_publish",
			"knowledge_dataquery_human_review");

	ManagementActor editor, reviewer;

	AtomicInteger reads;

	AtomicBoolean live;

	final String sql = "SELECT COUNT(*) AS n FROM dev_patrol_record WHERE company_id='A'";

	final String other = "SELECT COUNT(*) AS n FROM dev_patrol_record WHERE company_id='Z'";

	@BeforeEach
	void setup() throws Exception {
		f = new DataQueryAiChatTest();
		f.setup();
		try (var connection = f.source.getConnection()) {
			org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,
					new org.springframework.core.io.FileSystemResource(
							"../docs/dataquery/sql-example-selection-schema.sql"));
		}
		f.jdbc.update("UPDATE ai_dataquery_access SET targets_json=?",
				"[{\"type\":\"user\",\"targetId\":\"2\"},{\"type\":\"user\",\"targetId\":\"3\"}]");
		editor = new ManagementActor(f.owner, permissions);
		reviewer = new ManagementActor(new Identity("1", "3"), permissions);
		reads = new AtomicInteger();
		live = new AtomicBoolean(true);
		store = make(f.clock);
		f.metadata.sqlExamples(store);
	}

	@AfterEach
	void close() {
		f.cleanup();
	}

	DataQuerySqlExamples make(Clock clock) {
		return new DataQuerySqlExamples(f.source, f.json, clock, f.metadata, (identity, scope, query) -> {
			reads.incrementAndGet();
			try (var c = f.source.getConnection()) {
				return DataQuerySqlExecutor.read(c, query);
			}
			catch (java.sql.SQLException e) {
				throw DataQuerySqlExecutor.failure(e);
			}
		}, (actor, capability) -> {
			if (!live.get())
				throw new DataQueryException("MANAGEMENT_FORBIDDEN");
		});
	}

	Input input() {
		return new Input("10", "2026年8月各部门巡视记录有多少条", List.of("2026年8月各部门巡查记录数量"), sql,
				"合成测试：按指定公司统计主记录，不关联子表；不是有效巡视正式定义。", "主记录一行计一次", "独立合成夹具：A有一条，Z没有记录，不引用生产数字。",
				List.of(new RegressionCase("原查询正例", sql, List.of("N"), List.of(List.of(1))),
						new RegressionCase("不存在公司反例", other, List.of("N"), List.of(List.of(0)))));
	}

	Input change(Input i, String question, List<RegressionCase> cases) {
		return new Input(i.dataId(), question, i.aliases(), i.sql(), i.definition(), i.grain(), i.businessBasis(),
				cases);
	}

	Detail verified() {
		var d = store.create(editor, input(), null);
		var checked = store.verify(editor, d.version(), 1);
		assertThat(checked.state()).withFailMessage(String.valueOf(checked.report())).isEqualTo("VERIFIED");
		return checked;
	}

	Detail publish(Detail d) {
		store.approve(reviewer, d.version(), d.revision());
		return store.publish(reviewer, d.version(), d.revision(), "人工核对合成依据后发布测试示例");
	}

	List<Match> search(String question) {
		var scope = f.metadata.resolve(f.owner);
		return store.retrieve(f.owner, question, scope, f.metadata.inventory(f.owner, scope.source()));
	}

	void fails(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
		assertThatThrownBy(call).isInstanceOf(DataQueryException.class).hasMessage(code);
	}

	void secondDataset() {
		f.jdbc.execute("ALTER TABLE ai_data ADD dataset_name VARCHAR(100)");
		f.jdbc.update("UPDATE ai_data SET dataset_name='Patrol'");
		f.jdbc.update(
				"INSERT INTO ai_datasource VALUES(21,1,'standards','jdbc:mysql://synthetic.invalid:13306/cloud_upms','reader','synthetic-only','mysql','0')");
		f.jdbc.update("INSERT INTO ai_data VALUES(11,1,'standards','0','sys_standard','Standards')");
		f.jdbc.update("INSERT INTO ai_data_table VALUES(102,1,'standards','sys_standard','standard','standard')");
		f.jdbc.update("INSERT INTO ai_data_field VALUES(102,1,'standards','id','bigint','standard','standard')");
		f.jdbc.execute("CREATE TABLE sys_role_menu(role_id BIGINT,menu_id BIGINT)");
		f.jdbc
			.execute("CREATE TABLE sys_menu(menu_id BIGINT,tenant_id BIGINT,permission VARCHAR(100),del_flag CHAR(1))");
		f.jdbc.update("INSERT INTO sys_user_role VALUES(2,1,1)");
		f.jdbc.update("INSERT INTO sys_role VALUES(1,1,'Metadata manager','0')");
		f.jdbc.update("INSERT INTO sys_role_menu VALUES(1,1)");
		f.jdbc.update("INSERT INTO sys_menu VALUES(1,1,'knowledge_aiData_view','0')");
	}

	@Test
	void secondDatasetDraftIsIsolatedAndDoesNotGrantExecution() {
		secondDataset();
		var draftInput = new Input("11", "查询标准数量", List.of(), "SELECT COUNT(*) AS n FROM cloud_upms.sys_standard",
				"仅供维护第二个数据集的示例草稿", "设备一行一条", "", List.of());
		var draft = store.create(editor, draftInput, null);
		assertThat(draft.input().dataId()).isEqualTo("11");
		assertThat(store.list(editor).examples()).isEmpty();
		assertThat(store.list(editor, "11").examples()).extracting(Detail::version).containsExactly(draft.version());
		assertThat(store.list(editor, "11").executionStatus()).isEqualTo("MANAGEMENT_ONLY");
		var missingView = new HashSet<>(permissions);
		missingView.remove("knowledge_aiData_view");
		var restricted = new ManagementActor(f.owner, missingView);
		assertThat(store.datasets(restricted)).extracting(DataQueryMetadataRepository.ExampleDataset::dataId)
			.containsExactly("10");
		fails("MANAGEMENT_FORBIDDEN", () -> store.inspect(restricted, draft.version()));
		assertThat(store.datasets(editor)).extracting(DataQueryMetadataRepository.ExampleDataset::dataId)
			.containsExactly("10", "11");
		store.selectReferences(editor, new Selection("11", 0, "OFF", List.of()));
		assertThat(store.selection(editor, "10").revision()).isZero();
		assertThat(store.inspect(editor, draft.version()).input()).isEqualTo(draftInput);
		fails("SQL_EXAMPLE_EXECUTION_NOT_CONFIGURED", () -> store.verify(editor, draft.version(), 1));
		assertThat(reads).hasValue(0);
		f.jdbc.update("UPDATE sys_menu SET del_flag='1'");
		fails("DATASET_NOT_AUTHORIZED", () -> store.inspect(editor, draft.version()));
	}

	@Test
	void datasetCatalogAndDraftsRecheckSourceTenantMembershipAndScope() {
		secondDataset();
		var other = new Input("11", "查询标准数量", List.of(), "SELECT COUNT(*) AS n FROM sys_standard", "标准表总记录数", "标准一行一次",
				"", List.of());
		var draft = store.create(editor, other, null);
		fails("SQL_TABLE_NOT_ALLOWED",
				() -> store.create(editor,
						new Input("11", other.question(), List.of(), "SELECT COUNT(*) FROM top_device_biz.sys_standard",
								other.definition(), other.grain(), "", List.of()),
						null));
		fails("DRAFT_NOT_FOUND",
				() -> store.inspect(new ManagementActor(new Identity("2", "2"), permissions), draft.version()));
	}

	@Test
	void selectedCandidatesAffectNextQueryButDoNotPublishDraftsOrReplayAnswers() {
		var draft = verified();
		fails("SQL_EXAMPLE_NOT_SELECTABLE",
				() -> store.selectReferences(editor, new Selection("10", 0, "SELECTED", List.of(draft.family()))));
		var first = publish(draft);
		var second = publish(verified());
		assertThat(search(input().question())).hasSize(2);
		store.selectReferences(editor, new Selection("10", 0, "SELECTED", List.of(first.family())));
		assertThat(search(input().question())).extracting(Match::version).containsExactly(first.version());
		var context = f.service.begin(f.owner, "reader", f.message(991, input().question()));
		store.selectReferences(editor, new Selection("10", 1, "OFF", List.of()));
		fails("SQL_EXAMPLE_CHANGED", () -> store.recheck(context));
		assertThat(search(input().question())).isEmpty();
		assertThat(store.list(editor).examples()).anyMatch(d -> d.version().equals(second.version()) && d.active());
		store.selectReferences(editor, new Selection("10", 2, "SELECTED", List.of()));
		assertThat(search(input().question())).isEmpty();
		store.selectReferences(editor, new Selection("10", 3, "AUTO", List.of()));
		assertThat(search(input().question())).hasSize(2);
	}

	@Test
	void selectedFamilyFollowsApprovedRevisionAndStopsOnDisable() {
		var original = publish(verified());
		store.selectReferences(editor, new Selection("10", 0, "SELECTED", List.of(original.family())));
		var next = store.revise(editor, original.version(), original.revision());
		next = store.verify(editor, next.version(), next.revision());
		var active = publish(next);
		assertThat(search(input().question())).extracting(Match::version).containsExactly(active.version());
		store.disable(reviewer, active.version(), active.revision(), "停止测试示例");
		assertThat(search(input().question())).isEmpty();
		assertThat(store.selection(editor, "10").families()).containsExactly(original.family());
	}

	@Test
	void selectionIsScopedOptimisticPersistentAndCannotSelectUnknownOrDraftFamilies() {
		fails("SQL_EXAMPLE_SCOPE_MISMATCH",
				() -> store.selectReferences(editor, new Selection("11", 0, "OFF", List.of())));
		fails("SQL_EXAMPLE_NOT_SELECTABLE",
				() -> store.selectReferences(editor, new Selection("10", 0, "SELECTED", List.of("sqlex:10:missing"))));
		store.selectReferences(editor, new Selection("10", 0, "OFF", List.of()));
		fails("STATE_CONFLICT", () -> store.selectReferences(editor, new Selection("10", 0, "AUTO", List.of())));
		assertThat(make(f.clock).selection(editor, "10").mode()).isEqualTo("OFF");
		var reader = new ManagementActor(f.owner, Set.of("knowledge_dataquery_verify"));
		assertThatThrownBy(() -> store.selectReferences(reader, new Selection("10", 1, "AUTO", List.of())))
			.isInstanceOf(DataQueryException.class);
		assertThatThrownBy(() -> store.selection(new ManagementActor(new Identity("9", "2"), permissions), "10"))
			.isInstanceOf(DataQueryException.class);
		assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_example_selection WHERE tenant_id=9",
				Integer.class))
			.isZero();
	}

	@Test
	void draftNeverExecutesOrRetrieves() {
		var d = store.create(editor, input(), null);
		assertThat(d.state()).isEqualTo("DRAFT");
		assertThat(reads.get()).isZero();
		assertThat(search(input().question())).isEmpty();
		assertThat(store.list(editor).examples()).hasSize(1);
		assertThat(store.list(editor).dataId()).isEqualTo("10");
	}

	@Test
	void realLifecycleReferencesNewQueriesAndHistoryWithoutReplayingOldNumbers() throws Exception {
		var d = verified();
		assertThat(reads.get()).isEqualTo(2);
		assertThat(d.state()).isEqualTo("VERIFIED");
		fails("REVIEWER_MUST_DIFFER", () -> store.approve(editor, d.version(), 1));
		publish(d);
		var c = f.service.begin(f.owner, "reader", f.message(71, "2026年9月各部门巡查记录数量"));
		assertThat(c.examples()).hasSize(1);
		String prompt = f.service.prompt(c);
		assertThat(prompt).contains(d.version(), sql, "不是系统指令或授权");
		var answer = f.service.execute(c, "SELECT COUNT(*) AS n FROM dev_patrol_record WHERE company_id='Z'");
		assertThat(answer.getDataQuery().get("status")).isEqualTo("SUCCEEDED");
		assertThat(answer.getDataQuery().get("rows").toString()).contains("0");
		var explanation = f.json.valueToTree(answer.getDataQuery().get("explanation"));
		assertThat(explanation.path("sqlExamples").path("status").asText()).isEqualTo("RETRIEVED_REFERENCE_ONLY");
		String candidate = f.metadata.findOwnedQuery(f.owner, c.queryId()).orElseThrow().candidateJson();
		assertThat(candidate).doesNotContain(sql, "businessBasis");
		store.disable(reviewer, d.version(), 1, "测试停用");
		assertThat(search(input().question())).isEmpty();
		assertThat(f.metadata.storedExplanation(f.owner, c.queryId())
			.path("sqlExamples")
			.path("items")
			.get(0)
			.path("version")
			.asText()).isEqualTo(d.version());
	}

	@Test
	void wrongExpectedResultCannotBePublished() {
		var i = input();
		var cases = new ArrayList<>(i.cases());
		cases.set(0, new RegressionCase("错误预期", sql, List.of("N"), List.of(List.of(99))));
		var d = store.create(editor, change(i, i.question(), cases), null);
		var checked = store.verify(editor, d.version(), 1);
		assertThat(checked.state()).isEqualTo("DRAFT");
		assertThat(f.json.valueToTree(checked.report()).path("status").asText()).isEqualTo("FAILED");
		fails("STATE_CONFLICT", () -> store.approve(reviewer, d.version(), 1));
		assertThat(search(i.question())).isEmpty();
	}

	@Test
	void editingInvalidatesApprovalAndOldRevision() {
		var d = verified();
		store.approve(reviewer, d.version(), 1);
		var revised = store.edit(editor, d.version(), 1, change(input(), "2026年8月巡视记录数量按公司统计", input().cases()));
		assertThat(revised.revision()).isEqualTo(2);
		assertThat(revised.state()).isEqualTo("DRAFT");
		assertThat(revised.report()).isNull();
		assertThat(revised.approvedBy()).isNull();
		fails("REVISION_CONFLICT", () -> store.publish(reviewer, d.version(), 1, "旧审批不得沿用"));
		fails("STATE_CONFLICT", () -> store.publish(reviewer, d.version(), 2, "没有重新验证"));
	}

	@Test
	void verificationRequiresDistinctCasesAndOriginalSql() {
		var i = input();
		var d = store.create(editor, change(i, i.question(), List.of(i.cases().get(0))), null);
		fails("SQL_EXAMPLE_CASES_REQUIRED", () -> store.verify(editor, d.version(), 1));
		var duplicated = List.of(i.cases().get(0), new RegressionCase("重复用例", sql, List.of("N"), List.of(List.of(1))));
		store.edit(editor, d.version(), 1, change(i, i.question(), duplicated));
		fails("SQL_EXAMPLE_CASES_DISTINCT_REQUIRED", () -> store.verify(editor, d.version(), 2));
		assertThat(reads.get()).isZero();
	}

	@Test
	void unsafeAndUnselectedSqlRejectedBeforeStorageOrExecution() {
		var i = input();
		for (String bad : List.of("DELETE FROM dev_patrol_record", "SELECT * FROM mysql.user",
				"SELECT * FROM not_selected")) {
			assertThatThrownBy(() -> store.create(editor, new Input(i.dataId(), i.question(), i.aliases(), bad,
					i.definition(), i.grain(), i.businessBasis(), List.of()), null))
				.isInstanceOf(DataQueryException.class);
		}
		assertThat(reads.get()).isZero();
		assertThat(store.list(editor).examples()).isEmpty();
	}

	@Test
	void schemaDriftStopsRetrievalAndPublicationButAllowsDisable() {
		var d = publish(verified());
		f.jdbc.update("UPDATE ai_data_field SET virtual_comment='changed'");
		assertThat(search(input().question())).isEmpty();
		assertThat(store.inspect(editor, d.version()).applicable()).isFalse();
		assertThat(store.disable(reviewer, d.version(), 1, "元数据变化后停用").state()).isEqualTo("DISABLED");
	}

	@Test
	void oldVerificationExpiresButPublishedExamplesDoNotExpireEveryThirtyMinutes() {
		var d = verified();
		var later = make(Clock.offset(f.clock, Duration.ofMinutes(31)));
		fails("VALIDATION_EXPIRED", () -> later.approve(reviewer, d.version(), 1));
		publish(d);
		var scope = f.metadata.resolve(f.owner);
		assertThat(later.retrieve(f.owner, input().question(), scope, f.metadata.inventory(f.owner, scope.source())))
			.hasSize(1);
	}

	@Test
	void tenantDatasetManagementAndLivePermissionAreChecked() {
		var d = verified();
		fails("MANAGEMENT_FORBIDDEN", () -> store.inspect(new ManagementActor(f.owner, Set.of()), d.version()));
		assertThatThrownBy(() -> store.inspect(new ManagementActor(new Identity("2", "2"), permissions), d.version()))
			.isInstanceOf(DataQueryException.class);
		var i = input();
		fails("DATASET_NOT_AUTHORIZED", () -> store.create(editor, new Input("999", i.question(), i.aliases(), i.sql(),
				i.definition(), i.grain(), i.businessBasis(), i.cases()), null));
		live.set(false);
		fails("MANAGEMENT_FORBIDDEN", () -> store.verify(editor, d.version(), 1));
	}

	@Test
	void revocationOfVerifierInvalidatesApproval() {
		var d = verified();
		f.jdbc.update("UPDATE ai_dataquery_access SET targets_json=?", "[{\"type\":\"user\",\"targetId\":\"3\"}]");
		fails("DATASET_NOT_AUTHORIZED", () -> store.approve(reviewer, d.version(), 1));
	}

	@Test
	void concurrentNewVersionsCompareActivePointerAndRollback() {
		var first = publish(verified());
		var a = store.revise(editor, first.version(), 1);
		var b = store.revise(editor, first.version(), 1);
		store.verify(editor, a.version(), 1);
		publish(a);
		store.verify(editor, b.version(), 1);
		store.approve(reviewer, b.version(), 1);
		fails("ACTIVE_RULE_CHANGED", () -> store.publish(reviewer, b.version(), 1, "过时基础版本不能发布"));
		assertThat(store.inspect(editor, b.version()).state()).isEqualTo("APPROVED");
		store.rollback(reviewer, a.version(), 1, first.version(), "人工恢复之前已验证版本");
		assertThat(search(input().question())).extracting(Match::version).containsExactly(first.version());
		fails("STATE_CONFLICT", () -> store.edit(editor, first.version(), 1, input()));
	}

	@Test
	void disableBetweenGenerationAndExecutionDoesNotRunQuery() {
		var d = publish(verified());
		var c = f.service.begin(f.owner, "reader", f.message(72, input().question()));
		f.service.prompt(c);
		store.disable(reviewer, d.version(), 1, "生成期间停用");
		int before = f.executions.get();
		var result = f.service.execute(c, sql);
		assertThat(result.getDataQuery().get("code")).isEqualTo("SQL_EXAMPLE_CHANGED");
		assertThat(f.executions.get()).isEqualTo(before);
	}

	@Test
	void normalizedMonthAndAliasMatchButUnrelatedQuestionDoesNot() {
		publish(verified());
		assertThat(search("2026年9月各部门巡查记录数量")).hasSize(1);
		assertThat(search("查询发票开票金额与客户地址")).isEmpty();
		assertThat(search("下一页")).isEmpty();
	}

	@Test
	void atMostThreeReferencesAndContentBudget() {
		for (int n = 0; n < 5; n++)
			publish(verified());
		assertThat(search(input().question())).hasSize(3);
		assertThat(store.prompt(search(input().question())).length()).isLessThan(14500);
	}

	@Test
	void ownedSourceIsRequiredAndNoSeedWhenEvidenceUnavailable() {
		fails("QUERY_NOT_FOUND", () -> store.seed(editor, UUID.randomUUID().toString()));
		var c = f.service.begin(f.owner, "reader", f.message(73, input().question()));
		f.service.execute(c, sql);
		fails("SQL_EXAMPLE_SEED_UNAVAILABLE", () -> store.seed(editor, c.queryId()));
		fails("QUERY_NOT_FOUND", () -> store.seed(reviewer, c.queryId()));
	}

	@Test
	void forgedExpectedColumnsFailAndNoResultsLeakInReport() {
		var i = input();
		var cases = new ArrayList<>(i.cases());
		cases.set(0, new RegressionCase("列名不同", sql, List.of("wrong"), List.of(List.of(1))));
		var d = store.create(editor, change(i, i.question(), cases), null);
		var report = f.json.valueToTree(store.verify(editor, d.version(), 1).report());
		assertThat(report.path("status").asText()).isEqualTo("FAILED");
		assertThat(report.toString()).doesNotContain("company_id", "actualRows", sql);
	}

	@Test
	void databaseFailureIsReportedOnceWithoutAutomaticRepair() {
		var bad = new DataQuerySqlExamples(f.source, f.json, f.clock, f.metadata, (identity, scope, q) -> {
			reads.incrementAndGet();
			throw new DataQueryException("DATASOURCE_RESOURCE_LIMIT");
		}, (a, c) -> {
		});
		var d = bad.create(editor, input(), null);
		var checked = bad.verify(editor, d.version(), 1);
		assertThat(reads.get()).isEqualTo(1);
		assertThat(checked.state()).isEqualTo("DRAFT");
		assertThat(f.json.valueToTree(checked.report()).path("cases").get(0).path("code").asText())
			.isEqualTo("DATASOURCE_RESOURCE_LIMIT");
	}

	@Test
	void successfulOwnedEvidenceSeedsOnlySqlAndQuestionWithoutRerunning() throws Exception {
		try (var c = f.source.getConnection()) {
			org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(c,
					new org.springframework.core.io.FileSystemResource("../docs/dataquery/investigation-schema.sql"));
		}
		var evidence = new DataQueryQueryEvidence(f.source, f.json, f.clock,
				Base64.getEncoder().encodeToString(new byte[32]));
		f.metadata.investigations(evidence, null);
		var c = f.service.begin(f.owner, "reader", f.message(79, input().question()));
		f.service.execute(c, sql);
		int before = f.executions.get();
		var seed = store.seed(editor, c.queryId());
		assertThat(seed.get("question")).isEqualTo(input().question());
		assertThat(seed.get("sql").toString()).contains("company_id", "'A'");
		assertThat(seed).doesNotContainKeys("rows", "password", "authorization");
		assertThat(f.executions.get()).isEqualTo(before);
		var input = input();
		var fromQuery = store.create(editor, input, c.queryId());
		assertThat(fromQuery.sourceQueryId()).isEqualTo(c.queryId());
		f.jdbc.update("UPDATE ai_dataquery_query_evidence SET expires_at=?", LocalDateTime.of(2020, 1, 1, 0, 0));
		fails("INVESTIGATION_EVIDENCE_UNAVAILABLE", () -> store.seed(editor, c.queryId()));
	}

	@Test
	void ordinaryAclUserCanReusePublishedExampleButCannotManageIt() {
		var d = publish(verified());
		f.jdbc.update("INSERT INTO sys_user VALUES(4,1,'other-reader','0','0')");
		f.jdbc.update("INSERT INTO sys_tenant_user VALUES(1,4)");
		f.jdbc.update("UPDATE ai_dataquery_access SET targets_json=?",
				"[{\"type\":\"user\",\"targetId\":\"2\"},{\"type\":\"user\",\"targetId\":\"3\"},{\"type\":\"user\",\"targetId\":\"4\"}]");
		var other = new Identity("1", "4");
		var scope = f.metadata.resolve(other);
		assertThat(store.retrieve(other, input().question(), scope, f.metadata.inventory(other, scope.source())))
			.extracting(Match::version)
			.containsExactly(d.version());
		fails("MANAGEMENT_FORBIDDEN", () -> store.list(new ManagementActor(other, Set.of())));
		f.jdbc.update("UPDATE sys_user SET lock_flag='1' WHERE user_id=4");
		fails("DATASET_NOT_AUTHORIZED",
				() -> store.retrieve(other, input().question(), scope, f.metadata.inventory(other, scope.source())));
	}

	@Test
	void metadataChangeAfterVerificationAndCorruptPublishedReportFailClosed() {
		var d = verified();
		f.jdbc.update("UPDATE ai_data_field SET virtual_comment='new meaning'");
		fails("SQL_EXAMPLE_DEPENDENCY_CHANGED", () -> store.approve(reviewer, d.version(), 1));
		store.edit(editor, d.version(), 1, input());
		store.verify(editor, d.version(), 2);
		publish(store.inspect(editor, d.version()));
		f.jdbc.update("UPDATE ai_dataquery_rule_version SET regression_report='{}' WHERE rule_version=?", d.version());
		fails("SQL_EXAMPLE_INVALID", () -> search(input().question()));
	}

	@Test
	void httpIdentityAndUnknownPublicationFieldsCannotBeInjected() throws Exception {
		var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
			.standaloneSetup(new DataQuerySqlExampleController(store))
			.build();
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.get("/data-query/manage/sql-examples"))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
		assertThatThrownBy(() -> f.json.readValue("{\"revision\":1,\"approvedBy\":\"3\"}",
				DataQuerySqlExampleController.Revision.class))
			.isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
		assertThatThrownBy(() -> f.json.readValue("{\"dataId\":\"10\",\"tenantId\":\"2\"}", Input.class))
			.isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
	}

	@Test
	void selectionErrorsHaveExplicitHttpAndContractBoundaries() throws Exception {
		assertThat(DataQueryResponses.failure(new DataQueryException("SQL_EXAMPLE_SELECTION_INVALID"))
			.getStatusCode()
			.value()).isEqualTo(400);
		assertThat(DataQueryResponses.failure(new DataQueryException("SQL_EXAMPLE_NOT_SELECTABLE"))
			.getStatusCode()
			.value()).isEqualTo(400);
		assertThat(DataQueryResponses.failure(new DataQueryException("SQL_EXAMPLE_SCOPE_MISMATCH"))
			.getStatusCode()
			.value()).isEqualTo(403);
		assertThatThrownBy(() -> f.json.readValue(
				"{\"dataId\":\"10\",\"revision\":0,\"mode\":\"OFF\",\"families\":[],\"tenantId\":\"9\"}",
				Selection.class))
			.isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
		var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
			.standaloneSetup(new DataQuerySqlExampleController(store))
			.build();
		org.springframework.security.core.context.SecurityContextHolder.clearContext();
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.get("/data-query/manage/sql-examples/selection")
			.param("dataId", "10"))
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
	}

	@Test
	void featureConfigurationDoesNotCreateStoreByDefault() {
		new org.springframework.boot.test.context.runner.ApplicationContextRunner()
			.withUserConfiguration(DataQuerySqlExamplesConfiguration.class)
			.run(context -> assertThat(context).doesNotHaveBean(DataQuerySqlExamples.class));
	}

}
