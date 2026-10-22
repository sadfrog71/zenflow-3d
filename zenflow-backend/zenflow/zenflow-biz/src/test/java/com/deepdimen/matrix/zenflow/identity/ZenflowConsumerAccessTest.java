package com.deepdimen.matrix.zenflow.identity;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

class ZenflowConsumerAccessTest {
    @Test void frozenStreamIdentityCannotContinueAfterPasswordResetAndSubjectComesFromToken() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE app_user(user_id BIGINT,tenant_id BIGINT,username VARCHAR(64),password VARCHAR(128),email VARCHAR(254),auth_version BIGINT,lock_flag CHAR(1),del_flag CHAR(1))");
        jdbc.execute("CREATE TABLE app_user_role(user_id BIGINT,role_id BIGINT)");
        jdbc.update("INSERT INTO app_user VALUES(42,1,'zf42','hash',NULL,1,'0','0')");
        var product=new ZenflowProperties();product.setTenantId(1);product.setOauthClientId("zenflow-ios");
        var users=new ZenflowUserDetailsService(jdbc,product);MatrixUser prior=users.loadExternal(42);
        var access=new ZenflowConsumerAccess(users,product);
        assertThrows(ResponseStatusException.class,()->access.verify(prior));
        prior.getAttributes().put(SecurityConstants.CLIENT_ID,"zenflow-admin");
        assertThrows(ResponseStatusException.class,()->access.verify(prior));
        prior.getAttributes().put(SecurityConstants.CLIENT_ID,"zenflow-ios");
        assertEquals("APP_USER",access.identity(prior,"test","TEXT_CONVERSATION").subjectType());
        jdbc.update("UPDATE app_user SET auth_version=2 WHERE user_id=42");
        assertThrows(ResponseStatusException.class,()->access.verify(prior));
        var current=users.loadExternal(42);current.getAttributes().put(SecurityConstants.CLIENT_ID,"zenflow-ios");
        assertNotNull(access.verify(current));
        jdbc.update("UPDATE app_user SET lock_flag='1' WHERE user_id=42");
        assertThrows(ResponseStatusException.class,()->access.verify(current));
    }
}
