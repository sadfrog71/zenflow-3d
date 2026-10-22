package com.deepdimen.matrix.zenflow.operation;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** Shared compact receipts; no text, audio, URLs or storage keys are copied into this table. */
public class ZenflowOperationReceipts {
    public record Receipt(String clientRequestId,String operationType,String state,String resultType,String resultId,Integer resultVersion,String safeErrorCode) {}
    private static final Set<String> TYPES=Set.of("GARDEN_PATCH","MEDIA_CREATE_UPLOAD","MEDIA_COMPLETE","MEDIA_DELETE");
    private final JdbcTemplate jdbc;
    private final ZenflowConsumerAccess access;
    private final ObjectMapper json;
    public ZenflowOperationReceipts(JdbcTemplate jdbc,ZenflowConsumerAccess access,ObjectMapper json){this.jdbc=jdbc;this.access=access;this.json=json;}
    public Receipt get(String type,String key){
        var owner=access.capsuleIdentity();validate(owner,type,key);
        var rows=jdbc.queryForList("SELECT * FROM zf_client_operation WHERE tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND operation_type=? AND client_request_id=?",owner.tenantId(),owner.ownerUserId(),type,key);
        if(rows.isEmpty())throw failure(HttpStatus.NOT_FOUND,"OPERATION_NOT_FOUND");
        return receipt(rows.get(0));
    }
    /** Caller holds the owner lock in the same transaction as the business mutation. */
    public Receipt find(CapsuleIdentity owner,String type,String key,String hash){
        transaction();validate(owner,type,key);
        var rows=jdbc.queryForList("SELECT * FROM zf_client_operation WHERE tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND operation_type=? AND client_request_id=?",owner.tenantId(),owner.ownerUserId(),type,key);
        if(rows.isEmpty())return null;
        if(!hash.equals(rows.get(0).get("payload_hash")))throw failure(HttpStatus.CONFLICT,"IDEMPOTENCY_KEY_REUSED");
        return receipt(rows.get(0));
    }
    public Receipt record(CapsuleIdentity owner,String type,String key,String hash,String state,String resultType,String id,Integer version,String error){
        transaction();validate(owner,type,key);
        if(hash==null||!hash.matches("[a-f0-9]{64}")||!Set.of("PROCESSING","SUCCEEDED","REJECTED").contains(state))throw new IllegalArgumentException("INVALID_OPERATION_RECEIPT");
        jdbc.update("INSERT INTO zf_client_operation(id,tenant_id,subject_type,owner_user_id,operation_type,client_request_id,payload_hash,state,result_type,result_id,result_version,safe_error_code) VALUES(?,?,'APP_USER',?,?,?,?,?,?,?,?,?)",IdWorker.getId(),owner.tenantId(),owner.ownerUserId(),type,key,hash,state,resultType,id,version,error);
        return new Receipt(key,type,state,resultType,id,version,error);
    }
    public Receipt finish(CapsuleIdentity owner,String type,String key,String hash,String state,String resultType,String id,Integer version,String error){
        transaction();validate(owner,type,key);
        if(!Set.of("SUCCEEDED","REJECTED").contains(state))throw new IllegalArgumentException("INVALID_OPERATION_RECEIPT");
        var existing=find(owner,type,key,hash);
        if(existing==null)throw new IllegalStateException("OPERATION_RECEIPT_MISSING");
        if(!"PROCESSING".equals(existing.state()))return existing;
        int updated=jdbc.update("UPDATE zf_client_operation SET state=?,result_type=?,result_id=?,result_version=?,safe_error_code=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND operation_type=? AND client_request_id=? AND payload_hash=? AND state='PROCESSING'",state,resultType,id,version,error,owner.tenantId(),owner.ownerUserId(),type,key,hash);
        if(updated!=1)throw new IllegalStateException("OPERATION_RECEIPT_CHANGED");
        return new Receipt(key,type,state,resultType,id,version,error);
    }
    public String hash(JsonNode body){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(body).toString().getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private JsonNode canonical(JsonNode node){
        if(node.isObject()){var result=json.createObjectNode();var names=new java.util.TreeSet<String>();node.fieldNames().forEachRemaining(names::add);names.forEach(name->result.set(name,canonical(node.get(name))));return result;}
        if(node.isArray()){var result=json.createArrayNode();node.forEach(item->result.add(canonical(item)));return result;}
        return node;
    }
    public static void validateKey(String key){if(key==null||!key.matches("[A-Za-z0-9_-]{8,128}"))throw failure(HttpStatus.BAD_REQUEST,"INVALID_OPERATION_KEY");}
    private static void validate(CapsuleIdentity owner,String type,String key){
        if(owner==null||!"APP_USER".equals(owner.subjectType())||owner.tenantId()==null||owner.ownerUserId()==null)throw failure(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");
        if(type==null||!TYPES.contains(type))throw failure(HttpStatus.BAD_REQUEST,"INVALID_OPERATION_TYPE");validateKey(key);
    }
    private static void transaction(){if(!TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("OPERATION_TRANSACTION_REQUIRED");}
    private static Receipt receipt(Map<String,Object> row){return new Receipt(row.get("client_request_id").toString(),row.get("operation_type").toString(),row.get("state").toString(),(String)row.get("result_type"),(String)row.get("result_id"),row.get("result_version")==null?null:((Number)row.get("result_version")).intValue(),(String)row.get("safe_error_code"));}
    private static ResponseStatusException failure(HttpStatus status,String code){return new ResponseStatusException(status,code);}
}
