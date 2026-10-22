package com.deepdimen.matrix.zenflow.capsule;

import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Only consumes product-owned private note deletion events. Never logs private content. */
@Component
@ConditionalOnProperty(name = "zenflow.core.cleanup-enabled", havingValue = "true")
public class ZenflowNoteDeleteWorker {
    private static final Logger LOG = LoggerFactory.getLogger(ZenflowNoteDeleteWorker.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final long tenant;
    private final String worker = "note-cleanup-" + UUID.randomUUID();

    public ZenflowNoteDeleteWorker(JdbcTemplate jdbc, PlatformTransactionManager manager,
            ObjectMapper json, ZenflowProperties properties) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(manager);
        this.json = json;
        this.tenant = properties.getTenantId();
    }

    @Scheduled(fixedDelayString = "${zenflow.core.cleanup-interval-ms:5000}", initialDelay = 10000)
    public void poll() {
        try {
            var claim = tx.execute(ignored -> claim());
            if (claim == null) return;
            try {
                tx.executeWithoutResult(ignored -> clean(claim));
            } catch (Exception failure) {
                // Rollback includes both body removal and event state; a fresh epoch owns retries.
                jdbc.update("UPDATE zf_outbox SET state=IF(attempts>=5,'DEAD','PENDING'), "
                        + "error_code='NOTE_CLEANUP_FAILED',lease_owner=NULL,lease_until=NULL, "
                        + "next_attempt_at=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 30 SECOND), "
                        + "version=version+1,updated_at=UTC_TIMESTAMP(3) "
                        + "WHERE id=? AND tenant_id=? AND state='RUNNING' AND lease_owner=? AND lease_epoch=?",
                        claim.id(), tenant, worker, claim.epoch());
                LOG.warn("Private note cleanup deferred; eventId={}", claim.id());
            }
        } catch (Exception failure) {
            LOG.warn("Private note cleanup storage unavailable");
        }
    }

    private Claim claim() {
        jdbc.update("UPDATE zf_outbox SET state='DEAD',error_code='NOTE_CLEANUP_RETRY_EXHAUSTED', "
                + "lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=UTC_TIMESTAMP(3) "
                + "WHERE tenant_id=? AND event_type='PRIVATE_NOTE_DELETE' AND state='RUNNING' "
                + "AND attempts>=5 AND lease_until<UTC_TIMESTAMP(3)",tenant);
        var rows = jdbc.queryForList("SELECT id,lease_epoch FROM zf_outbox WHERE tenant_id=? "
                + "AND event_type='PRIVATE_NOTE_DELETE' AND aggregate_type='CAPSULE' "
                + "AND attempts<5 AND ((state='PENDING' AND next_attempt_at<=UTC_TIMESTAMP(3)) "
                + "OR (state='RUNNING' AND lease_until<UTC_TIMESTAMP(3))) "
                + "ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED", tenant);
        if (rows.isEmpty()) return null;
        long id = ((Number) rows.get(0).get("id")).longValue();
        long epoch = ((Number) rows.get(0).get("lease_epoch")).longValue() + 1;
        jdbc.update("UPDATE zf_outbox SET state='RUNNING',attempts=attempts+1,lease_epoch=?, "
                + "lease_owner=?,lease_until=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 30 SECOND), "
                + "version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=?", epoch, worker, id, tenant);
        return new Claim(id, epoch);
    }

