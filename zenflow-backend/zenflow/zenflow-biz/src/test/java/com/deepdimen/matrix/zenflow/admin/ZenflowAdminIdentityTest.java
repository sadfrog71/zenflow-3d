package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.server.ResponseStatusException;

class ZenflowAdminIdentityTest {
    private record Fixture(JdbcTemplate jdbc,ZenflowAdminUserDetailsService identities) {}
    private Fixture fixture() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE sys_user(user_id BIGINT,tenant_id BIGINT,username VARCHAR(64),password VARCHAR(128),email VARCHAR(254),lock_flag CHAR(1),del_flag CHAR(1))");
        jdbc.execute("CREATE TABLE sys_role(role_id BIGINT,tenant_id BIGINT,role_code VARCHAR(64),del_flag CHAR(1))");
        jdbc.execute("CREATE TABLE sys_user_role(user_id BIGINT,role_id BIGINT)");
        jdbc.update("INSERT INTO sys_user VALUES(42,1,'operator','hash','operator@example.invalid','0','0'),(43,2,'foreign','hash',NULL,'0','0'),(44,1,'ordinary','hash',NULL,'0','0')");
        jdbc.update("INSERT INTO sys_role VALUES(7,1,'ZF_ADMIN','0'),(8,2,'ZF_ADMIN','0'),(9,1,'OTHER','0')");
        jdbc.update("INSERT INTO sys_user_role VALUES(42,7),(43,8),(44,9)");
        var product=new ZenflowProperties();product.setTenantId(1);
        return new Fixture(jdbc,new ZenflowAdminUserDetailsService(jdbc,product));
    }
    @Test void requiresProductTenantAndDedicatedRoleAndRejectsRevokedRole() {
        var f=fixture();
        assertTrue(f.identities.support("zenflow-admin","authorization_code"));
        assertFalse(f.identities.support("zenflow-ios","authorization_code"));
        assertThrows(UsernameNotFoundException.class,()->f.identities.loadUserByUsername("foreign"));
        assertThrows(UsernameNotFoundException.class,()->f.identities.loadUserByUsername("ordinary"));
        var prior=(MatrixUser)f.identities.loadUserByUsername("operator");
        f.jdbc.update("DELETE FROM sys_user_role WHERE user_id=42");
        assertThrows(UsernameNotFoundException.class,()->f.identities.loadUserByUser(prior));
    }
    @Test void sameNumericIdDoesNotAllowConsumerSubjectIntoAdmin() {
        var f=fixture();
        var consumer=new MatrixUser(42L,"operator",java.util.List.of(7L),null,null,null,null,null,null,null,
            null,1L,"hash",true,true,"1",true,null,true,java.util.List.of());
        assertThrows(UsernameNotFoundException.class,()->f.identities.loadUserByUser(consumer));
    }
    @Test void accessRejectsSessionWrongClientAndLockedIdentity() {
        var f=fixture();var user=(MatrixUser)f.identities.loadUserByUsername("operator");
        var access=new ZenflowAdminAccess(f.identities);
        try {
            assertThrows(ResponseStatusException.class,access::require);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));
            assertThrows(ResponseStatusException.class,access::require);
            user.getAttributes().put(SecurityConstants.CLIENT_ID,"zenflow-ios");
            assertThrows(ResponseStatusException.class,access::require);
            user.getAttributes().put(SecurityConstants.CLIENT_ID,"zenflow-admin");
            assertEquals(42L,access.require().getId());
            f.jdbc.update("UPDATE sys_user SET lock_flag='1' WHERE user_id=42");
            assertThrows(ResponseStatusException.class,access::require);
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void delegatedTaskRechecksRoleAndRestoresTenantSecurityEvenOnFailure() {
        var f=fixture();var access=new ZenflowAdminAccess(f.identities);var prior=SecurityContextHolder.getContext();
        try {
            TenantContextHolder.setTenantId(99L);TenantContextHolder.setTenantSkip();
            assertThrows(IllegalStateException.class,()->access.runDelegated(42L,()->{
                assertEquals(1L,TenantContextHolder.getTenantId());assertFalse(TenantContextHolder.getTenantSkip());assertEquals(42L,access.require().getId());
                f.jdbc.update("DELETE FROM sys_user_role WHERE user_id=42");
                assertThrows(ResponseStatusException.class,access::require);throw new IllegalStateException("controlled");
            }));
            assertSame(prior,SecurityContextHolder.getContext());assertEquals(99L,TenantContextHolder.getTenantId());assertTrue(TenantContextHolder.getTenantSkip());
            assertThrows(UsernameNotFoundException.class,()->access.runDelegated(42L,()->fail("revoked administrator executed")));
            assertSame(prior,SecurityContextHolder.getContext());assertEquals(99L,TenantContextHolder.getTenantId());
        } finally {SecurityContextHolder.clearContext();TenantContextHolder.clear();}
    }
    @Test void taskDelegationRejectsForeignOrdinaryAndLockedAdministrators() {
        var f=fixture();var access=new ZenflowAdminAccess(f.identities);
        for(long id:java.util.List.of(43L,44L,999L))assertThrows(UsernameNotFoundException.class,()->access.runDelegated(id,()->fail("unauthorized execution")));
        f.jdbc.update("UPDATE sys_user SET lock_flag='1' WHERE user_id=42");
        assertThrows(UsernameNotFoundException.class,()->access.runDelegated(42L,()->fail("locked execution")));
    }

}
