package com.deepdimen.matrix.zenflow.identity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.util.UUID;

class ExternalAccountIntegrationTest {
    @Test void realMysqlIdentityAndBindingIsolation() {
        String url=System.getenv("ZF_TEST_DB_URL");
        Assumptions.assumeTrue(url!=null && url.contains("zenflow_local_20261006"),"requires dedicated disposable local database");
        var source=new DriverManagerDataSource(url,System.getenv("ZF_TEST_DB_USER"),System.getenv("ZF_TEST_DB_PASSWORD"));
        var jdbc=new JdbcTemplate(source);
        var props=new ZenflowProperties();props.setTenantId(1);props.setAuthHmacKey("local-test-key-01234567890123456789");
        var service=new ExternalAccountService(jdbc,props,new DataSourceTransactionManager(source));
        String suffix=UUID.randomUUID().toString();
        var google=new ExternalIdentityVerifier.Identity("google","https://accounts.google.com","google-test",suffix,"same@example.invalid",true);
        var apple=new ExternalIdentityVerifier.Identity("apple","https://appleid.apple.com","apple-test",suffix,"same@example.invalid",true);
        long first=service.login(google,"en-US"),second=service.login(apple,"en-US");
        try {
            assertNotEquals(first,second,"matching provider emails must not merge users");
            assertEquals(first,service.login(google,"en-US"));
            assertNull(jdbc.queryForObject("SELECT email_normalized FROM app_user WHERE user_id=?",String.class,first));
            assertThrows(IllegalStateException.class,()->service.bind(first,apple,1));
            assertThrows(IllegalStateException.class,()->service.unbind(first,"google",1));
            var facebook=new ExternalIdentityVerifier.Identity("facebook","https://www.facebook.com","fb-test",suffix,null,false);
            service.bind(first,facebook,1);
            assertEquals(2,service.methods(first).size());
            service.unbind(first,"facebook",1);
            assertEquals(1,service.methods(first).size());
            assertEquals(2,jdbc.queryForObject("SELECT auth_version FROM app_user WHERE user_id=?",Integer.class,first));
            assertThrows(IllegalStateException.class,()->service.bind(first,facebook,1));
        } finally {
            for(long id:new long[]{first,second}) {
                jdbc.update("DELETE FROM zf_external_identity WHERE user_id=?",id);
                jdbc.update("DELETE FROM zf_profile WHERE owner_user_id=?",id);
                jdbc.update("DELETE FROM zf_outbox WHERE aggregate_type='app_user' AND aggregate_id=?",Long.toString(id));
                jdbc.update("DELETE FROM app_user_role WHERE user_id=?",id);
                jdbc.update("DELETE FROM app_user WHERE user_id=?",id);
            }
        }
    }
}
