package com.deepdimen.matrix.zenflow.identity;

import com.deepdimen.matrix.common.data.tenant.ProductTenantLookup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ZenflowTenantLookup implements ProductTenantLookup {
    private final JdbcTemplate jdbc;
    private final ZenflowProperties properties;
    public ZenflowTenantLookup(JdbcTemplate jdbc, ZenflowProperties properties) {
        this.jdbc = jdbc; this.properties = properties;
    }
    public long defaultTenantId() { return properties.getTenantId(); }
    public boolean active(long id) {
        if (id != properties.getTenantId()) return false;
        return Integer.valueOf(1).equals(jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_tenant WHERE id=? AND status='0' AND del_flag='0' AND start_time<=UTC_TIMESTAMP() AND end_time>UTC_TIMESTAMP()", Integer.class, id));
    }
}
