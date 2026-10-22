package com.deepdimen.matrix.zenflow.conversation;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.*;
import com.deepdimen.matrix.assistant.session.AssistantSessionService;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import java.util.*;
import com.deepdimen.matrix.assistant.run.AssistantRunEngine;
import com.deepdimen.matrix.zenflow.companion.ZenflowRunAuthorization;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Product ownership boundary over the existing private assistant session and message stores. */
@Service
public class ZenflowConversationFacade {
    private final JdbcTemplate jdbc;
    private final ZenflowConsumerAccess access;
    private final AssistantSessionService sessions;
    private final TransactionTemplate tx;
    private final AssistantRunEngine engine;
    private final ZenflowConversationSources sources;
    private final ObjectMapper json;
    public ZenflowConversationFacade(JdbcTemplate jdbc,ZenflowConsumerAccess access,AssistantSessionService sessions,PlatformTransactionManager manager,AssistantRunEngine engine,ZenflowConversationSources sources,ObjectMapper json) {
        this.jdbc=jdbc;this.access=access;this.sessions=sessions;this.tx=new TransactionTemplate(manager);this.engine=engine;this.sources=sources;this.json=json;
    }
    IdentityEnvelope identity(){return access.identity(access.require(),UUID.randomUUID().toString(),"TEXT_CONVERSATION");}
    Map<String,Object> owned(IdentityEnvelope identity,String id,boolean lock) {
        if(id==null || !id.matches("[A-Za-z0-9_-]{1,64}"))throw error(HttpStatus.NOT_FOUND,"CONVERSATION_NOT_FOUND");
        var found=jdbc.queryForList("SELECT b.*,a.title,a.status,a.last_message_at FROM zf_conversation_binding b JOIN ai_assistant_session a ON a.id=b.session_id AND a.tenant_id=b.tenant_id AND a.owner_user_id=b.owner_user_id AND a.owner_subject_type='APP_USER' WHERE b.session_id=? AND b.tenant_id=? AND b.owner_user_id=? AND b.deleted_at IS NULL"+(lock?" FOR UPDATE":""),id,identity.tenantId(),identity.userId());
        if(found.size()!=1)throw error(HttpStatus.NOT_FOUND,"CONVERSATION_NOT_FOUND");return found.get(0);
    }
    public Map<String,Object> create(Map<String,Object> body) {
        fields(body,Set.of("title","locale"));String title=body.containsKey("title")?text(body.get("title"),80):"新对话";
        String locale=body.containsKey("locale")?text(body.get("locale"),16):"en-US";
        if(!Set.of("en-US","zh-CN").contains(locale))throw error(HttpStatus.BAD_REQUEST,"INVALID_LOCALE");
        var identity=identity();return tx.execute(ignored->{
            var configs=jdbc.queryForList("SELECT id FROM zf_companion_config WHERE tenant_id=? AND config_key='default'",identity.tenantId());
            if(configs.size()!=1)throw error(HttpStatus.SERVICE_UNAVAILABLE,"COMPANION_NOT_CONFIGURED");
            var session=sessions.create(identity,new CreateSessionRequest(title,List.of(),null));
            jdbc.update("INSERT INTO zf_conversation_binding(session_id,tenant_id,owner_user_id,companion_config_id,locale) VALUES(?,?,?,?,?)",session.assistantSessionId(),identity.tenantId(),identity.userId(),configs.get(0).get("id"),locale);
            return view(owned(identity,session.assistantSessionId(),false));
        });
    }
    public Map<String,Object> detail(String id){return view(owned(identity(),id,false));}
    public Map<String,Object> list(String cursor,int limit,String status) {
        var identity=identity();int safe=Math.max(1,Math.min(limit,50));String state=status==null?"ACTIVE":status;
        if(!Set.of("ACTIVE","ARCHIVED").contains(state))throw error(HttpStatus.BAD_REQUEST,"INVALID_CONVERSATION_STATUS");
        var args=new ArrayList<Object>(List.of(identity.tenantId(),identity.userId(),state));String after="";
        if(cursor!=null && !cursor.isBlank()) {
            try {
                String[] decoded=new String(Base64.getUrlDecoder().decode(cursor),java.nio.charset.StandardCharsets.UTF_8).split("\\|",2);
                if(decoded.length!=2 || !decoded[1].matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException();
                var stamp=java.sql.Timestamp.valueOf(decoded[0]);after=" AND (b.updated_at<? OR (b.updated_at=? AND b.session_id<?))";args.add(stamp);args.add(stamp);args.add(decoded[1]);
            }catch(RuntimeException invalid){throw error(HttpStatus.BAD_REQUEST,"INVALID_CONVERSATION_CURSOR");}
        }
        args.add(safe+1);
        var found=jdbc.queryForList("SELECT b.*,a.title,a.status,a.last_message_at FROM zf_conversation_binding b JOIN ai_assistant_session a ON a.id=b.session_id AND a.tenant_id=b.tenant_id AND a.owner_user_id=b.owner_user_id AND a.owner_subject_type='APP_USER' WHERE b.tenant_id=? AND b.owner_user_id=? AND a.status=? AND b.deleted_at IS NULL"+after+" ORDER BY b.updated_at DESC,b.session_id DESC LIMIT ?",args.toArray());
        boolean more=found.size()>safe;var items=more?found.subList(0,safe):found;var result=new LinkedHashMap<String,Object>();result.put("items",items.stream().map(this::view).toList());result.put("hasMore",more);
        result.put("nextCursor",more?Base64.getUrlEncoder().withoutPadding().encodeToString((items.get(items.size()-1).get("updated_at")+"|"+items.get(items.size()-1).get("session_id")).getBytes(java.nio.charset.StandardCharsets.UTF_8)):null);return result;
    }
    public Map<String,Object> patch(String id,Map<String,Object> body) {
        fields(body,Set.of("title","status","expectedVersion"));long version=version(body);String title=body.containsKey("title")?text(body.get("title"),80):null;
        String status=body.containsKey("status")?text(body.get("status"),16):null;
        if(title==null && status==null)throw error(HttpStatus.BAD_REQUEST,"EMPTY_CONVERSATION_UPDATE");
        if(status!=null && !Set.of("ACTIVE","ARCHIVED").contains(status))throw error(HttpStatus.BAD_REQUEST,"INVALID_CONVERSATION_STATUS");
        var identity=identity();return tx.execute(ignored->{
            var current=owned(identity,id,true);if(((Number)current.get("version")).longValue()!=version)throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
            var session=sessions.get(identity,id);
            if(title!=null)session=sessions.updateTitle(identity,id,new UpdateSessionTitleRequest(title,session.version()));
            if(status!=null && !session.status().name().equals(status)) {
                if(status.equals("ARCHIVED"))sessions.archive(identity,id,new UpdateSessionStatusRequest(session.version()));
                else sessions.restore(identity,id,new UpdateSessionStatusRequest(session.version()));
            }
            jdbc.update("UPDATE zf_conversation_binding SET version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE session_id=? AND tenant_id=? AND owner_user_id=?",id,identity.tenantId(),identity.userId());
            return view(owned(identity,id,false));
        });
    }
    public AssistantMessagePage messages(String id,Long before,int limit) {
        var identity=identity();owned(identity,id,false);return sessions.messagePage(identity,id,before,Math.min(limit,50));
    }
    public Map<String,Object> send(String id,Map<String,Object> body) {
        fields(body,Set.of("clientMessageId","text","context"));String client=text(body.get("clientMessageId"),64),content=text(body.get("text"),8000);
        if(!client.matches("[A-Za-z0-9_-]{1,64}"))throw error(HttpStatus.BAD_REQUEST,"INVALID_CLIENT_MESSAGE_ID");
        var selected=sources.selections(body.get("context"));String digest=ZenflowRunAuthorization.hash(json.valueToTree(Map.of("text",content,"capsules",selected)).toString());
        var identity=identity();return tx.execute(ignored->{
            var profiles=jdbc.queryForList("SELECT p.*,u.auth_version FROM zf_profile p JOIN app_user u ON u.tenant_id=p.tenant_id AND u.user_id=p.owner_user_id WHERE p.tenant_id=? AND p.owner_user_id=? AND u.del_flag='0' AND u.lock_flag='0' FOR UPDATE",identity.tenantId(),identity.userId());
            if(profiles.size()!=1)throw error(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_CHANGED");var profile=profiles.get(0);
            var conversation=owned(identity,id,true);
            var existing=jdbc.queryForList("SELECT r.id,r.status,r.user_message_id,r.response_message_id,x.input_digest FROM ai_assistant_execution_run r JOIN zf_companion_run_context x ON x.run_id=r.id AND x.tenant_id=r.tenant_id WHERE r.session_id=? AND r.tenant_id=? AND x.owner_user_id=? AND r.client_message_id=?",id,identity.tenantId(),identity.userId(),client);
            if(!existing.isEmpty()) {
                var previous=existing.get(0);if(!digest.equals(previous.get("input_digest")))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT");
                return accepted(previous.get("id").toString(),Objects.toString(previous.get("user_message_id"),""),Objects.toString(previous.get("response_message_id"),""),previous.get("status").toString());
            }
            if(!"ACTIVE".equals(conversation.get("status")))throw error(HttpStatus.CONFLICT,"CONVERSATION_ARCHIVED");
            var busy=jdbc.queryForList("SELECT id FROM ai_assistant_execution_run WHERE session_id=? AND tenant_id=? AND status NOT IN ('COMPLETED','FAILED','CANCELLED') LIMIT 1",id,identity.tenantId());
            if(!busy.isEmpty())throw error(HttpStatus.CONFLICT,"CONVERSATION_BUSY");
            var configs=jdbc.queryForList("SELECT rev.id AS companion_revision_id,br.id AS binding_revision_id,rev.model_id,rev.model_config_digest FROM zf_companion_config c JOIN ai_skill_binding b ON b.id=c.skill_binding_id AND b.tenant_id=c.tenant_id JOIN ai_skill_binding_revision br ON br.id=b.active_revision_id AND br.binding_id=b.id AND br.tenant_id=b.tenant_id JOIN zf_companion_revision rev ON rev.config_id=c.id AND rev.tenant_id=c.tenant_id AND rev.skill_release_id=br.release_id AND CAST(JSON_UNQUOTE(JSON_EXTRACT(br.parameter_json,'$.companionRevisionId')) AS CHAR)=CAST(rev.id AS CHAR) JOIN ai_skill_release release_row ON release_row.id=rev.skill_release_id AND release_row.tenant_id=rev.tenant_id WHERE c.id=? AND c.tenant_id=? AND c.status='ENABLED' AND br.enabled=1 AND rev.state='READY' AND release_row.status='PUBLISHED' FOR UPDATE",conversation.get("companion_config_id"),identity.tenantId());
            if(configs.size()!=1)throw error(HttpStatus.SERVICE_UNAVAILABLE,"COMPANION_NOT_READY");var fixed=configs.get(0);
            Long history=jdbc.queryForObject("SELECT COALESCE(MAX(sequence_no),0) FROM ai_assistant_message WHERE session_id=?",Long.class,id);
            var session=sessions.get(identity,id);var run=engine.startOrGet(identity,session,client,content,List.of(),true,null,List.of(),null,null,null);
            var prefs=new LinkedHashMap<String,Object>();prefs.put("locale",conversation.get("locale"));
            try{prefs.put("companionPreferences",json.readTree(profile.get("companion_preferences").toString()));}catch(Exception invalid){throw new IllegalStateException("PRIVATE_PREFERENCES_INVALID");}
            jdbc.update("INSERT INTO zf_companion_run_context(run_id,tenant_id,owner_user_id,session_id,binding_revision_id,companion_revision_id,model_id,model_config_digest,history_end_seq,preferences_snapshot_json,context_epoch,auth_version,input_digest) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",run.runId(),identity.tenantId(),identity.userId(),id,fixed.get("binding_revision_id"),fixed.get("companion_revision_id"),fixed.get("model_id"),fixed.get("model_config_digest"),history,json.valueToTree(prefs).toString(),profile.get("context_epoch"),profile.get("auth_version"),digest);
            sources.register(identity,id,run.runId(),history==null?0:history,selected,((Number)profile.get("memory_enabled")).intValue()==1);
            jdbc.update("UPDATE zf_conversation_binding SET active_run_id=?,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE session_id=? AND tenant_id=? AND owner_user_id=?",run.runId(),id,identity.tenantId(),identity.userId());
            return accepted(run.runId(),run.userMessageId(),run.responseMessageId(),run.status().name());
        });
    }
    public String responseRun(String sessionId,String messageId) {
        var identity=identity();owned(identity,sessionId,false);
        var rows=jdbc.queryForList("SELECT run_id FROM zf_companion_run_context WHERE session_id=? AND response_message_id=? AND tenant_id=? AND owner_user_id=?",sessionId,messageId,identity.tenantId(),identity.userId());
        if(rows.size()!=1)throw error(HttpStatus.NOT_FOUND,"CITATION_UNAVAILABLE");return rows.get(0).get("run_id").toString();
    }
    public Object snapshot(String runId) {
        var identity=identity();String session=runSession(identity,runId);return sessions.runSnapshot(identity,session,runId);
    }
    public Runnable streamGuard(String runId) {
        var user=access.require();var identity=access.identity(user,UUID.randomUUID().toString(),"TEXT_CONVERSATION");
        runSession(identity,runId);return ()->{access.verify(user);runSession(identity,runId);};
    }
    public Object cancel(String runId,String key) {
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,64}"))throw error(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_KEY_REQUIRED");
        var identity=identity();return sessions.cancelRun(identity,runSession(identity,runId),runId);
    }
    String runSession(IdentityEnvelope identity,String runId) {
        var rows=jdbc.queryForList("SELECT session_id,lineage_state FROM zf_companion_run_context WHERE run_id=? AND tenant_id=? AND owner_user_id=?",runId,identity.tenantId(),identity.userId());
        if(rows.size()!=1)throw error(HttpStatus.NOT_FOUND,"RUN_NOT_FOUND");String session=rows.get(0).get("session_id").toString();owned(identity,session,false);if(!"VALID".equals(rows.get(0).get("lineage_state")))throw error(HttpStatus.FORBIDDEN,"PRIVATE_CONTEXT_CHANGED");return session;
    }
    public Map<String,Object> delete(String id,long expected,String key) {
        if(expected<1)throw error(HttpStatus.BAD_REQUEST,"EXPECTED_VERSION_REQUIRED");var identity=identity();
        var capsule=new com.deepdimen.matrix.capsule.security.CapsuleIdentity(Long.valueOf(identity.tenantId()),Long.valueOf(identity.userId()),"APP_USER");
        var commands=new com.deepdimen.matrix.zenflow.capsule.ZenflowPrivateCommands(jdbc,tx,json);
        return commands.execute(capsule,"CONVERSATION_DELETE",key,Map.of("id",id,"expectedVersion",expected),"CONVERSATION",()->{
            var current=owned(identity,id,true);if(((Number)current.get("version")).longValue()!=expected)throw error(HttpStatus.CONFLICT,"VERSION_CONFLICT");
            var pending=jdbc.queryForList("SELECT id FROM ai_assistant_execution_run WHERE session_id=? AND tenant_id=? AND status NOT IN ('COMPLETED','FAILED','CANCELLED')",id,identity.tenantId());
            for(var run:pending)engine.requestCancel(identity,id,run.get("id").toString());
            jdbc.update("UPDATE zf_conversation_binding SET deleted_at=UTC_TIMESTAMP(3),version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE session_id=? AND tenant_id=? AND owner_user_id=?",id,identity.tenantId(),identity.userId());
            jdbc.update("UPDATE zf_companion_run_context SET lineage_state='INVALIDATED',updated_at=UTC_TIMESTAMP(3) WHERE session_id=? AND tenant_id=? AND owner_user_id=?",id,identity.tenantId(),identity.userId());
            jdbc.update("UPDATE zf_run_source src JOIN zf_companion_run_context x ON x.run_id=src.run_id AND x.tenant_id=src.tenant_id AND x.owner_user_id=src.owner_user_id SET src.status='INVALIDATED',src.updated_at=UTC_TIMESTAMP(3) WHERE x.session_id=? AND x.tenant_id=? AND x.owner_user_id=?",id,identity.tenantId(),identity.userId());
            jdbc.update("UPDATE zf_capsule_consent g LEFT JOIN zf_companion_run_context x ON x.run_id=g.scope_key AND x.tenant_id=g.tenant_id AND x.owner_user_id=g.owner_user_id SET g.state='REVOKED',g.version=g.version+1,g.revoked_at=UTC_TIMESTAMP(3),g.updated_at=UTC_TIMESTAMP(3) WHERE g.tenant_id=? AND g.owner_user_id=? AND g.state='ACTIVE' AND ((g.scope_type='SESSION' AND g.scope_key=?) OR (g.scope_type='TURN' AND x.session_id=?))",identity.tenantId(),identity.userId(),id,id);
            jdbc.update("INSERT INTO zf_outbox(id,tenant_id,event_type,aggregate_type,aggregate_id,dedupe_key,payload_json,next_attempt_at) VALUES(?,?,'PRIVATE_CONVERSATION_DELETE','CONVERSATION',?,?,?,UTC_TIMESTAMP(3))",com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),identity.tenantId(),id,ZenflowRunAuthorization.hash("conversation-delete:"+id),json.valueToTree(Map.of("ownerUserId",identity.userId())).toString());
            return Map.of("id",id,"deleteState","DELETE_REQUESTED");
        });
    }
    private Map<String,Object> accepted(String run,String user,String response,String status){return Map.of("runId",run,"userMessageId",user,"responseMessageId",response,"status",status,"eventsUrl","/api/zenflow/v1/runs/"+run+"/events");}
    private Map<String,Object> view(Map<String,Object> row) {
        var value=new LinkedHashMap<String,Object>();value.put("id",row.get("session_id"));value.put("title",row.get("title"));value.put("status",row.get("status"));value.put("locale",row.get("locale"));value.put("version",row.get("version"));value.put("activeRunId",row.get("active_run_id"));value.put("lastMessageAt",row.get("last_message_at"));value.put("createdAt",row.get("created_at"));value.put("updatedAt",row.get("updated_at"));return value;
    }
    static void fields(Map<String,Object> body,Set<String> names){if(body==null || !names.containsAll(body.keySet()))throw error(HttpStatus.BAD_REQUEST,"INVALID_CONVERSATION_FIELDS");}
    static String text(Object value,int max){if(!(value instanceof String text) || text.isBlank() || text.codePointCount(0,text.length())>max)throw error(HttpStatus.BAD_REQUEST,"INVALID_CONVERSATION_TEXT");return ((String)value).trim();}
    static long version(Map<String,Object> body){if(!(body.get("expectedVersion") instanceof Number number) || number.longValue()<1 || number.doubleValue()!=number.longValue())throw error(HttpStatus.BAD_REQUEST,"EXPECTED_VERSION_REQUIRED");return ((Number)body.get("expectedVersion")).longValue();}
    static ResponseStatusException error(HttpStatus status,String code){return new ResponseStatusException(status,code);}
}
