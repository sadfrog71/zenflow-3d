package com.deepdimen.matrix.zenflow.admin;

import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;

/** Aggregate metadata only; no account identifiers, message bodies, records or keys. */
@Service
public class ZenflowOperationsService {
    private final JdbcTemplate jdbc;
    private final ZenflowProperties product;
    private final ZenflowModelService models;
    public ZenflowOperationsService(JdbcTemplate jdbc,ZenflowProperties product,ZenflowModelService models){this.jdbc=jdbc;this.product=product;this.models=models;}
    public Map<String,Object> overview() {
        long tenant=product.getTenantId();
        return Map.of("checkedAt",java.time.Instant.now().toString(),"modelConfigured",models.available(),
                "userCount",count("SELECT COUNT(*) FROM app_user WHERE tenant_id=? AND del_flag='0'",tenant),
                "sessionCount",count("SELECT COUNT(*) FROM ai_assistant_session WHERE tenant_id=? AND owner_subject_type='APP_USER'",tenant),
                "noteCount",count("SELECT COUNT(*) FROM zf_capsule_ext WHERE tenant_id=? AND delete_state='ACTIVE'",tenant),
                "companion",jdbc.queryForList("SELECT config_key,status,version,updated_at FROM zf_companion_config WHERE tenant_id=? AND config_key='default'",tenant),
                "cleanup",jdbc.queryForList("SELECT state AS status,COUNT(*) AS total FROM zf_outbox WHERE tenant_id=? AND event_type='PRIVATE_NOTE_DELETE' GROUP BY state",tenant));
    }
    private long count(String sql,long tenant){return jdbc.queryForObject(sql,Long.class,tenant);}
}
