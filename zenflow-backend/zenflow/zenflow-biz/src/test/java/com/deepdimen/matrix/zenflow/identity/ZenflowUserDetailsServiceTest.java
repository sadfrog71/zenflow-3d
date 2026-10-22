package com.deepdimen.matrix.zenflow.identity;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class ZenflowUserDetailsServiceTest {
    @Test void isolatesProductTenantAndRejectsPriorAuthVersion() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:zf_identity;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE app_user(user_id BIGINT PRIMARY KEY, tenant_id BIGINT, username VARCHAR(64), password VARCHAR(128), email VARCHAR(254), email_normalized VARCHAR(254), email_verified_at TIMESTAMP, auth_version BIGINT, lock_flag CHAR(1), del_flag CHAR(1))");
        jdbc.execute("CREATE TABLE app_user_role(user_id BIGINT, role_id BIGINT)");
        jdbc.update("INSERT INTO app_user VALUES (42,1,'zf_42','hash','A@Example.com','a@example.com',CURRENT_TIMESTAMP,1,'0','0')");
        jdbc.update("INSERT INTO app_user VALUES (43,2,'zf_43','hash','a@example.com','a@example.com',CURRENT_TIMESTAMP,1,'0','0')");
        jdbc.update("INSERT INTO app_user_role VALUES (42,7)");
        var properties = new ZenflowProperties(); properties.setTenantId(1); properties.setOauthClientId("zenflow-ios");
        var service = new ZenflowUserDetailsService(jdbc, properties);
        MatrixUser prior = (MatrixUser) service.loadUserByUsername(" A@EXAMPLE.COM ");
        assertEquals(42L, prior.getId());
        assertEquals(1L, prior.getTenantId());
        assertTrue(service.support("zenflow-ios", "authorization_code"));
        assertFalse(service.support("matrix-mobile", "authorization_code"));
        jdbc.update("UPDATE app_user SET auth_version=2 WHERE user_id=42");
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUser(prior));
        MatrixUser current = (MatrixUser) service.loadUserByUsername("a@example.com");
        jdbc.update("UPDATE app_user SET lock_flag='1' WHERE user_id=42");
        assertFalse(service.loadUserByUser(current).isAccountNonLocked());
    }
}
