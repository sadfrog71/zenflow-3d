package com.deepdimen.matrix.admin.mobilepush;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PushDispatchRepository {

	public record Outbox(String id, long tenantId, long messageId, long relationId, long userId, int attempts,
			Instant expiresAt) {
	}

	public record Target(String installationId, String bindingId, String sessionId, String channel,
			String tokenCiphertext, String tokenHash) {
		@Override
		public String toString() {
			return "PushTarget[redacted]";
		}
	}

	public record Message(String sourceType) {
	}

	public record Delivery(String id, String bindingId, String status) {
	}

	private final JdbcTemplate jdbc;

	private final MobilePushProperties properties;

	public PushDispatchRepository(JdbcTemplate jdbc, MobilePushProperties properties) {
		this.jdbc = jdbc;
		this.properties = properties;
	}

	public List<String> ready() {
		jdbc.update(
				"UPDATE sys_push_outbox SET status='EXHAUSTED',failure_code='EXPIRED' WHERE environment=? AND expires_at<=CURRENT_TIMESTAMP(3) AND status IN ('QUEUED','RETRY','PROCESSING') AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP(3))",
				properties.getEnvironment());
		return jdbc.query(
				"SELECT id FROM sys_push_outbox WHERE environment=? AND next_attempt_at<=CURRENT_TIMESTAMP(3) AND expires_at>CURRENT_TIMESTAMP(3) AND (status IN ('QUEUED','RETRY') OR (status='PROCESSING' AND lease_until<CURRENT_TIMESTAMP(3))) ORDER BY created_at LIMIT 20",
				(rs, row) -> rs.getString(1), properties.getEnvironment());
	}

	public Outbox claim(String id, String owner) {
		int changed = jdbc.update(
				"UPDATE sys_push_outbox SET status='PROCESSING',lease_owner=?,lease_until=(CURRENT_TIMESTAMP(3)+INTERVAL '10' MINUTE),attempts=attempts+1 WHERE id=? AND environment=? AND next_attempt_at<=CURRENT_TIMESTAMP(3) AND expires_at>CURRENT_TIMESTAMP(3) AND (status IN ('QUEUED','RETRY') OR (status='PROCESSING' AND lease_until<CURRENT_TIMESTAMP(3)))",
				owner, id, properties.getEnvironment());
		if (changed != 1)
			return null;
		return jdbc.query(
				"SELECT id,tenant_id,message_id,relation_id,user_id,attempts,expires_at FROM sys_push_outbox WHERE id=? AND environment=? AND lease_owner=?",
				(rs, row) -> new Outbox(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5),
						rs.getInt(6), rs.getTimestamp(7).toInstant()),
				id, properties.getEnvironment(), owner)
			.stream()
			.findFirst()
			.orElse(null);
	}

	public Message message(Outbox outbox) {
		return jdbc
			.query("""
					SELECT m.source_type FROM sys_message m JOIN sys_message_relation r ON r.msg_id=m.id AND r.tenant_id=m.tenant_id
					WHERE m.id=? AND m.tenant_id=? AND r.id=? AND r.user_id=? AND m.send_flag='1' AND m.del_flag='0' AND r.del_flag='0'
					AND r.read_flag='0' AND (m.expire_time IS NULL OR m.expire_time>CURRENT_TIMESTAMP(3))
					""",
					(rs, row) -> new Message(rs.getString(1)), outbox.messageId(), outbox.tenantId(),
					outbox.relationId(), outbox.userId())
			.stream()
			.findFirst()
			.orElse(null);
	}

	public List<Target> targets(Outbox outbox) {
		return jdbc.query(
				"""
						SELECT i.id,s.binding_id,s.session_id,i.channel,i.token_ciphertext,i.token_hash FROM sys_push_installation i
						JOIN sys_push_subscription s ON s.installation_id=i.id AND s.environment=i.environment
						JOIN sys_push_outbox o ON o.id=? AND o.environment=s.environment AND o.tenant_id=s.tenant_id AND o.user_id=s.user_id
						WHERE i.environment=? AND i.app_id=? AND s.tenant_id=? AND s.user_id=? AND s.enabled=1 AND i.status='ACTIVE'
						AND i.permission_state='GRANTED' AND i.last_seen_at>(CURRENT_TIMESTAMP(3)-INTERVAL '30' DAY)
						AND s.subscribed_at<=o.created_at ORDER BY i.last_seen_at DESC LIMIT 5
						""",
				(rs, row) -> new Target(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
						rs.getString(5), rs.getString(6)),
				outbox.id(), properties.getEnvironment(), properties.applicationId(), outbox.tenantId(),
				outbox.userId());
	}

	public boolean stillCurrent(Outbox outbox, Target target, String owner) {
		Integer count = jdbc.queryForObject(
				"""
						SELECT COUNT(*) FROM sys_push_subscription s JOIN sys_push_installation i ON i.id=s.installation_id AND i.environment=s.environment
						JOIN sys_push_outbox o ON o.id=? AND o.environment=s.environment AND o.tenant_id=s.tenant_id AND o.user_id=s.user_id
						WHERE s.environment=? AND s.tenant_id=? AND s.user_id=? AND s.binding_id=? AND s.session_id=? AND s.enabled=1
						AND i.id=? AND i.token_hash=? AND i.status='ACTIVE' AND i.permission_state='GRANTED'
						AND o.status='PROCESSING' AND o.lease_owner=? AND o.lease_until>CURRENT_TIMESTAMP(3) AND o.expires_at>CURRENT_TIMESTAMP(3)
						""",
				Integer.class, outbox.id(), properties.getEnvironment(), outbox.tenantId(), outbox.userId(),
				target.bindingId(), target.sessionId(), target.installationId(), target.tokenHash(), owner);
		return count != null && count == 1;
	}

	public Delivery delivery(Outbox outbox, Target target) {
		jdbc.update(
				"INSERT INTO sys_push_delivery (id,outbox_id,environment,tenant_id,message_id,relation_id,installation_id,binding_id,channel,status,updated_at) VALUES (?,?,?,?,?,?,?,?,?,'QUEUED',CURRENT_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE id=id",
				UUID.randomUUID().toString(), outbox.id(), properties.getEnvironment(), outbox.tenantId(),
				outbox.messageId(), outbox.relationId(), target.installationId(), target.bindingId(), target.channel());
		return jdbc.query(
				"SELECT id,binding_id,status FROM sys_push_delivery WHERE environment=? AND tenant_id=? AND message_id=? AND relation_id=? AND installation_id=? AND channel=?",
				(rs, row) -> new Delivery(rs.getString(1), rs.getString(2), rs.getString(3)),
				properties.getEnvironment(), outbox.tenantId(), outbox.messageId(), outbox.relationId(),
				target.installationId(), target.channel())
			.get(0);
	}

	public boolean reserveRateSlot(Outbox outbox) {
		return jdbc.update(
				"""
						UPDATE sys_push_preference SET window_count=CASE WHEN window_started_at IS NULL OR window_started_at<=(CURRENT_TIMESTAMP(3)-INTERVAL '10' MINUTE) THEN 1 ELSE window_count+1 END,
						window_started_at=CASE WHEN window_started_at IS NULL OR window_started_at<=(CURRENT_TIMESTAMP(3)-INTERVAL '10' MINUTE) THEN CURRENT_TIMESTAMP(3) ELSE window_started_at END
						WHERE environment=? AND tenant_id=? AND user_id=? AND enabled=1
						AND (window_started_at IS NULL OR window_started_at<=(CURRENT_TIMESTAMP(3)-INTERVAL '10' MINUTE) OR window_count<5)
						""",
				properties.getEnvironment(), outbox.tenantId(), outbox.userId()) == 1;
	}

	public void result(Delivery delivery, Target target, PushProvider.Result result) {
		jdbc.update(
				"UPDATE sys_push_delivery SET status=?,attempts=attempts+1,provider_receipt=?,failure_code=?,updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND environment=? AND binding_id=?",
				result.status(), result.receipt(), result.failureCode(), delivery.id(), properties.getEnvironment(),
				target.bindingId());
		if ("INVALID_TOKEN".equals(result.status()))
			jdbc.update(
					"UPDATE sys_push_installation SET status='TOKEN_INVALID' WHERE id=? AND environment=? AND token_hash=?",
					target.installationId(), properties.getEnvironment(), target.tokenHash());
	}

	public void finish(Outbox outbox, String owner, boolean retry, String code) {
		String status = retry ? outbox.attempts() >= 8 ? "EXHAUSTED" : "RETRY"
				: code == null ? "COMPLETED" : "SUPPRESSED";
		long seconds = Math.min(3600, 30L << Math.min(outbox.attempts() - 1, 7));
		jdbc.update(
				"UPDATE sys_push_outbox SET status=?,failure_code=?,next_attempt_at=?,lease_owner=NULL,lease_until=NULL WHERE id=? AND environment=? AND lease_owner=? AND status='PROCESSING'",
				status, code, Timestamp.from(Instant.now().plusSeconds(seconds)), outbox.id(),
				properties.getEnvironment(), owner);
	}

}
