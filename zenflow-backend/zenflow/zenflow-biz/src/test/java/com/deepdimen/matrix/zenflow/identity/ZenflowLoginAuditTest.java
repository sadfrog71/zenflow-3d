package com.deepdimen.matrix.zenflow.identity;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.core.*;
class ZenflowLoginAuditTest {
 @Test void refreshingSameAuthorizationDoesNotCreateAnotherLoginAndAdminIsExcluded(){
  var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa","") );jdbc.execute("CREATE TABLE zf_profile(tenant_id BIGINT,owner_user_id BIGINT)");jdbc.execute("CREATE TABLE zf_user_login_event(tenant_id BIGINT,owner_user_id BIGINT,authorization_id VARCHAR(100),logged_in_at TIMESTAMP,PRIMARY KEY(tenant_id,authorization_id))");jdbc.update("INSERT INTO zf_profile VALUES(1,42)");
  var props=new ZenflowProperties();props.setTenantId(1);props.setOauthClientId("zenflow-ios");var audit=new ZenflowLoginAudit(jdbc,props);var user=mock(MatrixUser.class);when(user.getTenantId()).thenReturn(1L);when(user.getId()).thenReturn(42L);when(user.getUserType()).thenReturn("1");
  var client=RegisteredClient.withId(props.getOauthClientId()).clientId(props.getOauthClientId()).clientAuthenticationMethod(ClientAuthenticationMethod.NONE).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("https://example.invalid/callback").build();var now=Instant.parse("2026-10-07T00:00:00Z");
  var authorization=OAuth2Authorization.withRegisteredClient(client).id("fixture-authorization").principalName("fixture").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).attribute(Principal.class.getName(),new UsernamePasswordAuthenticationToken(user,null,List.of())).token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"fixture-token",now,now.plusSeconds(3600))).build();
  audit.issued(authorization);audit.issued(OAuth2Authorization.from(authorization).token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,"fixture-refreshed",now.plusSeconds(1800),now.plusSeconds(5400))).build());
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM zf_user_login_event",Integer.class));assertEquals(now,jdbc.queryForObject("SELECT logged_in_at FROM zf_user_login_event",java.sql.Timestamp.class).toInstant());
  when(user.getUserType()).thenReturn("0");audit.issued(OAuth2Authorization.from(authorization).id("fixture-admin").build());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM zf_user_login_event",Integer.class));
 }
}
