package com.deepdimen.matrix.zenflow.admin;

import com.deepdimen.matrix.zenflow.identity.EmailIdentity;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Product adapter for existing SYS_USER password and credential-version fields. */
@Service
public class ZenflowAdminPassword {
    private final JdbcTemplate jdbc;
    private final ZenflowAdminAccess access;
    private final TransactionTemplate tx;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
    public ZenflowAdminPassword(JdbcTemplate jdbc,ZenflowAdminAccess access,PlatformTransactionManager manager){this.jdbc=jdbc;this.access=access;this.tx=new TransactionTemplate(manager);}
    public void change(String currentPassword,String newPassword){
        if(currentPassword==null || currentPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"PASSWORD_INPUT_INVALID");
        try{EmailIdentity.checkPassword(newPassword);}catch(IllegalArgumentException invalid){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_PASSWORD");}
        if(currentPassword.equals(newPassword))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"PASSWORD_UNCHANGED");
        var actor=access.require();
        tx.executeWithoutResult(ignored->{
            var rows=jdbc.queryForList("SELECT password,password_modify_time FROM sys_user WHERE tenant_id=? AND user_id=? AND del_flag='0' AND lock_flag='0' FOR UPDATE",actor.getTenantId(),actor.getId());
            if(rows.size()!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"ADMIN_FORBIDDEN");
            access.require();
            if(!encoder.matches(currentPassword,rows.get(0).get("password").toString()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"CURRENT_PASSWORD_INVALID");
            var modified=java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).withNano(0);
            Object value=rows.get(0).get("password_modify_time");
            var prior=value instanceof java.sql.Timestamp t?t.toLocalDateTime():value instanceof java.time.LocalDateTime t?t:null;
            if(prior!=null&&!modified.isAfter(prior))modified=prior.plusSeconds(1);
            jdbc.update("UPDATE sys_user SET password=?,password_modify_time=?,password_expire_flag='0',update_time=? WHERE tenant_id=? AND user_id=? AND del_flag='0' AND lock_flag='0'",encoder.encode(newPassword),java.sql.Timestamp.valueOf(modified),java.sql.Timestamp.valueOf(modified),actor.getTenantId(),actor.getId());
        });
    }
}