    private void clean(Claim claim) {
        var rows = jdbc.queryForList("SELECT aggregate_id,payload_json FROM zf_outbox WHERE id=? "
                + "AND tenant_id=? AND state='RUNNING' AND lease_owner=? AND lease_epoch=? "
                + "AND lease_until>UTC_TIMESTAMP(3) FOR UPDATE", claim.id(), tenant, worker, claim.epoch());
        if (rows.isEmpty()) return;
        long capsule;
        long owner;
        try {
            capsule = Long.parseLong(rows.get(0).get("aggregate_id").toString());
            var payload = json.readTree(rows.get(0).get("payload_json").toString());
            if (payload.size() != 1 || !payload.has("ownerUserId")) throw new IllegalArgumentException();
            owner = Long.parseLong(payload.get("ownerUserId").asText());
            if (capsule <= 0 || owner <= 0) throw new IllegalArgumentException();
        } catch (Exception failure) {
            finish(claim, "DEAD", "NOTE_DELETE_EVENT_INVALID");
            return;
        }
        jdbc.queryForList("SELECT owner_user_id FROM zf_profile WHERE tenant_id=? AND owner_user_id=? FOR UPDATE", tenant, owner);
        var ext = jdbc.queryForList("SELECT delete_state FROM zf_capsule_ext WHERE tenant_id=? "
                + "AND owner_user_id=? AND capsule_id=? FOR UPDATE", tenant, owner, capsule);
        if (ext.size() != 1) {
            finish(claim, "DEAD", "NOTE_DELETE_TARGET_INVALID");
            return;
        }
        if ("DELETED".equals(ext.get(0).get("delete_state"))) {
            finish(claim, "SUCCEEDED", null);
            return;
        }
        if (!"DELETE_REQUESTED".equals(ext.get(0).get("delete_state"))
                || jdbc.queryForList("SELECT id FROM ai_capsule WHERE id=? AND tenant_id=? "
                        + "AND owner_user_id=? AND owner_subject_type='APP_USER' AND status='TRASHED' FOR UPDATE",
                        capsule, tenant, owner).size() != 1) {
            finish(claim, "DEAD", "NOTE_DELETE_TARGET_INVALID");
            return;
        }
        jdbc.update("DELETE FROM ai_capsule_revision WHERE capsule_id=? AND tenant_id=? "
                + "AND owner_user_id=? AND owner_subject_type='APP_USER'", capsule, tenant, owner);
        jdbc.update("DELETE FROM zf_capsule_revision_source WHERE capsule_id=? AND tenant_id=? AND owner_user_id=?", capsule, tenant, owner);
        jdbc.update("UPDATE ai_capsule SET title='已删除',tags=NULL,source_label=NULL,current_revision_id=0, "
                + "del_flag='1',version=version+1,update_time=UTC_TIMESTAMP(3) "
                + "WHERE id=? AND tenant_id=? AND owner_user_id=? AND owner_subject_type='APP_USER'", capsule, tenant, owner);
        jdbc.update("UPDATE zf_memory SET summary='',state='REVOKED',version=version+1,updated_at=UTC_TIMESTAMP(3) "
                + "WHERE capsule_id=? AND tenant_id=? AND owner_user_id=?", capsule, tenant, owner);
        jdbc.update("UPDATE zf_capsule_consent SET state='REVOKED',version=version+1, "
                + "revoked_at=COALESCE(revoked_at,UTC_TIMESTAMP(3)),updated_at=UTC_TIMESTAMP(3) "
                + "WHERE capsule_id=? AND tenant_id=? AND owner_user_id=? AND state<>'REVOKED'", capsule, tenant, owner);
        jdbc.update("UPDATE zf_capsule_ext SET delete_state='DELETED',version=version+1, "
                + "updated_at=UTC_TIMESTAMP(3) WHERE capsule_id=? AND tenant_id=? AND owner_user_id=?", capsule, tenant, owner);
        finish(claim, "SUCCEEDED", null);
    }

    private void finish(Claim claim, String state, String errorCode) {
        jdbc.update("UPDATE zf_outbox SET state=?,error_code=?,lease_owner=NULL,lease_until=NULL, "
                + "version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=? "
                + "AND state='RUNNING' AND lease_owner=? AND lease_epoch=?",
                state, errorCode, claim.id(), tenant, worker, claim.epoch());
    }

    private record Claim(long id, long epoch) { }
}

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "zenflow.core.cleanup-enabled", havingValue = "true")
class ZenflowCoreSchedulingConfiguration { }
