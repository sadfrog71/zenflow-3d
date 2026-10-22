package com.deepdimen.matrix.admin.mobilepush;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class MobilePushOutboxServiceImpl {

	private final JdbcTemplate jdbc;

	private final MobilePushProperties properties;

	public MobilePushOutboxServiceImpl(JdbcTemplate jdbc, MobilePushProperties properties) {
		this.jdbc = jdbc;
		this.properties = properties;
	}

	public void enqueue(Long tenantId, Long messageId) {
		if (!properties.isEnabled())
			return;
		if (tenantId == null || messageId == null || !TransactionSynchronizationManager.isActualTransactionActive())
			throw new IllegalStateException("推送 Outbox 必须与消息同事务写入");
		jdbc.update(
				"""
						INSERT INTO sys_push_outbox (id,environment,tenant_id,message_id,relation_id,user_id,status,attempts,next_attempt_at,expires_at,created_at)
						SELECT UUID(),?,m.tenant_id,m.id,r.id,r.user_id,'QUEUED',0,CURRENT_TIMESTAMP(3),
						LEAST(COALESCE(m.expire_time,(CURRENT_TIMESTAMP(3)+INTERVAL '24' HOUR)),(CURRENT_TIMESTAMP(3)+INTERVAL '24' HOUR)),CURRENT_TIMESTAMP(3)
						FROM sys_message m JOIN sys_message_relation r ON r.msg_id=m.id AND r.tenant_id=m.tenant_id
						WHERE m.id=? AND m.tenant_id=? AND m.send_flag='1' AND m.del_flag='0' AND r.del_flag='0'
						AND (m.expire_time IS NULL OR m.expire_time>CURRENT_TIMESTAMP(3))
						ON DUPLICATE KEY UPDATE id=id
						""",
				properties.getEnvironment(), messageId, tenantId);
	}

}
