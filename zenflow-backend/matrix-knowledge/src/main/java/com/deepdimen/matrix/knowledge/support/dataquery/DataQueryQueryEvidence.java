package com.deepdimen.matrix.knowledge.support.dataquery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import static com.deepdimen.matrix.knowledge.support.dataquery.ControlledQueryService.*;

/**
 * Opt-in, expiring, AEAD-bound replay evidence. No rows, credentials, tokens or model
 * memory.
 */
final class DataQueryQueryEvidence {

	record Snapshot(DataQuerySqlGuard.Prepared query, Authorization authorization, String inventoryDigest,
			String ruleVersion, String executionVersion, String conditionProvenance,
			List<DataQueryRelationStore.Match> relationVersions, DataQuerySqlGuard.Prepared logicalQuery) {
		Snapshot(DataQuerySqlGuard.Prepared query, Authorization authorization, String inventoryDigest,
				String ruleVersion, String executionVersion, String conditionProvenance,
				List<DataQueryRelationStore.Match> relationVersions) {
			this(query, authorization, inventoryDigest, ruleVersion, executionVersion, conditionProvenance,
					relationVersions, null);
		}

		@Override
		public String toString() {
			return "QueryEvidence[REDACTED]";
		}
	}

	private final JdbcTemplate jdbc;

	private final ObjectMapper json;

	private final Clock clock;

	private final SecretKeySpec key;

	DataQueryQueryEvidence(DataSource source, ObjectMapper json, Clock clock, String encodedKey) {
		jdbc = new JdbcTemplate(source);
		jdbc.setQueryTimeout(5);
		this.json = json;
		this.clock = clock;
		try {
			byte[] bytes = Base64.getDecoder().decode(encodedKey);
			if (bytes.length != 32)
				throw new IllegalArgumentException();
			key = new SecretKeySpec(bytes, "AES");
		}
		catch (RuntimeException e) {
			throw new IllegalArgumentException("DATAQUERY_EVIDENCE_KEY_REQUIRED");
		}
	}

	void save(DataQueryAiChatService.Context c, DataQuerySqlGuard.Prepared sql) {
		save(c, sql, sql);
	}

	void save(DataQueryAiChatService.Context c, DataQuerySqlGuard.Prepared sql,
			DataQuerySqlGuard.Prepared logicalQuery) {
		var s = new Snapshot(sql, c.authorization(), c.inventory().digest(), c.ruleVersion(),
				DataQueryAiChatService.EXECUTION_VERSION, "CAPTURED_FOR_EXECUTION_NOT_USER_CONFIRMATION", c.relations(),
				logicalQuery);
		String encrypted = seal(c.identity(), c.queryId(), s);
		jdbc.update("DELETE FROM ai_dataquery_query_evidence WHERE tenant_id=? AND user_id=? AND query_id=?",
				c.identity().tenantId(), c.identity().userId(), c.queryId());
		jdbc.update(
				"INSERT INTO ai_dataquery_query_evidence (query_id,tenant_id,user_id,encrypted_snapshot,expires_at) VALUES (?,?,?,?,?)",
				c.queryId(), c.identity().tenantId(), c.identity().userId(), encrypted,
				LocalDateTime.ofInstant(clock.instant().plus(Duration.ofDays(7)), ZoneOffset.UTC));
	}

	Snapshot load(Identity owner, String queryId) {
		var rows = jdbc.queryForList(
				"SELECT encrypted_snapshot FROM ai_dataquery_query_evidence WHERE tenant_id=? AND user_id=? AND query_id=? AND expires_at>?",
				String.class, owner.tenantId(), owner.userId(), queryId,
				LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
		if (rows.size() != 1)
			throw new DataQueryException("INVESTIGATION_EVIDENCE_UNAVAILABLE");
		try {
			byte[] blob = Base64.getDecoder().decode(rows.get(0));
			var cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOfRange(blob, 0, 12)));
			cipher.updateAAD(aad(owner, queryId));
			return json.readValue(cipher.doFinal(Arrays.copyOfRange(blob, 12, blob.length)), Snapshot.class);
		}
		catch (Exception e) {
			throw new DataQueryException("INVESTIGATION_EVIDENCE_UNAVAILABLE");
		}
	}

	String seal(Identity owner, String queryId, Snapshot s) {
		try {
			byte[] plain = json.writeValueAsBytes(s);
			if (plain.length > 60000)
				throw new IllegalArgumentException();
			byte[] nonce = new byte[12];
			new SecureRandom().nextBytes(nonce);
			var cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
			cipher.updateAAD(aad(owner, queryId));
			byte[] ciphertext = cipher.doFinal(plain);
			byte[] blob = Arrays.copyOf(nonce, nonce.length + ciphertext.length);
			System.arraycopy(ciphertext, 0, blob, nonce.length, ciphertext.length);
			return Base64.getEncoder().encodeToString(blob);
		}
		catch (Exception e) {
			throw new DataQueryException("AUDIT_UNAVAILABLE");
		}
	}

	void purge() {
		jdbc.update("DELETE FROM ai_dataquery_query_evidence WHERE expires_at<=?",
				LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
	}

	private byte[] aad(Identity owner, String queryId) {
		return ("dq-evidence-v1|" + owner.tenantId() + "|" + owner.userId() + "|" + queryId)
			.getBytes(StandardCharsets.UTF_8);
	}

}
