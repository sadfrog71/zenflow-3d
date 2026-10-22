package com.deepdimen.matrix.zenflow.media;

import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.deepdimen.matrix.zenflow.operation.ZenflowOperationReceipts;
import com.deepdimen.matrix.zenflow.operation.ZenflowOperationReceipts.Receipt;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Complete validates outside SQL locks; READY and its terminal receipt commit atomically. */
public final class ZenflowMediaCompletion {
    private record Job(CapsuleIdentity owner,String uploadId,String key,String hash,UUID token,Map<String,Object> row,Receipt receipt){}
    private final JdbcTemplate jdbc;private final ZenflowConsumerAccess access;private final TransactionTemplate tx;
    private final ZenflowOperationReceipts receipts;private final ZenflowMediaObjects objects;private final ZenflowAudioInspector inspector;
    private final Path spool;private final Clock clock;private final Duration lease;private final String storageRef;private final ObjectMapper json;
    public ZenflowMediaCompletion(JdbcTemplate jdbc,ZenflowConsumerAccess access,PlatformTransactionManager manager,ObjectMapper json,ZenflowMediaObjects objects,ZenflowAudioInspector inspector,Path spool,Clock clock,Duration lease,String storageRef){
        if(objects==null||inspector==null||spool==null||!spool.isAbsolute()||clock==null||lease==null||lease.compareTo(Duration.ofMinutes(1))<0||lease.compareTo(Duration.ofMinutes(10))>0||storageRef==null||!storageRef.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("MEDIA_COMPLETION_CONFIGURATION_REQUIRED");
        this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);this.json=json;this.receipts=new ZenflowOperationReceipts(jdbc,access,json);this.objects=objects;this.inspector=inspector;this.spool=spool;this.clock=clock;this.lease=lease;this.storageRef=storageRef;
    }
    public Receipt complete(String uploadId,int expectedVersion,String key)throws Exception{
        try{if(!UUID.fromString(uploadId).toString().equals(uploadId))throw new IllegalArgumentException();}catch(RuntimeException invalid){throw failure(HttpStatus.BAD_REQUEST,"INVALID_UPLOAD_ID");}
        ZenflowOperationReceipts.validateKey(key);if(expectedVersion<1)throw failure(HttpStatus.BAD_REQUEST,"INVALID_MEDIA_VERSION");
        var owner=owner();String hash=receipts.hash(json.createObjectNode().put("uploadId",uploadId).put("expectedVersion",expectedVersion).put("clientRequestId",key));
        Job job=tx.execute(status->claim(owner,uploadId,expectedVersion,key,hash));
        if(job.token()==null)return job.receipt();
        var row=job.row();var stage=new ZenflowMediaObjects.Stage(UUID.fromString(row.get("media_id").toString()),UUID.fromString(row.get("quarantine_key").toString()),row.get("sha256").toString(),((Number)row.get("declared_bytes")).longValue());
        try(var input=objects.inspectStage(stage,spool)){
            var audio=inspector.inspect(input,row.get("declared_mime").toString(),((Number)row.get("max_duration_seconds")).doubleValue());
            return tx.execute(status->{
                var current=ownedJob(job);
                int version=((Number)current.get("version")).intValue()+1;
                // Logical promotion of immutable, hash-verified bytes avoids a second non-atomic object write.
                jdbc.update("UPDATE zf_media_asset SET processing_state='READY',version=?,storage_ref=?,object_key=quarantine_key,quarantine_key=NULL,actual_mime=?,actual_bytes=?,actual_duration_seconds=?,updated_at=? WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=?",version,storageRef,audio.mime(),input.bytes(),audio.durationSeconds(),Timestamp.from(clock.instant()),row.get("media_id"),owner.tenantId(),owner.ownerUserId());
                clearLease(job);
                return receipts.finish(owner,"MEDIA_COMPLETE",key,hash,"SUCCEEDED","MEDIA",row.get("media_id").toString(),version,null);
            });
        }catch(Exception error){
            if(error instanceof java.io.IOException&&Set.of("INVALID_AUDIO_CONTENT","MEDIA_HASH_MISMATCH","MEDIA_SIZE_MISMATCH","MEDIA_SIZE_EXCEEDED").contains(error.getMessage())){
                return tx.execute(status->{var current=ownedJob(job);int version=((Number)current.get("version")).intValue()+1;
                    jdbc.update("UPDATE zf_media_asset SET processing_state='FAILED',version=?,updated_at=? WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=?",version,Timestamp.from(clock.instant()),row.get("media_id"),owner.tenantId(),owner.ownerUserId());clearLease(job);
                    return receipts.finish(owner,"MEDIA_COMPLETE",key,hash,"REJECTED","MEDIA",row.get("media_id").toString(),version,"INVALID_AUDIO_CONTENT");});
            }
            // A transient decoder/storage/commit failure remains PROCESSING; the same request can reclaim it.
            try{jdbc.update("UPDATE zf_media_upload SET lease_until=? WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND lease_token=?",Timestamp.from(clock.instant()),uploadId,owner.tenantId(),owner.ownerUserId(),job.token().toString());}catch(RuntimeException release){error.addSuppressed(release);}
            throw error;
        }
    }
    private Job claim(CapsuleIdentity owner,String uploadId,int expected,String key,String hash){
        lock(owner);var previous=receipts.find(owner,"MEDIA_COMPLETE",key,hash);var row=row(owner,uploadId);
        if(previous!=null&&!"PROCESSING".equals(previous.state()))return new Job(owner,uploadId,key,hash,null,row,previous);
        if(!((Timestamp)row.get("expires_at")).toInstant().isAfter(clock.instant())){
            jdbc.update("UPDATE zf_media_asset SET processing_state='EXPIRED',version=version+1,updated_at=? WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND processing_state IN ('RECEIVING','PROCESSING')",Timestamp.from(clock.instant()),row.get("media_id"),owner.tenantId(),owner.ownerUserId());
            var rejected=previous==null?receipts.record(owner,"MEDIA_COMPLETE",key,hash,"REJECTED",null,null,null,"MEDIA_UPLOAD_EXPIRED"):receipts.finish(owner,"MEDIA_COMPLETE",key,hash,"REJECTED",null,null,null,"MEDIA_UPLOAD_EXPIRED");
            return new Job(owner,uploadId,key,hash,null,row,rejected);
        }
        int version=((Number)row.get("version")).intValue();
        boolean resume=previous!=null&&"PROCESSING".equals(row.get("processing_state"))&&key.equals(row.get("processing_request_key"));
        if(!resume&&(version!=expected||row.get("received_at")==null||!"RECEIVING".equals(row.get("processing_state")))){
            var rejected=receipts.record(owner,"MEDIA_COMPLETE",key,hash,"REJECTED",null,null,null,"MEDIA_VERSION_OR_STATE_CONFLICT");return new Job(owner,uploadId,key,hash,null,row,rejected);
        }
        if(row.get("lease_until") instanceof Timestamp until&&until.toInstant().isAfter(clock.instant())){
            if(previous==null)previous=receipts.record(owner,"MEDIA_COMPLETE",key,hash,"REJECTED",null,null,null,"MEDIA_PROCESSING_BUSY");
            return new Job(owner,uploadId,key,hash,null,row,previous);
        }
        UUID token=UUID.randomUUID();
        jdbc.update("UPDATE zf_media_upload SET lease_token=?,lease_until=? WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=?",token.toString(),Timestamp.from(clock.instant().plus(lease)),uploadId,owner.tenantId(),owner.ownerUserId());
        if(!resume){version++;jdbc.update("UPDATE zf_media_asset SET processing_state='PROCESSING',version=?,processing_request_key=?,processing_base_version=?,updated_at=? WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=?",version,key,expected,Timestamp.from(clock.instant()),row.get("media_id"),owner.tenantId(),owner.ownerUserId());previous=receipts.record(owner,"MEDIA_COMPLETE",key,hash,"PROCESSING","MEDIA",row.get("media_id").toString(),version,null);}
        return new Job(owner,uploadId,key,hash,token,row,previous);
    }
    private Map<String,Object> ownedJob(Job job){
        lock(job.owner());var row=row(job.owner(),job.uploadId());
        if(!"PROCESSING".equals(row.get("processing_state"))||!job.key().equals(row.get("processing_request_key"))||!job.token().toString().equals(row.get("lease_token"))||!(row.get("lease_until") instanceof Timestamp until)||!until.toInstant().isAfter(clock.instant())||!((Timestamp)row.get("expires_at")).toInstant().isAfter(clock.instant()))throw failure(HttpStatus.CONFLICT,"MEDIA_PROCESSING_LEASE_LOST");return row;
    }
    private void clearLease(Job job){jdbc.update("UPDATE zf_media_upload SET lease_token=NULL,lease_until=NULL WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND lease_token=?",job.uploadId(),job.owner().tenantId(),job.owner().ownerUserId(),job.token().toString());}
    private Map<String,Object> row(CapsuleIdentity owner,String upload){var rows=jdbc.queryForList("SELECT a.*,u.media_id,u.received_at,u.lease_token,u.lease_until FROM zf_media_asset a JOIN zf_media_upload u ON u.media_id=a.id AND u.tenant_id=a.tenant_id AND u.subject_type=a.subject_type AND u.owner_user_id=a.owner_user_id WHERE u.id=? AND u.tenant_id=? AND u.subject_type='APP_USER' AND u.owner_user_id=?",upload,owner.tenantId(),owner.ownerUserId());if(rows.size()!=1)throw failure(HttpStatus.NOT_FOUND,"MEDIA_UPLOAD_NOT_FOUND");return rows.get(0);}
    private CapsuleIdentity owner(){var owner=access.capsuleIdentity();if(owner==null||!"APP_USER".equals(owner.subjectType())||owner.tenantId()==null||owner.ownerUserId()==null)throw failure(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");return owner;}
    private void lock(CapsuleIdentity owner){if(jdbc.queryForList("SELECT owner_user_id FROM zf_profile WHERE tenant_id=? AND owner_user_id=? FOR UPDATE",owner.tenantId(),owner.ownerUserId()).size()!=1||!owner.equals(owner()))throw failure(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");}
    private static ResponseStatusException failure(HttpStatus status,String code){return new ResponseStatusException(status,code);}
}
