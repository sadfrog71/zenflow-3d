package com.deepdimen.matrix.knowledge.support.dataquery;

import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.DataQuerySecurity.ManagementActor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real platform DDL / JDBC / transactions / controllers; all identities and TOP endpoints
 * are synthetic.
 */
class DataQuerySetupTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC);

	private static final String URL = "jdbc:mysql://synthetic-top.invalid:13306/top_device_biz";

	private static final String EXPIRY = "2026-10-01T00:00:00Z";

	private static final Identity OWNER = new Identity("1", "2");

	private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

	private DataSource source;

	private JdbcTemplate jdbc;

	private DataQuerySetupService service;

	private DataQueryMetadataRepository metadata;

	private DataQueryCorrectionService corrections;

	private MockMvc mvc;

	private ManagementActor admin, reviewer;

	@BeforeEach
	void setup() throws Exception {
		source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa",
				"");
		try (var connection = source.getConnection()) {
			ScriptUtils.executeSqlScript(connection, new FileSystemResource("../docs/dataquery/platform-schema.sql"));
			ScriptUtils.executeSqlScript(connection,
					new FileSystemResource("../docs/dataquery/configuration-schema.sql"));
			ScriptUtils.executeSqlScript(connection, new FileSystemResource("../docs/dataquery/access-schema.sql"));
		}
		jdbc = new JdbcTemplate(source);
		jdbc.execute(
				"CREATE TABLE ai_data (data_id BIGINT PRIMARY KEY,tenant_id BIGINT,dataset_name VARCHAR(80),ds_name VARCHAR(64),del_flag CHAR(1))");
		jdbc.execute(
				"CREATE TABLE ai_datasource (id BIGINT PRIMARY KEY,tenant_id BIGINT,name VARCHAR(64),url VARCHAR(1000),username VARCHAR(64),password VARCHAR(64),ds_type VARCHAR(32),del_flag CHAR(1))");
		jdbc.execute(
				"CREATE TABLE sys_user (user_id BIGINT PRIMARY KEY,tenant_id BIGINT,username VARCHAR(64),lock_flag CHAR(1),del_flag CHAR(1))");
		jdbc.update(
				"INSERT INTO ai_datasource VALUES (20,1,'top',?,'synthetic-reader','synthetic-password','mysql','0')",
				URL);
		jdbc.update("INSERT INTO ai_datasource VALUES (21,2,'other',?,'other-reader','synthetic-password','mysql','0')",
				URL);
		jdbc.update(
				"INSERT INTO ai_data VALUES (10,1,'synthetic TOP','top','0'),(11,1,'duplicate TOP','top','0'),(12,2,'other tenant','other','0')");
		jdbc.update(
				"INSERT INTO sys_user VALUES (2,1,'reader','0','0'),(3,1,'editor','0','0'),(4,1,'reviewer','0','0'),(5,1,'locked','1','0'),(22,2,'other-tenant','0','0')");
		jdbc.execute("CREATE TABLE sys_tenant_user (tenant_id BIGINT,user_id BIGINT,PRIMARY KEY(tenant_id,user_id))");
		jdbc.update("INSERT INTO sys_tenant_user SELECT tenant_id,user_id FROM sys_user");
		MatrixAccessFixture.organization(jdbc);
		metadata = new DataQueryMetadataRepository(source, json, CLOCK);
		corrections = new DataQueryCorrectionService(source, metadata, json, CLOCK);
		service = new DataQuerySetupService(source, json, CLOCK, true, false);
		admin = actor("1", "3", "knowledge_aiData_view", "knowledge_dataquery_configure",
				"knowledge_dataquery_human_review", "knowledge_dataquery_edit", "knowledge_dataquery_verify");
		reviewer = actor("1", "4", "knowledge_aiData_view", "knowledge_dataquery_review", "knowledge_dataquery_publish",
				"knowledge_dataquery_human_review");
		mvc = MockMvcBuilders
			.standaloneSetup(new DataQuerySetupController(service), new DataQueryCorrectionController(corrections))
			.build();
	}

	@AfterEach
	void clearIdentity() {
		SecurityContextHolder.clearContext();
	}

	private ManagementActor actor(String tenant, String user, String... permissions) {
		return new ManagementActor(new Identity(tenant, user), Set.of(permissions));
	}

	private void login(ManagementActor actor) {
		MatrixUser user = mock(MatrixUser.class);
		when(user.getId()).thenReturn(Long.valueOf(actor.identity().userId()));
		when(user.getTenantId()).thenReturn(Long.valueOf(actor.identity().tenantId()));
		SecurityContextHolder.getContext()
			.setAuthentication(new TestingAuthenticationToken(user, null, actor.permissions().toArray(String[]::new)));
	}

	private DataQuerySetupService.Binding bind() {
		String fingerprint = (String) service.view(admin, "10").get("connectionFingerprint");
		return service.saveBinding(admin, "10", "unconfigured", fingerprint, true,
				"Synthetic human-approved test binding");
	}

	private List<DataQueryAccessRepository.Target> targets() {
		return List.of(new DataQueryAccessRepository.Target("user", "2"),
				new DataQueryAccessRepository.Target("dept", "100"));
	}

	private void grant() {
		service.saveAccess(admin, "10", "unconfigured", targets(), EXPIRY, "Synthetic MATRIX dataset access");
	}

	private String initial() {
		return corrections.initialDraft(admin, "10", DataQueryMetadataRepository.DEFINITION_DIGEST, true, "2026-08",
				"Synthetic TEST definition confirmation; not production business approval");
	}

	@Test
	void readOnlyStatusWorksWhileStorageAndExecutionAreOffAndNoCredentialEscapes() throws Exception {
		var off = new DataQuerySetupService(source, json, CLOCK, false, false);
		var result = off.view(admin, "10");
		assertThat(((List<?>) result.get("blockers"))
			.containsAll(List.of("SETUP_STORAGE_DISABLED", "CONTROLLED_EXECUTION_DISABLED"))).isTrue();
		assertThat(json.writeValueAsString(result)).doesNotContain("synthetic-reader", "synthetic-password",
				"password");
		assertThatThrownBy(() -> off.saveBinding(admin, "10", "unconfigured", "x", true, "reason"))
			.hasMessage("SETUP_STORAGE_DISABLED");
		jdbc.execute("DROP TABLE ai_dataquery_config_event");
		assertThat(((List<?>) service.view(admin, "10").get("blockers")).contains("SETUP_SCHEMA_REQUIRED")).isTrue();
	}

	@Test
	void managementStorageCanRunBeforeExecutionWithoutCreatingControlledQueryService() {
		new ApplicationContextRunner()
			.withUserConfiguration(DataQueryConfiguration.class, DataQueryStorageConfiguration.class)
			.withBean(DataSource.class, () -> source)
			.withBean(ObjectMapper.class, () -> json)
			.withPropertyValues("ai.dataquery.enabled=false", "ai.dataquery.persistence.enabled=true")
			.run(context -> {
				assertThat(context).hasSingleBean(DataQuerySetupService.class)
					.hasSingleBean(DataQueryCorrectionService.class)
					.hasSingleBean(DataQueryMetadataRepository.class);
				assertThat(context).doesNotHaveBean(ControlledQueryService.class)
					.doesNotHaveBean(DataQueryQuestionService.class);
			});
	}

	@Test
	void httpRequiresTrustedIdentityExplicitHumanPermissionAndRejectsIdentityFields() throws Exception {
		mvc.perform(get("/data-query/setup/10")).andExpect(status().isUnauthorized());
		login(actor("1", "3", "knowledge_aiData_view"));
		mvc.perform(get("/data-query/setup/10"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.canConfigure").value(false));
		mvc.perform(get("/data-query/setup/10/users")).andExpect(status().isForbidden());
		login(admin);
		for (String extra : List.of("tenantId", "approvedBy", "approvedAt", "sql", "executionEnabled")) {
			mvc.perform(put("/data-query/setup/10/binding").contentType("application/json")
				.content("{\"expectedRevision\":\"unconfigured\",\"enabled\":true,\"reason\":\"test\",\"" + extra
						+ "\":\"forged\"}"))
				.andExpect(status().isBadRequest());
		}
		mvc.perform(get("/data-query/setup/12")).andExpect(status().isNotFound());
		assertThat(service.users(admin, "10", "")).extracting(DataQuerySetupService.UserOption::userId)
			.doesNotContain("22");
		assertThatThrownBy(
				() -> service.saveBinding(actor("1", "3", "knowledge_aiData_view", "knowledge_dataquery_configure"),
						"10", "unconfigured", "x", true, "test"))
			.hasMessage("MANAGEMENT_FORBIDDEN");
	}

	@Test
	void bindingIsCompareAndSetSourcePinnedAndCannotTransferTenantPilotGrants() {
		var bound = bind();
		assertThat(metadata.binding(OWNER).dataId()).isEqualTo(10);
		assertThatThrownBy(() -> service.saveBinding(admin, "10", "unconfigured", bound.fingerprint(), true, "retry"))
			.hasMessage("REVISION_CONFLICT");
		assertThatThrownBy(
				() -> service.saveBinding(admin, "11", bound.revision(), bound.fingerprint(), true, "rebind"))
			.hasMessage("TENANT_PILOT_ALREADY_BOUND");
		jdbc.update("UPDATE ai_datasource SET username='changed-reader' WHERE id=20");
		assertThatThrownBy(
				() -> service.saveBinding(admin, "10", bound.revision(), bound.fingerprint(), true, "stale preview"))
			.hasMessage("DATASOURCE_BINDING_CHANGED");
		var revoked = service.saveBinding(admin, "10", bound.revision(), null, false, "Revoke changed source");
		assertThat(revoked.enabled()).isFalse();
		assertThat(metadata.registeredPilotIds()).containsExactly(10L);
		assertThat(
				new DataQuerySetupService(source, json, CLOCK, false, false).view(admin, "10").get("registeredPilot"))
			.isEqualTo(true);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_dataquery_config_event", Integer.class)).isEqualTo(2);
	}

	@Test
	void ambiguousOrUnsafeSourcesAreNotApprovedAndNoUrlIsReturnedForUnsafeTargets() {
		jdbc.update("INSERT INTO ai_datasource VALUES (23,1,'top',?,'reader','synthetic','mysql','0')", URL);
		assertThatThrownBy(this::bind).hasMessage("SETUP_SOURCE_AMBIGUOUS");
		jdbc.update("DELETE FROM ai_datasource WHERE id=23");
		jdbc.update("UPDATE ai_datasource SET url='jdbc:mysql://outside.invalid/another_database' WHERE id=20");
		assertThat(service.view(admin, "10").get("endpoint")).isNull();
		assertThatThrownBy(this::bind).hasMessage("DATASOURCE_CONFIGURATION_UNSAFE");
	}

	@Test
	void grantPairingExpiryTenantIsolationRevocationAndRevisionsAreEnforced() {
		bind();
		grant();
		var resolved = metadata.resolve(OWNER);
		assertThat(resolved.source().dataId()).isEqualTo(10);
		assertThatThrownBy(() -> service.saveAccess(admin, "10", service.access(admin, "10").revision(),
				List.of(new DataQueryAccessRepository.Target("user", "22")), EXPIRY, "cross tenant"))
			.hasMessage("SETUP_TARGET_NOT_FOUND");
		assertThatThrownBy(() -> service.saveAccess(admin, "10", service.access(admin, "10").revision(),
				List.of(new DataQueryAccessRepository.Target("user", "5")), EXPIRY, "inactive user"))
			.hasMessage("SETUP_TARGET_NOT_FOUND");
		assertThatThrownBy(() -> service.saveAccess(admin, "10", "unconfigured", targets(), EXPIRY, "stale"))
			.hasMessage("REVISION_CONFLICT");
		var current = service.access(admin, "10");
		for (String expiry : Arrays.asList(null, "2026-09-01T00:00:00Z", "not-a-time"))
			assertThatThrownBy(
					() -> service.saveAccess(admin, "10", current.revision(), targets(), expiry, "invalid expiry"))
				.hasMessage("INVALID_REQUEST");
		assertThatThrownBy(() -> service.saveAccess(admin, "10", current.revision(),
				List.of(new DataQueryAccessRepository.Target("user", "*")), EXPIRY, "no wildcard"))
			.hasMessage("INVALID_REQUEST");
		var revoked = service.saveAccess(admin, "10", current.revision(), List.of(), null, "revoke all explicit pairs");
		assertThat(revoked.targets()).isEmpty();
		assertThat(revoked.revision()).isNotEqualTo(current.revision());
		assertThatThrownBy(() -> metadata.resolve(OWNER)).hasMessage("DATASET_NOT_AUTHORIZED");
	}

	@Test
	void auditFailureRollsBackConfigurationInsteadOfReportingSaved() {
		bind();
		grant();
		var before = service.access(admin, "10");
		jdbc.execute(
				"ALTER TABLE ai_dataquery_config_event ADD CONSTRAINT synthetic_audit_failure CHECK (action <> 'DATASET_ACCESS') NOCHECK");
		assertThatThrownBy(() -> service.saveAccess(admin, "10", before.revision(), List.of(), null, "revocation"))
			.isInstanceOf(org.springframework.dao.DataAccessException.class);
		assertThat(service.access(admin, "10")).isEqualTo(before);
		assertThat(metadata.resolve(OWNER).revision()).isNotBlank();
	}

	@Test
	void dynamicBindingFencesOldSqlAndDuplicateDatasetWhileExecutionIsOff() {
		var beans = new StaticListableBeanFactory();
		beans.addBean("metadata", metadata);
		beans.addBean("fence", new DataQueryLegacyFence(source));
		var gate = new DataQueryPilotGate("", beans.getBeanProvider(DataQueryQuestionService.class));
		ReflectionTestUtils.setField(gate, "legacyFences", beans.getBeanProvider(DataQueryLegacyFence.class));
		assertThat(gate.isControlled(10L)).isFalse();
		var binding = bind();
		assertThat(gate.isControlled(10L)).isTrue();
		assertThatThrownBy(() -> gate.rejectLegacy(10L)).hasMessage("CONTROLLED_QUERY_REQUIRED");
		assertThatThrownBy(() -> gate.rejectLegacy(11L)).hasMessage("CONTROLLED_QUERY_REQUIRED");
		service.saveBinding(admin, "10", binding.revision(), binding.fingerprint(), false, "disabled but keep fence");
		assertThatThrownBy(() -> gate.rejectLegacy(10L)).hasMessage("CONTROLLED_QUERY_REQUIRED");
		jdbc.execute("DROP TABLE ai_dataquery_binding");
		assertThatThrownBy(() -> gate.rejectLegacy(11L)).hasMessage("PILOT_INVENTORY_UNAVAILABLE");
	}

	@Test
	void initialRuleHasNoAutomaticApprovalAndUsesExistingHumanLifecycleAndCurrentRuleReader() {
		bind();
		grant();
		assertThatThrownBy(() -> corrections.initialDraft(admin, "10", DataQueryMetadataRepository.DEFINITION_DIGEST,
				false, "2026-08", "not acknowledged"))
			.hasMessage("INVALID_REQUEST");
		String version = initial();
		assertThat(metadata.current(OWNER)).isNull();
		assertThat(corrections.verify(admin, version, 1)).startsWith("PASS: initial TEST");
		assertThatThrownBy(() -> corrections.approve(admin, version, 1)).hasMessage("MANAGEMENT_FORBIDDEN");
		var selfReviewer = actor("1", "3", "knowledge_dataquery_review", "knowledge_dataquery_human_review");
		assertThatThrownBy(() -> corrections.approve(selfReviewer, version, 1)).hasMessage("REVIEWER_MUST_DIFFER");
		corrections.approve(reviewer, version, 1);
		assertThat(metadata.current(OWNER)).isNull();
		corrections.publish(reviewer, version, 1, "Independent TEST release");
		assertThat(metadata.current(OWNER).version()).isEqualTo(version);
		assertThat(metadata.resolve(OWNER).revision()).isNotBlank();
		assertThatThrownBy(this::initial).hasMessage("ACTIVE_RULE_CHANGED");
		assertThat(jdbc.queryForObject("SELECT from_version FROM ai_dataquery_release_event", String.class)).isEmpty();
	}

	@Test
	void initialDraftEditsInvalidateApprovalAndConcurrentInitialPublicationDoesNotOverwrite() {
		bind();
		String first = initial(), second = initial();
		corrections.verify(admin, first, 1);
		corrections.approve(reviewer, first, 1);
		corrections.edit(admin, first, 1, List.of("巡视"), "2026-09");
		assertThatThrownBy(() -> corrections.publish(reviewer, first, 1, "stale approval"))
			.hasMessage("REVISION_CONFLICT");
		corrections.verify(admin, first, 2);
		corrections.approve(reviewer, first, 2);
		corrections.verify(admin, second, 1);
		corrections.approve(reviewer, second, 1);
		corrections.publish(reviewer, first, 2, "first initial release");
		assertThatThrownBy(() -> corrections.publish(reviewer, second, 1, "competing initial release"))
			.hasMessage("ACTIVE_RULE_CHANGED");
		assertThat(corrections.inspect(reviewer, second).get("state")).isEqualTo("APPROVED");
		assertThat(metadata.current(OWNER).version()).isEqualTo(first);
		assertThatThrownBy(() -> corrections.inspect(actor("2", "22", "knowledge_dataquery_verify"), first))
			.hasMessage("DRAFT_NOT_FOUND");
	}

	@Test
	void membershipNotDefaultTenantControlsSelectionAndGrantTargets() {
		bind();
		jdbc.update("UPDATE sys_user SET tenant_id=2 WHERE user_id=2");
		assertThat(service.users(admin, "10", "reader")).extracting(DataQuerySetupService.UserOption::userId)
			.containsExactly("2");
		service.saveAccess(admin, "10", "unconfigured", targets(), EXPIRY, "explicit paired scope");
		assertThat(metadata.resolve(OWNER).revision()).isNotBlank();
		assertThat(service.users(admin, "10", "other-tenant")).isEmpty();
		assertThatThrownBy(() -> service.saveAccess(admin, "10", service.access(admin, "10").revision(),
				List.of(new DataQueryAccessRepository.Target("user", "22")), EXPIRY, "other tenant"))
			.hasMessage("SETUP_TARGET_NOT_FOUND");
		jdbc.update("DELETE FROM sys_tenant_user WHERE tenant_id=1 AND user_id=2");
		assertThat(service.users(admin, "10", "reader")).isEmpty();
		assertThatThrownBy(() -> metadata.resolve(OWNER)).hasMessage("DATASET_NOT_AUTHORIZED");
		assertThatThrownBy(() -> service.saveAccess(admin, "10", service.access(admin, "10").revision(), targets(),
				EXPIRY, "removed member"))
			.hasMessage("SETUP_TARGET_NOT_FOUND");
	}

	@Test
	void explicitTestPrincipalSupportsAuditedHumanReviewButNeverSkipsEvidenceOrPublication() {
		bind();
		var policy = DataQueryReviewPolicy.testPrincipal("1:3");
		var testMetadata = new DataQueryMetadataRepository(source, json, CLOCK, policy);
		var testCorrections = new DataQueryCorrectionService(source, testMetadata, json, CLOCK);
		var human = actor("1", "3", "knowledge_aiData_view", "knowledge_dataquery_edit", "knowledge_dataquery_verify",
				"knowledge_dataquery_review", "knowledge_dataquery_publish", "knowledge_dataquery_human_review");
		String version = initial();
		assertThat(testCorrections.workbench(human).testSelfReviewAllowed()).isTrue();
		assertThat(testCorrections.workbench(reviewer).testSelfReviewAllowed()).isFalse();
		assertThat(policy.allows(new Identity("2", "3"))).isFalse();
		assertThatThrownBy(() -> testCorrections.approve(human, version, 1)).hasMessage("STATE_CONFLICT");
		testCorrections.verify(human, version, 1);
		assertThatThrownBy(() -> testCorrections.approve(actor("1", "3", "knowledge_dataquery_review"), version, 1))
			.hasMessage("MANAGEMENT_FORBIDDEN");
		testCorrections.approve(human, version, 1);
		assertThat(testMetadata.current(OWNER)).isNull();
		testCorrections.edit(human, version, 1, List.of("巡视"), "2026-09");
		assertThatThrownBy(() -> testCorrections.publish(human, version, 1, "stale")).hasMessage("REVISION_CONFLICT");
		assertThatThrownBy(() -> testCorrections.approve(human, version, 2)).hasMessage("STATE_CONFLICT");
		testCorrections.verify(human, version, 2);
		testCorrections.approve(human, version, 2);
		testCorrections.publish(human, version, 2, "explicit test human publish");
		assertThat(testMetadata.current(OWNER).version()).isEqualTo(version);
		assertThat(metadata.current(OWNER)).isNull(); // removing the exception also
														// disables self-approved
														// execution
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM ai_dataquery_config_event WHERE action='TEST_SELF_REVIEW' AND actor_id=3",
				Integer.class))
			.isEqualTo(2);
		assertThat(new DataQuerySetupService(source, json, CLOCK, true, true, policy).view(human, "10")
			.get("activeRuleVersion")).isEqualTo(version);
		assertThatThrownBy(() -> testCorrections.inspect(actor("2", "3", "knowledge_dataquery_verify"), version))
			.hasMessage("DRAFT_NOT_FOUND");
	}

	@Test
	void testReviewPolicyHasNoWildcardAndIsIndependentByDefault() {
		assertThat(DataQueryReviewPolicy.testPrincipal("").allows(new Identity("1", "3"))).isFalse();
		for (String value : List.of("*", "1:*", "1:3,2:4", "1", "0:3", "1:0"))
			assertThatThrownBy(() -> DataQueryReviewPolicy.testPrincipal(value))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void targetsRejectWildcardDuplicateMalformedTypeAndLossyIds() {
		for (var targets : List.of(List.of(new DataQueryAccessRepository.Target("all", "1")),
				List.of(new DataQueryAccessRepository.Target("dept", " 100")),
				List.of(new DataQueryAccessRepository.Target("user", "01")),
				List.of(new DataQueryAccessRepository.Target("role", "9223372036854775808")),
				List.of(new DataQueryAccessRepository.Target("user", "2"),
						new DataQueryAccessRepository.Target("user", "2"))))
			assertThatThrownBy(() -> DataQueryAccessRepository.checked(targets)).hasMessage("INVALID_REQUEST");
	}

	@Test
	void httpSetupReceiptsLeadToTheSameRuleLifecycleWithoutSeedingPublishedRows() throws Exception {
		login(admin);
		String body = mvc.perform(get("/data-query/setup/10"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String fingerprint = json.readTree(body).get("connectionFingerprint").asText();
		mvc.perform(put("/data-query/setup/10/binding").contentType("application/json")
			.content(json.writeValueAsString(Map.of("expectedRevision", "unconfigured", "expectedFingerprint",
					fingerprint, "enabled", true, "reason", "explicit synthetic binding"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dataId").value("10"))
			.andExpect(jsonPath("$.revision").isString());
		mvc.perform(put("/data-query/setup/10/access").contentType("application/json")
			.content(json.writeValueAsString(Map.of("expectedRevision", "unconfigured", "targets", targets(),
					"validUntil", EXPIRY, "reason", "synthetic exact scope"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.targets.length()").value(2));
		mvc.perform(put("/data-query/setup/10/access").contentType("application/json")
			.content(
					"{\"expectedRevision\":\"x\",\"targets\":[{\"type\":\"user\",\"targetId\":\"2\",\"tenantId\":2}],\"reason\":\"forged\"}"))
			.andExpect(status().isBadRequest());
		String receipt = mvc
			.perform(post("/data-query/manage/initial-drafts").contentType("application/json")
				.content(json.writeValueAsString(Map.of("dataId", "10", "definitionDigest",
						DataQueryMetadataRepository.DEFINITION_DIGEST, "acknowledged", true, "expectedMonth", "2026-08",
						"reason", "explicit synthetic TEST definition"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.revision").value(1))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String version = json.readTree(receipt).get("ruleVersion").asText();
		mvc.perform(post("/data-query/manage/drafts/" + version + "/verify").contentType("application/json")
			.content("{\"revision\":1}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.report").value(org.hamcrest.Matchers.startsWith("PASS: initial TEST")));
		login(reviewer);
		mvc.perform(post("/data-query/manage/drafts/" + version + "/approve").contentType("application/json")
			.content("{\"revision\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
		assertThat(metadata.current(OWNER)).isNull();
		mvc.perform(post("/data-query/manage/drafts/" + version + "/publish").contentType("application/json")
			.content("{\"revision\":1,\"reason\":\"independent human release\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("PUBLISHED"));
		assertThat(metadata.current(OWNER).version()).isEqualTo(version);
		assertThat(metadata.resolve(OWNER).revision()).isNotBlank();
	}

	@Test
	void reapprovingChangedSourceRevokesOldScopesInsteadOfCarryingPermissionsToNewReader() {
		var previous = bind();
		grant();
		var oldScope = service.access(admin, "10");
		jdbc.update("UPDATE ai_datasource SET username='different-reader' WHERE id=20");
		String fingerprint = (String) service.view(admin, "10").get("connectionFingerprint");
		service.saveBinding(admin, "10", previous.revision(), fingerprint, true,
				"Explicit new source approval; old grants must be reapproved");
		assertThat(service.access(admin, "10").targets()).isEmpty();
		assertThat(service.access(admin, "10").revision()).isNotEqualTo(oldScope.revision());
		assertThatThrownBy(() -> metadata.resolve(OWNER)).hasMessage("DATASET_NOT_AUTHORIZED");
		assertThat(jdbc.queryForObject(
				"SELECT COUNT(*) FROM ai_dataquery_config_event WHERE action='BINDING_SOURCE_CHANGE'", Integer.class))
			.isEqualTo(1);
	}

	@Test
	void platformUtcExpiryAcceptsJdbcTimestampAndLocalDateTimeWithoutDriverSpecificCast() {
		var local = LocalDateTime.of(2026, 10, 1, 0, 0);
		assertThat(DataQuerySetupService.utcExpiry(local)).isEqualTo(EXPIRY);
		assertThat(DataQuerySetupService.utcExpiry(java.sql.Timestamp.valueOf(local))).isEqualTo(EXPIRY);
		assertThat(DataQuerySetupService.utcExpiry(null)).isNull();
		assertThatThrownBy(() -> DataQuerySetupService.utcExpiry("untrusted string"))
			.hasMessage("SETUP_METADATA_INVALID");
	}

	@Test
	void eachSystemTargetGrantsDatasetWithoutSourceIdMappingAndDepartmentsAreExact() {
		bind();
		for (var target : List.of(new DataQueryAccessRepository.Target("dept", "100"),
				new DataQueryAccessRepository.Target("role", "300"),
				new DataQueryAccessRepository.Target("user", "2"))) {
			service.saveAccess(admin, "10", service.access(admin, "10").revision(), List.of(target), EXPIRY,
					"Approve one MATRIX target");
			assertThat(metadata.resolve(OWNER).source().dataId()).isEqualTo(10);
			assertThatThrownBy(() -> metadata.resolve(new Identity("1", "3"))).hasMessage("DATASET_NOT_AUTHORIZED");
		}
		// Parent 100 does not automatically include editor's child department 101.
		service.saveAccess(admin, "10", service.access(admin, "10").revision(),
				List.of(new DataQueryAccessRepository.Target("dept", "100")), EXPIRY, "Exact department");
		assertThatThrownBy(() -> metadata.resolve(new Identity("1", "3"))).hasMessage("DATASET_NOT_AUTHORIZED");
		jdbc.update("DELETE FROM sys_user_dept WHERE user_id=2");
		// A forged relation belonging to another tenant must not match the tenant-1
		// department.
		jdbc.update("INSERT INTO sys_user_dept VALUES (2,100,2)");
		assertThatThrownBy(() -> metadata.resolve(OWNER)).hasMessage("DATASET_NOT_AUTHORIZED");
	}

	@Test
	void inactiveAndCrossTenantObjectsAreRejectedAtSaveAndRecheckedAtExecution() {
		bind();
		for (var target : List.of(new DataQueryAccessRepository.Target("dept", "200"),
				new DataQueryAccessRepository.Target("dept", "102"),
				new DataQueryAccessRepository.Target("role", "400"),
				new DataQueryAccessRepository.Target("role", "301"), new DataQueryAccessRepository.Target("user", "5")))
			assertThatThrownBy(
					() -> service.saveAccess(admin, "10", "unconfigured", List.of(target), EXPIRY, "Invalid target"))
				.hasMessage("SETUP_TARGET_NOT_FOUND");
		service.saveAccess(admin, "10", "unconfigured", List.of(new DataQueryAccessRepository.Target("role", "300")),
				EXPIRY, "Role access");
		assertThat(metadata.resolve(OWNER)).isNotNull();
		jdbc.update("UPDATE sys_role SET del_flag='1' WHERE role_id=300");
		assertThatThrownBy(() -> metadata.resolve(OWNER)).hasMessage("DATASET_NOT_AUTHORIZED");
		jdbc.update("UPDATE sys_role SET del_flag='0' WHERE role_id=300");
		jdbc.update("DELETE FROM sys_user_role WHERE user_id=2");
		jdbc.update("INSERT INTO sys_user_role VALUES (2,300,2)");
		assertThatThrownBy(() -> metadata.resolve(OWNER)).hasMessage("DATASET_NOT_AUTHORIZED");
		service.saveAccess(admin, "10", service.access(admin, "10").revision(), targets(), EXPIRY,
				"Direct user and department");
		jdbc.update("UPDATE sys_user SET lock_flag='1' WHERE user_id=2");
		assertThatThrownBy(() -> metadata.resolve(OWNER)).hasMessage("DATASET_NOT_AUTHORIZED");
		jdbc.update("UPDATE sys_user SET lock_flag='0',del_flag='1' WHERE user_id=2");
		assertThatThrownBy(() -> metadata.resolve(OWNER)).hasMessage("DATASET_NOT_AUTHORIZED");
	}

	@Test
	void membershipRemovalInvalidatesPlanEvenWhenDirectUserStillMatchesAndReaderIsNotOpened() {
		bind();
		grant();
		var candidate = new PatrolQueryCompiler.Candidate("top.patrol", "valid_patrol_count", "department",
				LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 1), "Asia/Shanghai", "DESC");
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var controlled = new ControlledQueryService(metadata::resolve,
				i -> new PublishedRule("synthetic-rule", DEFINITION, true), execution -> {
					calls.incrementAndGet();
					return List.of();
				}, mock(QueryAudit.class), CLOCK, 10, Duration.ofMinutes(5));
		var handle = controlled.validate(OWNER, candidate).planHandle();
		var authorization = metadata.resolve(OWNER);
		jdbc.update("DELETE FROM sys_user_dept WHERE tenant_id=1 AND user_id=2");
		assertThat(metadata.resolve(OWNER)).isNotEqualTo(authorization);
		assertThatThrownBy(() -> controlled.execute(OWNER, handle)).hasMessage("AUTHORIZATION_CHANGED");
		var reader = new ApprovedDatasourceQueryExecutor(metadata, c -> {
			throw new AssertionError("Reader must not open");
		});
		assertThatThrownBy(() -> reader
			.execute(new QueryExecution(OWNER, authorization, new PatrolQueryCompiler().compile(candidate))))
			.hasMessage("AUTHORIZATION_CHANGED");
		var result = controlled.execute(OWNER, controlled.validate(OWNER, candidate).planHandle());
		assertThat(result.rows()).isEmpty();
		assertThat(calls.get()).isEqualTo(1);
	}

	@Test
	void legacyPairsNeverBecomeDatasetAccessAndMissingMigrationFailsClosed() throws Exception {
		bind();
		jdbc.update(
				"INSERT INTO ai_dataquery_grant VALUES (1,2,'top.patrol','old-company','old-dept','old-revision',1,3,?,NULL)",
				LocalDateTime.ofInstant(CLOCK.instant(), ZoneOffset.UTC));
		assertThatThrownBy(() -> metadata.resolve(OWNER)).hasMessage("DATASET_NOT_AUTHORIZED");
		login(admin);
		mvc.perform(get("/data-query/setup/10/grants/2"))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.code").value("AUTHORIZATION_MODEL_CHANGED"));
		mvc.perform(put("/data-query/setup/10/grants/2").contentType("application/json").content("{\"pairs\":[]}"))
			.andExpect(status().isGone());
		assertThat(jdbc.queryForObject("SELECT enabled FROM ai_dataquery_grant", Integer.class)).isEqualTo(1);
		jdbc.execute("DROP TABLE ai_dataquery_access");
		assertThat(service.view(admin, "10").get("schemaReady")).isEqualTo(false);
		assertThatThrownBy(
				() -> service.saveAccess(admin, "10", "unconfigured", targets(), EXPIRY, "Missing migration"))
			.hasMessage("SETUP_SCHEMA_REQUIRED");
		assertThatThrownBy(() -> metadata.resolve(OWNER))
			.isInstanceOf(org.springframework.dao.DataAccessException.class);
	}

	@Test
	void accessEndpointRejectsForgedIdentityAndWrongDatasetAndReturnsServerNames() throws Exception {
		bind();
		login(admin);
		mvc.perform(put("/data-query/setup/10/access").contentType("application/json")
			.content(
					"{\"expectedRevision\":\"unconfigured\",\"targets\":[{\"type\":\"user\",\"targetId\":\"2\"}],\"tenantId\":2,\"reason\":\"forged\"}"))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/data-query/setup/12/access")).andExpect(status().isNotFound());
		mvc.perform(get("/data-query/setup/11/access")).andExpect(status().isConflict());
		grant();
		mvc.perform(get("/data-query/setup/10/access"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.authorizationModel").value("MATRIX_DATASET_ACL_V1"))
			.andExpect(jsonPath("$.targets[0].name").value("MATRIX Operations"));
		login(actor("1", "2", "knowledge_aiData_view"));
		mvc.perform(get("/data-query/setup/10/access")).andExpect(status().isForbidden());
	}

}
