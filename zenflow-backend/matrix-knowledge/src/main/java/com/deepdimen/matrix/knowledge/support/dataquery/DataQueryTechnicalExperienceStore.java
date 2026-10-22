package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

/**
 * Typed technical experiences in the existing version/active/release tables, NOT intent
 * terms.
 */
public final class DataQueryTechnicalExperienceStore {

	private final JdbcTemplate jdbc;

	private final TransactionTemplate tx;

	private final ObjectMapper json;

	private final Clock clock;

	private static final String DEFINITION = DataQuerySqlRepair.VERSION;

	DataQueryTechnicalExperienceStore(DataSource source, ObjectMapper json, Clock clock) {
		jdbc = new JdbcTemplate(source);
		jdbc.setQueryTimeout(5);
		tx = new TransactionTemplate(new DataSourceTransactionManager(source));
		tx.setTimeout(5);
		this.json = json;
		this.clock = clock;
	}

	public record Match(String status, String version) {
	}

	boolean ready() {
		try {
			jdbc.queryForList("SELECT technical_payload_json FROM ai_dataquery_rule_version WHERE 1=0");
			return true;
		}
		catch (org.springframework.dao.DataAccessException unavailable) {
			return false;
		}
	}

	private String key(DataQueryAiChatService.Context c, DataQuerySqlRepair.Diagnosis d) {
		return "sql-alias:" + c.authorization().source().dataId() + ":"
				+ DataQueryMetadataRepository.fingerprint(d.table()).substring(0, 20);
	}

	Match lookup(DataQueryAiChatService.Context c, DataQuerySqlRepair.Diagnosis d) {
		if (!ready())
			return new Match("UNAVAILABLE", null);
		var rows = jdbc.queryForList("""
				SELECT v.* FROM ai_dataquery_rule_active a JOIN ai_dataquery_rule_version v
				ON v.tenant_id=a.tenant_id AND v.dataset_key=a.dataset_key AND v.rule_version=a.rule_version
				WHERE a.tenant_id=? AND a.dataset_key=?
				""", c.identity().tenantId(), key(c, d));
		if (rows.isEmpty()) {
			// A dangling active pointer must not be treated as permission to relearn.
			Integer active = jdbc.queryForObject(
					"SELECT COUNT(*) FROM ai_dataquery_rule_active WHERE tenant_id=? AND dataset_key=?", Integer.class,
					c.identity().tenantId(), key(c, d));
			return new Match(active == 0 ? "ABSENT" : "INVALID", null);
		}
		var row = rows.get(0);
		String version = row.get("rule_version").toString();
		if ("DISABLED".equals(row.get("state")))
			return new Match("DISABLED", version);
		String payload = (String) row.get("technical_payload_json");
		String report = (String) row.get("regression_report");
		if (!"PUBLISHED".equals(row.get("state")) || !DEFINITION.equals(row.get("definition_key"))
				|| !Integer.valueOf(1).equals(row.get("validated_revision"))
				|| !Integer.valueOf(1).equals(row.get("edit_revision")) || payload == null || report == null
				|| !DataQueryMetadataRepository.fingerprint(payload).equals(row.get("content_digest"))
				|| !DataQueryMetadataRepository.fingerprint(payload, report).equals(row.get("regression_digest")))
			return new Match("INVALID", version);
		try {
			var value = json.readTree(payload);
			if (!DataQuerySqlRepair.KIND.equals(value.path("kind").asText())
					|| !d.table().equals(value.path("table").asText()))
				return new Match("INVALID", version);
			var origin = jdbc.queryForList(
					"SELECT status,result_code FROM ai_dataquery_query WHERE tenant_id=? AND query_id=?",
					c.identity().tenantId(), value.path("originQueryId").asText());
			if (origin.size() != 1 || !"FINISHED".equals(origin.get(0).get("status"))
					|| !"OK".equals(origin.get(0).get("result_code")))
				return new Match("INVALID", version);
			boolean current = c.inventory().digest().equals(value.path("inventoryDigest").asText())
					&& c.authorization()
						.source()
						.connectionFingerprint()
						.equals(value.path("connectionFingerprint").asText())
					&& c.authorization().source().revision().equals(value.path("bindingRevision").asText())
					&& Long.toString(c.authorization().source().dataId()).equals(value.path("dataId").asText());
			return new Match(current ? "ACTIVE" : "STALE", version);
		}
		catch (Exception invalid) {
			return new Match("INVALID", version);
		}
	}

