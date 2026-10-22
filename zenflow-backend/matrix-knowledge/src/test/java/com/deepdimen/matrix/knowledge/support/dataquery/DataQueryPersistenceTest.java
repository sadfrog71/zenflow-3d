package com.deepdimen.matrix.knowledge.support.dataquery;

import com.alibaba.druid.pool.DruidDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;
import static org.assertj.core.api.Assertions.*;

/**
 * H2 runs the exact additive platform DDL. All bindings/accounts/grants here are
 * synthetic.
 */
class DataQueryPersistenceTest {

	private static final Identity OWNER = new Identity("1", "2");

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC);

	private static final LocalDateTime NOW = LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC);

	private static final String URL = "jdbc:mysql://synthetic-top.invalid:13306/top_device_biz";

	private static final PatrolQueryCompiler.Candidate PLAN = new PatrolQueryCompiler.Candidate("top.patrol",
			"valid_patrol_count", "department", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), "Asia/Shanghai",
			"DESC");

	private DataSource source;

	private JdbcTemplate jdbc;

	private DataQueryMetadataRepository repository;

	private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

	@BeforeEach
	void setup() throws Exception {
		source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa",
				"");
		initialize(source);
		jdbc = new JdbcTemplate(source);
		// Minimal representation of pre-existing MATRIX tables. They are not created by
		// the migration.
		jdbc.execute(
				"CREATE TABLE ai_datasource (id BIGINT PRIMARY KEY, tenant_id BIGINT, name VARCHAR(64), url VARCHAR(1000), username VARCHAR(64), password VARCHAR(64), ds_type VARCHAR(64), del_flag CHAR(1))");
		jdbc.execute(
				"CREATE TABLE ai_data (data_id BIGINT PRIMARY KEY, tenant_id BIGINT, ds_name VARCHAR(64), del_flag CHAR(1))");
		jdbc.execute(
				"CREATE TABLE sys_user (user_id BIGINT PRIMARY KEY,tenant_id BIGINT,username VARCHAR(64),lock_flag CHAR(1),del_flag CHAR(1))");
		jdbc.execute("CREATE TABLE sys_tenant_user (tenant_id BIGINT,user_id BIGINT,PRIMARY KEY(tenant_id,user_id))");
		jdbc.update("INSERT INTO sys_user VALUES (2,1,'reader','0','0'),(3,1,'editor','0','0')");
		jdbc.update("INSERT INTO sys_tenant_user VALUES (1,2),(1,3)");
		MatrixAccessFixture.organization(jdbc);
		repository = new DataQueryMetadataRepository(source, json, CLOCK);
	}

	private void initialize(DataSource dataSource) throws Exception {
		try (var connection = dataSource.getConnection()) {
			ScriptUtils.executeSqlScript(connection, new FileSystemResource("../docs/dataquery/platform-schema.sql"));
			ScriptUtils.executeSqlScript(connection, new FileSystemResource("../docs/dataquery/access-schema.sql"));
		}
	}

	private void bind() {
		jdbc.update(
				"INSERT INTO ai_datasource VALUES (20,1,'synthetic-source',?,'synthetic-reader','synthetic-only','mysql','0')",
				URL);
		jdbc.update("INSERT INTO ai_data VALUES (10,1,'synthetic-source','0')");
		jdbc.update("INSERT INTO ai_dataquery_binding VALUES (1,'top.patrol',10,20,'binding-1',?,1,9,?)",
				DataQueryMetadataRepository.connectionFingerprint(10, 20, URL, "synthetic-reader", "mysql"),
				NOW.minusDays(1));
	}

	private void grant() {
		jdbc.update("INSERT INTO ai_dataquery_access VALUES (1,'top.patrol',10,'access-1',?, ?,9,?)",
				"[{\"type\":\"user\",\"targetId\":\"2\"}]", NOW.plusDays(60), NOW.minusDays(1));
	}

	private void publishFixture(String version) {
		jdbc.update(
				"""
						INSERT INTO ai_dataquery_rule_version (tenant_id,dataset_key,rule_version,definition_key,definition_digest,state,created_by,created_at,regression_digest,validated_at,approved_by,approved_at,published_by,published_at) VALUES (1,'top.patrol',?,?,?,'PUBLISHED',3,?,?,?,4,?,4,?)
						""",
				version, DEFINITION, DataQueryMetadataRepository.DEFINITION_DIGEST, NOW.minusDays(4), "a".repeat(64),
				NOW.minusDays(3), NOW.minusDays(2), NOW.minusDays(1));
		jdbc.update("INSERT INTO ai_dataquery_rule_active VALUES (1,'top.patrol',?)", version);
	}

	private QueryAudit audit() {
		return new QueryAudit() {
			public void started(String id, Identity identity, String version, String revision,
					PatrolQueryCompiler.Candidate candidate) {
				repository.started(id, identity, version, revision, candidate);
			}

			public void origin(String id, Identity identity, QueryOrigin origin) {
				repository.origin(id, identity, origin);
			}

			public void finished(String id, Identity identity, String code, int groups) {
				repository.finished(id, identity, code, groups);
			}
		};
	}

	@Test
	void schemaIsAdditiveRepeatableAndDoesNotSeedAuthorizationOrRules() throws Exception {
		initialize(source);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_grant", Integer.class)).isZero();
		assertThat(repository.current(OWNER)).isNull();
		assertThatThrownBy(() -> repository.resolve(OWNER)).hasMessage("DATASOURCE_BINDING_REQUIRED");
	}

	@Test
	void grantsRequireExplicitTenantUserAndApprovedPairs() {
		bind();
		grant();
		Authorization scope = repository.resolve(OWNER);
		assertThat(scope.revision()).isNotBlank();
		assertThat(scope.source().datasourceId()).isEqualTo(20);
		assertThatThrownBy(() -> repository.resolve(new Identity("1", "3"))).hasMessage("DATASET_NOT_AUTHORIZED");
		assertThatThrownBy(() -> repository.resolve(new Identity("2", "2"))).hasMessage("DATASOURCE_BINDING_REQUIRED");
		jdbc.update("UPDATE ai_dataquery_access SET approved_by = NULL");
		assertThatThrownBy(() -> repository.resolve(OWNER)).hasMessage("DATASET_NOT_AUTHORIZED");
	}

	@Test
	void grantExpiryAndContentChangesInvalidateScope() {
		bind();
		grant();
		Authorization before = repository.resolve(OWNER);
		jdbc.update("UPDATE ai_dataquery_access SET access_revision = 'changed'");
		assertThat(repository.resolve(OWNER).revision()).isNotEqualTo(before.revision());
		jdbc.update("UPDATE ai_dataquery_access SET valid_until = ?", NOW);
		assertThatThrownBy(() -> repository.resolve(OWNER)).hasMessage("DATASET_NOT_AUTHORIZED");
	}

	@Test
	void datasourceChangesAndCrossTenantInventoryFailClosed() {
		bind();
		grant();
		var binding = repository.binding(OWNER);
		jdbc.update("UPDATE ai_datasource SET url = 'jdbc:mysql://other.invalid/top_device_biz'");
		assertThatThrownBy(() -> repository.binding(OWNER)).hasMessage("DATASOURCE_BINDING_CHANGED");
		assertThatThrownBy(() -> repository.credentials(OWNER, binding)).hasMessage("DATASOURCE_BINDING_CHANGED");
		jdbc.update("UPDATE ai_datasource SET url = ?, tenant_id = 2", URL);
		assertThatThrownBy(() -> repository.binding(OWNER)).hasMessage("DATASOURCE_BINDING_REQUIRED");
	}

	@Test
	void passwordRotationDoesNotRepublishBindingButNeverLeaksInToString() {
		bind();
		var binding = repository.binding(OWNER);
		jdbc.update("UPDATE ai_datasource SET password = 'rotated-synthetic-only'");
		assertThat(repository.binding(OWNER)).isEqualTo(binding);
		var credentials = repository.credentials(OWNER, binding);
		assertThat(credentials.toString()).isEqualTo("ReaderCredentials[REDACTED]");
		assertThat(credentials.password).isEqualTo("rotated-synthetic-only");
	}

	@Test
	void activeRulesRequireValidatedIndependentlyApprovedSupportedDefinition() {
		publishFixture("r1");
		assertThat(repository.current(OWNER).version()).isEqualTo("r1");
		assertThat(repository.current(new Identity("2", "2"))).isNull();
		jdbc.update("UPDATE ai_dataquery_rule_version SET approved_by = created_by");
		assertThat(repository.current(OWNER)).isNull();
		jdbc.update("UPDATE ai_dataquery_rule_version SET approved_by=4, definition_digest='tampered'");
		assertThatThrownBy(() -> repository.current(OWNER)).hasMessage("METRIC_DEFINITION_UNCONFIRMED");
		jdbc.update("UPDATE ai_dataquery_rule_version SET definition_digest=?, state='DRAFT'",
				DataQueryMetadataRepository.DEFINITION_DIGEST);
		assertThat(repository.current(OWNER)).isNull();
	}

	@Test
	void auditIsOwnershipScopedAndTerminalCompletionCannotBeOverwritten() {
		publishFixture("r1");
		String id = UUID.randomUUID().toString();
		repository.started(id, OWNER, "r1", "scope-1", PLAN);
		assertThat(repository.findOwnedQuery(new Identity("2", "2"), id)).isEmpty();
		assertThat(repository.findOwnedQuery(new Identity("1", "3"), id)).isEmpty();
		assertThatThrownBy(() -> repository.finished(id, new Identity("1", "3"), "OK", 1))
			.hasMessage("AUDIT_UNAVAILABLE");
		repository.finished(id, OWNER, "OK", 1);
		var record = repository.findOwnedQuery(OWNER, id).orElseThrow();
		assertThat(record.ruleVersion()).isEqualTo("r1");
		assertThat(record.candidateJson()).contains("valid_patrol_count").doesNotContain("password", "SELECT ");
		assertThat(record.status()).isEqualTo("FINISHED");
		assertThatThrownBy(() -> repository.finished(id, OWNER, "QUERY_FAILED", 0)).hasMessage("AUDIT_UNAVAILABLE");
		assertThat(repository.findOwnedQuery(OWNER, id).orElseThrow().resultCode()).isEqualTo("OK");
	}

	@Test
	void auditSurvivesOuterBusinessTransactionRollback() {
		publishFixture("r1");
		String id = UUID.randomUUID().toString();
		TransactionTemplate outer = new TransactionTemplate(new DataSourceTransactionManager(source));
		assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
			repository.started(id, OWNER, "r1", "scope-1", PLAN);
			throw new IllegalStateException("synthetic outer failure");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(repository.findOwnedQuery(OWNER, id)).isPresent();
	}

	@Test
	void fileBackedAuditSurvivesRepositoryAndConnectionRecreation(@TempDir Path directory) throws Exception {
		String url = "jdbc:h2:file:" + directory.resolve("audit").toAbsolutePath().toString().replace('\\', '/')
				+ ";MODE=MySQL";
		DataSource first = new DriverManagerDataSource(url, "sa", "");
		initialize(first);
		new JdbcTemplate(first).update(
				"INSERT INTO ai_dataquery_rule_version (tenant_id,dataset_key,rule_version,definition_key,definition_digest,state,created_by,created_at) VALUES (1,'top.patrol','r1',?,?,'DRAFT',3,?)",
				DEFINITION, DataQueryMetadataRepository.DEFINITION_DIGEST, NOW);
		String id = UUID.randomUUID().toString();
		new DataQueryMetadataRepository(first, json, CLOCK).started(id, OWNER, "r1", "scope-1", PLAN);
		DataSource reopened = new DriverManagerDataSource(url, "sa", "");
		assertThat(new DataQueryMetadataRepository(reopened, json, CLOCK).findOwnedQuery(OWNER, id)).isPresent();
	}

	@Test
	void persistedRuleAndGrantDrivePlanAndAuditWithoutModel() {
		bind();
		grant();
		publishFixture("r1");
		AtomicInteger executions = new AtomicInteger();
		ControlledQueryService service = new ControlledQueryService(repository::resolve, repository::current,
				execution -> {
					executions.incrementAndGet();
					return List.of();
				}, audit(), CLOCK, 10, Duration.ofMinutes(5));
		var plan = service.validate(OWNER, PLAN);
		var result = service.execute(OWNER, plan.planHandle());
		assertThat(repository.findOwnedQuery(OWNER, result.queryId()).orElseThrow().resultCode()).isEqualTo("OK");
		String stale = service.validate(OWNER, PLAN).planHandle();
		jdbc.update("UPDATE ai_dataquery_access SET access_revision='changed'");
		assertThatThrownBy(() -> service.execute(OWNER, stale)).hasMessage("AUTHORIZATION_CHANGED");
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void changedActiveVersionInvalidatesOldPlanButPreservesAudit() {
		bind();
		grant();
		publishFixture("r1");
		ControlledQueryService service = new ControlledQueryService(repository::resolve, repository::current,
				execution -> List.of(), audit(), CLOCK, 10, Duration.ofMinutes(5));
		var prior = service.execute(OWNER, service.validate(OWNER, PLAN).planHandle());
		String stale = service.validate(OWNER, PLAN).planHandle();
		// Synthetic fixture transition only; no production publication API is implemented
		// here.
		jdbc.update("DELETE FROM ai_dataquery_rule_active");
		publishFixture("r2");
		assertThatThrownBy(() -> service.execute(OWNER, stale)).hasMessage("RULE_CHANGED");
		assertThat(service.execute(OWNER, service.validate(OWNER, PLAN).planHandle()).ruleVersion()).isEqualTo("r2");
		assertThat(repository.findOwnedQuery(OWNER, prior.queryId()).orElseThrow().ruleVersion()).isEqualTo("r1");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM ai_dataquery_rule_version WHERE rule_version='r1'"))
			.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
	}

	@Test
	void approvedExecutorUsesBoundInventoryAndClosesLocalReaderPool() {
		bind();
		grant();
		Authorization authorization = repository.resolve(OWNER);
		AtomicReference<DruidDataSource> used = new AtomicReference<>();
		ApprovedDatasourceQueryExecutor executor = new ApprovedDatasourceQueryExecutor(repository, credentials -> {
			DruidDataSource local = new DruidDataSource();
			local.setUrl("jdbc:h2:mem:synthetic_reader_" + UUID.randomUUID());
			local.setUsername("sa");
			local.setPassword("");
			used.set(local);
			return local;
		});
		// This tests real JDBC/resource wiring with synthetic SQL, NOT the MySQL patrol
		// definition.
		var query = new PatrolQueryCompiler.CompiledQuery(
				"SELECT 'company-X' AS company_id, 'department-X' AS patrol_dept_id, CAST(NULL AS VARCHAR) AS department_name, 3 AS valid_patrol_count, 2 AS name_variant_count, 0 AS missing_name_count",
				List.of());
		var rows = executor.execute(new QueryExecution(OWNER, authorization, query));
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).count()).isEqualTo(3);
		assertThat(used.get().isClosed()).isTrue();
		jdbc.update("UPDATE ai_dataquery_access SET targets_json='[]'");
		assertThatThrownBy(() -> executor.execute(new QueryExecution(OWNER, authorization, query)))
			.hasMessage("DATASET_NOT_AUTHORIZED");
	}

	@Test
	void readerPoolEnforcesTlsAndBudgetsWithoutOpeningConnection() {
		var credentials = new DataQueryMetadataRepository.ReaderCredentials(URL, "synthetic-reader", "synthetic-only");
		try (DruidDataSource pool = ApprovedDatasourceQueryExecutor.newReaderPool(credentials)) {
			assertThat(pool.isInited()).isFalse();
			assertThat(pool.getMaxActive()).isEqualTo(1);
			assertThat(pool.getMaxWait()).isEqualTo(3000);
			assertThat(pool.getConnectProperties().getProperty("sslMode")).isEqualTo("VERIFY_IDENTITY");
			assertThat(pool.getConnectProperties().getProperty("allowMultiQueries")).isEqualTo("false");
			assertThat(pool.getConnectProperties().getProperty("connectionTimeZone")).isEqualTo("Asia/Shanghai");
		}
		for (String unsafe : List.of("jdbc:mysql://host/platform", URL + "?sslMode=DISABLED",
				URL + "?password=synthetic", URL + "?socketFactory=untrusted",
				"jdbc:mysql://user:synthetic@host/top_device_biz", URL + "?allowMultiQueries=true")) {
			assertThatThrownBy(() -> ApprovedDatasourceQueryExecutor.canonicalReaderUrl(unsafe))
				.hasMessage("DATASOURCE_CONFIGURATION_UNSAFE");
		}
		assertThat(ApprovedDatasourceQueryExecutor
			.canonicalReaderUrl(URL + "?sslMode=VERIFY_IDENTITY&characterEncoding=UTF-8")).isEqualTo(URL);
	}

	@Test
	void persistenceOptInCreatesAdaptersWithoutAutoMigration() {
		DataSource empty = new DriverManagerDataSource("jdbc:h2:mem:empty_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
				"sa", "");
		new ApplicationContextRunner().withUserConfiguration(DataQueryConfiguration.class)
			.withBean(DataSource.class, () -> empty)
			.withBean(ObjectMapper.class, () -> json)
			.withPropertyValues("ai.dataquery.enabled=true", "ai.dataquery.persistence.enabled=true")
			.run(context -> {
				assertThat(context).hasSingleBean(DataQueryMetadataRepository.class)
					.hasSingleBean(AuthorizationResolver.class)
					.hasSingleBean(RuleProvider.class)
					.hasSingleBean(QueryAudit.class)
					.hasSingleBean(QueryExecutor.class);
				assertThat(new JdbcTemplate(empty).queryForObject(
						"SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME LIKE 'AI_DATAQUERY_%'",
						Integer.class))
					.isZero();
			});
	}

	private DataQuerySecurity.ManagementActor actor(String tenant, String user, String... permissions) {
		return new DataQuerySecurity.ManagementActor(new Identity(tenant, user),
				java.util.Arrays.stream(permissions)
					.map(p -> "knowledge_dataquery_" + p)
					.collect(java.util.stream.Collectors.toSet()));
	}

	private DataQueryQuestionService questions(AtomicInteger executions) {
		return new DataQueryQuestionService(repository,
				new ControlledQueryService(repository::resolve, repository::current, q -> {
					executions.incrementAndGet();
					return List.of(new PatrolRow("company-X", "department-X", "Synthetic", 7, 1, 0));
				}, audit(), CLOCK, 10, Duration.ofMinutes(5)));
	}

	private String failedQuestion(DataQueryQuestionService questions) {
		return (String) questions.ask(OWNER, 10, "2026年8月各部门巡查记录有多少？按次数从高到低排列。", 77L).get("queryId");
	}

	@Test
	void correctionPublishesReusableRulesAndRollbackRestoresBehavior() throws Exception {
		bind();
		grant();
		publishFixture("r1");
		var count = new AtomicInteger();
		var questions = questions(count);
		String queryId = failedQuestion(questions);
		assertThat(templateQuestion(questions, "2026年8月各部门巡查记录数", 701).get("status")).isEqualTo("NEEDS_CLARIFICATION");
		assertThat(httpQuestion(questions, "2026年8月各部门巡查记录数").get("ruleVersion").asText()).isEqualTo("r1");
		assertThat(count.get()).isZero();
		var corrections = new DataQueryCorrectionService(source, repository, json, CLOCK);
		var editor = actor("1", "3", "edit", "verify");
		var reviewer = actor("1", "4", "review", "publish", "human_review");
		String feedback = corrections.feedback(OWNER, queryId, DataQueryCorrectionService.Category.INTENT_UNDERSTANDING,
				"巡查应按已确认的巡视意图解释", 77L);
		String version = corrections.draft(editor, feedback, List.of("巡视", "巡查"), "2026-08");
		assertThat(repository.current(OWNER).version()).isEqualTo("r1");
		assertThat(failedQuestion(questions)).isNotEqualTo(queryId);
		assertThat(count.get()).isZero();
		assertThat(corrections.verify(editor, version, 1)).startsWith("PASS:");
		corrections.approve(reviewer, version, 1);
		corrections.publish(reviewer, version, 1, "Synthetic human approval");
		var response = templateQuestion(questions, "2026年8月各部门巡查记录数", 702);
		assertThat(response.get("status")).isEqualTo("SUCCEEDED");
		assertThat(response.get("ruleVersion")).isEqualTo(version);
		assertThat(httpQuestion(questions, "2026年9月各部门巡查记录数").get("ruleVersion").asText()).isEqualTo(version);
		assertThat(count.get()).isEqualTo(2);
		corrections.rollback(reviewer, version, "r1", "Synthetic rollback");
		assertThat(templateQuestion(questions, "2026年8月各部门巡查记录数", 703).get("status")).isEqualTo("NEEDS_CLARIFICATION");
		assertThat(httpQuestion(questions, "2026年8月各部门巡查记录数").get("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
		assertThat(count.get()).isEqualTo(2);
		assertThat(repository.findOwnedQuery(OWNER, queryId).orElseThrow().ruleVersion()).isEqualTo("r1");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_release_event", Integer.class)).isEqualTo(2);
	}

	@Test
	void draftEditsInvalidateEvidenceAndCrossTenantOrModelReviewIsRejected() {
		bind();
		grant();
		publishFixture("r1");
		String queryId = failedQuestion(questions(new AtomicInteger()));
		var corrections = new DataQueryCorrectionService(source, repository, json, CLOCK);
		var editor = actor("1", "3", "edit", "verify", "review", "human_review");
		var reviewer = actor("1", "4", "review", "publish", "human_review");
		assertThatThrownBy(() -> corrections.feedback(new Identity("2", "2"), queryId,
				DataQueryCorrectionService.Category.INTENT_UNDERSTANDING, "x", 77L))
			.hasMessage("QUERY_NOT_FOUND");
		assertThatThrownBy(() -> corrections.feedback(new Identity("1", "9"), queryId,
				DataQueryCorrectionService.Category.INTENT_UNDERSTANDING, "x", 77L))
			.hasMessage("QUERY_NOT_FOUND");
		assertThatThrownBy(() -> corrections.feedback(OWNER, queryId,
				DataQueryCorrectionService.Category.INTENT_UNDERSTANDING, "x", 78L))
			.hasMessage("CHAT_RECORD_NOT_OWNED");
		String feedback = corrections.feedback(OWNER, queryId, DataQueryCorrectionService.Category.INTENT_UNDERSTANDING,
				"x", 77L);
		assertThatThrownBy(() -> corrections.draft(actor("2", "3", "edit"), feedback, List.of("巡视", "巡查"), "2026-08"))
			.hasMessage("FEEDBACK_NOT_FOUND");
		String version = corrections.draft(editor, feedback, List.of("巡视", "巡查"), "2026-08");
		assertThatThrownBy(() -> corrections.publish(reviewer, version, 1, "x")).hasMessage("STATE_CONFLICT");
		corrections.verify(editor, version, 1);
		assertThatThrownBy(() -> corrections.approve(editor, version, 1)).hasMessage("REVIEWER_MUST_DIFFER");
		assertThatThrownBy(() -> corrections.approve(actor("1", "5", "review"), version, 1))
			.hasMessage("MANAGEMENT_FORBIDDEN");
		assertThatThrownBy(() -> corrections.approve(actor("2", "4", "review", "human_review"), version, 1))
			.hasMessage("DRAFT_NOT_FOUND");
		corrections.approve(reviewer, version, 1);
		corrections.edit(editor, version, 1, List.of("巡视", "巡查"), "2026-08");
		assertThat(corrections.inspect(editor, version).get("regression_digest")).isNull();
		assertThatThrownBy(() -> corrections.publish(reviewer, version, 1, "x")).hasMessage("REVISION_CONFLICT");
		assertThatThrownBy(() -> corrections.publish(reviewer, version, 2, "x")).hasMessage("STATE_CONFLICT");
		assertThat(repository.current(OWNER).version()).isEqualTo("r1");
	}

	@Test
	void retainedTemplateAdapterUsesOwnedQuestionWithoutModelOrFreeSql() {
		bind();
		grant();
		publishFixture("r1");
		jdbc.execute(
				"CREATE TABLE ai_chat_record (record_id BIGINT,tenant_id BIGINT,username VARCHAR(64),question_text TEXT,del_flag CHAR(1),answer_text TEXT)");
		jdbc.update(
				"INSERT INTO ai_chat_record (record_id,tenant_id,username,question_text,del_flag) VALUES (77,1,'synthetic-user','2026年8月各部门巡视记录数','0')");
		var count = new AtomicInteger();
		var provider = new org.springframework.beans.factory.support.StaticListableBeanFactory();
		provider.addBean("questions", questions(count));
		var gate = new DataQueryPilotGate("10", provider.getBeanProvider(DataQueryQuestionService.class));
		var data = org.mockito.Mockito.mock(com.deepdimen.matrix.knowledge.service.AiDataService.class);
		var model = org.mockito.Mockito.mock(com.deepdimen.matrix.knowledge.support.provider.ModelProvider.class);
		var sql = org.mockito.Mockito
			.mock(com.deepdimen.matrix.knowledge.support.function.Chat2SqlFunctionCalling.class);
		var rule = new com.deepdimen.matrix.knowledge.support.rule.DatabaseChatRule(sql, null, null, data, gate, model);
		var message = new com.deepdimen.matrix.knowledge.dto.ChatMessageDTO();
		message.setMessageKey(77L);
		message.setContent("forged question");
		var ext = new com.deepdimen.matrix.knowledge.dto.ChatMessageDTO.ExtDetails();
		ext.setDataId(10L);
		message.setExtDetails(ext);
		var user = org.mockito.Mockito.mock(com.deepdimen.matrix.common.security.service.MatrixUser.class);
		org.mockito.Mockito.when(user.getId()).thenReturn(2L);
		org.mockito.Mockito.when(user.getTenantId()).thenReturn(1L);
		org.mockito.Mockito.when(user.getUsername()).thenReturn("synthetic-user");
		try {
			org.springframework.security.core.context.SecurityContextHolder.getContext()
				.setAuthentication(
						new org.springframework.security.authentication.TestingAuthenticationToken(user, null, "USER"));
			assertThat(gate.process(message).getDataQuery().get("status")).isEqualTo("SUCCEEDED");
			assertThat(count.get()).isEqualTo(1);
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
			assertThat(gate.process(message).getDataQuery().get("code")).isEqualTo("UNAUTHENTICATED");
			org.mockito.Mockito.verifyZeroInteractions(data, model, sql);
		}
		finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}
	}

	@Test
	void executionFailureRetainsFeedbackProvenanceAndNonIntentCategoriesCannotPublish() {
		bind();
		grant();
		publishFixture("r1");
		var questions = new DataQueryQuestionService(repository,
				new ControlledQueryService(repository::resolve, repository::current, q -> {
					throw new DataQueryException("QUERY_TIMEOUT");
				}, audit(), CLOCK, 10, Duration.ofMinutes(5)));
		var error = catchThrowableOfType(() -> questions.ask(OWNER, 10, "2026年8月各部门巡视记录数", 77L),
				DataQueryException.class);
		assertThat(error.queryId()).isNotNull();
		assertThat(error.ruleVersion()).isEqualTo("r1");
		assertThat(DataQueryResponses.failure(error).getBody().get("queryId")).isEqualTo(error.queryId());
		var corrections = new DataQueryCorrectionService(source, repository, json, CLOCK);
		for (var category : List.of(DataQueryCorrectionService.Category.BUSINESS_DEFINITION,
				DataQueryCorrectionService.Category.RELATION_OR_NAME_MAPPING,
				DataQueryCorrectionService.Category.SOURCE_DATA_MISSING)) {
			var feedback = corrections.feedback(OWNER, error.queryId(), category, "Needs specialist review", 77L);
			assertThatThrownBy(
					() -> corrections.draft(actor("1", "3", "edit"), feedback, List.of("巡视", "巡查"), "2026-08"))
				.hasMessage("UNSUPPORTED_CORRECTION");
		}
	}

	@Test
	void staleActiveVersionRollsBackPublicationAtomically() {
		bind();
		grant();
		publishFixture("r1");
		var questions = questions(new AtomicInteger());
		var corrections = new DataQueryCorrectionService(source, repository, json, CLOCK);
		var editor = actor("1", "3", "edit", "verify");
		var reviewer = actor("1", "4", "review", "publish", "human_review");
		var feedback = corrections.feedback(OWNER, failedQuestion(questions),
				DataQueryCorrectionService.Category.INTENT_UNDERSTANDING, "x", 77L);
		String first = corrections.draft(editor, feedback, List.of("巡视", "巡查"), "2026-08");
		String second = corrections.draft(editor, feedback, List.of("巡视", "巡查"), "2026-08");
		corrections.verify(editor, first, 1);
		corrections.approve(reviewer, first, 1);
		corrections.verify(editor, second, 1);
		corrections.approve(reviewer, second, 1);
		corrections.publish(reviewer, first, 1, "first");
		assertThatThrownBy(() -> corrections.publish(reviewer, second, 1, "stale")).hasMessage("ACTIVE_RULE_CHANGED");
		assertThat(corrections.inspect(editor, second).get("state")).isEqualTo("APPROVED");
		assertThat(repository.current(OWNER).version()).isEqualTo(first);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_release_event", Integer.class)).isEqualTo(1);
	}

	@Test
	void existingEvaluationReturnsLinkedFeedbackWithoutPublishing() {
		bind();
		grant();
		publishFixture("r1");
		var queryId = failedQuestion(questions(new AtomicInteger()));
		var corrections = new DataQueryCorrectionService(source, repository, json, CLOCK);
		var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
		beans.addBean("corrections", corrections);
		var evaluations = new com.deepdimen.matrix.knowledge.service.impl.AiChatRecordServiceImpl(null, null, null,
				beans.getBeanProvider(DataQueryCorrectionService.class));
		var mapper = org.mockito.Mockito.mock(com.deepdimen.matrix.knowledge.mapper.AiChatRecordMapper.class);
		org.springframework.test.util.ReflectionTestUtils.setField(evaluations, "baseMapper", mapper);
		var record = new com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity();
		record.setRecordId(77L);
		record.setUsername("synthetic-user");
		org.mockito.Mockito.when(mapper.selectById(77L)).thenReturn(record);
		var user = org.mockito.Mockito.mock(com.deepdimen.matrix.common.security.service.MatrixUser.class);
		org.mockito.Mockito.when(user.getId()).thenReturn(2L);
		org.mockito.Mockito.when(user.getTenantId()).thenReturn(1L);
		org.mockito.Mockito.when(user.getUsername()).thenReturn("synthetic-user");
		var request = new com.deepdimen.matrix.knowledge.dto.ChatEvaluateDTO();
		request.setMessageId(77L);
		request.setType("dislike");
		request.setQueryId(queryId);
		request.setCategory(DataQueryCorrectionService.Category.INTENT_UNDERSTANDING);
		request.setDescription("synthetic feedback");
		request.setRequestId(UUID.randomUUID().toString());
		try {
			org.springframework.security.core.context.SecurityContextHolder.getContext()
				.setAuthentication(
						new org.springframework.security.authentication.TestingAuthenticationToken(user, null, "USER"));
			var response = evaluations.evaluate(request);
			assertThat(response.getCode()).isZero();
			assertThat(((java.util.Map<?, ?>) response.getData()).get("feedbackId")).isNotNull();
			assertThat(evaluations.evaluate(request).getData()).isEqualTo(response.getData());
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_feedback", Integer.class)).isEqualTo(1);
			assertThat(repository.current(OWNER).version()).isEqualTo("r1");
			request.setQueryId(null);
			assertThat(evaluations.evaluate(request).getData()).isNull();
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_feedback", Integer.class)).isEqualTo(1);
		}
		finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}
	}

	private com.fasterxml.jackson.databind.JsonNode httpQuestion(DataQueryQuestionService questions, String question)
			throws Exception {
		var user = org.mockito.Mockito.mock(com.deepdimen.matrix.common.security.service.MatrixUser.class);
		org.mockito.Mockito.when(user.getId()).thenReturn(2L);
		org.mockito.Mockito.when(user.getTenantId()).thenReturn(1L);
		try {
			org.springframework.security.core.context.SecurityContextHolder.getContext()
				.setAuthentication(
						new org.springframework.security.authentication.TestingAuthenticationToken(user, null, "USER"));
			var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
				.standaloneSetup(new DataQueryQuestionController(questions))
				.build();
			var response = mvc
				.perform(
						org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/data-query/question")
							.contentType("application/json")
							.content(json.writeValueAsString(java.util.Map.of("dataId", "10", "question", question))))
				.andReturn()
				.getResponse();
			assertThat(response.getStatus()).isEqualTo(200);
			return json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
		}
		finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}
	}

	@Test
	void duplicateDatasetAndRenamedDatasourceCannotBypassPilotFence() {
		bind();
		var fence = new DataQueryLegacyFence(source);
		jdbc.update("INSERT INTO ai_data VALUES (11,1,'synthetic-source','0')");
		jdbc.update(
				"INSERT INTO ai_datasource VALUES (21,2,'renamed-source',?,'other-reader','synthetic-only','mysql','0')",
				URL + "?useUnicode=true");
		jdbc.update("INSERT INTO ai_data VALUES (12,2,'renamed-source','0')");
		jdbc.update(
				"INSERT INTO ai_datasource VALUES (22,1,'unrelated-source','jdbc:mysql://other.invalid:3306/other_db','reader','synthetic-only','mysql','0')");
		jdbc.update("INSERT INTO ai_data VALUES (13,1,'unrelated-source','0')");
		for (long id : List.of(11L, 12L))
			assertThatThrownBy(() -> fence.rejectProtectedAlias(id, java.util.Set.of(10L)))
				.hasMessage("CONTROLLED_QUERY_REQUIRED");
		fence.rejectProtectedAlias(13, java.util.Set.of(10L));
		assertThatThrownBy(() -> fence.rejectProtectedAlias(13, java.util.Set.of(99L)))
			.hasMessage("PILOT_INVENTORY_UNAVAILABLE");
		var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
		beans.addBean("fence", fence);
		var gate = new DataQueryPilotGate("10", beans.getBeanProvider(DataQueryQuestionService.class));
		org.springframework.test.util.ReflectionTestUtils.setField(gate, "legacyFences",
				beans.getBeanProvider(DataQueryLegacyFence.class));
		var mapper = org.mockito.Mockito.mock(com.deepdimen.matrix.knowledge.mapper.AiDataMapper.class);
		var executor = org.mockito.Mockito.mock(com.deepdimen.matrix.knowledge.service.AiDatasourceService.class);
		var controller = new com.deepdimen.matrix.knowledge.controller.DifyDataQueryController(mapper, null, null,
				executor,
				org.mockito.Mockito.mock(com.deepdimen.matrix.common.security.component.ApiTokenValidator.class), gate);
		for (long id : List.of(11L, 12L))
			assertThat(controller
				.query(java.util.Map.of("dataset_id", id, "sql", "SELECT * FROM device_task_record"),
						new org.springframework.mock.web.MockHttpServletRequest())
				.getStatusCode()
				.value()).isEqualTo(403);
		org.mockito.Mockito.verifyZeroInteractions(mapper, executor);
	}

	@Test
	void feedbackRetryIsPersistentlyIdempotentAndConflictCannotChangeOriginal() throws Exception {
		bind();
		grant();
		publishFixture("r1");
		var questions = questions(new AtomicInteger());
		String queryId = failedQuestion(questions);
		var corrections = new DataQueryCorrectionService(source, repository, json, CLOCK);
		String requestId = UUID.randomUUID().toString();
		String saved = corrections.feedback(OWNER, queryId, DataQueryCorrectionService.Category.INTENT_UNDERSTANDING,
				"same description", 77L, requestId);
		// Recreate service to prove a response-loss retry is not just an in-memory cache.
		var reopened = new DataQueryCorrectionService(source, repository, json, CLOCK);
		assertThat(reopened.feedback(OWNER, queryId, DataQueryCorrectionService.Category.INTENT_UNDERSTANDING,
				"same description", 77L, requestId))
			.isEqualTo(saved);
		assertThatThrownBy(() -> reopened.feedback(OWNER, queryId,
				DataQueryCorrectionService.Category.INTENT_UNDERSTANDING, "changed description", 77L, requestId))
			.hasMessage("IDEMPOTENCY_CONFLICT");
		assertThatThrownBy(() -> reopened.feedback(new Identity("2", "2"), queryId,
				DataQueryCorrectionService.Category.INTENT_UNDERSTANDING, "same description", 77L, requestId))
			.hasMessage("QUERY_NOT_FOUND");
		assertThatThrownBy(() -> reopened.feedback(OWNER, queryId,
				DataQueryCorrectionService.Category.INTENT_UNDERSTANDING, "same description", 78L, requestId))
			.hasMessage("CHAT_RECORD_NOT_OWNED");
		assertThatThrownBy(() -> reopened.feedback(OWNER, queryId,
				DataQueryCorrectionService.Category.INTENT_UNDERSTANDING, "same description", 77L, "not-a-uuid"))
			.hasMessage("INVALID_REQUEST");
		var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
		try {
			var calls = new java.util.ArrayList<java.util.concurrent.Callable<String>>();
			for (int n = 0; n < 4; n++)
				calls.add(() -> reopened.feedback(OWNER, queryId,
						DataQueryCorrectionService.Category.INTENT_UNDERSTANDING, "same description", 77L, requestId));
			for (var response : pool.invokeAll(calls))
				assertThat(response.get()).isEqualTo(saved);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_feedback", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT description FROM ai_dataquery_feedback", String.class))
			.isEqualTo("same description");
	}

	@Test
	void historyRestoresOwnedReferenceAndReceiptWithoutBrowserCacheButNeverGuessesAmbiguousQueries() {
		bind();
		grant();
		publishFixture("r1");
		var questions = questions(new AtomicInteger());
		String queryId = failedQuestion(questions);
		jdbc.execute(
				"CREATE TABLE ai_chat_record (record_id BIGINT,tenant_id BIGINT,username VARCHAR(64),question_text TEXT,del_flag CHAR(1))");
		String original = repository.findOwnedQuery(OWNER, queryId).orElseThrow().originalQuestion();
		jdbc.update("INSERT INTO ai_chat_record VALUES (77,1,'synthetic-user',?,'0')", original);
		var corrections = new DataQueryCorrectionService(source, repository, json, CLOCK);
		String receipt = corrections.feedback(OWNER, queryId, DataQueryCorrectionService.Category.INTENT_UNDERSTANDING,
				"x", 77L);
		var reopened = new DataQueryMetadataRepository(source, json, CLOCK);
		var history = reopened.findOwnedChatReferences(OWNER, "synthetic-user", List.of(77L));
		assertThat(history.get(77L).state()).isEqualTo("AVAILABLE");
		assertThat(history.get(77L).dataQuery().get("queryId")).isEqualTo(queryId);
		assertThat(history.get(77L).dataQuery().get("ruleVersion")).isEqualTo("r1");
		assertThat(history.get(77L).dataQuery()).doesNotContainKeys("rows", "candidate_json", "companyId");
		assertThat(history.get(77L).feedbackId()).isEqualTo(receipt);
		assertThat(reopened.findOwnedChatReferences(new Identity("2", "2"), "synthetic-user", List.of(77L))).isEmpty();
		assertThat(reopened.findOwnedChatReferences(new Identity("1", "9"), "synthetic-user", List.of(77L))).isEmpty();
		assertThat(reopened.findOwnedChatReferences(OWNER, "other-user", List.of(77L))).isEmpty();
		jdbc.update("UPDATE ai_chat_record SET question_text='changed' WHERE record_id=77");
		assertThat(reopened.findOwnedChatReferences(OWNER, "synthetic-user", List.of(77L)).get(77L).state())
			.isEqualTo("UNAVAILABLE");
		jdbc.update("UPDATE ai_chat_record SET question_text=? WHERE record_id=77", original);
		failedQuestion(questions);
		var ambiguous = reopened.findOwnedChatReferences(OWNER, "synthetic-user", List.of(77L)).get(77L);
		assertThat(ambiguous.state()).isEqualTo("AMBIGUOUS");
		assertThat(ambiguous.dataQuery()).isNull();
		assertThat(ambiguous.feedbackId()).isNull();
		jdbc.update("UPDATE ai_chat_record SET del_flag='1' WHERE record_id=77");
		assertThat(reopened.findOwnedChatReferences(OWNER, "synthetic-user", List.of(77L))).isEmpty();
	}

	@Test
	void existingHistoryEndpointAddsDurableReferenceAndFailsSafeWhenAuditStoreUnavailable() {
		bind();
		grant();
		publishFixture("r1");
		String queryId = failedQuestion(questions(new AtomicInteger()));
		jdbc.execute(
				"CREATE TABLE ai_chat_record (record_id BIGINT,tenant_id BIGINT,username VARCHAR(64),question_text TEXT,del_flag CHAR(1))");
		jdbc.update("INSERT INTO ai_chat_record VALUES (77,1,'synthetic-user',?,'0')",
				repository.findOwnedQuery(OWNER, queryId).orElseThrow().originalQuestion());
		var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
		beans.addBean("corrections", new DataQueryCorrectionService(source, repository, json, CLOCK));
		var record = new com.deepdimen.matrix.knowledge.entity.AiChatRecordEntity();
		record.setRecordId(77L);
		record.setUsername("synthetic-user");
		record.setAnswerText("historical answer");
		var service = org.mockito.Mockito.spy(new com.deepdimen.matrix.knowledge.service.impl.AiChatRecordServiceImpl(
				null, null, null, beans.getBeanProvider(DataQueryCorrectionService.class)));
		org.mockito.Mockito.doReturn(List.of(record))
			.when(service)
			.list(org.mockito.ArgumentMatchers.any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
		var user = org.mockito.Mockito.mock(com.deepdimen.matrix.common.security.service.MatrixUser.class);
		org.mockito.Mockito.when(user.getId()).thenReturn(2L);
		org.mockito.Mockito.when(user.getTenantId()).thenReturn(1L);
		org.mockito.Mockito.when(user.getUsername()).thenReturn("synthetic-user");
		try {
			org.springframework.security.core.context.SecurityContextHolder.getContext()
				.setAuthentication(
						new org.springframework.security.authentication.TestingAuthenticationToken(user, null, "USER"));
			var controller = new com.deepdimen.matrix.knowledge.controller.AiChatController(null, service, null, null);
			var response = controller.recordList("conversation").getData().get(0);
			assertThat(response.getDataQuery().get("queryId")).isEqualTo(queryId);
			assertThat(response.getDataQueryReferenceState()).isEqualTo("AVAILABLE");
			jdbc.execute("ALTER TABLE ai_dataquery_query RENAME TO unavailable_query_audit");
			response = controller.recordList("conversation").getData().get(0);
			assertThat(response.getAnswerText()).isEqualTo("historical answer");
			assertThat(response.getDataQueryReferenceState()).isEqualTo("UNAVAILABLE");
			assertThat(response.getDataQuery()).isNull();
		}
		finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}
	}

	@Test
	void feedbackHttpRetryReturnsSameIdAndConflictStatus() throws Exception {
		bind();
		grant();
		publishFixture("r1");
		String queryId = failedQuestion(questions(new AtomicInteger()));
		var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
			.standaloneSetup(
					new DataQueryCorrectionController(new DataQueryCorrectionService(source, repository, json, CLOCK)))
			.build();
		var user = org.mockito.Mockito.mock(com.deepdimen.matrix.common.security.service.MatrixUser.class);
		org.mockito.Mockito.when(user.getId()).thenReturn(2L);
		org.mockito.Mockito.when(user.getTenantId()).thenReturn(1L);
		try {
			org.springframework.security.core.context.SecurityContextHolder.getContext()
				.setAuthentication(
						new org.springframework.security.authentication.TestingAuthenticationToken(user, null, "USER"));
			String request = json.writeValueAsString(java.util.Map.of("queryId", queryId, "category",
					"INTENT_UNDERSTANDING", "description", "same", "requestId", UUID.randomUUID().toString()));
			var post = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/data-query/feedback")
				.contentType("application/json")
				.content(request);
			String first = mvc.perform(post).andReturn().getResponse().getContentAsString();
			assertThat(mvc.perform(post).andReturn().getResponse().getContentAsString()).isEqualTo(first);
			mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/data-query/feedback")
				.contentType("application/json")
				.content(request.replace("same", "different")))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_feedback", Integer.class)).isEqualTo(1);
		}
		finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}
	}

	@Test
	void managementWorkbenchIsBoundedTenantScopedAndReadableByIndependentReviewer() {
		bind();
		grant();
		publishFixture("r1");
		var corrections = new DataQueryCorrectionService(source, repository, json, CLOCK);
		String queryId = failedQuestion(questions(new AtomicInteger()));
		String feedback = corrections.feedback(OWNER, queryId, DataQueryCorrectionService.Category.INTENT_UNDERSTANDING,
				"synthetic description", null);
		var editor = actor("1", "3", "edit", "verify");
		String version = corrections.draft(editor, feedback, List.of("巡视", "巡查"), "2026-08");
		var reviewer = actor("1", "4", "review", "publish", "human_review");
		var workbench = corrections.workbench(reviewer);
		assertThat(workbench.activeVersion()).isEqualTo("r1");
		assertThat(workbench.actorId()).isEqualTo("4");
		assertThat(workbench.feedback()).hasSize(1);
		assertThat(workbench.feedback().get(0).get("feedback_id")).isEqualTo(feedback);
		assertThat(workbench.feedback().get(0).get("query_id")).isEqualTo(queryId);
		assertThat(workbench.feedback().get(0).get("rule_version")).isEqualTo("r1");
		assertThat(corrections.inspect(reviewer, version).get("state")).isEqualTo("DRAFT");
		assertThatThrownBy(() -> corrections.workbench(actor("1", "2"))).hasMessage("MANAGEMENT_FORBIDDEN");
		assertThatThrownBy(() -> corrections.workbench(actor("1", "4", "review"))).hasMessage("MANAGEMENT_FORBIDDEN");
		assertThatThrownBy(() -> corrections.edit(reviewer, version, 1, List.of("巡视"), "2026-08"))
			.hasMessage("MANAGEMENT_FORBIDDEN");
		var foreign = corrections.workbench(actor("2", "4", "review", "human_review"));
		assertThat(foreign.feedback()).isEmpty();
		assertThat(foreign.versions()).isEmpty();
		assertThat(foreign.activeVersion()).isNull();
		assertThatThrownBy(() -> corrections.inspect(actor("2", "4", "review", "human_review"), version))
			.hasMessage("DRAFT_NOT_FOUND");
		for (int i = 0; i < 55; i++) {
			corrections.feedback(OWNER, queryId, DataQueryCorrectionService.Category.SOURCE_DATA_MISSING,
					"bounded " + i, null);
		}
		assertThat(corrections.workbench(editor).feedback()).hasSize(50);
		assertThat(corrections.workbench(editor).versions()).hasSize(2);
	}

	@Test
	void managementHttpWorkflowReusesPublishedRulesAndRollsBackWithoutEditorReviewPermission() throws Exception {
		bind();
		grant();
		publishFixture("r1");
		var executions = new AtomicInteger();
		var corrections = new DataQueryCorrectionService(source, repository, json, CLOCK);
		var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
			.standaloneSetup(new DataQueryCorrectionController(corrections),
					new DataQueryQuestionController(questions(executions)))
			.build();
		String question = "{\"dataId\":10,\"question\":\"2026年8月各部门巡查记录数\"}";
		try {
			managementLogin(1, 2, "USER");
			var before = http(mvc, "POST", "/data-query/question", question);
			assertThat(before.get("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
			String queryId = before.get("queryId").asText();
			var receipt = http(mvc, "POST", "/data-query/feedback",
					json.writeValueAsString(java.util.Map.of("queryId", queryId, "category", "INTENT_UNDERSTANDING",
							"description", "synthetic alias", "requestId", UUID.randomUUID().toString())));
			managementLogin(1, 3, "knowledge_dataquery_edit", "knowledge_dataquery_verify");
			var draft = http(mvc, "POST", "/data-query/manage/drafts",
					json.writeValueAsString(java.util.Map.of("feedbackId", receipt.get("feedbackId").asText(),
							"intentTerms", List.of("巡视", "巡查"), "expectedMonth", "2026-08")));
			String version = draft.get("ruleVersion").asText();
			String path = "/data-query/manage/drafts/" + version;
			assertThat(http(mvc, "POST", path + "/verify", "{\"revision\":1}").get("report").asText())
				.startsWith("PASS:");
			managementLogin(1, 3, "knowledge_dataquery_review", "knowledge_dataquery_human_review");
			mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path + "/approve")
				.contentType("application/json")
				.content("{\"revision\":1}"))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
			managementLogin(1, 4, "knowledge_dataquery_review", "knowledge_dataquery_publish",
					"knowledge_dataquery_human_review");
			var details = http(mvc, "GET", path, null);
			assertThat(details.get("source_feedback").get("query_id").asText()).isEqualTo(queryId);
			assertThat(http(mvc, "GET", "/data-query/manage/workbench", null).get("feedback").size()).isEqualTo(1);
			http(mvc, "POST", path + "/approve", "{\"revision\":1}");
			http(mvc, "POST", path + "/publish", "{\"revision\":1,\"reason\":\"synthetic human approval\"}");
			assertThat(http(mvc, "GET", "/data-query/manage/workbench", null).get("activeVersion").asText())
				.isEqualTo(version);
			managementLogin(1, 2, "USER");
			var after = http(mvc, "POST", "/data-query/question", question);
			assertThat(after.get("status").asText()).isEqualTo("SUCCEEDED");
			assertThat(after.get("queryId").asText()).isNotEqualTo(queryId);
			assertThat(after.get("ruleVersion").asText()).isEqualTo(version);
			assertThat(executions.get()).isEqualTo(1);
			managementLogin(1, 4, "knowledge_dataquery_publish", "knowledge_dataquery_human_review");
			http(mvc, "POST", "/data-query/manage/rollback", json.writeValueAsString(java.util.Map
				.of("expectedActiveVersion", version, "targetVersion", "r1", "reason", "synthetic rollback")));
			managementLogin(1, 2, "USER");
			var restored = http(mvc, "POST", "/data-query/question", question);
			assertThat(restored.get("status").asText()).isEqualTo("NEEDS_CLARIFICATION");
			assertThat(restored.get("ruleVersion").asText()).isEqualTo("r1");
			assertThat(executions.get()).isEqualTo(1);
		}
		finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}
	}

	private void managementLogin(long tenant, long userId, String... permissions) {
		var user = org.mockito.Mockito.mock(com.deepdimen.matrix.common.security.service.MatrixUser.class);
		org.mockito.Mockito.when(user.getId()).thenReturn(userId);
		org.mockito.Mockito.when(user.getTenantId()).thenReturn(tenant);
		org.springframework.security.core.context.SecurityContextHolder.getContext()
			.setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken(user, null,
					permissions));
	}

	private com.fasterxml.jackson.databind.JsonNode http(org.springframework.test.web.servlet.MockMvc mvc,
			String method, String path, String body) throws Exception {
		var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.request(org.springframework.http.HttpMethod.valueOf(method), path);
		if (body != null)
			request.contentType("application/json").content(body);
		var response = mvc.perform(request)
			.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
			.andReturn()
			.getResponse();
		return json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
	}

	private java.util.Map<String, Object> templateQuestion(DataQueryQuestionService questions, String question,
			long messageId) {
		jdbc.execute(
				"CREATE TABLE IF NOT EXISTS ai_chat_record (record_id BIGINT,tenant_id BIGINT,username VARCHAR(64),question_text TEXT,del_flag CHAR(1),answer_text TEXT)");
		jdbc.update(
				"INSERT INTO ai_chat_record (record_id,tenant_id,username,question_text,del_flag) VALUES (?,1,'synthetic-user',?,'0')",
				messageId, question);
		var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
		beans.addBean("questions", questions);
		var gate = new DataQueryPilotGate("10", beans.getBeanProvider(DataQueryQuestionService.class));
		var data = org.mockito.Mockito.mock(com.deepdimen.matrix.knowledge.service.AiDataService.class);
		var model = org.mockito.Mockito.mock(com.deepdimen.matrix.knowledge.support.provider.ModelProvider.class);
		var sql = org.mockito.Mockito
			.mock(com.deepdimen.matrix.knowledge.support.function.Chat2SqlFunctionCalling.class);
		var rule = new com.deepdimen.matrix.knowledge.support.rule.DatabaseChatRule(sql, null, null, data, gate, model);
		var message = new com.deepdimen.matrix.knowledge.dto.ChatMessageDTO();
		message.setMessageKey(messageId);
		message.setContent("not trusted");
		var ext = new com.deepdimen.matrix.knowledge.dto.ChatMessageDTO.ExtDetails();
		ext.setDataId(10L);
		message.setExtDetails(ext);
		var user = org.mockito.Mockito.mock(com.deepdimen.matrix.common.security.service.MatrixUser.class);
		org.mockito.Mockito.when(user.getId()).thenReturn(2L);
		org.mockito.Mockito.when(user.getTenantId()).thenReturn(1L);
		org.mockito.Mockito.when(user.getUsername()).thenReturn("synthetic-user");
		try {
			org.springframework.security.core.context.SecurityContextHolder.getContext()
				.setAuthentication(
						new org.springframework.security.authentication.TestingAuthenticationToken(user, null, "USER"));
			var reply = gate.process(message);
			assertThat(jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=?", String.class,
					messageId))
				.isEqualTo(reply.getMessage());
			var result = reply.getDataQuery();
			org.mockito.Mockito.verifyZeroInteractions(data, model, sql);
			return result;
		}
		finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}
	}

	@Test
	void clarificationIsAuditedAndDoesNotRunAQueryOrRequireRuleCorrection() {
		bind();
		grant();
		publishFixture("r1");
		var executions = new AtomicInteger();
		var service = questions(executions);
		var response = service.ask(OWNER, 10, "8月份各部门巡视了多少次?", null);
		assertThat(response.get("code")).isEqualTo("QUERY_YEAR_REQUIRED");
		assertThat(response.get("message").toString()).contains("年份");
		assertThat(response).containsKey("clarification");
		assertThat(repository.findOwnedQuery(OWNER, response.get("queryId").toString()).orElseThrow().ruleVersion())
			.isEqualTo("r1");
		assertThat(executions.get()).isZero();
		var completed = service.ask(OWNER, 10, "2026年8月份各部门巡视了多少次?", null);
		assertThat(completed.get("status")).isEqualTo("SUCCEEDED");
		assertThat(completed.get("ruleVersion")).isEqualTo("r1");
		assertThat(executions.get()).isEqualTo(1);
	}

	@Test
	void retainedTemplateChatPersistsClarificationAndSuccessWithoutModelCallback() {
		bind();
		grant();
		publishFixture("r1");
		var executions = new AtomicInteger();
		var service = questions(executions);
		assertThat(templateQuestion(service, "8月份各部门巡视了多少次?", 801).get("code")).isEqualTo("QUERY_YEAR_REQUIRED");
		assertThat(executions.get()).isZero();
		assertThat(templateQuestion(service, "2026年8月各部门巡视了多少次?", 802).get("status")).isEqualTo("SUCCEEDED");
		assertThat(executions.get()).isEqualTo(1);
		var answer = jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=802", String.class);
		assertThat(answer).contains("|", "次数");
		assertThatThrownBy(() -> service.fromChat(new Identity("2", "2"), "synthetic-user", 802, 10))
			.isInstanceOf(DataQueryException.class);
		assertThatThrownBy(() -> service.fromChat(OWNER, "another-user", 802, 10))
			.isInstanceOf(DataQueryException.class);
		assertThat(jdbc.queryForObject("SELECT answer_text FROM ai_chat_record WHERE record_id=802", String.class))
			.isEqualTo(answer);
	}

	@Test
	void controlledChatAlsoPersistsDenialWithoutRunningSql() {
		bind();
		publishFixture("r1");
		var executions = new AtomicInteger();
		assertThat(templateQuestion(questions(executions), "2026年8月各部门巡视记录数", 803).get("status")).isEqualTo("REJECTED");
		assertThat(executions.get()).isZero();
	}

}
