package com.deepdimen.matrix.zenflow.identity;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Service
public class ExternalAccountService {
    private final JdbcTemplate jdbc;
    private final ZenflowProperties product;
    private final TransactionTemplate tx;
    public ExternalAccountService(JdbcTemplate jdbc,ZenflowProperties product,PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.product=product; this.tx=new TransactionTemplate(manager);
    }
    public long login(ExternalIdentityVerifier.Identity id,String locale) {
        try { return tx.execute(status -> {
            Long prior=find(id);
            if (prior!=null) { touch(id); applyName(prior,id); return prior; }
            long user=IdWorker.getId();
            var now=LocalDateTime.now(ZoneOffset.UTC);
            Long role=jdbc.query("SELECT role_id FROM app_role WHERE tenant_id=? AND role_code='ZF_CONSUMER' AND del_flag='0'",rs->rs.next()?rs.getLong(1):null,product.getTenantId());
            if (role==null) throw new IllegalStateException("CONSUMER_ROLE_MISSING");
            String secret=new SecretDigests(product.getAuthHmacKey()).randomHex(32);
            jdbc.update("INSERT INTO app_user(user_id,tenant_id,username,password,email,email_normalized,auth_version,phone,lock_flag,del_flag,create_time,update_time,last_modified_time) VALUES(?,?,?,?,NULL,NULL,1,NULL,'0','0',?,?,?)",
                user,product.getTenantId(),"zf_"+user,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(secret),now,now,now);
            jdbc.update("INSERT INTO app_user_role(user_id,role_id) VALUES(?,?)",user,role);
            jdbc.update("INSERT INTO zf_profile(tenant_id,owner_user_id,public_id,locale,companion_preferences,memory_enabled,context_epoch,version) VALUES(?,?,?,?,JSON_OBJECT(),0,1,1)",
                product.getTenantId(),user,new SecretDigests(product.getAuthHmacKey()).randomHex(16),"zh-CN".equals(locale)?"zh-CN":"en-US");
            insert(user,id,now); applyName(user,id); return user;
        }); } catch (DataIntegrityViolationException ex) {
            Long existing=find(id);
            if (existing==null) throw new IllegalStateException("IDENTITY_CONFLICT");
            return existing;
        }
    }
    public void bind(long user,ExternalIdentityVerifier.Identity id,long version) {
        try { tx.executeWithoutResult(status -> {
            if(lock(user)!=version) throw new IllegalStateException("AUTH_VERSION_CHANGED");
            Long existing=find(id);
            if (existing!=null) { if(existing==user) return; throw new IllegalStateException("IDENTITY_ALREADY_BOUND"); }
            insert(user,id,LocalDateTime.now(ZoneOffset.UTC));
        }); } catch (DataIntegrityViolationException ex) { throw new IllegalStateException("IDENTITY_ALREADY_BOUND"); }
    }
    public void unbind(long user,String provider,long version) {
        if (!List.of("google","facebook","apple").contains(provider)) throw new IllegalArgumentException("INVALID_PROVIDER");
        tx.executeWithoutResult(status -> {
            if(lock(user)!=version) throw new IllegalStateException("AUTH_VERSION_CHANGED");
            int external=jdbc.queryForObject("SELECT COUNT(*) FROM zf_external_identity WHERE tenant_id=? AND user_id=?",Integer.class,product.getTenantId(),user);
            int email=jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE tenant_id=? AND user_id=? AND email_verified_at IS NOT NULL AND email_normalized IS NOT NULL",Integer.class,product.getTenantId(),user);
            int exists=jdbc.queryForObject("SELECT COUNT(*) FROM zf_external_identity WHERE tenant_id=? AND user_id=? AND provider=?",Integer.class,product.getTenantId(),user,provider);
            if(exists==0) return;
            if(external+email<=1) throw new IllegalStateException("LAST_LOGIN_METHOD");
            jdbc.update("DELETE FROM zf_external_identity WHERE tenant_id=? AND user_id=? AND provider=?",product.getTenantId(),user,provider);
            jdbc.update("UPDATE app_user SET auth_version=auth_version+1 WHERE tenant_id=? AND user_id=?",product.getTenantId(),user);
            audit(user,provider,"UNBIND");
        });
    }
    public List<Map<String,Object>> methods(long user) {
        return jdbc.queryForList("SELECT provider,created_at AS boundAt FROM zf_external_identity WHERE tenant_id=? AND user_id=? ORDER BY provider",product.getTenantId(),user);
    }
    private long lock(long user) {
        return jdbc.queryForObject("SELECT auth_version FROM app_user WHERE tenant_id=? AND user_id=? AND del_flag='0' AND lock_flag='0' FOR UPDATE",Long.class,product.getTenantId(),user);
    }
    private Long find(ExternalIdentityVerifier.Identity id) {
        return jdbc.query("SELECT user_id FROM zf_external_identity WHERE tenant_id=? AND provider=? AND provider_app_scope=? AND subject=?",
            rs->rs.next()?rs.getLong(1):null,product.getTenantId(),id.provider(),id.scope(),id.subject());
    }
    private void applyName(long user,ExternalIdentityVerifier.Identity identity){
        if(identity.displayName()!=null&&!identity.displayName().isBlank())jdbc.update("UPDATE app_user SET name=? WHERE tenant_id=? AND user_id=? AND (name IS NULL OR TRIM(name)='')",identity.displayName(),product.getTenantId(),user);
    }
    private void touch(ExternalIdentityVerifier.Identity id) {
        jdbc.update("UPDATE zf_external_identity SET last_login_at=? WHERE tenant_id=? AND provider=? AND provider_app_scope=? AND subject=?",LocalDateTime.now(ZoneOffset.UTC),product.getTenantId(),id.provider(),id.scope(),id.subject());
    }
    private void insert(long user,ExternalIdentityVerifier.Identity id,LocalDateTime now) {
        jdbc.update("INSERT INTO zf_external_identity(id,tenant_id,user_id,provider,issuer,provider_app_scope,subject,provider_email,provider_email_verified,created_at,last_login_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            IdWorker.getId(),product.getTenantId(),user,id.provider(),id.issuer(),id.scope(),id.subject(),id.email(),id.emailVerified()?1:0,now,now);
        audit(user,id.provider(),"BIND");
    }
    private void audit(long user,String provider,String action) {
        jdbc.update("INSERT INTO zf_outbox(id,tenant_id,event_type,aggregate_type,aggregate_id,dedupe_key,payload_json,state,next_attempt_at) VALUES(?,?,'LOGIN_METHOD_CHANGED','app_user',?,?,JSON_OBJECT('provider',?,'action',?),'PENDING',?)",
            IdWorker.getId(),product.getTenantId(),Long.toString(user),new SecretDigests(product.getAuthHmacKey()).randomHex(16),provider,action,LocalDateTime.now(ZoneOffset.UTC));
    }
}
