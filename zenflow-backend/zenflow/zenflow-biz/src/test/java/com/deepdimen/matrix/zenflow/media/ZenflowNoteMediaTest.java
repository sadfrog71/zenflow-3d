package com.deepdimen.matrix.zenflow.media;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class ZenflowNoteMediaTest {
    private JdbcTemplate jdbc;private TransactionTemplate tx;private ZenflowNoteMedia media;
    private final CapsuleIdentity owner=new CapsuleIdentity(1L,42L,"APP_USER");
    private final ZenflowConsumerAccess access=mock(ZenflowConsumerAccess.class);
    private final String id=UUID.randomUUID().toString(),other=UUID.randomUUID().toString();
    @BeforeEach void setup(){
        var data=new JdbcDataSource();data.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(data);tx=new TransactionTemplate(new DataSourceTransactionManager(data));
        jdbc.execute("CREATE TABLE zf_profile(tenant_id BIGINT,owner_user_id BIGINT)");jdbc.update("INSERT INTO zf_profile VALUES(1,42),(1,43)");
        jdbc.execute("CREATE TABLE ai_capsule(id BIGINT PRIMARY KEY,tenant_id BIGINT,owner_user_id BIGINT,owner_subject_type VARCHAR(16),current_revision_id BIGINT,status VARCHAR(16),del_flag VARCHAR(1))");
        jdbc.execute("CREATE TABLE ai_capsule_revision(id BIGINT PRIMARY KEY,capsule_id BIGINT,tenant_id BIGINT,owner_user_id BIGINT,owner_subject_type VARCHAR(16),revision_no INT)");
        jdbc.execute("CREATE TABLE zf_capsule_ext(capsule_id BIGINT,tenant_id BIGINT,owner_user_id BIGINT,delete_state VARCHAR(24))");
        jdbc.execute("CREATE TABLE zf_media_asset(id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT,subject_type VARCHAR(16),owner_user_id BIGINT,purpose VARCHAR(24),processing_state VARCHAR(24),expires_at TIMESTAMP,sha256 VARCHAR(64),actual_mime VARCHAR(96),actual_bytes BIGINT,actual_duration_seconds DOUBLE,object_key VARCHAR(36))");
        jdbc.execute("CREATE TABLE zf_media_binding(id BIGINT PRIMARY KEY,tenant_id BIGINT,subject_type VARCHAR(16),owner_user_id BIGINT,media_id VARCHAR(36),target_type VARCHAR(32),target_id VARCHAR(64),target_version INT,role VARCHAR(32),UNIQUE(media_id,target_type,target_id,target_version,role))");
        for(long note:new long[]{99,199}){jdbc.update("INSERT INTO ai_capsule VALUES(?,1,42,'APP_USER',?,'ACTIVE','0')",note,note+1);jdbc.update("INSERT INTO ai_capsule_revision VALUES(?,?,1,42,'APP_USER',1)",note+1,note);jdbc.update("INSERT INTO zf_capsule_ext VALUES(?,1,42,'ACTIVE')",note);}
        for(String asset:List.of(id,other))jdbc.update("INSERT INTO zf_media_asset VALUES(?,1,'APP_USER',42,'PRIVATE_NOTE','READY',?,?,'audio/wav',100,1,?)",asset,Timestamp.from(Instant.parse("2026-10-22T11:00:00Z")),"a".repeat(64),UUID.randomUUID().toString());
        when(access.capsuleIdentity()).thenReturn(owner);media=new ZenflowNoteMedia(jdbc,access,Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"),ZoneOffset.UTC),4);
    }
    @Test void onlyBoundLiveParentCanAuthorizeEveryPlaybackRequest(){
        assertThrows(ResponseStatusException.class,()->media.pointer(id));
        tx.executeWithoutResult(status->media.bind(owner,"99",1,List.of(id)));
        assertEquals(id,media.pointer(id).mediaId().toString());assertEquals(List.of(id),media.ids("99",1));
        jdbc.update("UPDATE zf_capsule_ext SET delete_state='DELETE_REQUESTED' WHERE capsule_id=99");
        assertThrows(ResponseStatusException.class,()->media.pointer(id));assertThrows(ResponseStatusException.class,()->media.list("99",1));
    }
    @Test void wrongPurposeForeignOwnerOrUnreadyMediaCannotBind(){
        for(String change:List.of("purpose='PUBLIC_SUBMISSION'","owner_user_id=43","processing_state='PROCESSING'")){
            jdbc.update("UPDATE zf_media_asset SET "+change+" WHERE id=?",id);
            assertThrows(ResponseStatusException.class,()->tx.executeWithoutResult(status->media.bind(owner,"99",1,List.of(id))));
            jdbc.update("UPDATE zf_media_asset SET purpose='PRIVATE_NOTE',owner_user_id=42,processing_state='READY' WHERE id=?",id);
        }
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zf_media_binding",Integer.class));
    }
    @Test void originalMediaMayFollowItsOwnImmutableRevisionsButNotAnotherNote(){
        tx.executeWithoutResult(status->media.bind(owner,"99",1,List.of(id)));
        tx.executeWithoutResult(status->media.bind(owner,"99",1,List.of(id)));
        assertThrows(ResponseStatusException.class,()->tx.executeWithoutResult(status->media.bind(owner,"199",1,List.of(id))));
        jdbc.update("INSERT INTO ai_capsule_revision VALUES(101,99,1,42,'APP_USER',2)");jdbc.update("UPDATE ai_capsule SET current_revision_id=101 WHERE id=99");
        tx.executeWithoutResult(status->media.bind(owner,"99",2,List.of(id)));
        assertEquals(List.of(id),media.ids("99",1));assertEquals(List.of(id),media.ids("99",2));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM zf_media_binding",Integer.class));
        assertThrows(ResponseStatusException.class,()->tx.executeWithoutResult(status->media.bind(owner,"99",1,List.of(other))));
    }
    @Test void bindingBatchRollsBackAndExpiredUnboundMediaCannotBeAttached(){
        jdbc.update("UPDATE zf_media_asset SET processing_state='FAILED' WHERE id=?",other);
        assertThrows(ResponseStatusException.class,()->tx.executeWithoutResult(status->media.bind(owner,"99",1,List.of(id,other))));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zf_media_binding",Integer.class));
        jdbc.update("UPDATE zf_media_asset SET expires_at=? WHERE id=?",Timestamp.from(Instant.parse("2026-10-22T09:00:00Z")),id);
        assertThrows(ResponseStatusException.class,()->tx.executeWithoutResult(status->media.bind(owner,"99",1,List.of(id))));
    }
    @Test void foreignTenantAccountAndAdminCannotReadEvenWithKnownMediaId(){
        tx.executeWithoutResult(status->media.bind(owner,"99",1,List.of(id)));
        for(var identity:List.of(new CapsuleIdentity(1L,43L,"APP_USER"),new CapsuleIdentity(2L,42L,"APP_USER"),new CapsuleIdentity(1L,42L,"ADMIN_USER"))){when(access.capsuleIdentity()).thenReturn(identity);assertThrows(ResponseStatusException.class,()->media.pointer(id));}
        assertThrows(IllegalStateException.class,()->media.bind(owner,"99",1,List.of(id)));
    }
}
