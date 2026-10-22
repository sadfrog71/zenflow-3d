package com.deepdimen.matrix.auth.endpoint;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.admin.api.feign.RemoteTenantService;
import com.deepdimen.matrix.common.core.constant.enums.UserTypeEnum;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.annotation.Inner;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 管理后台跳转码接口，用于前台免登录跳转后台
 *
 * @author matrix
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class JumpTokenEndpoint {

	private static final String JUMP_TOKEN_PREFIX = "jump:token:";

	private static final String JUMP_RATE_PREFIX = "jump:rate:";

	private static final long JUMP_TOKEN_TTL = 30L;

	private static final int RATE_LIMIT = 5;

	private static final long RATE_WINDOW = 60L;

	private static final String DEFAULT_CLIENT_ID = "matrix";

	private static final AuthorizationGrantType PASSWORD_GRANT = new AuthorizationGrantType("password");

	// 原子性限流 Lua 脚本：INCR + EXPIRE 合并为单次操作
	private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
			"local c = redis.call('INCR', KEYS[1])\n" + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n"
					+ "return c",
			Long.class);

	private final OAuth2AuthorizationService authorizationService;

	private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

	private final RegisteredClientRepository registeredClientRepository;

	private final RedisTemplate<String, Object> redisTemplate;

	private final StringRedisTemplate stringRedisTemplate;

	private final RemoteTenantService remoteTenantService;

	private final AuthorizationServerSettings authorizationServerSettings;

	/**
	 * 生成一次性跳转码，TTL 30s，用于前台跳转后台免登录
	 * @return 跳转码
	 */
	@PostMapping("/jump-token")
	public R<String> generateJumpToken(@RequestParam(required = false) Long tenantId) {
		MatrixUser user = SecurityUtils.getUser();
		if (user == null) {
			return R.failed("未登录");
		}
		Long targetTenantId = tenantId == null ? user.getTenantId() : tenantId;
		if (targetTenantId == null) {
			return R.failed("租户上下文无效");
		}
		if (!targetTenantId.equals(user.getTenantId())) {
			R<Boolean> membership = remoteTenantService.canSwitchTenant(user.getId(), targetTenantId);
			if (membership == null || !membership.isOk() || !Boolean.TRUE.equals(membership.getData())) {
				return R.failed("无权切换到目标租户");
			}
		}

		// 限流：每分钟最多 5 次，使用 Lua 脚本保证原子性
		String rateKey = JUMP_RATE_PREFIX + user.getId();
		Long count = stringRedisTemplate.execute(RATE_LIMIT_SCRIPT, Collections.singletonList(rateKey),
				String.valueOf(RATE_WINDOW));
		if (count != null && count > RATE_LIMIT) {
			log.warn("用户 {} 触发跳转码生成限流，当前计数: {}", user.getId(), count);
			return R.failed("操作过于频繁，请稍后再试");
		}

		// 生成跳转码，存入 Redis
		String code = IdUtil.randomUUID();
		Map<String, Object> userInfo = new HashMap<>();
		userInfo.put("userId", user.getId());
		userInfo.put("username", user.getUsername());
		userInfo.put("tenantId", targetTenantId);
		userInfo.put("clientId", resolveClientId(user));
		redisTemplate.opsForValue().set(JUMP_TOKEN_PREFIX + code, JSONUtil.toJsonStr(userInfo), JUMP_TOKEN_TTL,
				TimeUnit.SECONDS);
		return R.ok(code);
	}

	/**
	 * 验证跳转码，返回 OAuth2 Token，验证后立即失效（一次性）
	 * @param code 跳转码
	 * @return OAuth2 Token 信息
	 */
	@Inner(value = false)
	@GetMapping("/verify-jump-token")
	public ResponseEntity<Map<String, Object>> verifyJumpToken(@RequestParam String code) {
		if (!StringUtils.hasText(code)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "invalid_code", "error_description", "跳转码无效或已过期"));
		}
		String key = JUMP_TOKEN_PREFIX + code;
		Object cached = redisTemplate.opsForValue().getAndDelete(key);
		if (cached == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(Map.of("error", "invalid_code", "error_description", "跳转码无效或已过期"));
		}

		Map<String, Object> userInfo = JSONUtil.parseObj(cached.toString()).toBean(Map.class);
		if (userInfo.get("userId") == null || userInfo.get("username") == null || userInfo.get("tenantId") == null) {
			log.error("跳转码对应的用户信息不完整，code前缀: {}", code.substring(0, Math.min(8, code.length())));
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "server_error", "error_description", "服务器内部错误"));
		}
		Long userId = Long.valueOf(userInfo.get("userId").toString());
		String username = userInfo.get("username").toString();
		Long tenantId = Long.valueOf(userInfo.get("tenantId").toString());
		Object cachedClientId = userInfo.get("clientId");
		String clientId = cachedClientId != null && StringUtils.hasText(cachedClientId.toString())
				? cachedClientId.toString() : DEFAULT_CLIENT_ID;

		RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
		if (registeredClient == null) {
			log.error("未找到客户端配置: {}", clientId);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "server_error", "error_description", "服务器内部错误"));
		}

		// 构建用户 principal（CustomeOAuth2TokenCustomizer 需要 MatrixUser）
		MatrixUser matrixUser = new MatrixUser(userId, username, null, null, null, null, null, null, null, null, null,
				tenantId, "", true, true, UserTypeEnum.TOB.getStatus(), true, null, true, Collections.emptyList());
		UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(matrixUser, null,
				Collections.emptyList());

		// authorizationGrant 的 getName() 返回 clientId，供 CustomeOAuth2TokenCustomizer 使用
		UsernamePasswordAuthenticationToken authorizationGrant = new UsernamePasswordAuthenticationToken(clientId,
				null);

		AuthorizationServerContext serverContext = AuthorizationServerContextHolder.getContext();
		if (serverContext == null) {
			serverContext = new AuthorizationServerContext() {
				@Override
				public String getIssuer() {
					return authorizationServerSettings.getIssuer();
				}

				@Override
				public AuthorizationServerSettings getAuthorizationServerSettings() {
					return authorizationServerSettings;
				}
			};
		}
		DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
			.registeredClient(registeredClient)
			.principal(principal)
			.authorizedScopes(registeredClient.getScopes())
			.authorizationGrantType(PASSWORD_GRANT)
			.authorizationGrant(authorizationGrant);
		tokenContextBuilder.authorizationServerContext(serverContext);

		// 生成 access token
		OAuth2TokenContext accessTokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build();
		OAuth2Token generatedAccessToken = tokenGenerator.generate(accessTokenContext);
		if (generatedAccessToken == null) {
			log.error("access token 生成失败，clientId: {}, username: {}", clientId, username);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Map.of("error", "server_error", "error_description", "Token生成失败"));
		}

		OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
				generatedAccessToken.getTokenValue(), generatedAccessToken.getIssuedAt(),
				generatedAccessToken.getExpiresAt(), registeredClient.getScopes());

		OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
			.principalName(username)
			.authorizationGrantType(PASSWORD_GRANT)
			.authorizedScopes(registeredClient.getScopes())
			.attribute(Principal.class.getName(), principal);

		if (generatedAccessToken instanceof ClaimAccessor) {
			authorizationBuilder.id(accessToken.getTokenValue())
				.token(accessToken, metadata -> metadata.put(OAuth2Authorization.Token.CLAIMS_METADATA_NAME,
						((ClaimAccessor) generatedAccessToken).getClaims()));
		}
		else {
			authorizationBuilder.id(accessToken.getTokenValue()).accessToken(accessToken);
		}

		// 生成 refresh token
		OAuth2RefreshToken refreshToken = null;
		if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
			OAuth2TokenContext refreshTokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN)
				.build();
			OAuth2Token generatedRefreshToken = tokenGenerator.generate(refreshTokenContext);
			if (generatedRefreshToken instanceof OAuth2RefreshToken) {
				refreshToken = (OAuth2RefreshToken) generatedRefreshToken;
				authorizationBuilder.refreshToken(refreshToken);
			}
		}

		authorizationService.save(authorizationBuilder.build());

		// 返回格式与 /oauth2/token 一致
		Map<String, Object> result = new HashMap<>();
		result.put("access_token", accessToken.getTokenValue());
		result.put("token_type", "Bearer");
		result.put("expires_in", ChronoUnit.SECONDS.between(accessToken.getIssuedAt(), accessToken.getExpiresAt()));
		if (refreshToken != null) {
			result.put("refresh_token", refreshToken.getTokenValue());
		}
		result.put("scope", String.join(" ", registeredClient.getScopes()));
		return ResponseEntity.ok(result);
	}

	private static String resolveClientId(MatrixUser user) {
		Object clientId = user.getAttribute("client_id");
		return clientId != null && StringUtils.hasText(clientId.toString())
				? clientId.toString() : DEFAULT_CLIENT_ID;
	}

}
