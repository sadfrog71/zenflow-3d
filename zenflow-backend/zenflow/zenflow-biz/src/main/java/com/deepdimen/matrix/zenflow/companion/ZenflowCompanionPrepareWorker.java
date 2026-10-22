package com.deepdimen.matrix.zenflow.companion;

import com.deepdimen.matrix.zenflow.admin.ZenflowAdminAccess;
import com.deepdimen.matrix.zenflow.admin.ZenflowModelService;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Durable public-only preparation: fixed source bodies, original managed release, no
 * online activation.
 */
@Component
@ConditionalOnProperty(name = "zenflow.companion.prepare-enabled", havingValue = "true", matchIfMissing = true)
public class ZenflowCompanionPrepareWorker {

	private final JdbcTemplate jdbc;

	private final TransactionTemplate tx;

	private final ZenflowAdminAccess access;

	private final ZenflowModelService models;

	private final ZenflowReferenceSnapshots snapshots;

	private final ZenflowReferenceIndexes fixedIndexes;

	private final ZenflowCompanionService companions;

	private final ObjectProvider<ZenflowReferenceIndexPort> indexes;

	private final ObjectMapper json;

	private final ZenflowCompanionPackage compiler;

	private final long tenant;

	private final String worker = "companion-prepare-" + UUID.randomUUID();

	public ZenflowCompanionPrepareWorker(JdbcTemplate jdbc, PlatformTransactionManager manager,
			ZenflowAdminAccess access, ZenflowModelService models, ZenflowReferenceSnapshots snapshots,
			ZenflowCompanionService companions, ZenflowReferenceIndexes fixedIndexes,
			ObjectProvider<ZenflowReferenceIndexPort> indexes, ObjectMapper json, ZenflowProperties product) {
		this.jdbc = jdbc;
		this.tx = new TransactionTemplate(manager);
		this.access = access;
		this.models = models;
		this.snapshots = snapshots;
		this.companions = companions;
		this.fixedIndexes = fixedIndexes;
		this.indexes = indexes;
		this.json = json;
		this.compiler = new ZenflowCompanionPackage(json);
		this.tenant = product.getTenantId();
	}

	@Scheduled(fixedDelayString = "${zenflow.companion.prepare-interval-ms:5000}",
			initialDelayString = "${zenflow.companion.prepare-initial-delay-ms:10000}")
	public void poll() {
		Claim claim = tx.execute(ignored -> claim());
		if (claim == null)
			return;
		try {
			access.runDelegated(claim.administratorId(), () -> execute(claim));
		}
		catch (Exception failure) {
			String code = failure instanceof ResponseStatusException response && response.getReason() != null
					&& response.getReason().matches("[A-Z_]{1,64}") ? response.getReason() : "COMPANION_PREPARE_FAILED";
			tx.executeWithoutResult(ignored -> {
				var row = lock(claim);
				if (row == null)
					return;
				jdbc.update(
						"UPDATE zf_companion_revision SET state='FAILED',error_code=?,lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND id=?",
						code, tenant, claim.revisionId());
				jdbc.update(
						"UPDATE zf_outbox SET state='DEAD',error_code=?,lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND id=?",
						code, tenant, claim.eventId());
			});
		}
	}

