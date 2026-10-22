package com.deepdimen.matrix.zenflow.media;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.server.ResponseStatusException;

class ZenflowMediaUploadsTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    private org.springframework.transaction.PlatformTransactionManager manager;
    private JdbcTemplate jdbc;private ZenflowMediaUploads uploads;
    private final ZenflowConsumerAccess access=mock(ZenflowConsumerAccess.class);private final ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup(){
        var data=new JdbcDataSource();data.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");jdbc=new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE zf_profile(tenant_id BIGINT,owner_user_id BIGINT,PRIMARY KEY(tenant_id,owner_user_id))");jdbc.update("INSERT INTO zf_profile VALUES(1,42),(1,43),(2,42)");
        jdbc.execute("CREATE TABLE zf_media_asset(id VARCHAR(36) PRIMARY KEY,tenant_id BIGINT,subject_type VARCHAR(16),owner_user_id BIGINT,purpose VARCHAR(24),processing_state VARCHAR(24),version INT,declared_mime VARCHAR(96),declared_bytes BIGINT,declared_duration_seconds DOUBLE,sha256 VARCHAR(64),max_bytes BIGINT,max_duration_seconds DOUBLE,expires_at TIMESTAMP,quarantine_key VARCHAR(256),object_key VARCHAR(256),storage_ref VARCHAR(64),processing_request_key VARCHAR(128),processing_base_version INT,actual_mime VARCHAR(96),actual_bytes BIGINT,actual_duration_seconds DOUBLE,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE zf_media_upload(id VARCHAR(36) PRIMARY KEY,media_id VARCHAR(36) UNIQUE,tenant_id BIGINT,subject_type VARCHAR(16),owner_user_id BIGINT,version INT,received_at TIMESTAMP,lease_token VARCHAR(36),lease_until TIMESTAMP)");
        jdbc.execute("CREATE TABLE zf_client_operation(id BIGINT PRIMARY KEY,tenant_id BIGINT,subject_type VARCHAR(16),owner_user_id BIGINT,operation_type VARCHAR(48),client_request_id VARCHAR(128),payload_hash VARCHAR(64),state VARCHAR(16),result_type VARCHAR(48),result_id VARCHAR(64),result_version INT,safe_error_code VARCHAR(64),updated_at TIMESTAMP,UNIQUE(tenant_id,subject_type,owner_user_id,operation_type,client_request_id))");
        jdbc.execute("CREATE TABLE zf_outbox(id BIGINT PRIMARY KEY,tenant_id BIGINT,event_type VARCHAR(64),aggregate_type VARCHAR(48),aggregate_id VARCHAR(64),dedupe_key VARCHAR(64),payload_json VARCHAR(4000),state VARCHAR(16) DEFAULT 'PENDING',attempts BIGINT DEFAULT 0,lease_epoch BIGINT DEFAULT 0,lease_owner VARCHAR(128),lease_until TIMESTAMP,next_attempt_at TIMESTAMP,error_code VARCHAR(64),version BIGINT DEFAULT 1,updated_at TIMESTAMP,UNIQUE(tenant_id,dedupe_key))");
        jdbc.execute("CREATE TABLE zf_media_binding(id BIGINT PRIMARY KEY,tenant_id BIGINT,subject_type VARCHAR(16),owner_user_id BIGINT,media_id VARCHAR(36),target_type VARCHAR(32),target_id VARCHAR(64),target_version INT DEFAULT 1,role VARCHAR(32) DEFAULT 'ORIGINAL_AUDIO')");
        jdbc.execute("CREATE TABLE ai_capsule(id BIGINT PRIMARY KEY,tenant_id BIGINT,owner_user_id BIGINT,owner_subject_type VARCHAR(16),status VARCHAR(16),del_flag VARCHAR(1),current_revision_id BIGINT)");
        jdbc.execute("CREATE TABLE ai_capsule_revision(id BIGINT PRIMARY KEY,capsule_id BIGINT,tenant_id BIGINT,owner_user_id BIGINT,owner_subject_type VARCHAR(16),revision_no INT)");jdbc.update("INSERT INTO ai_capsule_revision VALUES(100,99,1,42,'APP_USER',1)");
        jdbc.execute("CREATE TABLE zf_capsule_ext(capsule_id BIGINT,tenant_id BIGINT,owner_user_id BIGINT,delete_state VARCHAR(24))");
        jdbc.update("INSERT INTO ai_capsule VALUES(99,1,42,'APP_USER','ACTIVE','0',100)");jdbc.update("INSERT INTO zf_capsule_ext VALUES(99,1,42,'ACTIVE')");
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,42L,"APP_USER"));
        manager=new DataSourceTransactionManager(data);
        uploads=new ZenflowMediaUploads(jdbc,access,manager,json,Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"),ZoneOffset.UTC),new ZenflowMediaUploads.Limits(5242880,60,Duration.ofHours(1)));
    }
    private ObjectNode body(){return json.createObjectNode().put("clientRequestId","upload-request-1").put("purpose","PRIVATE_NOTE").put("mime","audio/mp4").put("bytes",1024).put("durationSeconds",2).put("sha256","a".repeat(64));}
    @Test void repeatedRegistrationReturnsOneUploadAndNeverClaimsAudioOrBusinessReady(){
        var first=uploads.create(body());assertEquals(first,uploads.create(body()));assertEquals("2026-10-22T11:00:00Z",first.expiresAt());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM zf_media_asset",Integer.class));
        assertEquals("RECEIVING",jdbc.queryForObject("SELECT processing_state FROM zf_media_asset",String.class));
        assertEquals("MEDIA_UPLOAD",jdbc.queryForObject("SELECT result_type FROM zf_client_operation",String.class));
        assertFalse(json.valueToTree(first).has("objectKey"));
    }
    @Test void sameKeyCannotChangePurposeAndAnotherOwnerOrTenantGetsIndependentRegistration(){
        var first=uploads.create(body());
        assertEquals(409,assertThrows(ResponseStatusException.class,()->uploads.create(body().put("purpose","PUBLIC_SUBMISSION"))).getStatusCode().value());
        for(var owner:new CapsuleIdentity[]{new CapsuleIdentity(1L,43L,"APP_USER"),new CapsuleIdentity(2L,42L,"APP_USER")}){
            when(access.capsuleIdentity()).thenReturn(owner);assertNotEquals(first.mediaId(),uploads.create(body()).mediaId());
        }
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM zf_media_asset",Integer.class));
    }
    @Test void receiptFailureRollsBackBothAssetAndUpload(){
        jdbc.execute("ALTER TABLE zf_client_operation ADD CONSTRAINT reject_upload CHECK(operation_type<>'MEDIA_CREATE_UPLOAD')");
        assertThrows(org.springframework.dao.DataAccessException.class,()->uploads.create(body()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zf_media_asset",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zf_media_upload",Integer.class));
    }
    @Test void rejectsInvalidDeclarationsAndAdministratorsBeforeCreatingAssets(){
        assertThrows(ResponseStatusException.class,()->uploads.create(body().put("ownerUserId",42)));
        assertThrows(ResponseStatusException.class,()->uploads.create(body().put("bytes",5242881)));
        assertThrows(ResponseStatusException.class,()->uploads.create(body().put("durationSeconds",61)));
        assertThrows(ResponseStatusException.class,()->uploads.create(body().put("purpose","CHAT_ATTACHMENT")));
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,42L,"ADMIN_USER"));assertThrows(ResponseStatusException.class,()->uploads.create(body()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zf_media_asset",Integer.class));
    }
    private ZenflowMediaObjects objects(){
        var properties=new com.deepdimen.matrix.common.file.core.FileProperties();var local=new com.deepdimen.matrix.common.file.local.LocalFileProperties();local.setBasePath(directory.resolve("objects").toString());properties.setLocal(local);
        var objects=new ZenflowMediaObjects(new com.deepdimen.matrix.common.file.local.LocalFileTemplate(properties),"invelle-private-test","voice");
        return objects;
    }
    private ZenflowMediaTransfers transfers(){
        return new ZenflowMediaTransfers(jdbc,access,manager,objects(),directory,Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"),ZoneOffset.UTC),Duration.ofMinutes(2));
    }
    private ZenflowMediaUploads.Upload registration(byte[] data)throws Exception{return uploads.create(body().put("bytes",data.length).put("sha256",ZenflowMediaInputTest.hash(data)));}
    @Test void receivedContentRemainsQuarantinedAndIdenticalRetryCreatesNoSecondObject()throws Exception{
        byte[] data="synthetic quarantine bytes".getBytes();var upload=registration(data);var transfers=transfers();
        var first=transfers.receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data));assertEquals(2,first.version());assertEquals("RECEIVING",first.state());
        assertEquals(first,transfers.receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data)));
        assertEquals(first,transfers.receive(upload.uploadId(),2,new java.io.ByteArrayInputStream(data)));
        assertNotNull(jdbc.queryForObject("SELECT quarantine_key FROM zf_media_asset",String.class));
        try(var paths=java.nio.file.Files.walk(directory.resolve("objects"))){assertEquals(1,paths.filter(java.nio.file.Files::isRegularFile).count());}
    }
    @Test void invalidBytesReleaseUnwrittenLeaseAndAllowCorrectRetry()throws Exception{
        byte[] data="correct content".getBytes();var upload=registration(data);var transfers=transfers();
        assertThrows(java.io.IOException.class,()->transfers.receive(upload.uploadId(),1,new java.io.ByteArrayInputStream("wrong".getBytes())));
        assertNull(jdbc.queryForObject("SELECT lease_token FROM zf_media_upload",String.class));
        assertEquals(2,transfers.receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data)).version());
        assertThrows(java.io.IOException.class,()->transfers.receive(upload.uploadId(),1,new java.io.ByteArrayInputStream("changed content".getBytes())));
    }
    @Test void expiredOrForeignUploadIsRejectedBeforeReadingAnyBytes()throws Exception{
        byte[] data="private bytes".getBytes();var upload=registration(data);var transfers=transfers();
        var unread=new java.io.InputStream(){public int read(){throw new AssertionError("Unauthorized request read bytes");}};
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,43L,"APP_USER"));
        assertThrows(ResponseStatusException.class,()->transfers.receive(upload.uploadId(),1,unread));
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,42L,"APP_USER"));
        jdbc.update("UPDATE zf_media_asset SET expires_at=?",java.sql.Timestamp.from(Instant.parse("2026-10-22T09:00:00Z")));
        assertEquals(410,assertThrows(ResponseStatusException.class,()->transfers.receive(upload.uploadId(),1,unread)).getStatusCode().value());
    }
    @Test void activeOtherLeasePreventsConcurrentContentConsumption()throws Exception{
        byte[] data="private bytes".getBytes();var upload=registration(data);var transfers=transfers();
        jdbc.update("UPDATE zf_media_upload SET lease_token=?,lease_until=?",UUID.randomUUID().toString(),java.sql.Timestamp.from(Instant.parse("2026-10-22T10:01:00Z")));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->transfers.receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data))).getStatusCode().value());
        assertNull(jdbc.queryForObject("SELECT quarantine_key FROM zf_media_asset",String.class));
    }

    @Test void identityChangeAfterStorageCannotFinalizeReceivedMetadata()throws Exception{
        byte[] data="private bytes".getBytes();var upload=registration(data);var transfers=transfers();
        var first=new CapsuleIdentity(1L,42L,"APP_USER");var other=new CapsuleIdentity(1L,43L,"APP_USER");
        when(access.capsuleIdentity()).thenReturn(first,first,first,other);
        assertThrows(ResponseStatusException.class,()->transfers.receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data)));
        assertNull(jdbc.queryForObject("SELECT received_at FROM zf_media_upload",java.sql.Timestamp.class));
        assertNull(jdbc.queryForObject("SELECT quarantine_key FROM zf_media_asset",String.class));
        assertNotNull(jdbc.queryForObject("SELECT lease_token FROM zf_media_upload",String.class));
    }
    @Test void metadataFailureKeepsPrivateStageAndRecoveryIntentWithoutClaimingReceived()throws Exception{
        byte[] data="private bytes".getBytes();var upload=registration(data);var transfers=transfers();
        jdbc.execute("ALTER TABLE zf_media_asset ADD CONSTRAINT reject_stage CHECK(quarantine_key IS NULL)");
        assertThrows(org.springframework.dao.DataAccessException.class,()->transfers.receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data)));
        assertNull(jdbc.queryForObject("SELECT received_at FROM zf_media_upload",java.sql.Timestamp.class));
        assertNotNull(jdbc.queryForObject("SELECT lease_token FROM zf_media_upload",String.class));
        try(var paths=java.nio.file.Files.walk(directory.resolve("objects"))){assertEquals(1,paths.filter(java.nio.file.Files::isRegularFile).count());}
    }

    private ZenflowStageCleanup cleanupAt(String time,ZenflowMediaObjects storage){return new ZenflowStageCleanup(jdbc,manager,storage,Clock.fixed(Instant.parse(time),ZoneOffset.UTC));}
    @Test void cleanupRemovesSupersededStageButProtectsAdoptedStage()throws Exception{
        byte[] data="private bytes".getBytes();var upload=registration(data);var transfers=transfers();
        jdbc.execute("ALTER TABLE zf_media_asset ADD CONSTRAINT reject_stage CHECK(quarantine_key IS NULL)");
        assertThrows(org.springframework.dao.DataAccessException.class,()->transfers.receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data)));
        jdbc.execute("ALTER TABLE zf_media_asset DROP CONSTRAINT reject_stage");
        jdbc.update("UPDATE zf_media_upload SET lease_until=?",java.sql.Timestamp.from(Instant.parse("2026-10-22T09:00:00Z")));
        transfers.receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM zf_outbox WHERE event_type='MEDIA_STAGE_DELETE'",Integer.class));
        var cleanup=cleanupAt("2026-10-22T10:03:00Z",objects());assertFalse(cleanup.pollOne(2));assertTrue(cleanup.pollOne(1));assertTrue(cleanup.pollOne(1));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM zf_outbox WHERE state='SUCCEEDED'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT attempts FROM zf_outbox WHERE state='PENDING'",Integer.class));
        try(var paths=java.nio.file.Files.walk(directory.resolve("objects"))){assertEquals(1,paths.filter(java.nio.file.Files::isRegularFile).count());}
    }
    @Test void cleanupNeverClaimsPrivateNoteEvents(){
        jdbc.update("INSERT INTO zf_outbox(id,tenant_id,event_type,aggregate_type,aggregate_id,dedupe_key,payload_json,next_attempt_at) VALUES(1,1,'PRIVATE_NOTE_DELETE','CAPSULE','5','note-event','{}',?)",java.sql.Timestamp.from(Instant.parse("2026-10-22T09:00:00Z")));
        assertFalse(cleanupAt("2026-10-22T10:03:00Z",objects()).pollOne(1));assertEquals("PENDING",jdbc.queryForObject("SELECT state FROM zf_outbox",String.class));assertEquals(0,jdbc.queryForObject("SELECT attempts FROM zf_outbox",Integer.class));
    }
    @Test void lateStageWriteReopensItsCompletedCleanupTombstone()throws Exception{
        byte[] data="private bytes".getBytes();var upload=registration(data);var transfers=transfers();
        assertThrows(java.io.IOException.class,()->transfers.receive(upload.uploadId(),1,new java.io.ByteArrayInputStream("wrong".getBytes())));
        var cleanup=cleanupAt("2026-10-22T10:03:00Z",objects());assertTrue(cleanup.pollOne(1));assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT state FROM zf_outbox",String.class));
        UUID token=UUID.fromString(jdbc.queryForObject("SELECT aggregate_id FROM zf_outbox",String.class));ZenflowMediaObjects.Stage stage;
        try(var input=ZenflowMediaInput.receive(new java.io.ByteArrayInputStream(data),directory,data.length,100,ZenflowMediaInputTest.hash(data))){stage=objects().stage(UUID.fromString(upload.mediaId()),token,input);}
        cleanup.requeue(new CapsuleIdentity(1L,42L,"APP_USER"),stage,Instant.parse("2026-10-22T10:03:00Z"));
        assertTrue(cleanup.pollOne(1));assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT state FROM zf_outbox",String.class));
        try(var paths=java.nio.file.Files.walk(directory.resolve("objects"))){assertEquals(0,paths.filter(java.nio.file.Files::isRegularFile).count());}
    }
    @Test void repeatedStorageCleanupFailuresBackOffThenBecomeVisibleDeadEvents()throws Exception{
        byte[] data="private bytes".getBytes();var upload=registration(data);
        assertThrows(java.io.IOException.class,()->transfers().receive(upload.uploadId(),1,new java.io.ByteArrayInputStream("wrong".getBytes())));
        var files=mock(com.deepdimen.matrix.common.file.core.FileTemplate.class);doThrow(new java.io.IOException("storage unavailable")).when(files).removeObject(anyString(),anyString());
        var cleanup=cleanupAt("2026-10-22T10:03:00Z",new ZenflowMediaObjects(files,"invelle-private-test","voice"));
        assertTrue(cleanup.pollOne(1));assertFalse(cleanup.pollOne(1));
        for(int i=1;i<5;i++){jdbc.update("UPDATE zf_outbox SET next_attempt_at=?",java.sql.Timestamp.from(Instant.parse("2026-10-22T10:02:00Z")));assertTrue(cleanup.pollOne(1));}
        assertEquals("DEAD",jdbc.queryForObject("SELECT state FROM zf_outbox",String.class));assertEquals(5,jdbc.queryForObject("SELECT attempts FROM zf_outbox",Integer.class));assertFalse(cleanup.pollOne(1));
    }

    @Test void completedTombstoneRechecksLateWriteEvenIfProducerCrashesBeforeRequeue()throws Exception{
        byte[] data="private bytes".getBytes();var upload=registration(data);
        assertThrows(java.io.IOException.class,()->transfers().receive(upload.uploadId(),1,new java.io.ByteArrayInputStream("wrong".getBytes())));
        assertTrue(cleanupAt("2026-10-22T10:03:00Z",objects()).pollOne(1));
        UUID token=UUID.fromString(jdbc.queryForObject("SELECT aggregate_id FROM zf_outbox",String.class));
        try(var input=ZenflowMediaInput.receive(new java.io.ByteArrayInputStream(data),directory,data.length,100,ZenflowMediaInputTest.hash(data))){objects().stage(UUID.fromString(upload.mediaId()),token,input);}
        // No producer requeue: this models the crash window after a slow object write.
        assertTrue(cleanupAt("2026-10-23T10:04:00Z",objects()).pollOne(1));
        assertEquals("SUCCEEDED",jdbc.queryForObject("SELECT state FROM zf_outbox",String.class));
        try(var paths=java.nio.file.Files.walk(directory.resolve("objects"))){assertEquals(0,paths.filter(java.nio.file.Files::isRegularFile).count());}
    }

    private ZenflowMediaCompletion completion(){
        String probe=System.getProperty("invelle.test.ffprobe"),decoder=System.getProperty("invelle.test.ffmpeg");
        org.junit.jupiter.api.Assumptions.assumeTrue(probe!=null&&decoder!=null,"Explicit decoder paths required");
        return new ZenflowMediaCompletion(jdbc,access,manager,json,objects(),new ZenflowAudioInspector(java.nio.file.Path.of(probe),java.nio.file.Path.of(decoder),10),directory,Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"),ZoneOffset.UTC),Duration.ofMinutes(2),"synthetic-private");
    }
    private ZenflowMediaUploads.Upload waveRegistration(byte[] data)throws Exception{return uploads.create(body().put("mime","audio/wav").put("bytes",data.length).put("sha256",ZenflowMediaInputTest.hash(data)));}
    @Test void actualDecodeMakesMediaReadyWithStableReceiptAndNoSecondObject()throws Exception{
        var completion=completion();byte[] data=ZenflowAudioInspectorTest.wave();var upload=waveRegistration(data);transfers().receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data));
        var result=completion.complete(upload.uploadId(),2,"complete-request-1");assertEquals("SUCCEEDED",result.state());assertEquals(4,result.resultVersion());
        assertEquals(result,completion.complete(upload.uploadId(),2,"complete-request-1"));
        assertEquals("READY",jdbc.queryForObject("SELECT processing_state FROM zf_media_asset",String.class));assertNull(jdbc.queryForObject("SELECT quarantine_key FROM zf_media_asset",String.class));
        assertEquals(.25,jdbc.queryForObject("SELECT actual_duration_seconds FROM zf_media_asset",Double.class),.001);
        var token=UUID.fromString(jdbc.queryForObject("SELECT object_key FROM zf_media_asset",String.class));
        var pointer=new ZenflowMediaObjects.Pointer(UUID.fromString(upload.mediaId()),ZenflowMediaInputTest.hash(data),data.length,"audio/wav",token);
        var output=new java.io.ByteArrayOutputStream();objects().stream(pointer,null,id->{},output);assertArrayEquals(data,output.toByteArray());
        try(var paths=java.nio.file.Files.walk(directory.resolve("objects"))){assertEquals(1,paths.filter(java.nio.file.Files::isRegularFile).count());}
    }
    @Test void invalidDeclaredAudioTypeNeverBecomesReadyAndFailedStageCanBeCleaned()throws Exception{
        var completion=completion();byte[] data=ZenflowAudioInspectorTest.wave();var upload=registration(data);transfers().receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data));
        var result=completion.complete(upload.uploadId(),2,"complete-request-1");assertEquals("REJECTED",result.state());assertEquals("INVALID_AUDIO_CONTENT",result.safeErrorCode());
        assertEquals("FAILED",jdbc.queryForObject("SELECT processing_state FROM zf_media_asset",String.class));assertNull(jdbc.queryForObject("SELECT object_key FROM zf_media_asset",String.class));
        assertTrue(cleanupAt("2026-10-22T10:03:00Z",objects()).pollOne(1));
        try(var paths=java.nio.file.Files.walk(directory.resolve("objects"))){assertEquals(0,paths.filter(java.nio.file.Files::isRegularFile).count());}
    }
    @Test void finalReceiptFailureRollsBackReadyAndSameRequestCanResumeProcessing()throws Exception{
        var completion=completion();byte[] data=ZenflowAudioInspectorTest.wave();var upload=waveRegistration(data);transfers().receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data));
        jdbc.execute("ALTER TABLE zf_client_operation ADD CONSTRAINT reject_complete CHECK(operation_type<>'MEDIA_COMPLETE' OR state<>'SUCCEEDED')");
        assertThrows(org.springframework.dao.DataAccessException.class,()->completion.complete(upload.uploadId(),2,"complete-request-1"));
        assertEquals("PROCESSING",jdbc.queryForObject("SELECT processing_state FROM zf_media_asset",String.class));assertNull(jdbc.queryForObject("SELECT object_key FROM zf_media_asset",String.class));
        assertEquals("PROCESSING",jdbc.queryForObject("SELECT state FROM zf_client_operation WHERE operation_type='MEDIA_COMPLETE'",String.class));
        jdbc.execute("ALTER TABLE zf_client_operation DROP CONSTRAINT reject_complete");
        assertEquals("SUCCEEDED",completion().complete(upload.uploadId(),2,"complete-request-1").state());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM zf_client_operation",Integer.class));
    }
    @Test void referencedReadyObjectSurvivesUploadExpiryAndUnboundExpiredObjectIsRetired()throws Exception{
        var completion=completion();byte[] data=ZenflowAudioInspectorTest.wave();var upload=waveRegistration(data);transfers().receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data));completion.complete(upload.uploadId(),2,"complete-request-1");
        jdbc.update("INSERT INTO zf_media_binding(id,tenant_id,subject_type,owner_user_id,media_id,target_type,target_id) VALUES(1,1,'APP_USER',42,?,'NOTE','99')",upload.mediaId());
        assertTrue(cleanupAt("2026-10-22T12:00:00Z",objects()).pollOne(1));assertEquals("READY",jdbc.queryForObject("SELECT processing_state FROM zf_media_asset",String.class));
        jdbc.update("DELETE FROM zf_media_binding");assertTrue(cleanupAt("2026-10-22T12:01:00Z",objects()).pollOne(1));assertEquals("EXPIRED",jdbc.queryForObject("SELECT processing_state FROM zf_media_asset",String.class));
        try(var paths=java.nio.file.Files.walk(directory.resolve("objects"))){assertEquals(0,paths.filter(java.nio.file.Files::isRegularFile).count());}
    }

    @Test void expiredCompletionReturnsRejectedReceiptAndRetiresUnfinishedAsset()throws Exception{
        var completion=completion();byte[] data=ZenflowAudioInspectorTest.wave();var upload=waveRegistration(data);transfers().receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data));
        jdbc.update("UPDATE zf_media_asset SET expires_at=?",java.sql.Timestamp.from(Instant.parse("2026-10-22T09:00:00Z")));
        var result=completion.complete(upload.uploadId(),2,"complete-request-1");assertEquals("REJECTED",result.state());assertEquals("MEDIA_UPLOAD_EXPIRED",result.safeErrorCode());
        assertEquals("EXPIRED",jdbc.queryForObject("SELECT processing_state FROM zf_media_asset",String.class));assertNull(jdbc.queryForObject("SELECT actual_mime FROM zf_media_asset",String.class));
    }
    @Test void completionCannotUseAnotherOwnerAndWrongVersionCannotStartDecoding()throws Exception{
        var completion=completion();byte[] data=ZenflowAudioInspectorTest.wave();var upload=waveRegistration(data);transfers().receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data));
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,43L,"APP_USER"));assertEquals(404,assertThrows(ResponseStatusException.class,()->completion.complete(upload.uploadId(),2,"complete-request-1")).getStatusCode().value());
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,42L,"APP_USER"));
        assertEquals("REJECTED",completion.complete(upload.uploadId(),1,"complete-request-1").state());assertEquals("RECEIVING",jdbc.queryForObject("SELECT processing_state FROM zf_media_asset",String.class));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->completion.complete(upload.uploadId(),2,"complete-request-1")).getStatusCode().value());
    }

    @Test void parentDeletionRetiresReadyAudioAndOldRevisionBindingsCannotKeepItForever()throws Exception{
        var completion=completion();byte[] data=ZenflowAudioInspectorTest.wave();var upload=waveRegistration(data);transfers().receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data));completion.complete(upload.uploadId(),2,"complete-request-1");
        jdbc.update("INSERT INTO zf_media_binding(id,tenant_id,subject_type,owner_user_id,media_id,target_type,target_id) VALUES(1,1,'APP_USER',42,?,'NOTE','99')",upload.mediaId());
        jdbc.update("INSERT INTO zf_media_binding(id,tenant_id,subject_type,owner_user_id,media_id,target_type,target_id) VALUES(2,1,'APP_USER',42,?,'NOTE','99')",upload.mediaId());
        jdbc.update("UPDATE zf_capsule_ext SET delete_state='DELETE_REQUESTED' WHERE capsule_id=99");
        assertTrue(cleanupAt("2026-10-22T10:03:00Z",objects()).pollOne(1));
        assertEquals("DELETED",jdbc.queryForObject("SELECT processing_state FROM zf_media_asset",String.class));
        try(var paths=java.nio.file.Files.walk(directory.resolve("objects"))){assertEquals(0,paths.filter(java.nio.file.Files::isRegularFile).count());}
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM zf_media_binding",Integer.class));
    }
    @Test void failedParentAudioDeletionRemainsDeniedAndRetriesFromDeleteRequested()throws Exception{
        var completion=completion();byte[] data=ZenflowAudioInspectorTest.wave();var upload=waveRegistration(data);transfers().receive(upload.uploadId(),1,new java.io.ByteArrayInputStream(data));completion.complete(upload.uploadId(),2,"complete-request-1");
        jdbc.update("INSERT INTO zf_media_binding(id,tenant_id,subject_type,owner_user_id,media_id,target_type,target_id) VALUES(1,1,'APP_USER',42,?,'NOTE','99')",upload.mediaId());jdbc.update("UPDATE zf_capsule_ext SET delete_state='DELETED'");
        var files=mock(com.deepdimen.matrix.common.file.core.FileTemplate.class);doThrow(new java.io.IOException("storage unavailable")).when(files).removeObject(anyString(),anyString());
        assertTrue(cleanupAt("2026-10-22T10:03:00Z",new ZenflowMediaObjects(files,"invelle-private-test","voice")).pollOne(1));assertEquals("DELETE_REQUESTED",jdbc.queryForObject("SELECT processing_state FROM zf_media_asset",String.class));
        assertTrue(cleanupAt("2026-10-22T10:04:00Z",objects()).pollOne(1));assertEquals("DELETED",jdbc.queryForObject("SELECT processing_state FROM zf_media_asset",String.class));
    }

    @Test void httpUploadCompleteStatusAndParentAuthorizedRangeStreamUseActualDecodedBytes()throws Exception{
        var notes=new ZenflowNoteMedia(jdbc,access,Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"),ZoneOffset.UTC),4);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new ZenflowMediaController(uploads,transfers(),completion(),new ZenflowMediaQueries(jdbc,access),notes,objects())).build();
        byte[] data=ZenflowAudioInspectorTest.wave();var declaration=body().put("mime","audio/wav").put("bytes",data.length).put("sha256",ZenflowMediaInputTest.hash(data));
        var created=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/zenflow/v1/media/uploads").contentType("application/json").content(declaration.toString()).header("Idempotency-Key","upload-request-1")).andReturn().getResponse();
        assertEquals(201,created.getStatus());var registration=json.readTree(created.getContentAsString()).get("data");String upload=registration.get("uploadId").asText(),id=registration.get("mediaId").asText();
        var received=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/zenflow/v1/media/uploads/"+upload+"/content").contentType("application/octet-stream").header("Upload-Version",1).content(data)).andReturn().getResponse();assertEquals(200,received.getStatus());assertEquals("RECEIVING",json.readTree(received.getContentAsString()).at("/data/state").asText());
        var completed=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/zenflow/v1/media/uploads/"+upload+"/complete").contentType("application/json").content("{\"expectedVersion\":2,\"clientRequestId\":\"complete-request-1\"}")).andReturn().getResponse();assertEquals(200,completed.getStatus());assertEquals("SUCCEEDED",json.readTree(completed.getContentAsString()).at("/data/state").asText());
        var status=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/zenflow/v1/media/"+id+"/status")).andReturn().getResponse();assertEquals("READY",json.readTree(status.getContentAsString()).at("/data/state").asText());assertEquals("no-store",status.getHeader("Cache-Control"));
        assertEquals(404,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/zenflow/v1/media/"+id+"/content")).andReturn().getResponse().getStatus());
        new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(ignored->notes.bind(new CapsuleIdentity(1L,42L,"APP_USER"),"99",1,java.util.List.of(id)));
        var audio=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/zenflow/v1/media/"+id+"/content").header("Range","bytes=0-43")).andReturn().getResponse();assertEquals(206,audio.getStatus());assertArrayEquals(java.util.Arrays.copyOf(data,44),audio.getContentAsByteArray());assertEquals("bytes 0-43/"+data.length,audio.getHeader("Content-Range"));assertEquals("no-store",audio.getHeader("Cache-Control"));
        assertEquals(416,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/zenflow/v1/media/"+id+"/content").header("Range","bytes=999999-")).andReturn().getResponse().getStatus());
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,43L,"APP_USER"));assertEquals(404,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/zenflow/v1/media/"+id+"/content").header("Range","bytes=999999-")).andReturn().getResponse().getStatus());
        when(access.capsuleIdentity()).thenReturn(new CapsuleIdentity(1L,42L,"APP_USER"));jdbc.update("UPDATE zf_capsule_ext SET delete_state='DELETE_REQUESTED'");assertEquals(404,mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/zenflow/v1/media/"+id+"/content")).andReturn().getResponse().getStatus());
    }
    @Test void mismatchedHttpIdempotencyHeaderCannotRegisterAnything()throws Exception{
        var notes=new ZenflowNoteMedia(jdbc,access,Clock.fixed(Instant.parse("2026-10-22T10:00:00Z"),ZoneOffset.UTC),4);
        var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new ZenflowMediaController(uploads,transfers(),completion(),new ZenflowMediaQueries(jdbc,access),notes,objects())).build();
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/zenflow/v1/media/uploads").contentType("application/json").content(body().toString()).header("Idempotency-Key","different-request")).andReturn().getResponse();assertEquals(400,response.getStatus());assertEquals("IDEMPOTENCY_KEY_MISMATCH",json.readTree(response.getContentAsString()).at("/data/errorCode").asText());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM zf_media_asset",Integer.class));
    }

}
