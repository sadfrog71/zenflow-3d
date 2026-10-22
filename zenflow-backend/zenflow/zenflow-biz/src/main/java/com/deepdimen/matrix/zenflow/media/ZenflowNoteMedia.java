package com.deepdimen.matrix.zenflow.media;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Private note media only. Public submissions and letters require their own parent-policy adapters. */
public class ZenflowNoteMedia {
    public record Media(String id,String mime,long bytes,double durationSeconds){}
    private final JdbcTemplate jdbc;private final ZenflowConsumerAccess access;private final Clock clock;private final int maxAttachments;
    public ZenflowNoteMedia(JdbcTemplate jdbc,ZenflowConsumerAccess access,Clock clock,int maxAttachments){
        if(clock==null||maxAttachments<1||maxAttachments>16)throw new IllegalArgumentException("INVALID_NOTE_MEDIA_CONFIGURATION");this.jdbc=jdbc;this.access=access;this.clock=clock;this.maxAttachments=maxAttachments;
    }
    public List<String> ids(String noteId,int revision){return list(noteId,revision).stream().map(Media::id).toList();}
    public List<Media> list(String noteId,int revision){
        var owner=owner();parent(owner,noteId,revision,false);
        return jdbc.queryForList("SELECT a.id,a.actual_mime,a.actual_bytes,a.actual_duration_seconds FROM zf_media_binding b JOIN zf_media_asset a ON a.id=b.media_id AND a.tenant_id=b.tenant_id AND a.subject_type=b.subject_type AND a.owner_user_id=b.owner_user_id WHERE b.tenant_id=? AND b.subject_type='APP_USER' AND b.owner_user_id=? AND b.target_type='NOTE' AND b.target_id=? AND b.target_version=? AND b.role='ORIGINAL_AUDIO' AND a.purpose='PRIVATE_NOTE' AND a.processing_state='READY' ORDER BY b.id",owner.tenantId(),owner.ownerUserId(),noteId,revision).stream().map(row->new Media(row.get("id").toString(),row.get("actual_mime").toString(),((Number)row.get("actual_bytes")).longValue(),((Number)row.get("actual_duration_seconds")).doubleValue())).toList();
    }
    public List<String> validateIds(Object value){
        if(!(value instanceof List<?> values)||values.size()>maxAttachments)throw bad();
        var result=values.stream().map(item->{if(!(item instanceof String text))throw bad();try{if(!UUID.fromString(text).toString().equals(text))throw bad();}catch(IllegalArgumentException invalid){throw bad();}return text;}).toList();
        if(result.stream().distinct().count()!=result.size())throw bad();return result;
    }
    /** The target version is the immutable capsule revision number, not its mutable metadata version. */
    public void bind(CapsuleIdentity expectedOwner,String noteId,int revision,List<String> mediaIds){
        if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("MEDIA_BINDING_TRANSACTION_REQUIRED");
        var owner=owner();if(!owner.equals(expectedOwner))throw missing();validateIds(mediaIds);
        if(jdbc.queryForList("SELECT owner_user_id FROM zf_profile WHERE tenant_id=? AND owner_user_id=? FOR UPDATE",owner.tenantId(),owner.ownerUserId()).size()!=1)throw missing();
        if(!owner.equals(owner()))throw missing();
        parent(owner,noteId,revision,true);
        for(String id:mediaIds){
            var rows=jdbc.queryForList("SELECT expires_at FROM zf_media_asset WHERE id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND purpose='PRIVATE_NOTE' AND processing_state='READY' FOR UPDATE",id,owner.tenantId(),owner.ownerUserId());
            if(rows.size()!=1)throw missing();
            var bindings=jdbc.queryForList("SELECT target_type,target_id,target_version FROM zf_media_binding WHERE media_id=? AND tenant_id=? AND subject_type='APP_USER' AND owner_user_id=?",id,owner.tenantId(),owner.ownerUserId());
            if(bindings.stream().anyMatch(binding->!"NOTE".equals(binding.get("target_type"))||!noteId.equals(binding.get("target_id"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"MEDIA_ALREADY_BOUND");
            if(bindings.isEmpty()&&!((Timestamp)rows.get(0).get("expires_at")).toInstant().isAfter(clock.instant()))throw new ResponseStatusException(HttpStatus.CONFLICT,"MEDIA_EXPIRED");
            if(bindings.stream().noneMatch(binding->((Number)binding.get("target_version")).intValue()==revision))jdbc.update("INSERT INTO zf_media_binding(id,tenant_id,subject_type,owner_user_id,media_id,target_type,target_id,target_version,role) VALUES(?,?,'APP_USER',?,?,'NOTE',?,?,'ORIGINAL_AUDIO')",IdWorker.getId(),owner.tenantId(),owner.ownerUserId(),id,noteId,revision);
        }
    }
    /** No unbound-owner shortcut: a live owned parent and an immutable revision binding are mandatory. */
    public ZenflowMediaObjects.Pointer pointer(String mediaId){
        validateIds(List.of(mediaId));var owner=owner();
        var rows=jdbc.queryForList("SELECT DISTINCT a.id,a.sha256,a.actual_bytes,a.actual_mime,a.object_key FROM zf_media_asset a JOIN zf_media_binding b ON b.media_id=a.id AND b.tenant_id=a.tenant_id AND b.subject_type=a.subject_type AND b.owner_user_id=a.owner_user_id JOIN ai_capsule c ON b.target_type='NOTE' AND c.id=b.target_id AND c.tenant_id=b.tenant_id AND c.owner_user_id=b.owner_user_id AND c.owner_subject_type='APP_USER' JOIN zf_capsule_ext e ON e.capsule_id=c.id AND e.tenant_id=c.tenant_id AND e.owner_user_id=c.owner_user_id JOIN ai_capsule_revision r ON r.capsule_id=c.id AND r.tenant_id=c.tenant_id AND r.owner_user_id=c.owner_user_id AND r.owner_subject_type='APP_USER' AND r.revision_no=b.target_version WHERE a.id=? AND a.tenant_id=? AND a.subject_type='APP_USER' AND a.owner_user_id=? AND a.purpose='PRIVATE_NOTE' AND a.processing_state='READY' AND c.del_flag='0' AND c.status IN ('ACTIVE','ARCHIVED') AND e.delete_state='ACTIVE'",mediaId,owner.tenantId(),owner.ownerUserId());
        if(rows.size()!=1)throw missing();var row=rows.get(0);return new ZenflowMediaObjects.Pointer(UUID.fromString(mediaId),row.get("sha256").toString(),((Number)row.get("actual_bytes")).longValue(),row.get("actual_mime").toString(),UUID.fromString(row.get("object_key").toString()));
    }
    private void parent(CapsuleIdentity owner,String id,int revision,boolean current){
        if(id==null||!id.matches("[1-9][0-9]{0,18}")||revision<1)throw bad();
        var rows=jdbc.queryForList("SELECT c.id FROM ai_capsule c JOIN zf_capsule_ext e ON e.capsule_id=c.id AND e.tenant_id=c.tenant_id AND e.owner_user_id=c.owner_user_id JOIN ai_capsule_revision r ON r.capsule_id=c.id AND r.tenant_id=c.tenant_id AND r.owner_user_id=c.owner_user_id WHERE c.id=? AND c.tenant_id=? AND c.owner_subject_type='APP_USER' AND c.owner_user_id=? AND c.del_flag='0' AND c.status IN ('ACTIVE','ARCHIVED') AND e.delete_state='ACTIVE' AND r.owner_subject_type='APP_USER' AND r.revision_no=?"+(current?" AND r.id=c.current_revision_id":""),id,owner.tenantId(),owner.ownerUserId(),revision);
        if(rows.size()!=1)throw missing();
    }
    private CapsuleIdentity owner(){var owner=access.capsuleIdentity();if(owner==null||!"APP_USER".equals(owner.subjectType())||owner.tenantId()==null||owner.ownerUserId()==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");return owner;}
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"MEDIA_OR_PARENT_NOT_FOUND");}
    private static ResponseStatusException bad(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_NOTE_MEDIA");}
}
