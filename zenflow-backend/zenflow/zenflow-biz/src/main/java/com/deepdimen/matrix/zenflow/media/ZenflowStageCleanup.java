package com.deepdimen.matrix.zenflow.media;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Stage deletion uses the existing outbox and never claims note or other product events. */
public final class ZenflowStageCleanup {
    private static final String EVENT="MEDIA_STAGE_DELETE";
    private final JdbcTemplate jdbc;private final TransactionTemplate tx;private final ZenflowMediaObjects objects;private final Clock clock;
    private final ObjectMapper json=new ObjectMapper();private final String worker="media-stage-"+UUID.randomUUID();
    private record Claim(long id,long epoch,String token,String payload){}
    public ZenflowStageCleanup(JdbcTemplate jdbc,PlatformTransactionManager manager,ZenflowMediaObjects objects,Clock clock){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);this.objects=objects;this.clock=clock;}
    public void plan(CapsuleIdentity owner,String uploadId,ZenflowMediaObjects.Stage stage,Instant due){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("MEDIA_STAGE_TRANSACTION_REQUIRED");
        if(!"APP_USER".equals(owner.subjectType()))throw new IllegalArgumentException("CONSUMER_REQUIRED");
        var payload=json.createObjectNode().put("ownerUserId",owner.ownerUserId().toString()).put("uploadId",uploadId).put("mediaId",stage.mediaId().toString()).put("sha256",stage.sha256()).put("bytes",stage.bytes());
        jdbc.update("INSERT INTO zf_outbox(id,tenant_id,event_type,aggregate_type,aggregate_id,dedupe_key,payload_json,next_attempt_at) VALUES(?,?,'MEDIA_STAGE_DELETE','MEDIA_STAGE',?,?,?,?)",IdWorker.getId(),owner.tenantId(),stage.token().toString(),key(stage),payload.toString(),Timestamp.from(due));
    }
    /** A late object write reopens its own tombstone and fences an older cleanup completion. */
    public void requeue(CapsuleIdentity owner,ZenflowMediaObjects.Stage stage,Instant due){
        jdbc.update("UPDATE zf_outbox SET state='PENDING',attempts=0,lease_owner=NULL,lease_until=NULL,lease_epoch=lease_epoch+1,next_attempt_at=?,error_code=NULL,version=version+1,updated_at=? WHERE tenant_id=? AND event_type='MEDIA_STAGE_DELETE' AND aggregate_type='MEDIA_STAGE' AND aggregate_id=? AND dedupe_key=?",Timestamp.from(due),Timestamp.from(clock.instant()),owner.tenantId(),stage.token().toString(),key(stage));
    }
    public boolean pollOne(long tenant){
        if(tenant<=0)throw new IllegalArgumentException("INVALID_MEDIA_TENANT");
        Claim claim=tx.execute(status->claim(tenant));if(claim==null)return false;
        ZenflowMediaObjects.Stage stage;long owner;String upload;
        try{
            var payload=json.readTree(claim.payload());
            if(!payload.isObject()||payload.size()!=5||!payload.path("bytes").isIntegralNumber()||!payload.path("bytes").canConvertToLong())throw new IllegalArgumentException();
            owner=Long.parseLong(payload.path("ownerUserId").asText());if(owner<=0)throw new IllegalArgumentException();
            upload=UUID.fromString(payload.path("uploadId").asText()).toString();
            stage=new ZenflowMediaObjects.Stage(UUID.fromString(payload.path("mediaId").asText()),UUID.fromString(claim.token()),payload.path("sha256").asText(),payload.path("bytes").longValue());
        }catch(Exception invalid){finish(tenant,claim,"DEAD","MEDIA_STAGE_EVENT_INVALID",false);return true;}
        try{
            boolean protectedStage=tx.execute(status->protectedStage(tenant,owner,upload,stage));
            if(protectedStage){finish(tenant,claim,"PENDING",null,true);return true;}
            objects.removeStage(stage);
            jdbc.update("UPDATE zf_media_asset SET processing_state='DELETED',version=version+1,updated_at=? WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND processing_state='DELETE_REQUESTED' AND object_key=?",Timestamp.from(clock.instant()),stage.mediaId().toString(),tenant,owner,stage.token().toString());
            finish(tenant,claim,"SUCCEEDED",null,false);
        }catch(Exception failure){finish(tenant,claim,"PENDING","MEDIA_STAGE_CLEANUP_FAILED",false);}
        return true;
    }
    private Claim claim(long tenant){
        var now=Timestamp.from(clock.instant());
        jdbc.update("UPDATE zf_outbox SET state='DEAD',error_code='MEDIA_STAGE_RETRY_EXHAUSTED',lease_owner=NULL,lease_until=NULL,version=version+1,updated_at=? WHERE tenant_id=? AND event_type='MEDIA_STAGE_DELETE' AND aggregate_type='MEDIA_STAGE' AND attempts>=5 AND state='RUNNING' AND lease_until<?",now,tenant,now);
        var rows=jdbc.queryForList("SELECT id,lease_epoch,aggregate_id,payload_json,state,attempts FROM zf_outbox WHERE tenant_id=? AND event_type='MEDIA_STAGE_DELETE' AND aggregate_type='MEDIA_STAGE' AND (((state='PENDING' OR state='SUCCEEDED') AND next_attempt_at<=? AND (state='SUCCEEDED' OR attempts<5)) OR (state='RUNNING' AND attempts<5 AND lease_until<?)) ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED",tenant,now,now);
        if(rows.isEmpty())return null;var row=rows.get(0);long id=((Number)row.get("id")).longValue(),epoch=((Number)row.get("lease_epoch")).longValue()+1;
        long attempts="SUCCEEDED".equals(row.get("state"))?1:((Number)row.get("attempts")).longValue()+1;
        jdbc.update("UPDATE zf_outbox SET state='RUNNING',attempts=?,lease_epoch=?,lease_owner=?,lease_until=?,version=version+1,updated_at=? WHERE id=? AND tenant_id=?",attempts,epoch,worker,Timestamp.from(clock.instant().plusSeconds(30)),now,id,tenant);
        return new Claim(id,epoch,row.get("aggregate_id").toString(),row.get("payload_json").toString());
    }
    private boolean protectedStage(long tenant,long owner,String upload,ZenflowMediaObjects.Stage stage){
        jdbc.queryForList("SELECT owner_user_id FROM zf_profile WHERE tenant_id=? AND owner_user_id=? FOR UPDATE",tenant,owner);
        var assets=jdbc.queryForList("SELECT quarantine_key,object_key,processing_state,expires_at FROM zf_media_asset WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? FOR UPDATE",stage.mediaId().toString(),tenant,owner);
        if(!assets.isEmpty()){
            var asset=assets.get(0);
            if(java.util.Set.of("RECEIVING","PROCESSING").contains(asset.get("processing_state"))&&!((Timestamp)asset.get("expires_at")).toInstant().isAfter(clock.instant())){
                jdbc.update("UPDATE zf_media_asset SET processing_state='EXPIRED',version=version+1,updated_at=? WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND processing_state IN ('RECEIVING','PROCESSING')",Timestamp.from(clock.instant()),stage.mediaId().toString(),tenant,owner);
            }
            if("READY".equals(asset.get("processing_state"))&&stage.token().toString().equals(asset.get("object_key"))){
                var bindings=jdbc.queryForList("SELECT DISTINCT target_type,target_id FROM zf_media_binding WHERE media_id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=?",stage.mediaId().toString(),tenant,owner);
                if(!bindings.isEmpty()){
                    for(var binding:bindings){
                        // Other parent adapters must supply their own deletion policy before being enabled.
                        if(!"NOTE".equals(binding.get("target_type")))return true;
                        var parents=jdbc.queryForList("SELECT c.id FROM ai_capsule c JOIN zf_capsule_ext e ON e.capsule_id=c.id AND e.tenant_id=c.tenant_id AND e.owner_user_id=c.owner_user_id WHERE c.id=? AND c.tenant_id=? AND c.owner_subject_type='APP_USER' AND c.owner_user_id=? AND c.del_flag='0' AND c.status IN ('ACTIVE','ARCHIVED') AND e.delete_state='ACTIVE'",binding.get("target_id"),tenant,owner);
                        if(!parents.isEmpty())return true;
                    }
                    jdbc.update("UPDATE zf_media_asset SET processing_state='DELETE_REQUESTED',version=version+1,updated_at=? WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND processing_state='READY' AND object_key=?",Timestamp.from(clock.instant()),stage.mediaId().toString(),tenant,owner,stage.token().toString());
                    return false;
                }
                if(((Timestamp)asset.get("expires_at")).toInstant().isAfter(clock.instant()))return true;
                jdbc.update("UPDATE zf_media_asset SET processing_state='EXPIRED',version=version+1,updated_at=? WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND processing_state='READY' AND object_key=?",Timestamp.from(clock.instant()),stage.mediaId().toString(),tenant,owner,stage.token().toString());
            }
            if(stage.token().toString().equals(asset.get("quarantine_key"))&&java.util.Set.of("RECEIVING","PROCESSING","READY").contains(asset.get("processing_state"))&&((Timestamp)asset.get("expires_at")).toInstant().isAfter(clock.instant()))return true;
        }
        var uploads=jdbc.queryForList("SELECT lease_token,lease_until FROM zf_media_upload WHERE id=? AND media_id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=?",upload,stage.mediaId().toString(),tenant,owner);
        return !uploads.isEmpty()&&stage.token().toString().equals(uploads.get(0).get("lease_token"))&&uploads.get(0).get("lease_until") instanceof Timestamp until&&until.toInstant().isAfter(clock.instant());
    }
    private void finish(long tenant,Claim claim,String state,String error,boolean defer){
        String update="UPDATE zf_outbox SET state=?,error_code=?,attempts="+(defer?"attempts-1":"attempts")+",lease_owner=NULL,lease_until=NULL,next_attempt_at=?,version=version+1,updated_at=? WHERE id=? AND tenant_id=? AND event_type='MEDIA_STAGE_DELETE' AND aggregate_type='MEDIA_STAGE' AND state='RUNNING' AND lease_owner=? AND lease_epoch=? AND lease_until>?";
        jdbc.update(update,state,error,Timestamp.from(clock.instant().plusSeconds("SUCCEEDED".equals(state)?86400:30)),Timestamp.from(clock.instant()),claim.id(),tenant,worker,claim.epoch(),Timestamp.from(clock.instant()));
        if(!defer&&"PENDING".equals(state))jdbc.update("UPDATE zf_outbox SET state='DEAD' WHERE id=? AND tenant_id=? AND event_type='MEDIA_STAGE_DELETE' AND aggregate_type='MEDIA_STAGE' AND state='PENDING' AND attempts>=5",claim.id(),tenant);
    }
    private static String key(ZenflowMediaObjects.Stage stage){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((EVENT+":"+stage.mediaId()+":"+stage.token()).getBytes(StandardCharsets.US_ASCII)));}catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
