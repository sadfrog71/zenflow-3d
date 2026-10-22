package com.deepdimen.matrix.zenflow.capsule;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Short private-data commands store metadata receipts only, never bodies or summaries. */
public final class ZenflowPrivateCommands {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    public ZenflowPrivateCommands(JdbcTemplate jdbc, TransactionTemplate tx, ObjectMapper json) {
        this.jdbc=jdbc;this.tx=tx;this.json=json;
    }
    public Map<String,Object> execute(CapsuleIdentity identity,String operation,String key,
            Map<String,Object> input,String resourceType,Supplier<Map<String,Object>> action) {
        if(!"APP_USER".equals(identity.subjectType()))throw new IllegalStateException("PRIVATE_IDENTITY_REQUIRED");
        if(key==null || !key.matches("[A-Za-z0-9_-]{8,64}"))ZenflowCapsuleFacade.bad("IDEMPOTENCY_KEY_REQUIRED");
        String hash=ZenflowCapsuleFacade.digest(json.valueToTree(new TreeMap<>(input)).toString());
        return tx.execute(ignored->{
            lock(identity);
            var rows=jdbc.queryForList("SELECT request_hash,state,result_meta FROM zf_command_receipt "
                    + "WHERE tenant_id=? AND actor_type='APP_USER' AND actor_id=? AND operation=? "
                    + "AND idempotency_key=? FOR UPDATE",identity.tenantId(),identity.ownerUserId().toString(),operation,key);
            if(!rows.isEmpty()) {
                if(!hash.equals(rows.get(0).get("request_hash")))ZenflowCapsuleFacade.conflict("IDEMPOTENCY_CONFLICT");
                if(!"SUCCEEDED".equals(rows.get(0).get("state")))ZenflowCapsuleFacade.conflict("COMMAND_IN_PROGRESS");
                try{return json.readValue(rows.get(0).get("result_meta").toString(),new TypeReference<Map<String,Object>>(){});}
                catch(Exception failure){throw new IllegalStateException("INVALID_COMMAND_RECEIPT");}
            }
            long id=IdWorker.getId();
            jdbc.update("INSERT INTO zf_command_receipt(id,tenant_id,actor_type,actor_id,operation, "
                    + "idempotency_key,request_hash,state) VALUES(?,?,'APP_USER',?,?,?,?,'PROCESSING')",
                    id,identity.tenantId(),identity.ownerUserId().toString(),operation,key,hash);
            var result=action.get();
            if(!Set.of("id","state","deleteState","contextEpoch","version").containsAll(result.keySet()))
                throw new IllegalStateException("PRIVATE_BODY_MUST_NOT_ENTER_RECEIPT");
            jdbc.update("UPDATE zf_command_receipt SET state='SUCCEEDED',resource_type=?,resource_id=?, "
                    + "result_meta=?,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE id=? AND tenant_id=?",
                    resourceType,result.get("id"),json.valueToTree(result).toString(),id,identity.tenantId());
            return result;
        });
    }
    void lock(CapsuleIdentity identity) {
        if(jdbc.queryForList("SELECT owner_user_id FROM zf_profile WHERE tenant_id=? AND owner_user_id=? FOR UPDATE",
                identity.tenantId(),identity.ownerUserId()).size()!=1)ZenflowCapsuleFacade.missing();
    }
}