	private void execute(Claim claim) {
		var input = tx.execute(ignored -> {
			var row = require(claim);
			var selectedPolicy = policy(row);
			return new Input(row, selectedPolicy, snapshots.capture(claim.revisionId(), selectedPolicy));
		});
		if (input.policy().hasRetrieval()) {
			var index = indexes.getIfAvailable();
			if (index == null)
				throw error("REFERENCE_INDEX_NOT_READY");
			index.prepare(claim.revisionId(), input.fixed(), () -> heartbeat(claim));
		}
		heartbeat(claim);
		String modelId = input.row().get("model_id").toString();
		var model = models.publishedSnapshot(modelId);
		if (!compiler.modelDigest(modelId, model).equals(input.row().get("model_config_digest")))
			throw error("COMPANION_MODEL_CHANGED");
		var indexConfiguration = !input.policy().hasRetrieval() ? Map.<String, Object>of()
				: fixedIndexes.verify(input.row());
		var prepared = compiler.compile(claim.revisionId(), modelId, authoring(input.row()), model, input.fixed(),
				indexConfiguration);
		tx.executeWithoutResult(ignored -> {
			access.require();
			var row = require(claim);
			snapshots.verify(claim.revisionId(), input.policy(), input.fixed().digest());
			if (!compiler.modelDigest(modelId, models.publishedSnapshot(modelId))
				.equals(row.get("model_config_digest")))
				throw error("COMPANION_MODEL_CHANGED");
			if (input.policy().hasRetrieval())
				fixedIndexes.verify(row);
			companions.sealPreparation(row, prepared);
			jdbc.update(
					"UPDATE zf_outbox SET state='SUCCEEDED',error_code=NULL,lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND id=?",
					tenant, claim.eventId());
		});
	}

	private Claim claim() {
		// Lock order matches requests: configuration -> revision -> event. Candidate
		// selection does not lock.
		var candidates = jdbc.queryForList(
				"SELECT e.id,e.aggregate_id,r.config_id FROM zf_outbox e JOIN zf_companion_revision r ON r.tenant_id=e.tenant_id AND r.id=e.aggregate_id WHERE e.tenant_id=? AND e.event_type='COMPANION_PREPARE' AND e.aggregate_type='COMPANION_REVISION' AND r.state='PREPARING' AND ((e.state='PENDING' AND e.next_attempt_at<=UTC_TIMESTAMP(3)) OR (e.state='RUNNING' AND e.lease_until<UTC_TIMESTAMP(3))) ORDER BY e.id LIMIT 1",
				tenant);
		if (candidates.isEmpty())
			return null;
		var candidate = candidates.get(0);
		jdbc.queryForList("SELECT id FROM zf_companion_config WHERE tenant_id=? AND id=? FOR UPDATE", tenant,
				candidate.get("config_id"));
		var revisions = jdbc.queryForList(
				"SELECT id,lease_epoch FROM zf_companion_revision WHERE tenant_id=? AND id=? AND state='PREPARING' FOR UPDATE",
				tenant, candidate.get("aggregate_id"));
		if (revisions.size() != 1)
			return null;
		var events = jdbc.queryForList(
				"SELECT id,payload_json,attempts FROM zf_outbox WHERE tenant_id=? AND id=? AND ((state='PENDING' AND next_attempt_at<=UTC_TIMESTAMP(3)) OR (state='RUNNING' AND lease_until<UTC_TIMESTAMP(3))) FOR UPDATE",
				tenant, candidate.get("id"));
		if (events.size() != 1)
			return null;
		var event = events.get(0);
		long actor;
		try {
			var payload = json.readTree(event.get("payload_json").toString());
			if (payload.size() != 1 || !payload.path("administratorId").asText().matches("[1-9][0-9]{0,18}"))
				throw new IllegalArgumentException();
			actor = Long.parseLong(payload.path("administratorId").asText());
		}
		catch (Exception invalid) {
			retire(candidate, "COMPANION_TASK_ACTOR_INVALID");
			return null;
		}
		if (((Number) event.get("attempts")).longValue() >= 5) {
			retire(candidate, "COMPANION_PREPARE_RETRY_EXHAUSTED");
			return null;
		}
		long epoch = ((Number) revisions.get(0).get("lease_epoch")).longValue() + 1;
		jdbc.update(
				"UPDATE zf_companion_revision SET lease_epoch=?,lease_owner=?,lease_until=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 60 SECOND),attempts=attempts+1,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND id=?",
				epoch, worker, tenant, candidate.get("aggregate_id"));
		jdbc.update(
				"UPDATE zf_outbox SET state='RUNNING',lease_epoch=?,lease_owner=?,lease_until=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 60 SECOND),attempts=attempts+1,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND id=?",
				epoch, worker, tenant, event.get("id"));
		return new Claim(((Number) event.get("id")).longValue(), candidate.get("aggregate_id").toString(), epoch,
				actor);
	}

