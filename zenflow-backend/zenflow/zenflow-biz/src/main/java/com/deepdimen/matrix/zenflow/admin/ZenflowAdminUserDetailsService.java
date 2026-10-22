package com.deepdimen.matrix.zenflow.admin;

import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.core.constant.enums.UserTypeEnum;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.service.MatrixUserDetailsService;
import com.deepdimen.matrix.zenflow.identity.ZenflowProperties;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Product administrators reuse the existing SYS_USER and OAuth identity infrastructure. */
@Service
public class ZenflowAdminUserDetailsService implements MatrixUserDetailsService {
    public static final String CLIENT = "zenflow-admin";
    public static final String ROLE = "ZF_ADMIN";
    private final JdbcTemplate jdbc;
    private final ZenflowProperties product;
    public ZenflowAdminUserDetailsService(JdbcTemplate jdbc, ZenflowProperties product) {
        this.jdbc=jdbc; this.product=product;
    }
    @Override public boolean support(String client, String grant) { return CLIENT.equals(client); }
    @Override public int getOrder() { return 100; }
    @Override public UserDetails loadUserByUsername(String username) {
        var rows=jdbc.queryForList("SELECT u.*,r.role_id AS matched_role_id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.user_id JOIN sys_role r ON r.role_id=ur.role_id AND r.tenant_id=u.tenant_id WHERE u.tenant_id=? AND u.username=? AND u.del_flag='0' AND r.role_code=? AND r.del_flag='0'", product.getTenantId(), username, ROLE);
        if (rows.isEmpty() || rows.stream().map(row->row.get("user_id")).distinct().count()!=1) throw new UsernameNotFoundException("登录失败");
        var row=rows.get(0); long id=((Number)row.get("user_id")).longValue();
        List<Long> roles=rows.stream().map(match->((Number)match.get("matched_role_id")).longValue()).toList();
        return new MatrixUser(id, username, roles, null,null,null,null,null,null,null,
            (String)row.get("email"),product.getTenantId(),SecurityConstants.BCRYPT+row.get("password"),
            true,true,UserTypeEnum.TOB.getStatus(),true,modified(row.get("password_modify_time")),"0".equals(row.get("lock_flag")),AuthorityUtils.createAuthorityList("ROLE_ZF_ADMIN","knowledge_skill_import","knowledge_skill_approve","knowledge_skill_disable"));
    }
    private static java.time.LocalDateTime modified(Object value) {
        if(value instanceof java.sql.Timestamp t)return t.toLocalDateTime();
        return value instanceof java.time.LocalDateTime t?t:null;
    }
    /** Internal task delegation resolves the current dedicated product administrator, never a generic SYS_USER. */
    public MatrixUser loadAdministrator(long id) {
        var names=jdbc.queryForList("SELECT username FROM sys_user WHERE user_id=? AND tenant_id=? AND del_flag='0'",String.class,id,product.getTenantId());
        if(names.size()!=1)throw new UsernameNotFoundException("管理员不可用");
        var user=(MatrixUser)loadUserByUsername(names.get(0));
        if(!Objects.equals(user.getId(),id) || !user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired())throw new UsernameNotFoundException("管理员不可用");
        return user;
    }
    @Override public UserDetails loadUserByUser(MatrixUser user) {
        if (user==null || !UserTypeEnum.TOB.getStatus().equals(user.getUserType()) || !Objects.equals(product.getTenantId(),user.getTenantId())) throw new UsernameNotFoundException("登录失效");
        MatrixUser current=(MatrixUser)loadUserByUsername(user.getUsername());
        if (!Objects.equals(current.getId(),user.getId()) || !Objects.equals(current.getPasswordModifyTime(),user.getPasswordModifyTime())) throw new UsernameNotFoundException("登录失效");
        return current;
    }
}
