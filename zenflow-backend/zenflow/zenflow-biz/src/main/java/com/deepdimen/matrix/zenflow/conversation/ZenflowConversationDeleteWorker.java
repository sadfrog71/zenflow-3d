package com.deepdimen.matrix.zenflow.conversation;

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
public class ZenflowConversationDeleteWorker {
    private static final Logger LOG = LoggerFactory.getLogger(ZenflowConversationDeleteWorker.class);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final long tenant;
    private final String worker = "conversation-cleanup-" + UUID.randomUUID();

    public ZenflowConversationDeleteWorker(JdbcTemplate jdbc, PlatformTransactionManager manager,
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
                        + "error_code='CONVERSATION_CLEANUP_FAILED',lease_owner=NULL,lease_until=NULL, "
                        + "next_attempt_at=DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 30 SECOND), "
                        + "version=version+1,updated_at=UTC_TIMESTAMP(3) "
                        + "WHERE id=? AND tenant_id=? AND state='RUNNING' AND lease_owner=? AND lease_epoch=?",
                        claim.id(), tenant, worker, claim.epoch());
                LOG.warn("Private conversation cleanup deferred; eventId={}", claim.id());
            }
        } catch (Exception failure) {
            LOG.warn("Private conversation cleanup storage unavailable");
        }
    }

    private Claim claim() {
        jdbc.update("UPDATE zf_outbox SET state='DEAD',error_code='CONVERSATION_CLEANUP_RETRY_EXHAUSTED', "
                + "lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=UTC_TIMESTAMP(3) "
                + "WHERE tenant_id=? AND event_type='PRIVATE_CONVERSATION_DELETE' AND state='RUNNING' "
                + "AND attempts>=5 AND lease_until<UTC_TIMESTAMP(3)",tenant);
        var rows = jdbc.queryForList("SELECT id,lease_epoch FROM zf_outbox WHERE tenant_id=? "
                + "AND event_type='PRIVATE_CONVERSATION_DELETE' AND aggregate_type='CONVERSATION' "
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
        String session=rows.get(0).get("aggregate_id").toString();String owner;
        try {
            var payload=json.readTree(rows.get(0).get("payload_json").toString());
            if(payload.size()!=1 || !payload.path("ownerUserId").asText().matches("[0-9]{1,19}") || !session.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException();
            owner=payload.path("ownerUserId").asText();
        }catch(Exception invalid){finish(claim,"DEAD","CONVERSATION_DELETE_TARGET_INVALID");return;}
        var owned=jdbc.queryForList("SELECT b.session_id FROM zf_conversation_binding b JOIN ai_assistant_session a ON a.id=b.session_id AND a.tenant_id=b.tenant_id AND a.owner_user_id=b.owner_user_id AND a.owner_subject_type='APP_USER' WHERE b.session_id=? AND b.tenant_id=? AND b.owner_user_id=? AND b.deleted_at IS NOT NULL FOR UPDATE",session,tenant,owner);
        if(owned.size()!=1){finish(claim,"DEAD","CONVERSATION_DELETE_TARGET_INVALID");return;}
        var active=jdbc.queryForList("SELECT id FROM ai_assistant_execution_run WHERE session_id=? AND tenant_id=? AND status NOT IN ('COMPLETED','FAILED','CANCELLED') LIMIT 1 FOR UPDATE",session,tenant);
        if(!active.isEmpty())throw new IllegalStateException("CONVERSATION_CANCEL_PENDING");
        // All body-bearing native stores belong to the already locked APP_USER session.
        jdbc.update("DELETE e FROM ai_assistant_run_event e JOIN ai_assistant_execution_run r ON r.id=e.run_id WHERE r.session_id=? AND r.tenant_id=?",session,tenant);
        jdbc.update("DELETE s FROM ai_skill_run s JOIN zf_companion_run_context x ON (x.skill_run_id=s.id OR x.run_id=s.business_run_id) AND x.tenant_id=s.tenant_id WHERE x.session_id=? AND x.tenant_id=? AND x.owner_user_id=? AND s.actor_type='APP_USER' AND s.subject_type='APP_USER' AND s.actor_id=? AND s.subject_id=?",session,tenant,owner,owner,owner);
        jdbc.update("DELETE FROM zf_run_source WHERE tenant_id=? AND owner_user_id=? AND run_id IN (SELECT run_id FROM zf_companion_run_context WHERE session_id=? AND tenant_id=? AND owner_user_id=?)",tenant,owner,session,tenant,owner);
        jdbc.update("DELETE FROM zf_companion_run_context WHERE session_id=? AND tenant_id=? AND owner_user_id=?",session,tenant,owner);
        jdbc.update("DELETE FROM ai_assistant_execution_run WHERE session_id=? AND tenant_id=?",session,tenant);
        jdbc.update("DELETE FROM ai_assistant_citation WHERE context_pack_id IN (SELECT id FROM ai_assistant_context_pack WHERE session_id=?)",session);
        jdbc.update("DELETE FROM ai_assistant_context_pack WHERE session_id=?",session);
        jdbc.update("DELETE FROM ai_assistant_exchange WHERE session_id=?",session);
        jdbc.update("DELETE FROM ai_assistant_external_conversation WHERE session_id=?",session);
        jdbc.update("DELETE FROM ai_assistant_message WHERE session_id=?",session);
        jdbc.update("UPDATE ai_assistant_session SET title='已删除',version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=? AND owner_user_id=? AND owner_subject_type='APP_USER'",session,tenant,owner);
        jdbc.update("UPDATE zf_conversation_binding SET active_run_id=NULL,updated_at=UTC_TIMESTAMP(3) WHERE session_id=? AND tenant_id=? AND owner_user_id=?",session,tenant,owner);
        finish(claim,"SUCCEEDED",null);
    }

    private void finish(Claim claim, String state, String errorCode) {
        jdbc.update("UPDATE zf_outbox SET state=?,error_code=?,lease_owner=NULL,lease_until=NULL, "
                + "version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=? "
                + "AND state='RUNNING' AND lease_owner=? AND lease_epoch=?",
                state, errorCode, claim.id(), tenant, worker, claim.epoch());
    }

    private record Claim(long id, long epoch) { }
}
