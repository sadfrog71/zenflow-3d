package com.deepdimen.matrix.auth.endpoint;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deepdimen.matrix.admin.api.feign.RemoteTenantService;
import com.deepdimen.matrix.common.core.constant.enums.UserTypeEnum;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

class JumpTokenEndpointTenantSwitchTest {

	private static final Long USER_ID = 1L;
	private static final Long CURRENT_TENANT_ID = 1L;
	private static final Long TARGET_TENANT_ID = 2052188107445968897L;

	private final OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
	private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator = mock(OAuth2TokenGenerator.class);
	private final RegisteredClientRepository registeredClientRepository = mock(RegisteredClientRepository.class);
	private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
	private final StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
	private final RemoteTenantService remoteTenantService = mock(RemoteTenantService.class);
	private final AuthorizationServerSettings authorizationServerSettings = AuthorizationServerSettings.builder()
			.issuer("http://localhost:10199").build();
	private final ValueOperations<String, Object> valueOperations = mock(ValueOperations.class);

	private JumpTokenEndpoint endpoint;

	@BeforeEach
	void setUp() {
		MatrixUser user = new MatrixUser(USER_ID, "admin", List.of(), null, List.of(), List.of(), null, null, null,
			null, null, CURRENT_TENANT_ID, "", true, true, "", true, null, true, List.of());
		SecurityContextHolder.getContext().setAuthentication(
			new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
		when(stringRedisTemplate.execute(any(), anyList(), any())).thenReturn(1L);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		endpoint = new JumpTokenEndpoint(authorizationService, tokenGenerator, registeredClientRepository,
			redisTemplate, stringRedisTemplate, remoteTenantService, authorizationServerSettings);
	}

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void exposesOnlyOneTimeCodeExchangeWithoutAuthentication() throws NoSuchMethodException {
		Inner inner = JumpTokenEndpoint.class.getDeclaredMethod("verifyJumpToken", String.class)
			.getAnnotation(Inner.class);

		assertNotNull(inner);
		assertFalse(inner.value());
		assertFalse(JumpTokenEndpoint.class.getDeclaredMethod("generateJumpToken", Long.class)
			.isAnnotationPresent(Inner.class));
	}

	@Test
	void issuesOneTimeCodeForAuthorizedTargetTenant() {
		when(remoteTenantService.canSwitchTenant(USER_ID, TARGET_TENANT_ID)).thenReturn(R.ok(true));
		MatrixUser currentUser = (MatrixUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
		currentUser.getAttributes().put("client_id", "matrix-mobile");

		R<String> result = endpoint.generateJumpToken(TARGET_TENANT_ID);

		assertTrue(result.isOk());
		assertNotNull(result.getData());
		verify(valueOperations).set(eq("jump:token:" + result.getData()),
			org.mockito.ArgumentMatchers.argThat(value -> value != null
				&& value.toString().contains("\"tenantId\":" + TARGET_TENANT_ID)
				&& value.toString().contains("\"clientId\":\"matrix-mobile\"")),
			eq(30L), eq(TimeUnit.SECONDS));
	}

	@Test
	void rejectsTargetTenantWithoutMembership() {
		when(remoteTenantService.canSwitchTenant(USER_ID, TARGET_TENANT_ID)).thenReturn(R.ok(false));

		R<String> result = endpoint.generateJumpToken(TARGET_TENANT_ID);

		assertFalse(result.isOk());
		verify(redisTemplate, never()).opsForValue();
	}

	@Test
	void consumesOneTimeCodeWithoutFilterBoundAuthorizationServerContext() {
		RegisteredClient client = RegisteredClient.withId("matrix-mobile-client")
				.clientId("matrix-mobile")
				.authorizationGrantType(new AuthorizationGrantType("password"))
				.scope("server")
				.build();
		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
				"target-access-token", Instant.now(), Instant.now().plusSeconds(3600), client.getScopes());
		when(valueOperations.getAndDelete("jump:token:switch-code")).thenReturn(
				"{\"userId\":1,\"username\":\"admin\",\"tenantId\":" + TARGET_TENANT_ID
						+ ",\"clientId\":\"matrix-mobile\"}");
		when(registeredClientRepository.findByClientId("matrix-mobile")).thenReturn(client);
		when(tokenGenerator.generate(any())).thenReturn(accessToken);

		var response = endpoint.verifyJumpToken("switch-code");

		assertTrue(response.getStatusCode().is2xxSuccessful());
		assertNotNull(response.getBody());
		assertTrue(response.getBody().containsKey("access_token"));
		verify(authorizationService).save(org.mockito.ArgumentMatchers.argThat(authorization -> {
			UsernamePasswordAuthenticationToken principal = authorization.getAttribute(Principal.class.getName());
			if (principal == null || !(principal.getPrincipal() instanceof MatrixUser user)) {
				return false;
			}
			assertEquals(TARGET_TENANT_ID, user.getTenantId());
			assertEquals(UserTypeEnum.TOB.getStatus(), user.getUserType());
			return true;
		}));
	}

	@Test
	void consumesLegacyCodeUsingTheWebClientFallback() {
		RegisteredClient client = RegisteredClient.withId("matrix-client")
				.clientId("matrix")
				.authorizationGrantType(new AuthorizationGrantType("password"))
				.scope("server")
				.build();
		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
				"legacy-access-token", Instant.now(), Instant.now().plusSeconds(3600), client.getScopes());
		when(valueOperations.getAndDelete("jump:token:legacy-code")).thenReturn(
				"{\"userId\":1,\"username\":\"admin\",\"tenantId\":" + TARGET_TENANT_ID + "}");
		when(registeredClientRepository.findByClientId("matrix")).thenReturn(client);
		when(tokenGenerator.generate(any())).thenReturn(accessToken);

		var response = endpoint.verifyJumpToken("legacy-code");

		assertTrue(response.getStatusCode().is2xxSuccessful());
		verify(registeredClientRepository).findByClientId("matrix");
	}
}
