package com.deepdimen.matrix.zenflow.media;

import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** A successful receive only creates quarantine content. It never reports the asset READY. */
public final class ZenflowMediaTransfers {
    public record Received(String uploadId,String mediaId,int version,String state){}
    private record Claim(CapsuleIdentity owner,String uploadId,UUID mediaId,UUID token,Map<String,Object> row,boolean replay){}
    private final JdbcTemplate jdbc;private final ZenflowConsumerAccess access;private final TransactionTemplate tx;
    private final ZenflowMediaObjects objects;private final Path spool;private final Clock clock;private final Duration lease;private final ZenflowStageCleanup cleanup;
    public ZenflowMediaTransfers(JdbcTemplate jdbc,ZenflowConsumerAccess access,PlatformTransactionManager manager,ZenflowMediaObjects objects,Path spool,Clock clock,Duration lease){
        if(objects==null||spool==null||!spool.isAbsolute()||clock==null||lease==null||lease.isZero()||lease.isNegative()||lease.compareTo(Duration.ofMinutes(10))>0)throw new IllegalArgumentException("MEDIA_TRANSFER_CONFIGURATION_REQUIRED");
        this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);this.objects=objects;this.spool=spool;this.clock=clock;this.lease=lease;this.cleanup=new ZenflowStageCleanup(jdbc,manager,objects,clock);
    }
    public Received receive(String uploadId,int expectedVersion,InputStream stream)throws Exception{
        uuid(uploadId);if(expectedVersion<1)throw failure(HttpStatus.BAD_REQUEST,"INVALID_MEDIA_VERSION");
        var owner=owner();
        Claim claim=tx.execute(status->{
            lock(owner);var row=row(owner,uploadId);validAsset(row);
            int version=number(row,"upload_version").intValue();
            if(row.get("received_at")!=null){
                if(expectedVersion!=version&&expectedVersion!=version-1)throw failure(HttpStatus.CONFLICT,"MEDIA_VERSION_CONFLICT");
                return new Claim(owner,uploadId,uuid(row.get("media_id").toString()),null,row,true);
            }
            if(version!=expectedVersion)throw failure(HttpStatus.CONFLICT,"MEDIA_VERSION_CONFLICT");
            if(!"RECEIVING".equals(row.get("processing_state")))throw failure(HttpStatus.CONFLICT,"MEDIA_STATE_CONFLICT");
            if(row.get("lease_until") instanceof Timestamp until&&until.toInstant().isAfter(clock.instant()))throw failure(HttpStatus.CONFLICT,"MEDIA_UPLOAD_BUSY");
            UUID token=UUID.randomUUID();
            cleanup.plan(owner,uploadId,new ZenflowMediaObjects.Stage(uuid(row.get("media_id").toString()),token,row.get("sha256").toString(),number(row,"declared_bytes").longValue()),clock.instant().plus(lease));
            jdbc.update("UPDATE zf_media_upload SET lease_token=?,lease_until=? WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=?",token.toString(),Timestamp.from(clock.instant().plus(lease)),uploadId,owner.tenantId(),owner.ownerUserId());
            return new Claim(owner,uploadId,uuid(row.get("media_id").toString()),token,row,false);
        });
        // The request stream and object storage are outside any database transaction.
        boolean storageAttempted=false;
        try(var input=ZenflowMediaInput.receive(stream,spool,number(claim.row(),"declared_bytes").longValue(),number(claim.row(),"max_bytes").longValue(),claim.row().get("sha256").toString())){
            if(claim.replay()){
                return tx.execute(status->{lock(owner);var current=row(owner,uploadId);validAsset(current);return received(current);});
            }
            if(!owner.equals(owner()))throw failure(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");
            storageAttempted=true;var stage=objects.stage(claim.mediaId(),claim.token(),input);
            cleanup.requeue(owner,stage,clock.instant().plus(lease));
            return tx.execute(status->{
                lock(owner);var current=row(owner,uploadId);validAsset(current);
                if(!claim.token().toString().equals(current.get("lease_token"))||!(current.get("lease_until") instanceof Timestamp until)||!until.toInstant().isAfter(clock.instant())||number(current,"upload_version").intValue()!=expectedVersion||!"RECEIVING".equals(current.get("processing_state")))throw failure(HttpStatus.CONFLICT,"MEDIA_UPLOAD_LEASE_LOST");
                jdbc.update("UPDATE zf_media_asset SET quarantine_key=?,version=version+1,updated_at=? WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=?",claim.token().toString(),Timestamp.from(clock.instant()),claim.mediaId().toString(),owner.tenantId(),owner.ownerUserId());
                jdbc.update("UPDATE zf_media_upload SET received_at=?,version=version+1,lease_token=NULL,lease_until=NULL WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=?",Timestamp.from(clock.instant()),uploadId,owner.tenantId(),owner.ownerUserId());
                return received(row(owner,uploadId));
            });
        }catch(Exception failure){
            if(!claim.replay()&&storageAttempted){
                try{cleanup.requeue(owner,new ZenflowMediaObjects.Stage(claim.mediaId(),claim.token(),claim.row().get("sha256").toString(),number(claim.row(),"declared_bytes").longValue()),clock.instant().plus(lease));}
                catch(RuntimeException retry){failure.addSuppressed(retry);}
            }
            if(!claim.replay()&&!storageAttempted){
                try{jdbc.update("UPDATE zf_media_upload SET lease_token=NULL,lease_until=NULL WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND lease_token=? AND received_at IS NULL",uploadId,owner.tenantId(),owner.ownerUserId(),claim.token().toString());}
                catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            }
            throw failure;
        }
        // Every token has its own precommitted outbox intent, including superseded leases.
        // Never delete a stage blindly: a database commit acknowledgement might have been lost.
    }
    private void validAsset(Map<String,Object> row){
        if(!((Timestamp)row.get("expires_at")).toInstant().isAfter(clock.instant()))throw failure(HttpStatus.GONE,"MEDIA_UPLOAD_EXPIRED");
        if(!java.util.Set.of("RECEIVING","PROCESSING","READY").contains(row.get("processing_state")))throw failure(HttpStatus.CONFLICT,"MEDIA_STATE_CONFLICT");
    }
    private Map<String,Object> row(CapsuleIdentity owner,String uploadId){
        var rows=jdbc.queryForList("SELECT a.*,u.id AS upload_id,u.media_id,u.version AS upload_version,u.received_at,u.lease_token,u.lease_until FROM zf_media_upload u JOIN zf_media_asset a ON a.id=u.media_id AND a.tenant_id=u.tenant_id AND a.subject_type=u.subject_type AND a.owner_user_id=u.owner_user_id WHERE u.id=? AND u.tenant_id=? AND u.subject_type='APP_USER' AND u.owner_user_id=?",uploadId,owner.tenantId(),owner.ownerUserId());
        if(rows.size()!=1)throw failure(HttpStatus.NOT_FOUND,"MEDIA_UPLOAD_NOT_FOUND");return rows.get(0);
    }
    private void lock(CapsuleIdentity owner){if(jdbc.queryForList("SELECT owner_user_id FROM zf_profile WHERE tenant_id=? AND owner_user_id=? FOR UPDATE",owner.tenantId(),owner.ownerUserId()).size()!=1||!owner.equals(owner()))throw failure(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");}
    private CapsuleIdentity owner(){var owner=access.capsuleIdentity();if(owner==null||!"APP_USER".equals(owner.subjectType())||owner.tenantId()==null||owner.ownerUserId()==null)throw failure(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");return owner;}
    private static Received received(Map<String,Object> row){return new Received(row.get("upload_id").toString(),row.get("media_id").toString(),number(row,"upload_version").intValue(),row.get("processing_state").toString());}
    private static Number number(Map<String,Object> row,String key){return (Number)row.get(key);}
    private static UUID uuid(String value){try{UUID id=UUID.fromString(value);if(!id.toString().equals(value))throw new IllegalArgumentException();return id;}catch(RuntimeException invalid){throw failure(HttpStatus.BAD_REQUEST,"INVALID_UPLOAD_ID");}}
    private static ResponseStatusException failure(HttpStatus status,String code){return new ResponseStatusException(status,code);}
}
