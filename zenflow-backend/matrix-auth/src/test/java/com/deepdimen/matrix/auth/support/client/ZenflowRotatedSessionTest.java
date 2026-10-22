package com.deepdimen.matrix.auth.support.client;
import com.deepdimen.matrix.common.security.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.*;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ZenflowRotatedSessionTest {
    @Test void oldIndexCannotReviveRotatedOrLoggedOutSession() throws Exception {
        @SuppressWarnings("unchecked") RedisTemplate<String,Object> redis=mock(RedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String,Object> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var service=new MatrixRedisOAuth2AuthorizationService(redis);
        var field=service.getClass().getDeclaredField("zenflowClientId");field.setAccessible(true);field.set(service,"zenflow-ios");
        service.setAuthorizationValidators(List.of(new MatrixAuthorizationValidator(){
            public boolean supports(String c){return true;} public boolean accepts(OAuth2Authorization a,OAuth2TokenType t){return true;}
        }));
        var client=RegisteredClient.withId("zenflow-ios").clientId("zenflow-ios").clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("zenflow://auth/callback").build();
        Instant now=Instant.now();
        var old=OAuth2Authorization.withRegisteredClient(client).id("session").principalName("fixture").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .token(new OAuth2RefreshToken("old",now,now.plusSeconds(60))).build();
        var current=OAuth2Authorization.from(old).token(new OAuth2RefreshToken("new",now,now.plusSeconds(60))).build();
        when(values.get("token::refresh_token::old")).thenReturn(old);
        when(values.get("token::refresh_token::new")).thenReturn(current);
        when(values.get("token::id::session")).thenReturn(current);
        assertNull(service.findByToken("old",OAuth2TokenType.REFRESH_TOKEN));
        assertNotNull(service.findByToken("new",OAuth2TokenType.REFRESH_TOKEN));
        when(values.get("token::id::session")).thenReturn(null);
        assertNull(service.findByToken("new",OAuth2TokenType.REFRESH_TOKEN));
    }
}
