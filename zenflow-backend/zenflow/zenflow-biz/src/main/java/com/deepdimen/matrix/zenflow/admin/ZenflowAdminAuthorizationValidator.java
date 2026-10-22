package com.deepdimen.matrix.zenflow.admin;

import com.deepdimen.matrix.common.security.service.MatrixAuthorizationValidator;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import java.security.Principal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Component;

/** Role withdrawal and account locking revoke all admin bearer and refresh use immediately. */
@Component
public class ZenflowAdminAuthorizationValidator implements MatrixAuthorizationValidator {
    private final ZenflowAdminUserDetailsService identities;
    public ZenflowAdminAuthorizationValidator(ZenflowAdminUserDetailsService identities){this.identities=identities;}
    @Override public boolean supports(String client){return "zenflow-admin".equals(client);}
    @Override public boolean accepts(OAuth2Authorization authorization,OAuth2TokenType type) {
        if(!supports(authorization.getRegisteredClientId()))return false;
        if(type!=null && "state".equals(type.getValue()))return true;
        Object stored=authorization.getAttributes().get(Principal.class.getName());
        if(!(stored instanceof UsernamePasswordAuthenticationToken auth) || !(auth.getPrincipal() instanceof MatrixUser user))return false;
        try {
            var current=identities.loadUserByUser(user);
            return current.isEnabled() && current.isAccountNonLocked() && current.isAccountNonExpired();
        } catch(RuntimeException failure){return false;}
    }
}
