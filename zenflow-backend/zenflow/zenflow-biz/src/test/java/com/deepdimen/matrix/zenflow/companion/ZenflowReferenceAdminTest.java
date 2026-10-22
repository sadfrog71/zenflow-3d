package com.deepdimen.matrix.zenflow.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.zenflow.admin.ZenflowAdminAccess;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.server.ResponseStatusException;

public class ZenflowReferenceAdminTest {

	private JdbcTemplate jdbc;

	private ZenflowReferenceAdmin admin;

	private ZenflowReferenceSources sources;

	private ZenflowAdminAccess access;

	private DataSourceTransactionManager manager;

	private ZenflowReferenceSnapshots snapshots;

	@BeforeEach
	void setup() {
		var data = new JdbcDataSource();
		data.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
		jdbc = new JdbcTemplate(data);
		jdbc.execute(
				"CREATE ALIAS UTC_TIMESTAMP FOR 'com.deepdimen.matrix.zenflow.companion.ZenflowReferenceAdminTest.utc'");
		jdbc.execute("CREATE TABLE sys_tenant(id BIGINT)");
		jdbc.update("INSERT INTO sys_tenant VALUES(1)");
		jdbc.execute(
				"CREATE TABLE ai_dataset(id BIGINT PRIMARY KEY,tenant_id BIGINT,name VARCHAR(200),description VARCHAR(255),collection_name VARCHAR(255),public_flag CHAR(1),del_flag CHAR(1),create_by VARCHAR(64),update_by VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP)");
		jdbc.execute(
				"CREATE TABLE ai_document(id BIGINT PRIMARY KEY,tenant_id BIGINT,dataset_id BIGINT,name VARCHAR(200),file_type VARCHAR(10),source_type CHAR(1),file_hash VARCHAR(64),file_status CHAR(1),slice_status CHAR(1),slice_count BIGINT,document_config VARCHAR(10000),del_flag CHAR(1),create_by VARCHAR(64),update_by VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP)");
		jdbc.execute(
				"CREATE TABLE ai_slice(id BIGINT PRIMARY KEY,tenant_id BIGINT,document_id BIGINT,content CLOB,char_count BIGINT,del_flag CHAR(1),slice_status CHAR(1),create_by VARCHAR(64),update_by VARCHAR(64),create_time TIMESTAMP,update_time TIMESTAMP)");
		jdbc.execute(
				"CREATE TABLE zf_reference_snapshot(id BIGINT,tenant_id BIGINT,document_id BIGINT,status VARCHAR(24),updated_at TIMESTAMP,companion_revision_id BIGINT,dataset_id BIGINT,slice_id BIGINT,source_version VARCHAR(64),source_hash VARCHAR(64),reference_kind VARCHAR(24),title VARCHAR(200),locale VARCHAR(16),content CLOB,source_locator VARCHAR(512),citation_visibility VARCHAR(16))");
		jdbc.execute(
				"CREATE TABLE zf_run_source(tenant_id BIGINT,run_id VARCHAR(64),reference_snapshot_id BIGINT,status VARCHAR(24),updated_at TIMESTAMP)");
		jdbc.execute(
				"CREATE TABLE zf_companion_run_context(tenant_id BIGINT,run_id VARCHAR(64),lineage_state VARCHAR(24),updated_at TIMESTAMP)");
		jdbc.execute(
				"CREATE TABLE zf_companion_revision(id BIGINT,tenant_id BIGINT,state VARCHAR(16),retrieval_policy_json VARCHAR(10000),reference_digest VARCHAR(64))");
		var product = new ZenflowProperties();
		product.setTenantId(1);
		access = mock(ZenflowAdminAccess.class);
		var actor = mock(MatrixUser.class);
		when(actor.getId()).thenReturn(42L);
		when(access.require()).thenReturn(actor);
		manager = new DataSourceTransactionManager(data);
		admin = new ZenflowReferenceAdmin(jdbc, product, access, manager);
		sources = new ZenflowReferenceSources(jdbc, product);
		snapshots = new ZenflowReferenceSnapshots(jdbc, product, sources);
	}

