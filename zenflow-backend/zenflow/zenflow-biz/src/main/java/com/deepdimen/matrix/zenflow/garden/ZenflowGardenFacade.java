package com.deepdimen.matrix.zenflow.garden;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.deepdimen.matrix.zenflow.operation.ZenflowOperationReceipts;
import com.deepdimen.matrix.zenflow.operation.ZenflowOperationReceipts.Receipt;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Not auto-enabled: route wiring requires the versioned server asset catalogue and migration. */
public class ZenflowGardenFacade {
    private final JdbcTemplate jdbc;
    private final ZenflowConsumerAccess access;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final ZenflowGardenOperations.Catalogue catalogue;
    private final String sceneVersion;
    private final String initialFishType;
    private final ZenflowOperationReceipts receipts;

    public ZenflowGardenFacade(JdbcTemplate jdbc, ZenflowConsumerAccess access, PlatformTransactionManager manager,
            ObjectMapper json, ZenflowGardenOperations.Catalogue catalogue, String sceneVersion, String initialFishType) {
        if (sceneVersion == null || !sceneVersion.matches("[A-Za-z0-9._-]{1,64}") || !catalogue.fishTypes().contains(initialFishType)) throw new IllegalArgumentException("INVALID_GARDEN_CATALOGUE");
        this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);this.json=json;
        this.catalogue=catalogue;this.sceneVersion=sceneVersion;this.initialFishType=initialFishType;this.receipts=new ZenflowOperationReceipts(jdbc,access,json);
    }

    public ObjectNode get() {
        var owner=owner();
        var rows=rows(owner);
        if(rows.isEmpty())throw failure(HttpStatus.NOT_FOUND,"GARDEN_NOT_FOUND");
        return snapshot(rows.get(0));
    }

    public Receipt receipt(String key) {return receipts.get("GARDEN_PATCH",key);}

    /** Returns a stable receipt, including rejected operations. A subsequent GET reads the latest snapshot. */
    public Receipt patch(JsonNode body) {
        if(body==null||!body.isObject())throw failure(HttpStatus.BAD_REQUEST,"INVALID_GARDEN_PATCH");
        body.fieldNames().forEachRemaining(field->{if(!Set.of("expectedVersion","clientRequestId","operations").contains(field))throw failure(HttpStatus.BAD_REQUEST,"INVALID_GARDEN_PATCH");});
        var version=body.path("expectedVersion");
        if(!version.isIntegralNumber()||!version.canConvertToInt()||version.intValue()<0)throw failure(HttpStatus.BAD_REQUEST,"INVALID_GARDEN_VERSION");
        if(!body.path("clientRequestId").isTextual())throw failure(HttpStatus.BAD_REQUEST,"INVALID_OPERATION_KEY");
        String key=body.get("clientRequestId").textValue();validateKey(key);
        var owner=owner();
        String hash=receipts.hash(body);
        return tx.execute(status->{
            // The existing owner profile serializes first creation as well as subsequent updates.
            if(jdbc.queryForList("SELECT owner_user_id FROM zf_profile WHERE tenant_id=? AND owner_user_id=? FOR UPDATE",owner.tenantId(),owner.ownerUserId()).size()!=1)throw failure(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");
            if(!owner.equals(owner()))throw failure(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");
            var previous=receipts.find(owner,"GARDEN_PATCH",key,hash);
            if(previous!=null)return previous;
            var rows=rows(owner);
            int current=rows.isEmpty()?0:((Number)rows.get(0).get("state_version")).intValue();
            if(current!=version.intValue())return record(owner,key,hash,"REJECTED",null,null,"GARDEN_VERSION_CONFLICT");
            ObjectNode next;
            try {
                if(current==0)next=initialize(body.path("operations"));
                else next=ZenflowGardenOperations.apply(snapshot(rows.get(0)),body.path("operations"),catalogue);
            }catch(IllegalArgumentException invalid){return record(owner,key,hash,"REJECTED",null,null,"INVALID_GARDEN_OPERATION");}
            if(current==Integer.MAX_VALUE)throw failure(HttpStatus.CONFLICT,"GARDEN_VERSION_LIMIT");
            String id=current==0?Long.toString(IdWorker.getId()):rows.get(0).get("id").toString();
            if(current==0){
                jdbc.update("INSERT INTO zf_garden_state(id,tenant_id,subject_type,owner_user_id,scene_version,state_version,fish_json,placements_json,environment_json) VALUES(?,?,'APP_USER',?,?,1,?,?,?)",Long.parseLong(id),owner.tenantId(),owner.ownerUserId(),sceneVersion,next.get("fish").toString(),next.get("placements").toString(),next.get("environment").toString());
            }else{
                var domains=new java.util.LinkedHashSet<String>();
                body.get("operations").forEach(op->{String type=op.path("type").asText();domains.add(type.startsWith("SET_FISH_")?"fish":type.equals("SET_ENVIRONMENT")?"environment":"placements");});
                var sql=new StringBuilder("UPDATE zf_garden_state SET ");var arguments=new java.util.ArrayList<Object>();
                for(String domain:domains){sql.append(domain).append("_json=?,");arguments.add(next.get(domain).toString());}
                sql.append("state_version=state_version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND subject_type='APP_USER' AND owner_user_id=? AND state_version=?");
                arguments.add(owner.tenantId());arguments.add(owner.ownerUserId());arguments.add(current);
                int updated=jdbc.update(sql.toString(),arguments.toArray());
                if(updated!=1)throw failure(HttpStatus.CONFLICT,"GARDEN_VERSION_CONFLICT");
            }
            return record(owner,key,hash,"SUCCEEDED",id,current+1,null);
        });
    }

    private ObjectNode initialize(JsonNode operations){
        if(!operations.isArray()||operations.size()!=1||!operations.get(0).isObject())throw new IllegalArgumentException();
        JsonNode init=operations.get(0);
        if(!"INIT".equals(init.path("type").asText()))throw new IllegalArgumentException();
        init.fieldNames().forEachRemaining(field->{if(!Set.of("type","choices").contains(field))throw new IllegalArgumentException();});
        ObjectNode state=json.createObjectNode();
        var fish=state.putArray("fish");
        for(int i=0;i<3;i++)fish.addObject().put("instanceId",Long.toString(IdWorker.getId())).put("archetypeId",initialFishType).put("name","");
        state.putArray("placements");
        String preset=catalogue.presets().contains("day")?"day":catalogue.presets().stream().sorted().findFirst().orElseThrow();
        state.putObject("environment").put("presetId",preset).put("lampEnabled",true);
        if(init.has("choices")){
            JsonNode choices=init.get("choices");
            if(!choices.isObject())throw new IllegalArgumentException();
            choices.fieldNames().forEachRemaining(field->{if(!field.equals("environment"))throw new IllegalArgumentException();});
            if(choices.has("environment")){
                JsonNode environment=choices.get("environment");
                if(!environment.isObject()||environment.size()!=2||!environment.has("presetId")||!environment.has("lampEnabled"))throw new IllegalArgumentException();
                ObjectNode operation=environment.deepCopy();operation.put("type","SET_ENVIRONMENT");
                return ZenflowGardenOperations.apply(state,json.createArrayNode().add(operation),catalogue);
            }
        }
        return state;
    }
    private CapsuleIdentity owner(){
        var owner=access.capsuleIdentity();
        if(owner==null||!"APP_USER".equals(owner.subjectType())||owner.tenantId()==null||owner.ownerUserId()==null)throw failure(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");
        return owner;
    }
    private java.util.List<Map<String,Object>> rows(CapsuleIdentity owner){
        return jdbc.queryForList("SELECT * FROM zf_garden_state WHERE tenant_id=? AND subject_type='APP_USER' AND owner_user_id=?",owner.tenantId(),owner.ownerUserId());
    }
    private ObjectNode snapshot(Map<String,Object> row){
        ObjectNode state=json.createObjectNode();
        state.put("gardenId",row.get("id").toString()).put("stateVersion",((Number)row.get("state_version")).intValue()).put("sceneVersion",row.get("scene_version").toString());
        try{
            state.set("fish",json.readTree(row.get("fish_json").toString()));state.set("placements",json.readTree(row.get("placements_json").toString()));state.set("environment",json.readTree(row.get("environment_json").toString()));
        }catch(java.io.IOException error){throw new IllegalStateException("GARDEN_STATE_INVALID",error);}
        state.put("updatedAt",((java.sql.Timestamp)row.get("updated_at")).toInstant().toString());
        return state;
    }
    private Receipt record(CapsuleIdentity owner,String key,String hash,String state,String id,Integer version,String error){
        return receipts.record(owner,"GARDEN_PATCH",key,hash,state,id==null?null:"GARDEN",id,version,error);
    }
    private static void validateKey(String key){ZenflowOperationReceipts.validateKey(key);}
    private static ResponseStatusException failure(HttpStatus status,String code){return new ResponseStatusException(status,code);}
}
