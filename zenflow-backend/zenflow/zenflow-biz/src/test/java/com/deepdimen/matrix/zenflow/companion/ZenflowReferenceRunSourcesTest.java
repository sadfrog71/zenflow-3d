package com.deepdimen.matrix.zenflow.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.*;
import com.deepdimen.matrix.assistant.generation.AssistantModelException;
import com.deepdimen.matrix.assistant.run.AssistantRunRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ZenflowReferenceRunSourcesTest {

	private JdbcTemplate jdbc;

	private TransactionTemplate tx;

	private ZenflowReferenceRunSources service;

	private ZenflowReferenceSnapshots snapshots;

	private final AssistantRunRecord run = new AssistantRunRecord("run", "session", "1", "9", null, null, null,
			"answer", RunStatus.MODEL_STREAMING, DispatchStatus.CLAIMED, null, null, "", 0, null, null, null, null,
			null, Instant.now(), null, null);

	private final ZenflowReferencePolicy policy = new ZenflowReferencePolicy(
			List.of(new ZenflowReferencePolicy.Reference("10", "MATERIAL", "en", "PUBLIC")), 6, .7);

	private final ZenflowReferenceSnapshots.Snapshot fixed = new ZenflowReferenceSnapshots.Snapshot("20", "1", "10",
			"30", "v1", ZenflowRunAuthorization.hash("public body"), "MATERIAL", "Public", "en", "public body", "",
			"PUBLIC");

	@BeforeEach
	void setup() throws Exception {
		var data = new JdbcDataSource();
		data.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
		jdbc = new JdbcTemplate(data);
		tx = new TransactionTemplate(new DataSourceTransactionManager(data));
		jdbc.execute(
				"CREATE TABLE zf_companion_revision(id BIGINT PRIMARY KEY,tenant_id BIGINT,state VARCHAR(24),retrieval_policy_json VARCHAR(10000),reference_digest VARCHAR(64))");
		jdbc.execute(
				"CREATE TABLE zf_reference_snapshot(id BIGINT PRIMARY KEY,tenant_id BIGINT,companion_revision_id BIGINT)");
		jdbc.execute(
				"CREATE TABLE zf_companion_run_context(run_id VARCHAR(64),tenant_id BIGINT,owner_user_id BIGINT,session_id VARCHAR(64),companion_revision_id BIGINT,lineage_state VARCHAR(24),response_message_id VARCHAR(64))");
		jdbc.execute(
				"CREATE TABLE zf_run_source(id BIGINT PRIMARY KEY,tenant_id BIGINT,owner_user_id BIGINT,run_id VARCHAR(64),source_key VARCHAR(64),source_kind VARCHAR(24),reference_snapshot_id BIGINT,content_hash VARCHAR(64),use_role VARCHAR(24),citation_key VARCHAR(128),derived_from_message_id VARCHAR(64),status VARCHAR(24) DEFAULT 'VALID',UNIQUE(tenant_id,run_id,source_key))");
		jdbc.update("INSERT INTO zf_companion_revision VALUES(100,1,'READY',?,'digest')",
				new ObjectMapper().writeValueAsString(policy.projection()));
		jdbc.update("INSERT INTO zf_reference_snapshot VALUES(20,1,100)");
		jdbc.update("INSERT INTO zf_companion_run_context VALUES('run',1,9,'session',100,'VALID','answer')");
		snapshots = mock(ZenflowReferenceSnapshots.class);
		when(snapshots.historyRoots("100", policy, "digest", Map.of("20", fixed.contentHash())))
			.thenReturn(List.of(fixed));
		service = new ZenflowReferenceRunSources(jdbc, new ObjectMapper(), snapshots,
				mock(ZenflowReferenceContextAdapter.class));
	}

	private List<Map<String, Object>> sources() {
		return jdbc.queryForList("SELECT * FROM zf_run_source WHERE run_id='run'");
	}

	@Test
	void publicRootsPersistIdempotentlyAndDoNotBecomeSensitive() {
		assertThrows(IllegalStateException.class, () -> service.attach(run, List.of(fixed)));
		tx.executeWithoutResult(ignored -> {
			service.attach(run, List.of(fixed));
			service.attach(run, List.of(fixed));
		});
		assertEquals(1, sources().size());
		var fragments = service.authorize(run, sources());
		assertEquals(1, fragments.size());
		assertEquals(DataClassification.PUBLIC, fragments.get(0).dataClassification());
		assertEquals("public body", fragments.get(0).content());
		jdbc.update("UPDATE zf_reference_snapshot SET tenant_id=2");
		assertThrows(AssistantModelException.class, () -> service.authorize(run, sources()));
	}

	@Test
	void referenceHistoryNeedsOriginalRootAndNeverResendsItsBody() {
		tx.executeWithoutResult(ignored -> service.attach(run, List.of(fixed)));
		var original = sources().get(0);
		jdbc.update("INSERT INTO zf_companion_run_context VALUES('ancestor',1,9,'session',100,'VALID','old-answer')");
		jdbc.update(
				"INSERT INTO zf_run_source(id,tenant_id,owner_user_id,run_id,source_key,source_kind,reference_snapshot_id,content_hash,use_role,citation_key) VALUES(90,1,9,'ancestor',?,'REFERENCE',20,?,'DIRECT','REFERENCE_20')",
				original.get("source_key"), fixed.contentHash());
		jdbc.update(
				"UPDATE zf_run_source SET use_role='HISTORY_DERIVED',derived_from_message_id='old-answer' WHERE run_id='run'");
		assertEquals(List.of(), service.authorize(run, sources()));
		jdbc.update("UPDATE zf_companion_run_context SET lineage_state='INVALIDATED' WHERE run_id='ancestor'");
		assertThrows(AssistantModelException.class, () -> service.authorize(run, sources()));
	}

	@Test
	void wrongPinnedRevisionAndCitationIdentityAreRejected() {
		tx.executeWithoutResult(ignored -> service.attach(run, List.of(fixed)));
		jdbc.update("UPDATE zf_run_source SET citation_key='REFERENCE_other'");
		assertThrows(AssistantModelException.class, () -> service.authorize(run, sources()));
		jdbc.update("UPDATE zf_run_source SET citation_key='REFERENCE_20'");
		jdbc.update("UPDATE zf_companion_run_context SET companion_revision_id=101");
		assertThrows(AssistantModelException.class, () -> service.authorize(run, sources()));
	}

}
