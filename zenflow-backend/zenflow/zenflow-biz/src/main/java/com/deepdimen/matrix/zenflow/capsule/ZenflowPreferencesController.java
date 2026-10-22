package com.deepdimen.matrix.zenflow.capsule;

import static com.deepdimen.matrix.zenflow.capsule.ZenflowCapsuleFacade.*;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.zenflow.identity.ZenflowConsumerAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/zenflow/v1/me")
public class ZenflowPreferencesController {
    private final JdbcTemplate jdbc;
    private final ZenflowConsumerAccess access;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    public ZenflowPreferencesController(JdbcTemplate jdbc,ZenflowConsumerAccess access,
            PlatformTransactionManager manager,ObjectMapper json) {
        this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);this.json=json;
    }
    @GetMapping public R<?> me(){return R.ok(profile(access.capsuleIdentity()));}
    @PatchMapping("/preferences") public R<?> preferences(@RequestBody Map<String,Object> body) {
        fields(body,Set.of("locale","companionPreferences","memoryEnabled","expectedVersion"));
        var identity=access.capsuleIdentity();int expected=version(body,"expectedVersion");
        String locale=body.containsKey("locale")?text(body,"locale",16,true):null;
        if(locale!=null && !Set.of("zh-CN","en-US").contains(locale))bad("INVALID_LOCALE");
        Boolean memory=null;
        if(body.containsKey("memoryEnabled")) {
            if(!(body.get("memoryEnabled") instanceof Boolean))bad("INVALID_MEMORY_PREFERENCE");
            memory=(Boolean)body.get("memoryEnabled");
        }
        String prefs=body.containsKey("companionPreferences")?preferences(body.get("companionPreferences")):null;
        if(locale==null && memory==null && prefs==null)bad("EMPTY_PREFERENCE_UPDATE");
        final Boolean enabled=memory;
        return tx.execute(ignored->{
            var rows=jdbc.queryForList("SELECT locale,companion_preferences,memory_enabled,version "
                    + "FROM zf_profile WHERE tenant_id=? AND owner_user_id=? FOR UPDATE",identity.tenantId(),identity.ownerUserId());
            if(rows.size()!=1)missing();var current=rows.get(0);
            if(((Number)current.get("version")).intValue()!=expected)conflict("VERSION_CONFLICT");
            boolean withdrawing=Boolean.FALSE.equals(enabled) && ((Number)current.get("memory_enabled")).intValue()!=0;
            jdbc.update("UPDATE zf_profile SET locale=?,companion_preferences=?,memory_enabled=?, "
                    + "context_epoch=context_epoch+1,version=version+1,updated_at=UTC_TIMESTAMP(3) "
                    + "WHERE tenant_id=? AND owner_user_id=? AND version=?",
                    locale==null?current.get("locale"):locale,prefs==null?current.get("companion_preferences"):prefs,
                    enabled==null?current.get("memory_enabled"):(enabled?1:0),identity.tenantId(),identity.ownerUserId(),expected);
            if(withdrawing) {
                jdbc.update("UPDATE zf_companion_run_context SET lineage_state='INVALIDATED',updated_at=UTC_TIMESTAMP(3) "
                        + "WHERE tenant_id=? AND owner_user_id=? AND run_id IN (SELECT run_id FROM zf_run_source "
                        + "WHERE tenant_id=? AND owner_user_id=? AND source_kind='MEMORY')",identity.tenantId(),identity.ownerUserId(),
                        identity.tenantId(),identity.ownerUserId());
                jdbc.update("UPDATE zf_run_source SET status='INVALIDATED',updated_at=UTC_TIMESTAMP(3) "
                        + "WHERE tenant_id=? AND owner_user_id=? AND source_kind='MEMORY'",identity.tenantId(),identity.ownerUserId());
            }
            return R.ok(profile(identity));
        });
    }
    private String preferences(Object value) {
        if(!(value instanceof Map<?,?> values) || !Set.of("tone","responseLength").containsAll(values.keySet()))bad("INVALID_COMPANION_PREFERENCES");
        var values=(Map<?,?>)value;
        if(values.containsKey("tone") && !Set.of("GENTLE","DIRECT").contains(values.get("tone")))bad("INVALID_COMPANION_PREFERENCES");
        if(values.containsKey("responseLength") && !Set.of("SHORT","BALANCED","DETAILED").contains(values.get("responseLength")))bad("INVALID_COMPANION_PREFERENCES");
        return json.valueToTree(values).toString();
    }
    private Map<String,Object> profile(CapsuleIdentity identity) {
        var rows=jdbc.queryForList("SELECT p.public_id,p.locale,p.companion_preferences,p.memory_enabled,p.version, "
                + "u.email,u.email_verified_at,u.nickname,u.name FROM zf_profile p JOIN app_user u "
                + "ON u.tenant_id=p.tenant_id AND u.user_id=p.owner_user_id "
                + "WHERE p.tenant_id=? AND p.owner_user_id=?",identity.tenantId(),identity.ownerUserId());
        if(rows.size()!=1)missing();var row=rows.get(0);var result=new LinkedHashMap<String,Object>();
        result.put("userId",identity.ownerUserId().toString());result.put("publicId",row.get("public_id"));
        result.put("email",row.get("email")==null?"":row.get("email"));result.put("emailVerified",row.get("email_verified_at")!=null);
        String nickname=row.get("nickname")==null?"":row.get("nickname").toString();
        result.put("displayName",nickname);result.put("locale",row.get("locale"));
        try{result.put("companionPreferences",json.readTree(row.get("companion_preferences").toString()));}
        catch(Exception failure){throw new IllegalStateException("PRIVATE_PREFERENCES_INVALID");}
        result.put("memoryEnabled",((Number)row.get("memory_enabled")).intValue()!=0);result.put("version",row.get("version"));
        return result;
    }
}
