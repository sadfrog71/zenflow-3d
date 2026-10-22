package com.deepdimen.matrix.zenflow.conversation;

import com.deepdimen.matrix.assistant.run.AssistantRunEngine;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;

/** Dedicated product instance recovers durable leases; invoked external calls never silently resend. */
@Component
@ConditionalOnProperty(name="zenflow.core.cleanup-enabled",havingValue="true")
public class ZenflowConversationRecovery {
    private final AssistantRunEngine engine;private final JdbcTemplate jdbc;private final long tenant;
    public ZenflowConversationRecovery(AssistantRunEngine engine,JdbcTemplate jdbc,ZenflowProperties product){this.engine=engine;this.jdbc=jdbc;this.tenant=product.getTenantId();}
    @Scheduled(fixedDelay=5000,initialDelay=15000) public void recover() {
        try {
            engine.recoverExpired(Instant.now(),32);
            jdbc.update("UPDATE zf_conversation_binding b JOIN ai_assistant_execution_run r ON r.id=b.active_run_id AND r.session_id=b.session_id AND r.tenant_id=b.tenant_id SET b.active_run_id=NULL,b.updated_at=UTC_TIMESTAMP(3) WHERE b.tenant_id=? AND r.status IN ('COMPLETED','FAILED','CANCELLED')",tenant);
        }catch(RuntimeException failure){LoggerFactory.getLogger(getClass()).warn("Conversation recovery deferred; type={}",failure.getClass().getSimpleName());}
    }
}
