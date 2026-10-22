package com.deepdimen.matrix.zenflow.identity;
import java.security.Principal;
import java.sql.Timestamp;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import com.deepdimen.matrix.common.security.service.MatrixUser;
@Service
public class ZenflowLoginAudit {
 private final JdbcTemplate jdbc;private final ZenflowProperties product;
 public ZenflowLoginAudit(JdbcTemplate jdbc,ZenflowProperties product){this.jdbc=jdbc;this.product=product;}
 public void issued(OAuth2Authorization authorization){
  if(!product.getOauthClientId().equals(authorization.getRegisteredClientId())||!AuthorizationGrantType.AUTHORIZATION_CODE.equals(authorization.getAuthorizationGrantType())||authorization.getAccessToken()==null)return;
  Object principal=authorization.getAttribute(Principal.class.getName());
  if(!(principal instanceof UsernamePasswordAuthenticationToken auth)||!(auth.getPrincipal() instanceof MatrixUser user)||!java.util.Objects.equals(product.getTenantId(),user.getTenantId())||!"1".equals(user.getUserType()))return;
  jdbc.update("INSERT IGNORE INTO zf_user_login_event(tenant_id,owner_user_id,authorization_id,logged_in_at) SELECT ?,?,?,? FROM zf_profile WHERE tenant_id=? AND owner_user_id=?",user.getTenantId(),user.getId(),authorization.getId(),Timestamp.from(authorization.getToken(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode.class)==null?authorization.getAccessToken().getToken().getIssuedAt():authorization.getToken(org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode.class).getToken().getIssuedAt()),user.getTenantId(),user.getId());
 }
}
