package com.deepdimen.matrix.admin.mobilepush;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import com.deepdimen.matrix.common.security.service.MatrixRedisOAuth2AuthorizationService;

class PushAuthorizationIndexTest {

	@Test
	void sessionIndexSurvivesUntilTheLastTokenExpiresAndIsRemovedOnLogout() {
		RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
		ValueOperations<String, Object> values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		var service = new MatrixRedisOAuth2AuthorizationService(redis);
		Instant now = Instant.now();
		var client = RegisteredClient.withId("client-id")
			.clientId("matrix-mobile")
			.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
			.build();
		var authorization = OAuth2Authorization.withRegisteredClient(client)
			.id("stable-id")
			.principalName("user")
			.authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
			.accessToken(
					new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access", now, now.plusSeconds(3600)))
			.refreshToken(new OAuth2RefreshToken("refresh", now, now.plusSeconds(60)))
			.build();
		service.save(authorization);
		ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
		verify(values).set(eq("token::id::stable-id"), same(authorization), ttl.capture());
		assertTrue(ttl.getValue().toSeconds() > 3500);
		when(values.get("token::id::stable-id")).thenReturn(authorization);
		assertSame(authorization, service.findById("stable-id"));
		service.remove(authorization);
		ArgumentCaptor<Collection> deleted = ArgumentCaptor.forClass(Collection.class);
		verify(redis).delete(deleted.capture());
		assertTrue(deleted.getValue().contains("token::id::stable-id"));
		assertTrue(deleted.getValue().contains("token::access_token::access"));
		assertTrue(deleted.getValue().contains("token::refresh_token::refresh"));
	}

}
