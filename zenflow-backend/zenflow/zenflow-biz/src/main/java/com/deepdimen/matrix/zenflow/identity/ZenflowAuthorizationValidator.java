package com.deepdimen.matrix.zenflow.identity;

import com.deepdimen.matrix.common.security.service.MatrixAuthorizationValidator;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import java.security.Principal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Component;

@Component
public class ZenflowAuthorizationValidator implements MatrixAuthorizationValidator {
    private final ZenflowLoginAudit audit;
    private final ZenflowProperties properties;
    private final ZenflowUserDetailsService identities;
    public ZenflowAuthorizationValidator(ZenflowProperties properties, ZenflowUserDetailsService identities) {
        this(properties,identities,null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public ZenflowAuthorizationValidator(ZenflowProperties properties,ZenflowUserDetailsService identities,ZenflowLoginAudit audit){this.properties=properties;this.identities=identities;this.audit=audit;}
    @Override public void issued(OAuth2Authorization authorization){if(audit!=null)audit.issued(authorization);}
    @Override public boolean supports(String clientId) { return properties.getOauthClientId().equals(clientId); }
    @Override public boolean accepts(OAuth2Authorization authorization, OAuth2TokenType tokenType) {
        if (tokenType != null && "state".equals(tokenType.getValue())) return true;
        Object stored = authorization.getAttributes().get(Principal.class.getName());
        if (!(stored instanceof UsernamePasswordAuthenticationToken auth)
                || !(auth.getPrincipal() instanceof MatrixUser user)) return false;
        try {
            var current = identities.loadUserByUser(user);
            return current.isAccountNonLocked() && current.isAccountNonExpired();
        }
        catch (RuntimeException ex) { return false; }
    }
}
