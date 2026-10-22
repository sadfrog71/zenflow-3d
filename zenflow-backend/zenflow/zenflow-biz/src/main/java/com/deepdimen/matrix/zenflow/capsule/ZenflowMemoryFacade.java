package com.deepdimen.matrix.zenflow.capsule;

import static com.deepdimen.matrix.zenflow.capsule.ZenflowCapsuleFacade.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** User-confirmed summaries only; a valid persistent grant never implies automatic memory. */
@Service
public class ZenflowMemoryFacade {
    private final JdbcTemplate jdbc;
    private final ZenflowConsumerAccess access;
    private final TransactionTemplate tx;
    private final ZenflowPrivateCommands commands;
    public ZenflowMemoryFacade(JdbcTemplate jdbc,ZenflowConsumerAccess access,
            PlatformTransactionManager manager,ObjectMapper json) {
        this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);
        this.commands=new ZenflowPrivateCommands(jdbc,tx,json);
    }
    public Map<String,Object> list(String cursor,int limit) {
        var identity=access.capsuleIdentity();int size=Math.min(50,Math.max(1,limit));
        var args=new ArrayList<Object>();args.add(identity.tenantId());args.add(identity.ownerUserId());
        String predicate=cursor==null || cursor.isBlank()?"":" AND m.id<?";
        if(!predicate.isEmpty())args.add(id(cursor));args.add(size+1);
        var rows=jdbc.queryForList("SELECT m.* FROM zf_memory m JOIN zf_capsule_ext e ON e.capsule_id=m.capsule_id "
                + "AND e.tenant_id=m.tenant_id AND e.owner_user_id=m.owner_user_id "
                + "WHERE m.tenant_id=? AND m.owner_user_id=? AND e.delete_state='ACTIVE'"
                + predicate + " ORDER BY m.id DESC LIMIT ?",args.toArray());
        boolean more=rows.size()>size;
        return Map.of("items",rows.stream().limit(size).map(this::dto).toList(),"hasMore",more,
                "nextCursor",more?rows.get(size-1).get("id").toString():"");
    }
    public Map<String,Object> create(Map<String,Object> body,String key) {
        fields(body,Set.of("consentId","summary"));var identity=access.capsuleIdentity();
        long consent=id(text(body,"consentId",64,true));String summary=text(body,"summary",2000,true);
        var result=commands.execute(identity,"MEMORY_CONFIRM",key,body,"MEMORY",()->{
            var source=validConsent(identity,consent);
            var prior=jdbc.queryForList("SELECT id,state,summary,version FROM zf_memory "
                    + "WHERE tenant_id=? AND owner_user_id=? AND consent_id=? FOR UPDATE",
                    identity.tenantId(),identity.ownerUserId(),consent);
            long memory;
            if(prior.isEmpty()) {
                memory=IdWorker.getId();jdbc.update("INSERT INTO zf_memory(id,tenant_id,owner_user_id, "
                        + "consent_id,capsule_id,capsule_revision_id,summary,state) VALUES(?,?,?,?,?,?,?,'ACTIVE')",
                        memory,identity.tenantId(),identity.ownerUserId(),consent,source.get("capsule_id"),
                        source.get("capsule_revision_id"),summary);
            } else {
                memory=((Number)prior.get(0).get("id")).longValue();
                if(!"REVOKED".equals(prior.get(0).get("state")))conflict("MEMORY_ALREADY_EXISTS");
                jdbc.update("UPDATE zf_memory SET summary=?,state='ACTIVE',version=version+1,updated_at=UTC_TIMESTAMP(3) "
                        + "WHERE id=? AND tenant_id=? AND owner_user_id=?",summary,memory,identity.tenantId(),identity.ownerUserId());
            }
            invalidate(identity,memory);
            return Map.of("id",Long.toString(memory));
        });
        return detail(identity,id(result.get("id").toString()));
    }
    public Map<String,Object> patch(String key,Map<String,Object> body) {
        fields(body,Set.of("summary","state","expectedVersion"));var identity=access.capsuleIdentity();
        long memory=id(key);int expected=version(body,"expectedVersion");
        String summary=body.containsKey("summary")?text(body,"summary",2000,true):null;
        String state=body.containsKey("state")?text(body,"state",16,true):null;
        if(state!=null && !Set.of("ACTIVE","PAUSED").contains(state))bad("INVALID_MEMORY_STATE");
        if(summary==null && state==null)bad("EMPTY_MEMORY_UPDATE");
        return tx.execute(ignored->{
            commands.lock(identity);var current=owned(identity,memory);
            if(((Number)current.get("version")).intValue()!=expected)conflict("VERSION_CONFLICT");
            if(!Set.of("ACTIVE","PAUSED").contains(current.get("state")))conflict("MEMORY_SOURCE_UNAVAILABLE");
            validConsent(identity,((Number)current.get("consent_id")).longValue());
            int changed=jdbc.update("UPDATE zf_memory SET summary=?,state=?,version=version+1,updated_at=UTC_TIMESTAMP(3) "
                    + "WHERE id=? AND tenant_id=? AND owner_user_id=? AND version=?",
                    summary==null?current.get("summary"):summary,state==null?current.get("state"):state,
                    memory,identity.tenantId(),identity.ownerUserId(),expected);
            if(changed!=1)conflict("VERSION_CONFLICT");invalidate(identity,memory);
            return detail(identity,memory);
        });
    }
    public Map<String,Object> delete(String key,int expected,String commandKey) {
        if(expected<1)bad("EXPECTED_VERSION_REQUIRED");var identity=access.capsuleIdentity();long memory=id(key);
        return commands.execute(identity,"MEMORY_REVOKE",commandKey,Map.of("id",key,"expectedVersion",expected),"MEMORY",()->{
            var current=owned(identity,memory);
            if(!"REVOKED".equals(current.get("state"))) {
                if(((Number)current.get("version")).intValue()!=expected)conflict("VERSION_CONFLICT");
                jdbc.update("UPDATE zf_memory SET summary='',state='REVOKED',version=version+1,updated_at=UTC_TIMESTAMP(3) "
                        + "WHERE id=? AND tenant_id=? AND owner_user_id=?",memory,identity.tenantId(),identity.ownerUserId());
                invalidate(identity,memory);
            }
            return Map.of("id",key,"state","REVOKED");
        });
    }
    private Map<String,Object> validConsent(CapsuleIdentity identity,long consent) {
        var rows=jdbc.queryForList("SELECT g.capsule_id,g.capsule_revision_id FROM zf_capsule_consent g "
                + "JOIN ai_capsule c ON c.id=g.capsule_id AND c.tenant_id=g.tenant_id AND c.owner_user_id=g.owner_user_id "
                + "JOIN ai_capsule_revision r ON r.id=g.capsule_revision_id AND r.capsule_id=c.id "
                + "AND r.tenant_id=g.tenant_id AND r.owner_user_id=g.owner_user_id "
                + "JOIN zf_capsule_ext e ON e.capsule_id=c.id AND e.tenant_id=c.tenant_id AND e.owner_user_id=c.owner_user_id "
                + "WHERE g.id=? AND g.tenant_id=? AND g.owner_user_id=? AND g.scope_type='PERSISTENT' "
                + "AND g.scope_key='*' AND g.state='ACTIVE' AND c.current_revision_id=g.capsule_revision_id "
                + "AND c.owner_subject_type='APP_USER' AND r.owner_subject_type='APP_USER' AND c.del_flag='0' "
                + "AND c.status='ACTIVE' AND r.content_hash=g.source_hash AND e.delete_state='ACTIVE' "
                + "AND e.text_state='CONFIRMED' FOR UPDATE",consent,identity.tenantId(),identity.ownerUserId());
        if(rows.size()!=1)conflict("MEMORY_SOURCE_UNAVAILABLE");return rows.get(0);
    }
    private Map<String,Object> owned(CapsuleIdentity identity,long memory) {
        var rows=jdbc.queryForList("SELECT m.* FROM zf_memory m JOIN zf_capsule_ext e ON e.capsule_id=m.capsule_id "
                + "AND e.tenant_id=m.tenant_id AND e.owner_user_id=m.owner_user_id "
                + "WHERE m.id=? AND m.tenant_id=? AND m.owner_user_id=? AND e.delete_state='ACTIVE'",
                memory,identity.tenantId(),identity.ownerUserId());
        if(rows.size()!=1)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"MEMORY_NOT_FOUND");
        return rows.get(0);
    }
    private Map<String,Object> detail(CapsuleIdentity identity,long memory){return dto(owned(identity,memory));}
    private Map<String,Object> dto(Map<String,Object> row) {
        var result=new LinkedHashMap<String,Object>();
        result.put("id",row.get("id").toString());result.put("consentId",row.get("consent_id").toString());
        result.put("noteId",row.get("capsule_id").toString());result.put("revisionId",row.get("capsule_revision_id").toString());
        result.put("summary",row.get("summary"));result.put("state",row.get("state"));result.put("version",row.get("version"));
        return result;
    }
    private void invalidate(CapsuleIdentity identity,long memory) {
        jdbc.update("UPDATE zf_companion_run_context SET lineage_state='INVALIDATED',updated_at=UTC_TIMESTAMP(3) "
                + "WHERE tenant_id=? AND owner_user_id=? AND run_id IN (SELECT run_id FROM zf_run_source "
                + "WHERE tenant_id=? AND owner_user_id=? AND memory_id=?)",identity.tenantId(),identity.ownerUserId(),
                identity.tenantId(),identity.ownerUserId(),memory);
        jdbc.update("UPDATE zf_run_source SET status='INVALIDATED',updated_at=UTC_TIMESTAMP(3) "
                + "WHERE tenant_id=? AND owner_user_id=? AND memory_id=?",identity.tenantId(),identity.ownerUserId(),memory);
        jdbc.update("UPDATE zf_profile SET context_epoch=context_epoch+1,updated_at=UTC_TIMESTAMP(3) "
                + "WHERE tenant_id=? AND owner_user_id=?",identity.tenantId(),identity.ownerUserId());
    }
}