	private void retire(Map<String, Object> candidate, String code) {
		jdbc.update(
				"UPDATE zf_companion_revision SET state='FAILED',error_code=?,lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND id=? AND state='PREPARING'",
				code, tenant, candidate.get("aggregate_id"));
		jdbc.update(
				"UPDATE zf_outbox SET state='DEAD',error_code=?,lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND id=?",
				code, tenant, candidate.get("id"));
	}

	private Map<String, Object> lock(Claim claim) {
		var configs = jdbc.queryForList(
				"SELECT c.id FROM zf_companion_config c JOIN zf_companion_revision r ON r.config_id=c.id AND r.tenant_id=c.tenant_id WHERE r.tenant_id=? AND r.id=? FOR UPDATE",
				tenant, claim.revisionId());
		if (configs.size() != 1)
			return null;
		var rows = jdbc.queryForList(
				"SELECT * FROM zf_companion_revision WHERE tenant_id=? AND id=? AND state='PREPARING' AND lease_owner=? AND lease_epoch=? AND lease_until>UTC_TIMESTAMP(3) FOR UPDATE",
				tenant, claim.revisionId(), worker, claim.epoch());
		if (rows.size() != 1)
			return null;
		Long events = jdbc.queryForObject(
				"SELECT COUNT(*) FROM zf_outbox WHERE tenant_id=? AND id=? AND event_type='COMPANION_PREPARE' AND aggregate_type='COMPANION_REVISION' AND aggregate_id=? AND state='RUNNING' AND lease_owner=? AND lease_epoch=? AND lease_until>UTC_TIMESTAMP(3) FOR UPDATE",
				Long.class, tenant, claim.eventId(), claim.revisionId(), worker, claim.epoch());
		return events != null && events == 1 ? rows.get(0) : null;
	}

	private Map<String, Object> require(Claim claim) {
		var row = lock(claim);
		if (row == null)
			throw error("COMPANION_PREPARE_LEASE_LOST");
		return row;
	}

	private void heartbeat(Claim claim) {
		tx.executeWithoutResult(ignored -> {
			access.require();
			require(claim);
			jdbc.update(
					"UPDATE zf_companion_revision SET lease_until=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 60 SECOND) WHERE tenant_id=? AND id=?",
					tenant, claim.revisionId());
			jdbc.update(
					"UPDATE zf_outbox SET lease_until=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 60 SECOND) WHERE tenant_id=? AND id=?",
					tenant, claim.eventId());
		});
	}

	private ZenflowReferencePolicy policy(Map<String, Object> row) {
		return ZenflowReferencePolicy.parse(read(row.get("retrieval_policy_json")));
	}

	private Map<String, Object> authoring(Map<String, Object> row) {
		return read(row.get("authoring_json"));
	}

	private Map<String, Object> read(Object value) {
		try {
			return json.readValue(value.toString(), new TypeReference<Map<String, Object>>() {
			});
		}
		catch (Exception invalid) {
			throw error("COMPANION_TASK_DATA_INVALID");
		}
	}

	private static ResponseStatusException error(String code) {
		return new ResponseStatusException(HttpStatus.CONFLICT, code);
	}

	private record Claim(long eventId, String revisionId, long epoch, long administratorId) {
	}

	private record Input(Map<String, Object> row, ZenflowReferencePolicy policy,
			ZenflowReferenceSnapshots.Frozen fixed) {
	}

}

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "zenflow.companion.prepare-enabled", havingValue = "true", matchIfMissing = true)
class ZenflowCompanionPrepareScheduling {

}
