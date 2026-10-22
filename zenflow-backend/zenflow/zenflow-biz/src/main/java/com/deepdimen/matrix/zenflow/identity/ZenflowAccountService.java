package com.deepdimen.matrix.zenflow.identity;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ZenflowAccountService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final EmailChallengeService challenges;
    private final ZenflowProperties properties;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public ZenflowAccountService(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                 EmailChallengeService challenges, ZenflowProperties properties) {
        this.jdbc = jdbc; this.transactions = new TransactionTemplate(manager);
        this.challenges = challenges; this.properties = properties;
    }

    public void register(String ticket, String password, String session, String locale) {
        EmailIdentity.checkPassword(password);
        String key = challenges.receiptKey(ticket);
        var prior = receipt("EMAIL_REGISTER", key);
        if (prior != null) { checkReplay(prior, challenges.requestDigest("REGISTER:" + session, prior.email, password)); return; }
        EmailChallengeService.Ticket grant = challenges.ticket(ticket, session, "REGISTER");
        String hash = challenges.requestDigest("REGISTER:" + session, grant.email(), password);
        try {
            transactions.executeWithoutResult(status -> {
                claim("EMAIL_REGISTER", key, hash);
                Long roleId = jdbc.query("SELECT role_id FROM app_role WHERE tenant_id=? AND role_code='ZF_CONSUMER' AND del_flag='0'",
                        rs -> rs.next() ? rs.getLong(1) : null, properties.getTenantId());
                if (roleId == null) throw new IllegalStateException("CONSUMER_ROLE_MISSING");
                Long id = IdWorker.getId();
                String publicId = new SecretDigests(properties.getAuthHmacKey()).randomHex(16);
                LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                jdbc.update("INSERT INTO app_user(user_id,tenant_id,username,password,email,email_normalized,email_verified_at,auth_version,phone,lock_flag,del_flag,create_time,update_time,last_modified_time) VALUES(?,?,?,?,?,?,?,1,NULL,'0','0',?,?,?)",
                        id, properties.getTenantId(), "zf_" + id, encoder.encode(password), grant.email(), grant.email(), now, now, now, now);
                jdbc.update("INSERT INTO app_user_role(user_id,role_id) VALUES(?,?)", id, roleId);
                jdbc.update("INSERT INTO zf_profile(tenant_id,owner_user_id,public_id,locale,companion_preferences,memory_enabled,context_epoch,version) VALUES(?,?,?,?,JSON_OBJECT(),0,1,1)",
                        properties.getTenantId(), id, publicId, supportedLocale(locale));
                complete("EMAIL_REGISTER", key, "app_user", id.toString());
            });
        } catch (DataIntegrityViolationException ex) {
            var finished = receipt("EMAIL_REGISTER", key);
            if (finished == null) throw new IllegalStateException("ACCOUNT_ALREADY_EXISTS");
            checkReplay(finished, hash);
        }
        challenges.discard(ticket);
    }

    public void reset(String ticket, String password, String session) {
        EmailIdentity.checkPassword(password);
        String key = challenges.receiptKey(ticket);
        var prior = receipt("PASSWORD_RESET", key);
        if (prior != null) { checkReplay(prior, challenges.requestDigest("RESET:" + session, prior.email, password)); return; }
        EmailChallengeService.Ticket grant = challenges.ticket(ticket, session, "RESET");
        if (grant.accountId().isBlank()) throw new IllegalArgumentException("INVALID_TICKET");
        String hash = challenges.requestDigest("RESET:" + session, grant.email(), password);
        try {
            transactions.executeWithoutResult(status -> {
                claim("PASSWORD_RESET", key, hash);
                Map<String, Object> user = jdbc.queryForMap("SELECT user_id,auth_version,email_normalized FROM app_user WHERE tenant_id=? AND user_id=? AND del_flag='0' AND lock_flag='0' FOR UPDATE",
                        properties.getTenantId(), Long.valueOf(grant.accountId()));
                long current = ((Number) user.get("auth_version")).longValue();
                if (current != Long.parseLong(grant.authVersion()) || !grant.email().equals(user.get("email_normalized"))) {
                    throw new IllegalStateException("AUTH_VERSION_CHANGED");
                }
                jdbc.update("UPDATE app_user SET password=?,last_modified_time=?,update_time=?,auth_version=? WHERE tenant_id=? AND user_id=? AND auth_version=?",
                        encoder.encode(password), LocalDateTime.now(ZoneOffset.UTC), LocalDateTime.now(ZoneOffset.UTC), current + 1,
                        properties.getTenantId(), Long.valueOf(grant.accountId()), current);
                long eventId = IdWorker.getId();
                jdbc.update("INSERT INTO zf_outbox(id,tenant_id,event_type,aggregate_type,aggregate_id,dedupe_key,payload_json,state,next_attempt_at) VALUES(?,?,'AUTH_REVOKED','app_user',?,?,JSON_OBJECT('authVersion',?),'PENDING',?)",
                        eventId, properties.getTenantId(), grant.accountId(), key, current + 1, LocalDateTime.now(ZoneOffset.UTC));
                complete("PASSWORD_RESET", key, "app_user", grant.accountId());
            });
        } catch (DataIntegrityViolationException ex) {
            var finished = receipt("PASSWORD_RESET", key);
            if (finished == null) throw ex;
            checkReplay(finished, hash);
        }
        challenges.discard(ticket);
    }

    private void claim(String operation, String key, String hash) {
        jdbc.update("INSERT INTO zf_command_receipt(id,tenant_id,actor_type,actor_id,operation,idempotency_key,request_hash,state) VALUES(?,?,'SERVICE','ZENFLOW_AUTH',?, ?,?,'PROCESSING')",
                IdWorker.getId(), properties.getTenantId(), operation, key, hash);
    }
    private void complete(String operation, String key, String type, String id) {
        jdbc.update("UPDATE zf_command_receipt SET state='SUCCEEDED',resource_type=?,resource_id=?,updated_at=? WHERE tenant_id=? AND actor_type='SERVICE' AND actor_id='ZENFLOW_AUTH' AND operation=? AND idempotency_key=?",
                type, id, LocalDateTime.now(ZoneOffset.UTC), properties.getTenantId(), operation, key);
    }
    private Receipt receipt(String operation, String key) {
        return jdbc.query("SELECT r.request_hash,r.state,u.email_normalized FROM zf_command_receipt r JOIN app_user u ON u.tenant_id=r.tenant_id AND CAST(u.user_id AS CHAR)=r.resource_id WHERE r.tenant_id=? AND r.actor_type='SERVICE' AND r.actor_id='ZENFLOW_AUTH' AND r.operation=? AND r.idempotency_key=?",
                rs -> rs.next() ? new Receipt(rs.getString(1), rs.getString(2), rs.getString(3)) : null,
                properties.getTenantId(), operation, key);
    }
    private void checkReplay(Receipt receipt, String hash) {
        if (!"SUCCEEDED".equals(receipt.state) || !SecretDigests.equalsDigest(receipt.hash, hash)) {
            throw new IllegalStateException("TICKET_REUSED");
        }
    }
    private static String supportedLocale(String locale) { return "en-US".equals(locale) ? "en-US" : "zh-CN"; }
    private record Receipt(String hash, String state, String email) {}
}
