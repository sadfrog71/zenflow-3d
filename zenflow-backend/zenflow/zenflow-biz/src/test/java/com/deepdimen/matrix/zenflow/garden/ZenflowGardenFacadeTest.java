package com.deepdimen.matrix.zenflow.garden;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.server.ResponseStatusException;

class ZenflowGardenFacadeTest {
    private final ObjectMapper json=new ObjectMapper();
    private final ZenflowConsumerAccess access=mock(ZenflowConsumerAccess.class);
    private JdbcTemplate jdbc;
    private ZenflowGardenFacade garden;
    @BeforeEach void setup(){
        var data=new JdbcDataSource();data.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE zf_profile(tenant_id BIGINT,owner_user_id BIGINT,PRIMARY KEY(tenant_id,owner_user_id))");
        jdbc.update("INSERT INTO zf_profile VALUES(1,42),(1,43),(2,42)");
        jdbc.execute("CREATE TABLE zf_garden_state(id BIGINT PRIMARY KEY,tenant_id BIGINT,subject_type VARCHAR(16),owner_user_id BIGINT,scene_version VARCHAR(64),state_version INT,fish_json VARCHAR(100000),placements_json VARCHAR(100000),environment_json VARCHAR(100000),updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(tenant_id,subject_type,owner_user_id))");
        jdbc.execute("CREATE TABLE zf_client_operation(id BIGINT PRIMARY KEY,tenant_id BIGINT,subject_type VARCHAR(16),owner_user_id BIGINT,operation_type VARCHAR(48),client_request_id VARCHAR(128),payload_hash VARCHAR(64),state VARCHAR(16),result_type VARCHAR(48),result_id VARCHAR(64),result_version INT,safe_error_code VARCHAR(64),UNIQUE(tenant_id,subject_type,owner_user_id,operation_type,client_request_id))");
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,42L,"APP_USER"));
        garden=new ZenflowGardenFacade(jdbc,access,new DataSourceTransactionManager(data),json,
            new ZenflowGardenOperations.Catalogue(Set.of("test-koi"),Map.of(),Set.of("day","rain"),0),"test-scene-v1","test-koi");
    }
    private com.deepdimen.matrix.zenflow.operation.ZenflowOperationReceipts.Receipt patch(String key,int version,String ops)throws Exception{
        return garden.patch(json.readTree("{\"clientRequestId\":\""+key+"\",\"expectedVersion\":"+version+",\"operations\":"+ops+"}"));
    }
    private void init()throws Exception{assertEquals("SUCCEEDED",patch("init-request",0,"[{\"type\":\"INIT\"}]").state());}
    @Test void getNeverCreatesAndInitializationGeneratesOnlyServerFish()throws Exception{
        assertEquals(404,assertThrows(ResponseStatusException.class,()->garden.get()).getStatusCode().value());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zf_garden_state",Integer.class));
        init();var state=garden.get();assertEquals(3,state.get("fish").size());assertEquals(1,state.path("stateVersion").intValue());
        assertNotEquals(state.at("/fish/0/instanceId"),state.at("/fish/1/instanceId"));
        assertEquals("REJECTED",patch("second-init",0,"[{\"type\":\"INIT\"}]").state());
        assertEquals(1,garden.get().path("stateVersion").intValue());
    }
    @Test void ownerAndTenantScopeBothStateAndReceipts()throws Exception{
        init();String first=garden.get().path("gardenId").asText();
        for(var owner:new CapsuleIdentity[]{new CapsuleIdentity(1L,43L,"APP_USER"),new CapsuleIdentity(2L,42L,"APP_USER")}){
            when(access.capsuleIdentity()).thenReturn(owner);
            assertThrows(ResponseStatusException.class,()->garden.get());
            assertThrows(ResponseStatusException.class,()->garden.receipt("init-request"));
            init();assertNotEquals(first,garden.get().path("gardenId").asText());
        }
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM zf_garden_state",Integer.class));
    }
    @Test void replayReturnsOriginalVersionWithoutReapplyingAndChangedPayloadConflicts()throws Exception{
        init();var original=garden.receipt("init-request");
        assertEquals("SUCCEEDED",patch("weather-request",1,"[{\"type\":\"SET_ENVIRONMENT\",\"presetId\":\"rain\"}]").state());
        assertEquals(original,patch("init-request",0,"[{\"type\":\"INIT\"}]"));
        assertEquals(2,garden.get().path("stateVersion").intValue());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->patch("weather-request",2,"[{\"type\":\"SET_ENVIRONMENT\",\"presetId\":\"day\"}]")).getStatusCode().value());
    }
    @Test void failedBatchAndStaleVersionHaveStableRejectedReceipts()throws Exception{
        init();var before=garden.get();
        String ops="[{\"type\":\"SET_ENVIRONMENT\",\"presetId\":\"rain\"},{\"type\":\"SET_FISH_NAME\",\"instanceId\":\"another-fish\",\"name\":\"x\"}]";
        var rejected=patch("invalid-batch",1,ops);assertEquals("REJECTED",rejected.state());assertEquals(before,garden.get());
        assertEquals(rejected,patch("invalid-batch",1,ops));
        assertEquals("GARDEN_VERSION_CONFLICT",patch("stale-version",0,"[{\"type\":\"INIT\"}]").safeErrorCode());
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM zf_client_operation",Integer.class));
    }
    @Test void receiptInsertFailureRollsBackBusinessUpdate()throws Exception{
        init();var before=garden.get();
        jdbc.execute("ALTER TABLE zf_client_operation ADD CONSTRAINT reject_test CHECK(client_request_id<>'rollback-request')");
        assertThrows(org.springframework.dao.DataAccessException.class,()->patch("rollback-request",1,"[{\"type\":\"SET_ENVIRONMENT\",\"presetId\":\"rain\"}]"));
        assertEquals(before,garden.get());
        assertThrows(ResponseStatusException.class,()->garden.receipt("rollback-request"));
    }
    @Test void initImportsOnlyExplicitAllowedEnvironmentChoices()throws Exception{
        assertEquals("SUCCEEDED",patch("chosen-weather",0,"[{\"type\":\"INIT\",\"choices\":{\"environment\":{\"presetId\":\"rain\",\"lampEnabled\":false}}}]").state());
        assertEquals("rain",garden.get().at("/environment/presetId").asText());
        assertFalse(garden.get().at("/environment/lampEnabled").booleanValue());
    }
    @Test void adminAndIdentityChangeDuringTransactionCannotReadOrWriteConsumerState()throws Exception{
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,42L,"ADMIN_USER"));
        assertThrows(ResponseStatusException.class,()->garden.get());
        assertThrows(ResponseStatusException.class,()->patch("admin-request",0,"[{\"type\":\"INIT\"}]"));
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,42L,"APP_USER"),new CapsuleIdentity(1L,43L,"APP_USER"));
        assertThrows(ResponseStatusException.class,()->patch("changed-owner",0,"[{\"type\":\"INIT\"}]"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zf_garden_state",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zf_client_operation",Integer.class));
    }
    @Test void concurrentSameBaseEditsCannotBothSucceed()throws Exception{
        init();var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
            var futures=new java.util.ArrayList<java.util.concurrent.Future<com.deepdimen.matrix.zenflow.operation.ZenflowOperationReceipts.Receipt>>();
            for(int i=0;i<2;i++){
                String key="concurrent-"+i;
                futures.add(executor.submit(()->{ready.countDown();start.await();return patch(key,1,"[{\"type\":\"SET_ENVIRONMENT\",\"presetId\":\"rain\"}]");}));
            }
            assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));start.countDown();
            var states=new java.util.ArrayList<String>();
            for(var result:futures)states.add(result.get(10,java.util.concurrent.TimeUnit.SECONDS).state());
            assertEquals(1,states.stream().filter("SUCCEEDED"::equals).count());
            assertEquals(1,states.stream().filter("REJECTED"::equals).count());
            assertEquals(2,garden.get().path("stateVersion").intValue());
        }finally{executor.shutdownNow();}
    }
}
