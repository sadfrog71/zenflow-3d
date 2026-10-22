package com.deepdimen.matrix.zenflow.identity;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.capsule.security.CapsuleIdentity;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Trusted consumer identity shared by product facades and retained-stream authorization. */
@Component
public class ZenflowConsumerAccess {
    private final ZenflowUserDetailsService identities;
    private final ZenflowProperties product;
    public ZenflowConsumerAccess(ZenflowUserDetailsService identities,ZenflowProperties product) {this.identities=identities;this.product=product;}
    public MatrixUser require() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof MatrixUser user)) throw denied();
        return verify(user);
    }
    public MatrixUser verify(MatrixUser user) {
        if(user==null || !product.getOauthClientId().equals(user.getAttributes().get(SecurityConstants.CLIENT_ID)))throw denied();
        try {
            var current=(MatrixUser)identities.loadUserByUser(user);
            if(!current.isEnabled() || !current.isAccountNonLocked() || !current.isAccountNonExpired())throw denied();
            current.getAttributes().put(SecurityConstants.CLIENT_ID,product.getOauthClientId());
            return current;
        } catch(UsernameNotFoundException | NumberFormatException error) { throw denied(); }
    }
    public IdentityEnvelope identity(MatrixUser user,String correlationId,String purpose) {
        var current=verify(user);
        return new IdentityEnvelope(current.getTenantId().toString(),current.getId().toString(),null,List.of(),List.of(),List.of(),correlationId,purpose,"APP_USER");
    }
    public CapsuleIdentity capsuleIdentity() {
        var user=require();return new CapsuleIdentity(user.getTenantId(),user.getId(),"APP_USER");
    }
    private ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"CONSUMER_AUTH_REQUIRED");}
}
