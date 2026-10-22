package com.deepdimen.matrix.zenflow.admin;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

class ZenflowModelSecurityTest {
    @Test void listIsTenantScopedAndNeverReturnsApiKeyOrRawMetadata() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE ai_model(id BIGINT,tenant_id BIGINT,name VARCHAR(64),model_name VARCHAR(100),provider VARCHAR(100),base_url VARCHAR(100),temperature DOUBLE,response_limit INT,assistant_model CHAR(1),ext_data VARCHAR(4096),api_key VARCHAR(255),del_flag CHAR(1),model_type VARCHAR(32),update_time TIMESTAMP)");
        jdbc.update("INSERT INTO ai_model VALUES(42,1,'My model','m','OpenAI compatible','https://example.invalid/v1',0.6,1024,'1',?,?,'0','Chat',CURRENT_TIMESTAMP)","{\"zenflowVersion\":4,\"zenflowTested\":true,\"zenflowAllowPrivateContext\":false}","credential-sentinel-secret");
        jdbc.update("INSERT INTO ai_model VALUES(43,2,'Foreign','m','p','https://foreign.invalid',0.6,1024,'0','{}','foreign-secret','0','Chat',CURRENT_TIMESTAMP)");
        var p=new ZenflowProperties();p.setTenantId(1);var json=new ObjectMapper();
        var service=new ZenflowModelService(jdbc,p,new DataSourceTransactionManager(source),json);
        var result=service.list();assertEquals(1,result.size());
        assertEquals("42",result.get(0).get("id"));assertEquals(true,result.get(0).get("active"));
        assertEquals(4,result.get(0).get("version"));assertEquals(true,result.get(0).get("hasApiKey"));
        String response=json.writeValueAsString(result);
        assertFalse(response.contains("credential-sentinel"));assertFalse(response.contains("ext_data"));assertFalse(response.contains("api_key"));
        assertThrows(ResponseStatusException.class,()->service.require("43"));
        jdbc.update("UPDATE ai_model SET ext_data='{}' WHERE id=42");
        assertFalse(service.available());
    }
    @Test void endpointRejectsLocalNetworksCredentialsAndInsecureUrls() {
        for(String uri:new String[]{"http://example.com/v1","https://127.0.0.1/v1","https://10.0.0.1/v1","https://169.254.169.254/latest","https://[::1]/v1","https://[fc00::1]/v1","https://user:pass@example.com/v1","https://example.com:8443/v1","https://example.com/v1?key=secret","https://example.com/v1#key"})
            assertThrows(ResponseStatusException.class,()->ZenflowModelService.validateEndpoint(uri),uri);
    }
}
