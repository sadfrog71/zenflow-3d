package com.deepdimen.matrix.zenflow.admin;

import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ZenflowAdminAccess {
    private final ZenflowAdminUserDetailsService identities;
    public ZenflowAdminAccess(ZenflowAdminUserDetailsService identities) { this.identities=identities; }
    /** Only used by a leased public companion task whose actor was persisted by the authenticated request. */
    public void runDelegated(long administratorId,Runnable action) {
        var previous=SecurityContextHolder.getContext();
        var previousTenant=TenantContextHolder.getTenantId();boolean previousSkip=TenantContextHolder.getTenantSkip();
        var current=identities.loadAdministrator(administratorId);
        current.getAttributes().put(SecurityConstants.CLIENT_ID,ZenflowAdminUserDetailsService.CLIENT);
        var delegated=SecurityContextHolder.createEmptyContext();
        delegated.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(current,"",current.getAuthorities()));
        try {TenantContextHolder.unsetTenantSkip();TenantContextHolder.setTenantId(current.getTenantId());SecurityContextHolder.setContext(delegated);require();action.run();}
        finally {SecurityContextHolder.setContext(previous);TenantContextHolder.clear();if(previousTenant!=null)TenantContextHolder.setTenantId(previousTenant);if(previousSkip)TenantContextHolder.setTenantSkip();}
    }
    public MatrixUser require() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof MatrixUser user)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"ADMIN_AUTH_REQUIRED");
        if (!ZenflowAdminUserDetailsService.CLIENT.equals(user.getAttributes().get(SecurityConstants.CLIENT_ID)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"ADMIN_FORBIDDEN");
        try {
            MatrixUser current=(MatrixUser)identities.loadUserByUser(user);
            if (!current.isEnabled() || !current.isAccountNonLocked() || !current.isAccountNonExpired())
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"ADMIN_FORBIDDEN");
            return current;
        }
        catch (RuntimeException error) { throw new ResponseStatusException(HttpStatus.FORBIDDEN,"ADMIN_FORBIDDEN"); }
    }
}
