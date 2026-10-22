package com.deepdimen.matrix.zenflow.admin;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Uses the inherited ai_model store; credentials are never projected to clients. */
@Service
public class ZenflowModelService {
    private final JdbcTemplate jdbc;
    private final ZenflowProperties product;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    public ZenflowModelService(JdbcTemplate jdbc, ZenflowProperties product, PlatformTransactionManager manager, ObjectMapper json) {
        this.jdbc=jdbc;this.product=product;this.tx=new TransactionTemplate(manager);this.json=json;
    }
    public List<Map<String,Object>> list() {
        return jdbc.queryForList("SELECT id,name,model_name,provider,base_url,temperature,response_limit,assistant_model,ext_data,CASE WHEN api_key IS NULL OR api_key='' THEN 0 ELSE 1 END AS has_key FROM ai_model WHERE tenant_id=? AND del_flag='0' AND model_type='Chat' ORDER BY update_time DESC,id DESC",product.getTenantId()).stream().map(this::projection).toList();
    }
    private Map<String,Object> projection(Map<String,Object> row) {
        var value=new LinkedHashMap<String,Object>(); value.put("id",row.get("id").toString());value.put("name",row.get("name"));value.put("modelName",row.get("model_name"));value.put("provider",row.get("provider"));value.put("baseUrl",row.get("base_url"));value.put("temperature",row.get("temperature"));value.put("maxOutputTokens",row.get("response_limit"));value.put("active","1".equals(row.get("assistant_model")));value.put("hasApiKey",((Number)row.get("has_key")).intValue()!=0);
        var ext=extension(row);value.put("apiProtocol",ZenflowModelProtocol.select(ext,row.get("provider")).name());value.put("contextWindow",ext.path("zenflowContextWindow").asInt(32768));value.put("version",ext.path("zenflowVersion").asInt(1));value.put("allowPrivateContext",ext.path("zenflowAllowPrivateContext").asBoolean(false));value.put("tested",ext.path("zenflowTested").asBoolean(false));value.put("published",ext.path("zenflowPublished").asBoolean(false));return value;
    }
    private com.fasterxml.jackson.databind.JsonNode extension(Map<String,Object> row) {
        try { return json.readTree(String.valueOf(row.getOrDefault("ext_data","{}"))); }
        catch(Exception ex) { return json.createObjectNode(); }
    }
    public Map<String,Object> save(String id, Map<String,Object> body) {
        if(!Set.of("name","modelName","provider","baseUrl","apiKey","temperature","maxOutputTokens","contextWindow","apiProtocol","allowPrivateContext","expectedVersion").containsAll(body.keySet())) bad("UNKNOWN_FIELD");
        String name=text(body,"name",64),model=text(body,"modelName",100),provider=text(body,"provider",100),base=text(body,"baseUrl",100);
        validateEndpoint(base);
        double temperature=number(body,"temperature",0.6).doubleValue();int limit=number(body,"maxOutputTokens",2048).intValue();
        if(!Double.isFinite(temperature) || temperature<0 || temperature>2 || limit<64 || limit>16384) bad("INVALID_MODEL_PARAMETERS");
        Number capacity=number(body,"contextWindow",32768);int window=capacity.intValue();
        if(capacity.doubleValue()!=window || window<2048 || window>2000000 || limit>=window) bad("INVALID_CONTEXT_WINDOW");
        boolean allow=Boolean.TRUE.equals(body.get("allowPrivateContext"));
        long key=id==null?IdWorker.getId():parse(id);
        return tx.execute(ignored->{
            var rows=jdbc.queryForList("SELECT * FROM ai_model WHERE id=? AND tenant_id=? AND model_type='Chat' AND del_flag='0' FOR UPDATE",key,product.getTenantId());
            if(id!=null && rows.size()!=1) missing();
            Map<String,Object> previous=rows.isEmpty()?Map.of():rows.get(0);int version=rows.isEmpty()?0:extension(previous).path("zenflowVersion").asInt(1);
            if(id!=null && number(body,"expectedVersion",-1).intValue()!=version) throw new ResponseStatusException(HttpStatus.CONFLICT,"VERSION_CONFLICT");
            if("1".equals(previous.get("assistant_model")) || extension(previous).path("zenflowPublished").asBoolean(false)) throw new ResponseStatusException(HttpStatus.CONFLICT,"ACTIVE_MODEL_IMMUTABLE_CREATE_NEW_MODEL");
            String secret=body.get("apiKey") instanceof String s && !s.isBlank()?s.trim():(String)previous.get("api_key");
            if(secret==null || secret.isBlank() || secret.length()>255) bad("MODEL_KEY_REQUIRED");
            int effectiveWindow=body.containsKey("contextWindow")?window:extension(previous).path("zenflowContextWindow").asInt(32768);
            if(limit>=effectiveWindow) bad("INVALID_CONTEXT_WINDOW");
            var protocol=body.containsKey("apiProtocol")?ZenflowModelProtocol.parse(body.get("apiProtocol")):ZenflowModelProtocol.select(extension(previous),provider);
            var metadata=json.createObjectNode();metadata.put("zenflowApiProtocol",protocol.name());metadata.put("zenflowContextWindow",effectiveWindow);metadata.put("zenflowVersion",version+1);metadata.put("zenflowAllowPrivateContext",allow);metadata.put("zenflowTested",false);
            if(rows.isEmpty()) jdbc.update("INSERT INTO ai_model(id,tenant_id,model_type,name,model_name,provider,base_url,api_key,temperature,response_limit,assistant_model,default_model,del_flag,ext_data,create_time,update_time) VALUES(?,?,'Chat',?,?,?,?,?,?,?,'0','0','0',?,UTC_TIMESTAMP(),UTC_TIMESTAMP())",key,product.getTenantId(),name,model,provider,base,secret,temperature,limit,metadata.toString());
            else jdbc.update("UPDATE ai_model SET name=?,model_name=?,provider=?,base_url=?,api_key=?,temperature=?,response_limit=?,ext_data=?,update_time=UTC_TIMESTAMP() WHERE id=? AND tenant_id=?",name,model,provider,base,secret,temperature,limit,metadata.toString(),key,product.getTenantId());
            return list().stream().filter(m->m.get("id").equals(Long.toString(key))).findFirst().orElseThrow();
        });
    }
    public Map<String,Object> require(String id) {
        var rows=jdbc.queryForList("SELECT * FROM ai_model WHERE id=? AND tenant_id=? AND del_flag='0' AND model_type='Chat'",parse(id),product.getTenantId());if(rows.size()!=1) missing();return rows.get(0);
    }
    public Map<String,Object> active() {
        var rows=jdbc.queryForList("SELECT * FROM ai_model WHERE tenant_id=? AND del_flag='0' AND model_type='Chat' AND assistant_model='1'",product.getTenantId());
        if(rows.size()!=1 || !extension(rows.get(0)).path("zenflowTested").asBoolean(false)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"MODEL_NOT_CONFIGURED");return rows.get(0);
    }
    public boolean available() { try { active();return true; } catch(ResponseStatusException error) { return false; } }
    /** Frozen public fields for a prepared companion; never returns credentials. */
    public Map<String,Object> publishedSnapshot(String id) {
        var row=require(id);var ext=extension(row);
        if(!ext.path("zenflowTested").asBoolean(false) || !ext.path("zenflowPublished").asBoolean(false))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"MODEL_PUBLISH_REQUIRED");
        var projected=new LinkedHashMap<String,Object>(row);projected.put("has_key",row.get("api_key")!=null?1:0);
        return projection(projected);
    }
    public void recordTest(String id,int version) {
        int count=jdbc.update("UPDATE ai_model SET ext_data=JSON_SET(ext_data,'$.zenflowTested',true),update_time=UTC_TIMESTAMP() WHERE id=? AND tenant_id=? AND JSON_EXTRACT(ext_data,'$.zenflowVersion')=? AND del_flag='0'",parse(id),product.getTenantId(),version);
        if(count!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"MODEL_CHANGED_DURING_TEST");
    }
    public Map<String,Object> activate(String id,int version) {
        return tx.execute(ignored->{
            // One stable tenant row serializes activation of the generated unique model key.
            jdbc.queryForList("SELECT id FROM sys_tenant WHERE id=? FOR UPDATE",product.getTenantId());
            var model=require(id);var ext=extension(model);
            if(ext.path("zenflowVersion").asInt()!=version) throw new ResponseStatusException(HttpStatus.CONFLICT,"VERSION_CONFLICT");
            if(!ext.path("zenflowTested").asBoolean(false)) throw new ResponseStatusException(HttpStatus.CONFLICT,"MODEL_TEST_REQUIRED");
            jdbc.update("UPDATE ai_model SET assistant_model='0' WHERE tenant_id=? AND assistant_model='1'",product.getTenantId());jdbc.update("UPDATE ai_model SET assistant_model='1',ext_data=JSON_SET(ext_data,'$.zenflowPublished',true) WHERE tenant_id=? AND id=?",product.getTenantId(),parse(id));return Map.of("modelId",id,"active",true);
        });
    }
    public static URI validateEndpoint(String value) {
        try {
            URI uri=URI.create(value);
            if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || (uri.getPort()!=-1 && uri.getPort()!=443)) bad("INVALID_MODEL_ENDPOINT");
            for(InetAddress address:InetAddress.getAllByName(uri.getHost())) if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress() || (address.getAddress().length==16 && (address.getAddress()[0]&0xfe)==0xfc)) bad("PRIVATE_MODEL_ENDPOINT_DENIED");
            return uri;
        } catch(ResponseStatusException ex) { throw ex; } catch(Exception ex) { bad("INVALID_MODEL_ENDPOINT");return null; }
    }
    private static String text(Map<String,Object> b,String key,int max) { if(!(b.get(key) instanceof String s) || s.isBlank() || s.length()>max) { bad("INVALID_"+key);return ""; } return ((String)b.get(key)).trim(); }
    private static Number number(Map<String,Object>b,String key,Number fallback) {return b.get(key) instanceof Number n?n:fallback;}
    private static long parse(String id) { try{return Long.parseLong(id);}catch(Exception ex){bad("INVALID_ID");return 0;} }
    private static void bad(String code){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,code);}
    private static void missing(){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"MODEL_NOT_FOUND");}
}