	/**
	 * Only after before-failed/after-executed evidence; not an HTTP/model publication
	 * operation.
	 */
	String learn(DataQueryAiChatService.Context c, DataQuerySqlRepair.Diagnosis d) {
		return tx.execute(status -> {
			var match = lookup(c, d);
			if ("ACTIVE".equals(match.status()))
				return match.version();
			if (!Set.of("ABSENT", "STALE").contains(match.status()))
				throw new DataQueryException("REPAIR_EXPERIENCE_CHANGED");
			var query = jdbc.queryForList(
					"SELECT status,result_code,candidate_json FROM ai_dataquery_query WHERE query_id=? AND tenant_id=? AND user_id=?",
					c.queryId(), c.identity().tenantId(), c.identity().userId());
			try {
				if (query.size() != 1 || !"FINISHED".equals(query.get(0).get("status"))
						|| !"OK".equals(query.get(0).get("result_code")))
					throw new DataQueryException("REPAIR_EVIDENCE_REQUIRED");
				var proof = json.readTree(query.get(0).get("candidate_json").toString()).path("repair");
				if (!"REPAIRED".equals(proof.path("status").asText()) || proof.path("attemptCount").asInt() != 2
						|| !d.proofDigest().equals(proof.path("proofDigest").asText())
						|| !"SQL_COLUMN_REFERENCE_INVALID".equals(proof.path("initialCode").asText()))
					throw new DataQueryException("REPAIR_EVIDENCE_REQUIRED");
			}
			catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
				throw new DataQueryException("REPAIR_EVIDENCE_REQUIRED");
			}
			String version = "tech-" + UUID.randomUUID();
			String payload = encode(Map.of("kind", DataQuerySqlRepair.KIND, "table", d.table(), "dataId",
					Long.toString(c.authorization().source().dataId()), "inventoryDigest", c.inventory().digest(),
					"connectionFingerprint", c.authorization().source().connectionFingerprint(), "bindingRevision",
					c.authorization().source().revision(), "originQueryId", c.queryId()));
			String report = encode(
					Map.of("verificationLevel", "TECHNICAL_ONLY", "before", "SQL_COLUMN_REFERENCE_INVALID", "after",
							"EXECUTED", "proofDigest", d.proofDigest(), "businessMeaningConfirmed", false));
			jdbc.update(
					"""
							INSERT INTO ai_dataquery_rule_version
							(tenant_id,dataset_key,rule_version,definition_key,definition_digest,state,technical_payload_json,content_digest,
							edit_revision,regression_report,validated_revision,created_by,created_at,regression_digest,validated_at,published_by,published_at)
							VALUES (?,?,?,?,?,'PUBLISHED',?,?,1,?,1,?,?,?,?,?,?)
							""",
					c.identity().tenantId(), key(c, d), version, DEFINITION,
					DataQueryMetadataRepository.fingerprint(DEFINITION), payload,
					DataQueryMetadataRepository.fingerprint(payload), report, c.identity().userId(), now(),
					DataQueryMetadataRepository.fingerprint(payload, report), now(), c.identity().userId(), now());
			if (match.version() == null) {
				jdbc.update("INSERT INTO ai_dataquery_rule_active VALUES(?,?,?)", c.identity().tenantId(), key(c, d),
						version);
			}
			else if (jdbc.update(
					"UPDATE ai_dataquery_rule_active SET rule_version=? WHERE tenant_id=? AND dataset_key=? AND rule_version=?",
					version, c.identity().tenantId(), key(c, d), match.version()) != 1)
				throw new DataQueryException("REPAIR_EXPERIENCE_CHANGED");
			jdbc.update("INSERT INTO ai_dataquery_release_event VALUES(?,?,?,?,?,?,?,?,?)",
					UUID.randomUUID().toString(), c.identity().tenantId(), key(c, d), c.identity().userId(),
					"AUTO_TECHNICAL", match.version() == null ? "" : match.version(), version,
					"Program-verified alias-only repair; SQL executed; business meaning NOT approved", now());
			return version;
		});
	}

	public Map<String, Object> list(DataQuerySecurity.ManagementActor actor) {
		actor.requireRead();
		if (!ready())
			return Map.of("available", false, "items", List.of());
		var rows = jdbc.queryForList("""
				SELECT v.rule_version AS version,v.state,v.created_at AS createdAt,v.regression_report AS verification,
				       v.technical_payload_json AS payload
				FROM ai_dataquery_rule_active a JOIN ai_dataquery_rule_version v
				ON v.tenant_id=a.tenant_id AND v.dataset_key=a.dataset_key AND v.rule_version=a.rule_version
				WHERE a.tenant_id=? AND v.definition_key=? ORDER BY v.created_at DESC,v.rule_version DESC LIMIT 50
				""", actor.identity().tenantId(), DEFINITION);
		var items = new ArrayList<Map<String, Object>>();
		for (var row : rows) {
			try {
				if (!Set.of("PUBLISHED", "DISABLED").contains(row.get("state")))
					throw new DataQueryException("REPAIR_EXPERIENCE_INVALID");
				var payload = json.readTree(row.get("payload").toString());
				items.add(Map.of("version", row.get("version"), "state", row.get("state"), "dataId",
						payload.path("dataId").asText(), "originQueryId", payload.path("originQueryId").asText(),
						"kind", DataQuerySqlRepair.KIND, "createdAt", row.get("createdAt").toString(),
						"verificationLevel", "TECHNICAL_ONLY"));
			}
			catch (Exception invalid) {
				throw new DataQueryException("REPAIR_EXPERIENCE_INVALID");
			}
		}
		return Map.of("available", true, "items", items);
	}

	public void disable(DataQuerySecurity.ManagementActor actor, String version, String reason) {
		actor.require("publish", true);
		if (version == null || !version.matches("tech-[0-9a-f-]{36}") || reason == null || reason.isBlank()
				|| reason.length() > 500)
			throw new DataQueryException("INVALID_REQUEST");
		tx.executeWithoutResult(status -> {
			var rows = jdbc.queryForList(
					"SELECT dataset_key,state FROM ai_dataquery_rule_version WHERE tenant_id=? AND rule_version=? AND definition_key=? FOR UPDATE",
					actor.identity().tenantId(), version, DEFINITION);
			if (rows.size() != 1)
				throw new DataQueryException("DRAFT_NOT_FOUND");
			String key = rows.get(0).get("dataset_key").toString();
			var active = jdbc.queryForList(
					"SELECT rule_version FROM ai_dataquery_rule_active WHERE tenant_id=? AND dataset_key=?",
					String.class, actor.identity().tenantId(), key);
			if (!active.equals(List.of(version)) || !"PUBLISHED".equals(rows.get(0).get("state")))
				throw new DataQueryException("STATE_CONFLICT");
			jdbc.update(
					"UPDATE ai_dataquery_rule_version SET state='DISABLED' WHERE tenant_id=? AND dataset_key=? AND rule_version=?",
					actor.identity().tenantId(), key, version);
			// Keep the tombstone pointer: a new failure cannot silently re-enable a
			// human-disabled experience.
			jdbc.update("INSERT INTO ai_dataquery_release_event VALUES(?,?,?,?,?,?,?,?,?)",
					UUID.randomUUID().toString(), actor.identity().tenantId(), key, actor.identity().userId(),
					"DISABLE_TECHNICAL", version, version, reason.trim(), now());
		});
	}

	private String encode(Object value) {
		try {
			return json.writeValueAsString(value);
		}
		catch (Exception invalid) {
			throw new DataQueryException("AUDIT_UNAVAILABLE");
		}
	}

	private LocalDateTime now() {
		return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
	}

}
