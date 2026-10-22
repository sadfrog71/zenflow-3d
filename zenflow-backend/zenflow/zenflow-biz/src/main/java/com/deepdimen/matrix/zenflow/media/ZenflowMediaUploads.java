package com.deepdimen.matrix.zenflow.media;

import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.deepdimen.matrix.zenflow.operation.ZenflowOperationReceipts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Registration is not upload completion or parent-content success. Not enabled before media configuration. */
public class ZenflowMediaUploads {
    public record Upload(String uploadId,String mediaId,int version,String expiresAt,String purpose,List<String> allowedMimeTypes,long maxBytes,double maxDurationSeconds){}
    public record Limits(long bytes,double seconds,Duration lifetime){
        public Limits{if(bytes<1||bytes>64L*1024*1024||!Double.isFinite(seconds)||seconds<=0||seconds>600||lifetime==null||lifetime.isNegative()||lifetime.isZero()||lifetime.compareTo(Duration.ofDays(1))>0)throw new IllegalArgumentException("INVALID_MEDIA_LIMITS");}
    }
    private static final List<String> MIME=List.of("audio/mp4","audio/wav","audio/ogg");
    private final JdbcTemplate jdbc;private final ZenflowConsumerAccess access;private final TransactionTemplate tx;
    private final ZenflowOperationReceipts receipts;private final Clock clock;private final Limits limits;
    public ZenflowMediaUploads(JdbcTemplate jdbc,ZenflowConsumerAccess access,PlatformTransactionManager manager,ObjectMapper json,Clock clock,Limits limits){
        if(clock==null||limits==null)throw new IllegalArgumentException("MEDIA_CONFIGURATION_REQUIRED");
        this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);this.receipts=new ZenflowOperationReceipts(jdbc,access,json);this.clock=clock;this.limits=limits;
    }
    public Upload create(JsonNode body){
        if(body==null||!body.isObject())throw invalid();
        body.fieldNames().forEachRemaining(key->{if(!Set.of("purpose","mime","bytes","durationSeconds","sha256","clientRequestId").contains(key))throw invalid();});
        String purpose=text(body,"purpose"),mime=text(body,"mime"),sha=text(body,"sha256"),key=text(body,"clientRequestId");
        ZenflowOperationReceipts.validateKey(key);
        if(!Set.of("PRIVATE_NOTE","PUBLIC_SUBMISSION","FRIEND_LETTER").contains(purpose)||!MIME.contains(mime)||!sha.matches("[a-f0-9]{64}"))throw invalid();
        if(!body.path("bytes").isIntegralNumber()||!body.path("bytes").canConvertToLong())throw invalid();
        long bytes=body.get("bytes").longValue();double seconds=body.path("durationSeconds").isNumber()?body.get("durationSeconds").doubleValue():Double.NaN;
        if(bytes<1||bytes>limits.bytes()||!Double.isFinite(seconds)||seconds<=0||seconds>limits.seconds())throw invalid();
        var owner=owner();String hash=receipts.hash(body);
        return tx.execute(status->{
            if(jdbc.queryForList("SELECT owner_user_id FROM zf_profile WHERE tenant_id=? AND owner_user_id=? FOR UPDATE",owner.tenantId(),owner.ownerUserId()).size()!=1||!owner.equals(owner()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");
            var previous=receipts.find(owner,"MEDIA_CREATE_UPLOAD",key,hash);
            if(previous!=null)return registration(owner,previous.resultId());
            String mediaId=UUID.randomUUID().toString(),uploadId=UUID.randomUUID().toString();
            Instant expires=clock.instant().plus(limits.lifetime());
            jdbc.update("INSERT INTO zf_media_asset(id,tenant_id,subject_type,owner_user_id,purpose,processing_state,version,declared_mime,declared_bytes,declared_duration_seconds,sha256,max_bytes,max_duration_seconds,expires_at) VALUES(?,?,'APP_USER',?,?,'RECEIVING',1,?,?,?,?,?,?,?)",mediaId,owner.tenantId(),owner.ownerUserId(),purpose,mime,bytes,seconds,sha,limits.bytes(),limits.seconds(),Timestamp.from(expires));
            jdbc.update("INSERT INTO zf_media_upload(id,media_id,tenant_id,subject_type,owner_user_id,version) VALUES(?,?,?,'APP_USER',?,1)",uploadId,mediaId,owner.tenantId(),owner.ownerUserId());
            receipts.record(owner,"MEDIA_CREATE_UPLOAD",key,hash,"SUCCEEDED","MEDIA_UPLOAD",uploadId,1,null);
            return registration(owner,uploadId);
        });
    }
    private Upload registration(CapsuleIdentity owner,String uploadId){
        var rows=jdbc.queryForList("SELECT u.id,u.media_id,a.expires_at,a.purpose,a.max_bytes,a.max_duration_seconds FROM zf_media_upload u JOIN zf_media_asset a ON a.id=u.media_id AND a.tenant_id=u.tenant_id AND a.subject_type=u.subject_type AND a.owner_user_id=u.owner_user_id WHERE u.id=? AND u.tenant_id=? AND u.subject_type='APP_USER' AND u.owner_user_id=?",uploadId,owner.tenantId(),owner.ownerUserId());
        if(rows.size()!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"MEDIA_UPLOAD_NOT_FOUND");
        var row=rows.get(0);return new Upload(row.get("id").toString(),row.get("media_id").toString(),1,((Timestamp)row.get("expires_at")).toInstant().toString(),row.get("purpose").toString(),MIME,((Number)row.get("max_bytes")).longValue(),((Number)row.get("max_duration_seconds")).doubleValue());
    }
    private CapsuleIdentity owner(){var owner=access.capsuleIdentity();if(owner==null||!"APP_USER".equals(owner.subjectType())||owner.tenantId()==null||owner.ownerUserId()==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");return owner;}
    private static String text(JsonNode body,String key){if(!body.path(key).isTextual())throw invalid();return body.get(key).textValue();}
    private static ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_MEDIA_DECLARATION");}
}
