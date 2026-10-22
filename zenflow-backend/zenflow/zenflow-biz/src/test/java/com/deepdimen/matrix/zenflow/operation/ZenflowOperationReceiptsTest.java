package com.deepdimen.matrix.zenflow.operation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class ZenflowOperationReceiptsTest {
    private final CapsuleIdentity owner=new CapsuleIdentity(1L,42L,"APP_USER");
    private final ZenflowConsumerAccess access=mock(ZenflowConsumerAccess.class);
    private final ObjectMapper json=new ObjectMapper();
    private ZenflowOperationReceipts receipts;private TransactionTemplate tx;private JdbcTemplate jdbc;
    @BeforeEach void setup(){
        var data=new JdbcDataSource();data.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(data);tx=new TransactionTemplate(new DataSourceTransactionManager(data));
        jdbc.execute("CREATE TABLE zf_client_operation(id BIGINT PRIMARY KEY,tenant_id BIGINT,subject_type VARCHAR(16),owner_user_id BIGINT,operation_type VARCHAR(48),client_request_id VARCHAR(128),payload_hash VARCHAR(64),state VARCHAR(16),result_type VARCHAR(48),result_id VARCHAR(64),result_version INT,safe_error_code VARCHAR(64),UNIQUE(tenant_id,subject_type,owner_user_id,operation_type,client_request_id))");
        when(access.capsuleIdentity()).thenReturn(owner);receipts=new ZenflowOperationReceipts(jdbc,access,json);
    }
    @Test void canonicalHashIgnoresObjectKeyOrderButKeepsOperationOrder()throws Exception{
        assertEquals(receipts.hash(json.readTree("{\"b\":{\"x\":2,\"y\":3},\"a\":1}")),receipts.hash(json.readTree("{\"a\":1,\"b\":{\"y\":3,\"x\":2}}")));
        assertNotEquals(receipts.hash(json.readTree("[1,2]")),receipts.hash(json.readTree("[2,1]")));
    }
    @Test void mediaAndGardenShareOneReceiptTableWithoutSharingKeysOrOwners(){
        tx.execute(status->{
            for(String type:new String[]{"GARDEN_PATCH","MEDIA_CREATE_UPLOAD"})receipts.record(owner,type,"same-request", "a".repeat(64),"SUCCEEDED",type.equals("GARDEN_PATCH")?"GARDEN":"MEDIA_UPLOAD","123",1,null);
            return null;
        });
        assertEquals("GARDEN",receipts.get("GARDEN_PATCH","same-request").resultType());
        assertEquals("MEDIA_UPLOAD",receipts.get("MEDIA_CREATE_UPLOAD","same-request").resultType());
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,43L,"APP_USER"));
        assertThrows(ResponseStatusException.class,()->receipts.get("MEDIA_CREATE_UPLOAD","same-request"));
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(2L,42L,"APP_USER"));
        assertThrows(ResponseStatusException.class,()->receipts.get("MEDIA_CREATE_UPLOAD","same-request"));
    }
    @Test void noReceiptCanBeCommittedOutsideBusinessTransactionOrSurviveRollback(){
        assertThrows(IllegalStateException.class,()->receipts.record(owner,"MEDIA_CREATE_UPLOAD","request-123","a".repeat(64),"SUCCEEDED","MEDIA_UPLOAD","123",1,null));
        assertThrows(IllegalStateException.class,()->tx.execute(status->{receipts.record(owner,"MEDIA_CREATE_UPLOAD","request-123","a".repeat(64),"SUCCEEDED","MEDIA_UPLOAD","123",1,null);throw new IllegalStateException("business rollback");}));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zf_client_operation",Integer.class));
    }
    @Test void mismatchedPayloadAndAdministratorCannotReuseConsumerReceipt(){
        tx.execute(status->{receipts.record(owner,"MEDIA_DELETE","request-123","a".repeat(64),"SUCCEEDED","MEDIA","123",2,null);return null;});
        assertThrows(ResponseStatusException.class,()->tx.execute(status->receipts.find(owner,"MEDIA_DELETE","request-123","b".repeat(64))));
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,42L,"ADMIN_USER"));
        assertThrows(ResponseStatusException.class,()->receipts.get("MEDIA_DELETE","request-123"));
        assertThrows(ResponseStatusException.class,()->receipts.get("ARBITRARY_TYPE","request-123"));
    }
    @Test void commonHttpRouteReturnsPrivateReceiptAndRejectsUnknownOperationTypes()throws Exception{
        tx.execute(status->{receipts.record(owner,"MEDIA_COMPLETE","request-123","a".repeat(64),"SUCCEEDED","MEDIA","123",2,null);return null;});
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new ZenflowOperationController(receipts)).build();
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/zenflow/v1/operations/request-123").param("operationType","MEDIA_COMPLETE")).andReturn().getResponse();
        assertEquals(200,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));assertEquals("MEDIA",json.readTree(response.getContentAsString()).at("/data/resultType").asText());
        assertEquals(400,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/zenflow/v1/operations/request-123").param("operationType","UNSUPPORTED")).andReturn().getResponse().getStatus());
    }
}