	public static java.time.LocalDateTime utc(int precision) {
		return java.time.LocalDateTime.now(java.time.Clock.systemUTC());
	}

	private Map<String, Object> body(String content, boolean approved) {
		return Map.of("title", "Public source", "content", content, "kind", "MATERIAL", "locale", "en",
				"citationVisibility", "PUBLIC", "approved", approved);
	}

	@Test
	void emptyTextExtractionHasSpecificError() {
		var body=Map.<String,Object>of("filename","empty.txt","base64",java.util.Base64.getEncoder().encodeToString("   ".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		var error=assertThrows(ResponseStatusException.class,()->admin.extract(body));
		assertEquals("REFERENCE_FILE_NO_TEXT",error.getReason());
	}

	@Test
	void createdSourceRequiresExplicitApprovalAndDoesNotOpenLegacyPublicSharing() {
		var draft = admin.create(body("original\ntext", false));
		String id = draft.get("id").toString();
		var selection = new ZenflowReferencePolicy.Reference(id, "MATERIAL", "en", "PUBLIC");
		assertThrows(ResponseStatusException.class, () -> sources.load(selection));
		var edit = new HashMap<>(body("original\ntext", true));
		edit.put("expectedSourceVersion", draft.get("sourceVersion"));
		var approved = admin.update(id, edit);
		assertEquals("original\ntext", approved.get("content"));
		assertEquals(1, sources.load(selection).fragments().size());
		assertEquals("0", jdbc.queryForObject("SELECT public_flag FROM ai_dataset", String.class));
		assertThrows(ResponseStatusException.class, () -> admin.update(id, edit));
		assertNotEquals(draft.get("sourceVersion"), approved.get("sourceVersion"));
	}

	@Test
	void originalSlicesRoundTripUnicodeAndNormalEditsKeepFrozenCopies() {
		String content = "x".repeat(1999) + "🌿" + "y".repeat(2400) + "\nlast";
		var created = admin.create(body(content, true));
		String id = created.get("id").toString();
		assertEquals(content, admin.detail(id).get("content"));
		assertEquals(3L, ((Number) created.get("sliceCount")).longValue());
		var snapshot = sources.load(new ZenflowReferencePolicy.Reference(id, "MATERIAL", "en", "PUBLIC"));
		var updated = new HashMap<>(body("new body", true));
		updated.put("expectedSourceVersion", created.get("sourceVersion"));
		admin.update(id, updated);
		assertEquals(content,
				snapshot.fragments()
					.stream()
					.map(ZenflowReferenceSources.Fragment::content)
					.collect(java.util.stream.Collectors.joining()));
	}

	@Test
	void revokeInvalidatesPublicSnapshotsAndDependentLineageOnlyInThisTenant() {
		var created = admin.create(body("public text", true));
		String id = created.get("id").toString();
		jdbc.update("INSERT INTO zf_reference_snapshot(id,tenant_id,document_id,status) VALUES(90,1,?,'VALID')", id);
		jdbc.update("INSERT INTO zf_reference_snapshot(id,tenant_id,document_id,status) VALUES(91,2,?,'VALID')", id);
		jdbc.update("INSERT INTO zf_run_source VALUES(1,'run1',90,'VALID',NULL)");
		jdbc.update("INSERT INTO zf_run_source VALUES(2,'run2',91,'VALID',NULL)");
		jdbc.update("INSERT INTO zf_companion_run_context VALUES(1,'run1','VALID',NULL)");
		jdbc.update("INSERT INTO zf_companion_run_context VALUES(2,'run2','VALID',NULL)");
		assertEquals(false,
				admin.revoke(id, Map.of("expectedSourceVersion", created.get("sourceVersion"))).get("approved"));
		assertEquals("REVOKED",
				jdbc.queryForObject("SELECT status FROM zf_reference_snapshot WHERE id=90", String.class));
		assertEquals("VALID",
				jdbc.queryForObject("SELECT status FROM zf_reference_snapshot WHERE id=91", String.class));
		assertEquals("INVALIDATED", jdbc
			.queryForObject("SELECT lineage_state FROM zf_companion_run_context WHERE tenant_id=1", String.class));
		assertEquals("VALID", jdbc
			.queryForObject("SELECT lineage_state FROM zf_companion_run_context WHERE tenant_id=2", String.class));
	}

	@Test
	void foreignDataAndConsumerAuthorityCannotReachMaintenance() {
		var created = admin.create(body("public text", true));
		String id = created.get("id").toString();
		jdbc.update("UPDATE ai_document SET tenant_id=2 WHERE id=?", id);
		assertThrows(ResponseStatusException.class, () -> admin.detail(id));
		assertEquals(List.of(), admin.list(null).get("items"));
		when(access.require()).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
		assertThrows(ResponseStatusException.class, () -> admin.create(body("x", true)));
	}

	private ZenflowReferencePolicy preparePolicy(String id) throws Exception {
		var policy = new ZenflowReferencePolicy(
				List.of(new ZenflowReferencePolicy.Reference(id, "MATERIAL", "en", "PUBLIC")), 6, 0.7);
		jdbc.update("INSERT INTO zf_companion_revision VALUES(70,1,'PREPARING',?,NULL)",
				new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(policy.projection()));
		return policy;
	}

	private ZenflowReferenceSnapshots.Frozen capture(ZenflowReferencePolicy policy) {
		return new org.springframework.transaction.support.TransactionTemplate(manager)
			.execute(ignored -> snapshots.capture("70", policy));
	}

	@Test
	void frozenBodyAndDigestSurviveNormalSourceEditsAndRetry() throws Exception {
		var created = admin.create(body("original\npublic text", true));
		String id = created.get("id").toString();
		var policy = preparePolicy(id);
		var fixed = capture(policy);
		assertEquals("original\npublic text", fixed.snapshots().get(0).content());
		var edit = new HashMap<>(body("new edited public text", true));
		edit.put("expectedSourceVersion", created.get("sourceVersion"));
		admin.update(id, edit);
		assertEquals(fixed, capture(policy));
		assertEquals(fixed, snapshots.verify("70", policy, fixed.digest()));
		assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM zf_reference_snapshot", Integer.class));
	}

	@Test
	void revokedOrTamperedSnapshotAndChangedPolicyCannotBeVerified() throws Exception {
		var created = admin.create(body("fixed public text", true));
		String id = created.get("id").toString();
		var policy = preparePolicy(id);
		var fixed = capture(policy);
		var changed = new ZenflowReferencePolicy(policy.references(), 5, 0.7);
		assertThrows(ResponseStatusException.class, () -> capture(changed));
		assertThrows(ResponseStatusException.class, () -> snapshots.verify("70", changed, fixed.digest()));
		jdbc.update("UPDATE zf_reference_snapshot SET content='tampered'");
		assertThrows(ResponseStatusException.class, () -> snapshots.verify("70", policy, fixed.digest()));
		jdbc.update("UPDATE zf_reference_snapshot SET content='fixed public text'");
		admin.revoke(id, Map.of("expectedSourceVersion", created.get("sourceVersion")));
		assertThrows(ResponseStatusException.class, () -> snapshots.verify("70", policy, fixed.digest()));
	}

	@Test
	void selectedRootsRequireReadyPinnedRevisionAndStayFrozenUntilRevoked() throws Exception {
		var created = admin.create(body("original public ".repeat(500), true));
		String document = created.get("id").toString();
		var policy = preparePolicy(document);
		var fixed = capture(policy);
		var first = fixed.snapshots().get(0);
		var hashes = Map.of(first.id(), first.contentHash());
		assertThrows(ResponseStatusException.class, () -> snapshots.selected("70", policy, fixed.digest(), hashes));
		jdbc.update("UPDATE zf_companion_revision SET state='READY' WHERE id=70");
		assertEquals(List.of(first), snapshots.selected("70", policy, fixed.digest(), hashes));
		var changedPolicy = new ZenflowReferencePolicy(policy.references(), 1, 0.9);
		assertThrows(ResponseStatusException.class,
				() -> snapshots.selected("70", changedPolicy, fixed.digest(), hashes));
		assertThrows(ResponseStatusException.class, () -> snapshots.selected("70", policy, "0".repeat(64), hashes));
		assertThrows(ResponseStatusException.class,
				() -> snapshots.selected("70", policy, fixed.digest(), Map.of(first.id(), "0".repeat(64))));
		var edit = new HashMap<>(body("future public text", true));
		edit.put("expectedSourceVersion", created.get("sourceVersion"));
		var changed = admin.update(document, edit);
		assertEquals(List.of(first), snapshots.selected("70", policy, fixed.digest(), hashes));
		admin.revoke(document, Map.of("expectedSourceVersion", changed.get("sourceVersion")));
		assertThrows(ResponseStatusException.class, () -> snapshots.selected("70", policy, fixed.digest(), hashes));
	}

	@Test
	void captureRequiresPrepareTransactionAndProductOwnedRevision() throws Exception {
		var created = admin.create(body("public text", true));
		var policy = preparePolicy(created.get("id").toString());
		assertThrows(IllegalStateException.class, () -> snapshots.capture("70", policy));
		jdbc.update("UPDATE zf_companion_revision SET tenant_id=2");
		assertThrows(ResponseStatusException.class, () -> capture(policy));
		jdbc.update("UPDATE zf_companion_revision SET tenant_id=1,state='READY'");
		assertThrows(ResponseStatusException.class, () -> capture(policy));
	}

	@Test
	void compilerCanOnlyConsumeConsistentFrozenPinsFromCapture() throws Exception {
		var created = admin.create(body("reviewed public text", true));
		var policy = preparePolicy(created.get("id").toString());
		var fixed = capture(policy);
		var model = Map.<String, Object>of("modelName", "fixture", "provider", "OPENAI", "baseUrl",
				"https://example.org/v1", "temperature", 0.6, "maxOutputTokens", 2048, "allowPrivateContext", false,
				"version", 1);
		var compiler = new ZenflowCompanionPackage(new com.fasterxml.jackson.databind.ObjectMapper());
		var authoring = Map.<String, Object>of("principles", "温和回应");
		var index = Map.<String, Object>of("embeddingModelId", "42", "embeddingModelDigest", "a".repeat(64),
				"dimensions", 2, "storeId", "7", "storeDigest", "b".repeat(64), "namespace", "invelle_public_1_70");
		var first = compiler.compile("70", "456", authoring, model, fixed, index);
		assertEquals(fixed.digest(), first.referenceDigest());
		assertTrue(first.release().getContentSnapshot().contains(fixed.digest()));
		assertEquals("[]", first.release().getResourceRefs());
		new com.deepdimen.matrix.discover.skill.ManagedSkillReleaseSnapshot(
				new com.fasterxml.jackson.databind.ObjectMapper())
			.verify(first.release());
		assertNotEquals(compiler.compile("70", "456", authoring, model).release().getReleaseDigest(),
				first.release().getReleaseDigest());
		for (var constructor : ZenflowReferenceSnapshots.Frozen.class.getDeclaredConstructors())
			assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
	}

    @Test void sliceEditsPreserveOtherBodiesAndDeleteRevokesSources() {
        var source=admin.create(body("a".repeat(2500),true));String document=source.get("id").toString();
        var chunks=admin.chunks(document);assertEquals(2,((List<?>)chunks.get("items")).size());
        var chunk=(Map<?,?>)((List<?>)chunks.get("items")).get(0);
        var edited=admin.editChunk(document,chunk.get("id").toString(),Map.of("content","edited","expectedSourceVersion",source.get("sourceVersion")));
        assertEquals("edited"+"a".repeat(500),admin.detail(document).get("content"));
        assertThrows(ResponseStatusException.class,()->admin.delete(document,source.get("sourceVersion").toString()));
        admin.delete(document,edited.get("sourceVersion").toString());
        assertThrows(ResponseStatusException.class,()->admin.detail(document));
        assertEquals(List.of(),admin.list(null).get("items"));
    }
}
