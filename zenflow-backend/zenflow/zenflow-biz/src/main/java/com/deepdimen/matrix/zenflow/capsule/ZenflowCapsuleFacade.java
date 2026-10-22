package com.deepdimen.matrix.zenflow.capsule;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.capsule.api.dto.CapsuleCreateRequest;
import com.deepdimen.matrix.capsule.api.dto.CapsuleUpdateRequest;
import com.deepdimen.matrix.capsule.api.enums.CapsuleRecordType;
import com.deepdimen.matrix.capsule.api.enums.CapsuleStatus;
import com.deepdimen.matrix.capsule.api.vo.CapsuleDetailVO;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.capsule.service.CapsuleService;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Consumer extension only; text and revisions remain exclusively in CapsuleService. */
@Service
public class ZenflowCapsuleFacade {
    private final CapsuleService capsules;
    private final JdbcTemplate jdbc;
    private final ZenflowConsumerAccess access;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private com.deepdimen.matrix.zenflow.media.ZenflowNoteMedia media;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setMedia(com.deepdimen.matrix.zenflow.media.ZenflowNoteMedia media){this.media=media;}
    public ZenflowCapsuleFacade(CapsuleService capsules,JdbcTemplate jdbc,ZenflowConsumerAccess access,PlatformTransactionManager manager,ObjectMapper json) {
        this.capsules=capsules;this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);this.json=json;
    }
    public Map<String,Object> create(Map<String,Object> body) {
        fields(body,Set.of("clientRecordId","kind","title","text","tags","mediaIds","capturedAt","sourceMessageId"));
        var identity=access.capsuleIdentity();String client=text(body,"clientRecordId",64,true);
        if(!client.matches("[A-Za-z0-9_-]{8,64}"))bad("INVALID_CLIENT_RECORD_ID");
        String kind=body.containsKey("kind")?text(body,"kind",24,true):"TEXT";
        if(!Set.of("TEXT","VOICE","CHAT_EXCERPT").contains(kind))bad("NOTE_KIND_UNAVAILABLE");
        List<String> mediaIds=mediaIds(body);
        if(kind.equals("VOICE")&&mediaIds.isEmpty())bad("VOICE_MEDIA_REQUIRED");
        String title=text(body,"title",120,false),content=text(body,"text",50000,mediaIds.isEmpty()||kind.equals("CHAT_EXCERPT")),tags=tags(body.get("tags"));
        var captured=body.get("capturedAt")==null?null:parseTime(body.get("capturedAt"));
        String source=kind.equals("CHAT_EXCERPT")?text(body,"sourceMessageId",64,true):null;
        if(!kind.equals("CHAT_EXCERPT") && body.containsKey("sourceMessageId"))bad("INVALID_NOTE_SOURCE");
        String hash=digest(json.valueToTree(new java.util.TreeMap<>(body)).toString());
        return tx.execute(ignored->{
            lock(identity);
            var prior=jdbc.queryForList("SELECT capsule_id,request_hash,delete_state FROM zf_capsule_ext WHERE tenant_id=? AND owner_user_id=? AND client_record_id=?",identity.tenantId(),identity.ownerUserId(),client);
            if(!prior.isEmpty()) {if(!hash.equals(prior.get(0).get("request_hash")))conflict("IDEMPOTENCY_CONFLICT");return detail(identity,((Number)prior.get(0).get("capsule_id")).longValue());}
            String sourceRole=null;
            if(source!=null) {
                var rows=jdbc.queryForList("SELECT m.role,m.content,m.status FROM ai_assistant_message m JOIN ai_assistant_session s ON s.id=m.session_id JOIN zf_conversation_binding b ON b.session_id=s.id WHERE m.id=? AND s.tenant_id=? AND s.owner_user_id=? AND s.owner_subject_type='APP_USER' AND b.deleted_at IS NULL",source,identity.tenantId(),identity.ownerUserId());
                if(rows.size()!=1 || !Set.of("USER","ASSISTANT").contains(rows.get(0).get("role")) || !"COMPLETED".equals(rows.get(0).get("status")) || !rows.get(0).get("content").toString().contains(content))bad("INVALID_NOTE_SOURCE");
                sourceRole=rows.get(0).get("role").toString();
            }
            var request=new CapsuleCreateRequest();request.setTitle(title);request.setContent(content);request.setRecordType(CapsuleRecordType.OTHER);request.setTags(tags);request.setSourceLabel("Invelle private record");request.setCapturedAt(captured);
            var result=capsules.create(identity,request);
            jdbc.update("INSERT INTO zf_capsule_ext(capsule_id,tenant_id,owner_user_id,kind,text_state,client_record_id,request_hash) VALUES(?,?,?,?,?,?,?)",result.getId(),identity.tenantId(),identity.ownerUserId(),kind,content.isBlank()?"EMPTY":"CONFIRMED",client,hash);
            jdbc.update("INSERT INTO zf_capsule_revision_source(capsule_revision_id,tenant_id,owner_user_id,capsule_id,source_kind,source_message_id,author_role) VALUES(?,?,?,?,?,?,?)",result.getCurrentRevisionId(),identity.tenantId(),identity.ownerUserId(),result.getId(),source==null?"MANUAL":"CHAT_EXCERPT",source,sourceRole==null?"USER":sourceRole);
            if(!mediaIds.isEmpty())media.bind(identity,result.getId().toString(),result.getRevisionNo(),mediaIds);
            return detail(identity,result.getId());
        });
    }
    public Map<String,Object> list(String cursor,int limit,String kind,String status,Boolean favorite,String keyword) {
        var identity=access.capsuleIdentity();int size=Math.min(50,Math.max(1,limit));
        var args=new ArrayList<Object>(List.of(identity.tenantId(),identity.ownerUserId()));
        var sql=new StringBuilder("SELECT c.id,e.pinned FROM ai_capsule c JOIN zf_capsule_ext e ON e.capsule_id=c.id AND e.tenant_id=c.tenant_id AND e.owner_user_id=c.owner_user_id WHERE c.tenant_id=? AND c.owner_user_id=? AND c.owner_subject_type='APP_USER' AND c.del_flag='0' AND e.delete_state='ACTIVE'");
        if(cursor!=null && !cursor.isBlank()) {
            var before=noteCursor(cursor);
            sql.append(" AND (e.pinned<? OR (e.pinned=? AND c.id<?))");
            args.add(before.pinned());args.add(before.pinned());args.add(before.id());
        }
        if(kind!=null){if(!Set.of("TEXT","VOICE","CHAT_EXCERPT").contains(kind))bad("INVALID_NOTE_KIND");sql.append(" AND e.kind=?");args.add(kind);}
        if(status!=null){if(!Set.of("ACTIVE","ARCHIVED").contains(status))bad("INVALID_NOTE_STATUS");sql.append(" AND c.status=?");args.add(status);}
        if(favorite!=null){sql.append(" AND c.favorite_flag=?");args.add(favorite?"1":"0");}
        if(keyword!=null && !keyword.isBlank()){if(keyword.length()>120)bad("INVALID_KEYWORD");sql.append(" AND LOCATE(?,c.title)>0");args.add(keyword);}
        sql.append(" ORDER BY e.pinned DESC,c.id DESC LIMIT ?");args.add(size+1);
        var rows=jdbc.queryForList(sql.toString(),args.toArray());boolean more=rows.size()>size;
        var page=rows.stream().limit(size).toList();
        var items=page.stream().map(row->detail(identity,((Number)row.get("id")).longValue())).toList();
        String next=more?noteCursor(flag(page.get(size-1).get("pinned"))?1:0,((Number)page.get(size-1).get("id")).longValue()):"";
        return Map.of("items",items,"hasMore",more,"nextCursor",next);
    }
    public Map<String,Object> detail(String key){return detail(access.capsuleIdentity(),id(key));}
    private Map<String,Object> detail(CapsuleIdentity identity,long key) {
        var ext=extension(identity,key);var note=capsules.detail(identity,key);
        var result=new LinkedHashMap<String,Object>();result.put("id",note.getId().toString());result.put("version",note.getVersion());result.put("currentRevisionId",note.getCurrentRevisionId().toString());result.put("revisionNo",note.getRevisionNo());result.put("kind",ext.get("kind"));result.put("textState",ext.get("text_state"));result.put("title",note.getTitle());result.put("text",note.getContent());result.put("tags",note.getTags()==null || note.getTags().isBlank()?List.of():Arrays.asList(note.getTags().split(",")));result.put("status",note.getStatus().name());result.put("favorite","1".equals(note.getFavoriteFlag()));result.put("pinned",flag(ext.get("pinned")));result.put("media",media==null?List.of():media.list(note.getId().toString(),note.getRevisionNo()));
        result.put("createdAt",utc(note.getCreateTime()));result.put("updatedAt",utc(note.getUpdateTime()));result.put("capturedAt",utc(note.getCapturedAt()));
        result.put("allowedScopes",consents(identity,key).stream().filter(c->"ACTIVE".equals(c.get("state")) && note.getCurrentRevisionId().toString().equals(c.get("revisionId"))).toList());return result;
    }
    public Map<String,Object> patch(String key,Map<String,Object> body) {
        fields(body,Set.of("title","text","tags","mediaIds","expectedVersion"));var identity=access.capsuleIdentity();long noteId=id(key);int expected=version(body,"expectedVersion");
        List<String> requestedMedia=body.containsKey("mediaIds")?mediaIds(body):null;
        String title=body.containsKey("title")?text(body,"title",120,false):null;String content=body.containsKey("text")?text(body,"text",50000,false):null;String tags=body.containsKey("tags")?tags(body.get("tags")):null;
        return tx.execute(ignored->{lock(identity);extension(identity,noteId);var current=capsules.detail(identity,noteId);if(current.getVersion()!=expected)conflict("VERSION_CONFLICT");
            List<String> previousMedia=media==null?List.of():media.ids(key,current.getRevisionNo());
            List<String> nextMedia=requestedMedia==null?previousMedia:requestedMedia;
            String nextContent=content==null?current.getContent():content;
            if(nextContent.isBlank()&&nextMedia.isEmpty())bad("EMPTY_NOTE");
            if(!nextContent.equals(current.getContent())||!nextMedia.equals(previousMedia)) {
                var request=new CapsuleUpdateRequest();request.setVersion(expected);request.setTitle(title==null?current.getTitle():title);request.setContent(nextContent);request.setTags(tags==null?current.getTags():tags);request.setRecordType(current.getRecordType());request.setSourceLabel(current.getSourceLabel());var updated=capsules.update(identity,noteId,request);if(media!=null&&!nextMedia.isEmpty())media.bind(identity,key,updated.getRevisionNo(),nextMedia);jdbc.update("INSERT INTO zf_capsule_revision_source(capsule_revision_id,tenant_id,owner_user_id,capsule_id,source_kind,author_role) VALUES(?,?,?,?,'MANUAL','USER')",updated.getCurrentRevisionId(),identity.tenantId(),identity.ownerUserId(),noteId);invalidate(identity,noteId,"STALE");
            } else capsules.updateMetadata(identity,noteId,title,tags,null,expected);
            jdbc.update("UPDATE zf_capsule_ext SET text_state=?,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE capsule_id=? AND tenant_id=? AND owner_user_id=?",nextContent.isBlank()?"EMPTY":"CONFIRMED",noteId,identity.tenantId(),identity.ownerUserId());return detail(identity,noteId);
        });
    }
    public Map<String,Object> state(String key,Map<String,Object> body) {
        fields(body,Set.of("archived","favorite","pinned","expectedVersion"));var identity=access.capsuleIdentity();long noteId=id(key);int expected=version(body,"expectedVersion");
        Boolean archived=bool(body,"archived"),favorite=bool(body,"favorite"),pinned=bool(body,"pinned");
        if(archived==null && favorite==null && pinned==null)bad("EMPTY_NOTE_UPDATE");
        return tx.execute(ignored->{lock(identity);extension(identity,noteId);var current=capsules.detail(identity,noteId);if(current.getVersion()!=expected)conflict("VERSION_CONFLICT");
            int revision=expected;
            if(archived!=null){capsules.changeStatus(identity,noteId,revision++,archived?CapsuleStatus.ARCHIVED:CapsuleStatus.ACTIVE);if(archived)invalidate(identity,noteId,"STALE");}
            if(favorite!=null)capsules.updateMetadata(identity,noteId,null,null,favorite,revision++);
            if(pinned!=null){capsules.updateMetadata(identity,noteId,null,null,null,revision++);jdbc.update("UPDATE zf_capsule_ext SET pinned=? WHERE capsule_id=? AND tenant_id=? AND owner_user_id=?",pinned?1:0,noteId,identity.tenantId(),identity.ownerUserId());}
            jdbc.update("UPDATE zf_capsule_ext SET version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE capsule_id=? AND tenant_id=? AND owner_user_id=?",noteId,identity.tenantId(),identity.ownerUserId());return detail(identity,noteId);
        });
    }
    public Map<String,Object> delete(String key,int expected) {
        var identity=access.capsuleIdentity();long noteId=id(key);
        return tx.execute(ignored->{lock(identity);var rows=jdbc.queryForList("SELECT delete_state FROM zf_capsule_ext WHERE capsule_id=? AND tenant_id=? AND owner_user_id=?",noteId,identity.tenantId(),identity.ownerUserId());if(rows.isEmpty())missing();
            if(!"ACTIVE".equals(rows.get(0).get("delete_state")))return Map.of("id",key,"deleteState",rows.get(0).get("delete_state"));
            capsules.trash(identity,noteId,expected);invalidate(identity,noteId,"REVOKED");
            jdbc.update("UPDATE zf_capsule_ext SET delete_state='DELETE_REQUESTED',deleted_at=UTC_TIMESTAMP(3),version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE capsule_id=? AND tenant_id=? AND owner_user_id=?",noteId,identity.tenantId(),identity.ownerUserId());
            jdbc.update("INSERT INTO zf_outbox(id,tenant_id,event_type,aggregate_type,aggregate_id,dedupe_key,payload_json,next_attempt_at) VALUES(?,?,'PRIVATE_NOTE_DELETE','CAPSULE',?,?,?,UTC_TIMESTAMP(3))",IdWorker.getId(),identity.tenantId(),key,digest("note-delete:"+key),"{\"ownerUserId\":\""+identity.ownerUserId()+"\"}");return Map.of("id",key,"deleteState","DELETE_REQUESTED");
        });
    }
    public Map<String,Object> delete(String key,int expected,String idempotencyKey) {
        if(expected<1)bad("EXPECTED_VERSION_REQUIRED");
        return receipt("NOTE_DELETE",idempotencyKey,Map.of("id",key,"expectedVersion",expected),()->delete(key,expected));
    }
    public Map<String,Object> versions(String key,String cursor,int limit) {
        var identity=access.capsuleIdentity();long noteId=id(key);extension(identity,noteId);int size=Math.min(50,Math.max(1,limit));
        Long parsedCursor=cursor==null || cursor.isBlank()?null:id(cursor);if(parsedCursor!=null && parsedCursor>Integer.MAX_VALUE)bad("INVALID_CURSOR");Integer before=parsedCursor==null?null:parsedCursor.intValue();
        var found=capsules.versions(identity,noteId,before,size+1);boolean more=found.size()>size;
        var items=found.stream().limit(size).map(v->{var m=new LinkedHashMap<String,Object>();m.put("id",v.getId().toString());m.put("revisionNo",v.getRevisionNo());m.put("text",v.getContent());m.put("changeReason",v.getChangeReason());m.put("createdAt",utc(v.getCreateTime()));
            var source=jdbc.queryForList("SELECT source_kind,author_role,source_message_id FROM zf_capsule_revision_source WHERE capsule_revision_id=? AND tenant_id=? AND owner_user_id=?",v.getId(),identity.tenantId(),identity.ownerUserId());
            m.put("sourceKind",source.isEmpty()?"MANUAL":source.get(0).get("source_kind"));m.put("authorRole",source.isEmpty()?"USER":source.get(0).get("author_role"));return (Map<String,Object>)m;}).toList();
        return Map.of("items",items,"hasMore",more,"nextCursor",more?found.get(size-1).getRevisionNo().toString():"");
    }
    public List<Map<String,Object>> consents(String key){var identity=access.capsuleIdentity();long noteId=id(key);extension(identity,noteId);return consents(identity,noteId);}
    private List<Map<String,Object>> consents(CapsuleIdentity identity,long noteId) {
        return jdbc.queryForList("SELECT id,capsule_revision_id,scope_type,scope_key,state,version FROM zf_capsule_consent WHERE tenant_id=? AND owner_user_id=? AND capsule_id=? ORDER BY id DESC",identity.tenantId(),identity.ownerUserId(),noteId).stream().map(c->Map.<String,Object>of("id",c.get("id").toString(),"revisionId",c.get("capsule_revision_id").toString(),"scope",c.get("scope_type"),"scopeKey",c.get("scope_key"),"state",c.get("state"),"version",c.get("version"))).toList();
    }
    public Map<String,Object> grant(String key,Map<String,Object> body) {
        fields(body,Set.of("revisionId","scope","sessionId","expectedVersion"));var identity=access.capsuleIdentity();long noteId=id(key),revisionId=id(text(body,"revisionId",64,true));int expected=version(body,"expectedVersion");String scope=text(body,"scope",16,true);
        if(!Set.of("SESSION","PERSISTENT").contains(scope))bad("INVALID_CONSENT_SCOPE");String scopeKey=scope.equals("PERSISTENT")?"*":text(body,"sessionId",64,true);
        if(scope.equals("PERSISTENT") && body.containsKey("sessionId"))bad("INVALID_CONSENT_SCOPE");
        return tx.execute(ignored->{lock(identity);extension(identity,noteId);var current=capsules.detail(identity,noteId);if(current.getVersion()!=expected)conflict("VERSION_CONFLICT");if(current.getCurrentRevisionId()!=revisionId || current.getStatus()!=CapsuleStatus.ACTIVE)conflict("NOTE_REVISION_UNAVAILABLE");
            if(scope.equals("SESSION") && jdbc.queryForList("SELECT b.session_id FROM zf_conversation_binding b JOIN ai_assistant_session s ON s.id=b.session_id AND s.tenant_id=b.tenant_id AND s.owner_user_id=b.owner_user_id WHERE b.session_id=? AND b.tenant_id=? AND b.owner_user_id=? AND s.owner_subject_type='APP_USER' AND b.deleted_at IS NULL",scopeKey,identity.tenantId(),identity.ownerUserId()).isEmpty())missing();
            var existing=jdbc.queryForList("SELECT id,state FROM zf_capsule_consent WHERE tenant_id=? AND owner_user_id=? AND capsule_revision_id=? AND scope_type=? AND scope_key=? FOR UPDATE",identity.tenantId(),identity.ownerUserId(),revisionId,scope,scopeKey);
            if(!existing.isEmpty() && "ACTIVE".equals(existing.get(0).get("state")))return consents(identity,noteId).stream().filter(c->existing.get(0).get("id").toString().equals(c.get("id"))).findFirst().orElseThrow();
            if(!existing.isEmpty())invalidateConsentSources(identity,((Number)existing.get(0).get("id")).longValue());
            long consentId=IdWorker.getId();
            jdbc.update("INSERT INTO zf_capsule_consent(id,tenant_id,owner_user_id,capsule_id,capsule_revision_id,source_hash,scope_type,scope_key) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE state='ACTIVE',revoked_at=NULL,version=version+1,updated_at=UTC_TIMESTAMP(3)",consentId,identity.tenantId(),identity.ownerUserId(),noteId,revisionId,current.getContentHash(),scope,scopeKey);
            epoch(identity);return consents(identity,noteId).stream().filter(c->revisionId==Long.parseLong(c.get("revisionId").toString()) && scopeKey.equals(c.get("scopeKey")) && scope.equals(c.get("scope"))).findFirst().orElseThrow();
        });
    }
    public Map<String,Object> revoke(String key,String consentKey,int expectedVersion) {
        var identity=access.capsuleIdentity();long noteId=id(key),consentId=id(consentKey);
        return tx.execute(ignored->{lock(identity);extension(identity,noteId);var rows=jdbc.queryForList("SELECT state,version FROM zf_capsule_consent WHERE id=? AND tenant_id=? AND owner_user_id=? AND capsule_id=? FOR UPDATE",consentId,identity.tenantId(),identity.ownerUserId(),noteId);if(rows.size()!=1)missing();
            if("REVOKED".equals(rows.get(0).get("state")))return Map.of("id",consentKey,"state","REVOKED");
            if(((Number)rows.get(0).get("version")).intValue()!=expectedVersion)conflict("VERSION_CONFLICT");
            jdbc.update("UPDATE zf_capsule_consent SET state='REVOKED',version=version+1,revoked_at=UTC_TIMESTAMP(3),updated_at=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=? AND owner_user_id=?",consentId,identity.tenantId(),identity.ownerUserId());
            jdbc.update("UPDATE zf_memory SET state='REVOKED',version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE consent_id=? AND tenant_id=? AND owner_user_id=?",consentId,identity.tenantId(),identity.ownerUserId());
            invalidateConsentSources(identity,consentId);epoch(identity);return Map.of("id",consentKey,"state","REVOKED","contextEpoch",contextEpoch(identity));
        });
    }
    public Map<String,Object> revoke(String key,String consentKey,int expectedVersion,String idempotencyKey) {
        if(expectedVersion<1)bad("EXPECTED_VERSION_REQUIRED");
        return receipt("CONSENT_REVOKE",idempotencyKey,Map.of("id",key,"consentId",consentKey,"expectedVersion",expectedVersion),()->revoke(key,consentKey,expectedVersion));
    }
    private Map<String,Object> receipt(String operation,String key,Map<String,Object> input,java.util.function.Supplier<Map<String,Object>> action) {
        return new ZenflowPrivateCommands(jdbc,tx,json).execute(access.capsuleIdentity(),operation,key,input,"CAPSULE",action);
    }
    private Map<String,Object> extension(CapsuleIdentity identity,long noteId) {
        var rows=jdbc.queryForList("SELECT kind,text_state,pinned FROM zf_capsule_ext WHERE capsule_id=? AND tenant_id=? AND owner_user_id=? AND delete_state='ACTIVE'",noteId,identity.tenantId(),identity.ownerUserId());if(rows.size()!=1)missing();return rows.get(0);
    }
    private record NoteCursor(int pinned,long id) {}
    private static boolean flag(Object value){return value instanceof Boolean bool?bool:value instanceof Number number && number.intValue()!=0;}
    private static NoteCursor noteCursor(String value) {
        try {
            if(value.matches("\\d+")){long noteId=Long.parseLong(value);if(noteId<=0)throw new IllegalArgumentException();return new NoteCursor(0,noteId);}
            String[] parts=value.split(":",-1);
            if(parts.length!=2 || !Set.of("0","1").contains(parts[0]))throw new IllegalArgumentException();
            long noteId=Long.parseLong(parts[1]);if(noteId<=0)throw new IllegalArgumentException();
            return new NoteCursor(Integer.parseInt(parts[0]),noteId);
        } catch(Exception error) {bad("INVALID_CURSOR");return null;}
    }
    private static String noteCursor(int pinned,long noteId){return pinned+":"+noteId;}
    private void lock(CapsuleIdentity identity){if(jdbc.queryForList("SELECT owner_user_id FROM zf_profile WHERE tenant_id=? AND owner_user_id=? FOR UPDATE",identity.tenantId(),identity.ownerUserId()).size()!=1)missing();}
    private void epoch(CapsuleIdentity identity){jdbc.update("UPDATE zf_profile SET context_epoch=context_epoch+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND owner_user_id=?",identity.tenantId(),identity.ownerUserId());}
    private long contextEpoch(CapsuleIdentity identity){return jdbc.queryForObject("SELECT context_epoch FROM zf_profile WHERE tenant_id=? AND owner_user_id=?",Long.class,identity.tenantId(),identity.ownerUserId());}
    private void invalidate(CapsuleIdentity identity,long noteId,String state) {
        jdbc.update("UPDATE zf_capsule_consent SET state=?,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND owner_user_id=? AND capsule_id=? AND state='ACTIVE'",state,identity.tenantId(),identity.ownerUserId(),noteId);
        jdbc.update("UPDATE zf_memory SET state=?,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND owner_user_id=? AND capsule_id=? AND state IN ('ACTIVE','PAUSED')",state,identity.tenantId(),identity.ownerUserId(),noteId);
        invalidateSources(identity,noteId);epoch(identity);
    }
    private void invalidateConsentSources(CapsuleIdentity identity,long consentId) {
        jdbc.update("UPDATE zf_companion_run_context SET lineage_state='INVALIDATED',updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND owner_user_id=? AND run_id IN (SELECT run_id FROM zf_run_source WHERE tenant_id=? AND owner_user_id=? AND consent_id=?)",identity.tenantId(),identity.ownerUserId(),identity.tenantId(),identity.ownerUserId(),consentId);
        jdbc.update("UPDATE zf_run_source SET status='INVALIDATED',updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND owner_user_id=? AND consent_id=?",identity.tenantId(),identity.ownerUserId(),consentId);
    }
    private void invalidateSources(CapsuleIdentity identity,long noteId) {
        jdbc.update("UPDATE zf_companion_run_context SET lineage_state='INVALIDATED',updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND owner_user_id=? AND run_id IN (SELECT run_id FROM zf_run_source WHERE tenant_id=? AND owner_user_id=? AND capsule_id=?)",identity.tenantId(),identity.ownerUserId(),identity.tenantId(),identity.ownerUserId(),noteId);
        jdbc.update("UPDATE zf_run_source SET status='INVALIDATED',updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND owner_user_id=? AND capsule_id=?",identity.tenantId(),identity.ownerUserId(),noteId);
    }
    private List<String> mediaIds(Map<String,Object> body){
        if(!body.containsKey("mediaIds"))return List.of();
        if(media!=null)return media.validateIds(body.get("mediaIds"));
        if(!(body.get("mediaIds") instanceof List<?> ids)||!ids.isEmpty())bad("VOICE_UNAVAILABLE");
        return List.of();
    }
    static String digest(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalStateException("HASH_UNAVAILABLE");}}
    static long id(String value){try{long id=Long.parseLong(value);if(id<=0)throw new IllegalArgumentException();return id;}catch(Exception error){bad("INVALID_ID");return 0;}}
    static int version(Map<String,Object> body,String key){if(!(body.get(key) instanceof Number n) || n.doubleValue()!=n.intValue() || n.intValue()<1)bad("EXPECTED_VERSION_REQUIRED");return ((Number)body.get(key)).intValue();}
    static void fields(Map<String,Object> body,Set<String> keys){if(!keys.containsAll(body.keySet()))bad("UNKNOWN_FIELD");}
    static String text(Map<String,Object> body,String key,int max,boolean required){Object value=body.get(key);if(value==null && !required)return "";if(!(value instanceof String s) || s.length()>max || (required && s.isBlank()))bad("INVALID_"+key.toUpperCase(java.util.Locale.ROOT));return ((String)value).trim();}
    private static String tags(Object value){if(value==null)return "";if(!(value instanceof List<?> list) || list.size()>20)bad("INVALID_TAGS");var list=(List<?>)value;for(Object tag:list)if(!(tag instanceof String s) || s.isBlank() || s.length()>30 || s.contains(","))bad("INVALID_TAGS");return String.join(",",list.stream().map(Object::toString).map(String::trim).distinct().toList());}
    private static Boolean bool(Map<String,Object> body,String key){if(!body.containsKey(key))return null;if(!(body.get(key) instanceof Boolean))bad("INVALID_STATE");return (Boolean)body.get(key);}
    private static java.time.LocalDateTime parseTime(Object value){try{var time=OffsetDateTime.parse(value.toString());if(time.isAfter(OffsetDateTime.now().plusMinutes(5)) || time.getYear()<1970)throw new IllegalArgumentException();return time.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();}catch(Exception error){bad("INVALID_CAPTURED_AT");return null;}}
    private static String utc(java.time.LocalDateTime value){return value==null?"":value.atOffset(ZoneOffset.UTC).toString();}
    static void bad(String code){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,code);}
    static void conflict(String code){throw new ResponseStatusException(HttpStatus.CONFLICT,code);}
    static void missing(){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"NOTE_NOT_FOUND");}
}
