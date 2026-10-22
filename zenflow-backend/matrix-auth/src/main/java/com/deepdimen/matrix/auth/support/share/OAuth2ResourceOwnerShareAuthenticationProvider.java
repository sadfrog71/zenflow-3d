package com.deepdimen.matrix.auth.support.share;

import com.deepdimen.matrix.admin.api.feign.RemoteAgentService;
import com.deepdimen.matrix.admin.api.feign.RemoteApiTokenService;
import com.deepdimen.matrix.admin.api.vo.AgentShareValidationVO;
import com.deepdimen.matrix.admin.api.vo.ApiTokenValidationVO;
import com.deepdimen.matrix.admin.api.vo.EmbedCodeContext;
import com.deepdimen.matrix.auth.support.base.OAuth2ResourceOwnerBaseAuthenticationProvider;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 使用一次性授权码的分享登录 Provider
 */
@Slf4j
public class OAuth2ResourceOwnerShareAuthenticationProvider
		extends OAuth2ResourceOwnerBaseAuthenticationProvider<OAuth2ResourceOwnerShareAuthenticationToken> {

	private final RemoteAgentService remoteAgentService;

	private final RemoteApiTokenService remoteApiTokenService;

	private final RedisTemplate<String, Object> redisTemplate;

	public OAuth2ResourceOwnerShareAuthenticationProvider(AuthenticationManager authenticationManager,
			OAuth2AuthorizationService authorizationService,
			OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator, RemoteAgentService remoteAgentService,
			RemoteApiTokenService remoteApiTokenService, RedisTemplate<String, Object> redisTemplate) {
		super(authenticationManager, authorizationService, tokenGenerator);
		this.remoteAgentService = remoteAgentService;
		this.remoteApiTokenService = remoteApiTokenService;
		this.redisTemplate = redisTemplate;
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return OAuth2ResourceOwnerShareAuthenticationToken.class.isAssignableFrom(authentication);
	}

	@Override
	public void checkClient(RegisteredClient registeredClient) {
		if (registeredClient == null || !registeredClient.getAuthorizationGrantTypes()
			.contains(new AuthorizationGrantType(SecurityConstants.SHARE))) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
		}
	}

	@Override
	public UsernamePasswordAuthenticationToken buildToken(Map<String, Object> reqParameters) {
		throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
	}

	@Override
	protected UsernamePasswordAuthenticationToken buildToken(
			OAuth2ResourceOwnerShareAuthenticationToken authentication, Map<String, Object> reqParameters) {
		String code = (String) reqParameters.get(SecurityConstants.SHARE_CODE);
		String codeHash = sha256(code);
		Object value = redisTemplate.opsForValue()
			.getAndDelete(SecurityConstants.EMBED_CODE_KEY_PREFIX + codeHash);
		if (!(value instanceof EmbedCodeContext context)) {
			log.warn("分享授权码无效, codeHashPrefix={}", codeHash.substring(0, 8));
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
		}

		R<ApiTokenValidationVO> tokenResult = remoteApiTokenService.findValidById(context.getApiTokenId());
		ApiTokenValidationVO token = tokenResult == null ? null : tokenResult.getData();
		R<AgentShareValidationVO> agentResult = remoteAgentService.validateShareAgent(context.getAgentId());
		AgentShareValidationVO agent = agentResult == null ? null : agentResult.getData();
		if (token == null || agent == null || !agent.isAvailable()
				|| !context.getTenantId().equals(token.getTenantId())
				|| !context.getTenantId().equals(agent.getTenantId())) {
			log.warn("分享授权码上下文失效, apiTokenId={}, tenantId={}, agentId={}", context.getApiTokenId(),
					context.getTenantId(), context.getAgentId());
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
		}

		authentication.setAgentId(context.getAgentId());
		TenantContextHolder.setTenantId(Long.parseLong(context.getTenantId()));
		return new UsernamePasswordAuthenticationToken(context.getUsername(), null);
	}

	@Override
	protected Map<String, Object> tokenResponseAdditionalParameters(
			OAuth2ResourceOwnerShareAuthenticationToken authentication, Map<String, Object> accessTokenClaims) {
		Map<String, Object> result = new LinkedHashMap<>(accessTokenClaims);
		result.put(SecurityConstants.SHARE_AGENT_ID_RESPONSE, authentication.getAgentId());
		return result;
	}

	private String sha256(String value) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256算法不可用", e);
		}
	}

}
