package com.deepdimen.matrix.zenflow.identity;

import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.core.constant.enums.UserTypeEnum;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.service.MatrixUserDetailsService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ZenflowUserDetailsService implements MatrixUserDetailsService {
    public static final String AUTH_VERSION = "zenflow.authVersion";
    private final JdbcTemplate jdbc;
    private final ZenflowProperties properties;

    public ZenflowUserDetailsService(JdbcTemplate jdbc, ZenflowProperties properties) {
        this.jdbc = jdbc; this.properties = properties;
    }

    @Override public boolean support(String clientId, String grantType) {
        return properties.getOauthClientId().equals(clientId);
    }
    @Override public int getOrder() { return 100; }

    @Override public UserDetails loadUserByUsername(String username) {
        String normalized;
        try { normalized = EmailIdentity.normalize(username); }
        catch (IllegalArgumentException ex) { throw new UsernameNotFoundException("登录失败"); }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT user_id,username,password,email,tenant_id,auth_version,lock_flag FROM app_user WHERE tenant_id=? AND email_normalized=? AND email_verified_at IS NOT NULL AND del_flag='0'",
                properties.getTenantId(), normalized);
        if (rows.size() != 1) throw new UsernameNotFoundException("登录失败");
        return principal(rows.get(0));
    }

    @Override public UserDetails loadUserByUser(MatrixUser prior) {
        if (prior == null || !UserTypeEnum.TOC.getStatus().equals(prior.getUserType())
                || !Objects.equals(prior.getTenantId(), properties.getTenantId())) {
            throw new UsernameNotFoundException("登录已失效");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT user_id,username,password,email,tenant_id,auth_version,lock_flag FROM app_user WHERE tenant_id=? AND user_id=? AND del_flag='0'",
                properties.getTenantId(), prior.getId());
        if (rows.size() != 1 || !Objects.equals(rows.get(0).get("username"), prior.getUsername())) {
            throw new UsernameNotFoundException("登录已失效");
        }
        Object version = prior.getAttributes().get(AUTH_VERSION);
        if (version == null || Long.parseLong(version.toString()) != ((Number) rows.get(0).get("auth_version")).longValue()) {
            throw new UsernameNotFoundException("登录已失效");
        }
        return principal(rows.get(0));
    }

    public MatrixUser loadExternal(long id) {
        var rows=jdbc.queryForList("SELECT user_id,username,password,email,tenant_id,auth_version,lock_flag FROM app_user WHERE tenant_id=? AND user_id=? AND del_flag='0' AND lock_flag='0'",properties.getTenantId(),id);
        if (rows.size()!=1) throw new UsernameNotFoundException("登录已失效");
        return principal(rows.get(0));
    }

    private MatrixUser principal(Map<String, Object> row) {
        long id = ((Number) row.get("user_id")).longValue();
        Long tenant = ((Number) row.get("tenant_id")).longValue();
        List<Long> roles = jdbc.queryForList("SELECT role_id FROM app_user_role WHERE user_id=?", Long.class, id);
        var authorities = AuthorityUtils.createAuthorityList(roles.stream()
                .map(role -> SecurityConstants.ROLE + role).toArray(String[]::new));
        MatrixUser user = new MatrixUser(id, (String) row.get("username"), roles, null, null, null, null, null,
                null, null, (String) row.get("email"), tenant,
                SecurityConstants.BCRYPT + row.get("password"), true, true, UserTypeEnum.TOC.getStatus(), true,
                null, !"1".equals(row.get("lock_flag")), authorities);
        user.getAttributes().put(AUTH_VERSION, ((Number) row.get("auth_version")).longValue());
        return user;
    }
}
