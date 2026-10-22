package com.deepdimen.matrix.zenflow.identity;

import com.deepdimen.matrix.common.security.service.MatrixUser;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/zenflow/v1")
public class ZenflowProfileController {
    private final JdbcTemplate jdbc;
    private final ZenflowProperties properties;
    private final ZenflowExternalProperties external;
    private final ZenflowUserDetailsService identities;
    private com.deepdimen.matrix.zenflow.admin.ZenflowHomeSlogans slogans;
    @org.springframework.beans.factory.annotation.Autowired
    public void setSlogans(com.deepdimen.matrix.zenflow.admin.ZenflowHomeSlogans value){this.slogans=value;}
    public ZenflowProfileController(JdbcTemplate jdbc, ZenflowProperties properties, ZenflowUserDetailsService identities, ZenflowExternalProperties external) {
        this.jdbc = jdbc; this.properties = properties; this.identities = identities; this.external = external;
    }

    @GetMapping("/bootstrap")
    public Map<String, Object> bootstrap() {
        boolean ready=false;
        try {
            ready=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM zf_companion_config c JOIN ai_skill_binding b ON b.id=c.skill_binding_id AND b.tenant_id=c.tenant_id JOIN ai_skill_binding_revision br ON br.id=b.active_revision_id AND br.binding_id=b.id AND br.tenant_id=b.tenant_id JOIN zf_companion_revision rev ON rev.config_id=c.id AND rev.tenant_id=c.tenant_id AND rev.skill_release_id=br.release_id JOIN ai_skill_release r ON r.id=rev.skill_release_id AND r.tenant_id=rev.tenant_id JOIN ai_model m ON m.id=rev.model_id AND m.tenant_id=rev.tenant_id WHERE c.tenant_id=? AND c.config_key='default' AND c.status='ENABLED' AND br.enabled=1 AND rev.state='READY' AND r.status='PUBLISHED' AND m.del_flag='0' AND JSON_EXTRACT(m.ext_data,'$.zenflowTested')=true AND JSON_EXTRACT(m.ext_data,'$.zenflowPublished')=true)",Boolean.class,properties.getTenantId()));
        }catch(org.springframework.dao.DataAccessException unavailable){ready=false;}
        return Map.of("name", "此间", "brand", "Invelle", "capabilities", Map.of("scene",true,"chat",ready,"creation",ready,"notes",true,"voiceMedia",false,"social",false), "languages", List.of("zh-CN", "en-US"),
                "registrationMethods", java.util.stream.Stream.concat(java.util.stream.Stream.of("EMAIL"), external.enabled().stream().map(String::toUpperCase)).toList(), "publicVersion", "p1-core", "homeSlogans", slogans==null?com.deepdimen.matrix.zenflow.admin.ZenflowHomeSlogans.defaults():slogans.publicItems());
    }

    @GetMapping("/profile")
    public Map<String, Object> profile() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof MatrixUser user)) throw new IllegalStateException("AUTH_REQUIRED");
        identities.loadUserByUser(user);
        var rows = jdbc.queryForList("SELECT p.public_id,p.locale,p.memory_enabled,p.version,u.nickname,u.name,u.email,(SELECT e.provider_email FROM zf_external_identity e WHERE e.tenant_id=p.tenant_id AND e.user_id=p.owner_user_id AND e.provider_email_verified=1 ORDER BY e.last_login_at DESC,e.id LIMIT 1) AS identity_email FROM zf_profile p JOIN app_user u ON u.tenant_id=p.tenant_id AND u.user_id=p.owner_user_id WHERE p.tenant_id=? AND p.owner_user_id=?",
                properties.getTenantId(), user.getId());
        if (rows.size() != 1) throw new IllegalStateException("PROFILE_MISSING");
        var row = rows.get(0);
        String accountEmail = nonBlank(row.get("email"), row.get("identity_email"));
        String displayName = nonBlank(row.get("nickname"), row.get("name"));
        if (displayName.isEmpty() && !accountEmail.isEmpty()) displayName = accountEmail.split("@", 2)[0];
        return Map.of("displayName", displayName, "accountEmail", accountEmail, "userId", user.getId().toString(), "tenantId", properties.getTenantId() + "",
                "subjectType", "APP_USER", "publicId", row.get("public_id"), "email", row.get("email") == null ? "" : row.get("email"),
                "locale", row.get("locale"), "memoryEnabled", ((Number) row.get("memory_enabled")).intValue() != 0,
                "version", ((Number) row.get("version")).longValue());
    }
    static String nonBlank(Object... values) {
        for (Object value : values) {
            if (value instanceof String text && !text.isBlank()) return text.trim();
        }
        return "";
    }
}
