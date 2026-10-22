package com.deepdimen.matrix.zenflow.admin;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ZenflowHomeSlogans {
 private final JdbcTemplate jdbc;private final ZenflowProperties product;private final ZenflowAdminAccess access;private final ObjectMapper json;private final TransactionTemplate tx;
 public ZenflowHomeSlogans(JdbcTemplate jdbc,ZenflowProperties product,ZenflowAdminAccess access,ObjectMapper json,PlatformTransactionManager manager){this.jdbc=jdbc;this.product=product;this.access=access;this.json=json;this.tx=new TransactionTemplate(manager);}
 public static List<Map<String,Object>> defaults(){return List.of(Map.of("zh","不必着急。","en","Take your time.","enabled",true),Map.of("zh","让万物穿过自己。","en","Let everything pass through you.","enabled",true),Map.of("zh","把这一刻，还给自己。","en","Keep this moment for yourself.","enabled",true));}
 private Map<String,Object> read(){var rows=jdbc.queryForList("SELECT version,items_json FROM zf_home_slogans WHERE tenant_id=?",product.getTenantId());if(rows.isEmpty())return Map.of("version",0,"items",defaults());var row=rows.get(0);try{return Map.of("version",row.get("version"),"items",json.readValue(row.get("items_json").toString(),new TypeReference<List<Map<String,Object>>>(){}));}catch(Exception invalid){throw new IllegalStateException("Invalid home slogans configuration",invalid);}}
 public Map<String,Object> list(){access.require();return read();}
 public List<Map<String,Object>> publicItems(){return ((List<?>)read().get("items")).stream().map(x->(Map<String,Object>)x).filter(x->Boolean.TRUE.equals(x.get("enabled"))).map(x->Map.<String,Object>of("zh",x.get("zh"),"en",x.get("en"))).toList();}
 public Map<String,Object> save(Map<String,Object> body){access.require();var items=validate(body.get("items"));Object version=body.get("expectedVersion");if(!(version instanceof Number n)||n.longValue()<0||n.doubleValue()!=n.longValue())throw error("SLOGAN_INPUT_INVALID");String encoded;try{encoded=json.writeValueAsString(items);}catch(Exception invalid){throw error("SLOGAN_INPUT_INVALID");}return tx.execute(ignored->{jdbc.update("INSERT IGNORE INTO zf_home_slogans(tenant_id,version,items_json) VALUES(?,0,?)",product.getTenantId(),"[]");if(jdbc.update("UPDATE zf_home_slogans SET items_json=?,version=version+1,updated_at=UTC_TIMESTAMP(3) WHERE tenant_id=? AND version=?",encoded,product.getTenantId(),n.longValue())!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"SLOGAN_VERSION_CONFLICT");return read();});}
 static List<Map<String,Object>> validate(Object value){if(!(value instanceof List<?> rows)||rows.size()>100)throw error("SLOGAN_INPUT_INVALID");var clean=new ArrayList<Map<String,Object>>();for(Object row:rows){if(!(row instanceof Map<?,?> map)||!(map.get("zh") instanceof String zh)||!(map.get("en") instanceof String en)||!(map.get("enabled") instanceof Boolean active)||zh.isBlank()||zh.length()>80||en.length()>140||zh.chars().anyMatch(Character::isISOControl)||en.chars().anyMatch(Character::isISOControl))throw error("SLOGAN_INPUT_INVALID");clean.add(Map.of("zh",zh.strip(),"en",en.strip(),"enabled",active));}return clean;}
 private static ResponseStatusException error(String reason){return new ResponseStatusException(HttpStatus.BAD_REQUEST,reason);}
}
