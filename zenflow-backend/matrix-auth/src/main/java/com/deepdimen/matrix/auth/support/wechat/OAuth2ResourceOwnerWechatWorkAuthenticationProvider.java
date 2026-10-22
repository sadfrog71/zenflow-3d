package com.deepdimen.matrix.auth.support.wechat;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.deepdimen.matrix.admin.api.entity.AiAuthConfigEntity;
import com.deepdimen.matrix.admin.api.feign.RemoteAuthConfigService;
import com.deepdimen.matrix.auth.support.base.OAuth2ResourceOwnerBaseAuthenticationProvider;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.core.util.R;
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
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信登录 Provider
 *
 * @author matrix
 * @date 2026-03-12
 */
@Slf4j
public class OAuth2ResourceOwnerWechatWorkAuthenticationProvider
		extends OAuth2ResourceOwnerBaseAuthenticationProvider<OAuth2ResourceOwnerWechatWorkAuthenticationToken> {

	private static final String ACCESS_TOKEN_URL = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=%s&corpsecret=%s";

	private static final String GET_USER_INFO_URL = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token=%s&code=%s";

	private static final String REDIS_ACCESS_TOKEN_KEY = "wechat_work:access_token:%s";

	private static final long ACCESS_TOKEN_EXPIRE_SECONDS = 7000L;

	private final RemoteAuthConfigService remoteAuthConfigService;

	private final RedisTemplate<String, Object> redisTemplate;

	public OAuth2ResourceOwnerWechatWorkAuthenticationProvider(AuthenticationManager authenticationManager,
			OAuth2AuthorizationService authorizationService,
			OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
			RemoteAuthConfigService remoteAuthConfigService, RedisTemplate<String, Object> redisTemplate) {
		super(authenticationManager, authorizationService, tokenGenerator);
		this.remoteAuthConfigService = remoteAuthConfigService;
		this.redisTemplate = redisTemplate;
	}

	@Override
	public boolean supports(Class<?> authentication) {
		boolean supports = OAuth2ResourceOwnerWechatWorkAuthenticationToken.class.isAssignableFrom(authentication);
		log.debug("supports authentication={} returning {}", authentication, supports);
		return supports;
	}

	@Override
	public void checkClient(RegisteredClient registeredClient) {
		assert registeredClient != null;
		if (!registeredClient.getAuthorizationGrantTypes()
			.contains(new AuthorizationGrantType(SecurityConstants.WECHAT_WORK))) {
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
		}
	}

	@Override
	public UsernamePasswordAuthenticationToken buildToken(Map<String, Object> reqParameters) {
		String code = (String) reqParameters.get(SecurityConstants.WECHAT_WORK_CODE);
		String state = (String) reqParameters.get(SecurityConstants.WECHAT_WORK_STATE);

		// 强制验证 state 防 CSRF，state 不能为空
		if (!StringUtils.hasText(state)) {
			log.warn("企业微信登录缺少 state 参数");
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
		}
		String stateKey = String.format(SecurityConstants.WECHAT_WORK_REDIS_STATE_KEY, state);
		Object storedState = redisTemplate.opsForValue().get(stateKey);
		if (storedState == null) {
			log.warn("企业微信登录 state 无效或已过期: {}", state);
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
		}
		redisTemplate.delete(stateKey);

		// 获取租户ID（从请求参数中取，默认为0）
		Long tenantId = 0L;
		Object tenantIdParam = reqParameters.get("tenantId");
		if (tenantIdParam != null) {
			try {
				tenantId = Long.parseLong(tenantIdParam.toString());
			}
			catch (NumberFormatException e) {
				log.warn("企业微信登录 tenantId 参数格式错误: {}", tenantIdParam);
				throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_REQUEST);
			}
		}

		// 通过 Feign 获取企业微信配置
		R<AiAuthConfigEntity> configResult = remoteAuthConfigService.getByTenantAndType(tenantId,
				SecurityConstants.WECHAT_WORK);
		AiAuthConfigEntity config = configResult.getData();
		if (config == null || !StringUtils.hasText(config.getCorpId())) {
			log.error("企业微信认证配置不存在，tenantId={}", tenantId);
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
		}

		// 获取 access_token（优先读 Redis 缓存）
		String accessTokenKey = String.format(REDIS_ACCESS_TOKEN_KEY, config.getCorpId());
		String accessToken = (String) redisTemplate.opsForValue().get(accessTokenKey);
		if (!StringUtils.hasText(accessToken)) {
			String tokenResult = HttpUtil.get(
					String.format(ACCESS_TOKEN_URL, config.getCorpId(), config.getSecretEncrypted()));
			log.debug("获取企业微信 access_token 响应: {}", tokenResult);
			accessToken = JSONUtil.parseObj(tokenResult).getStr("access_token");
			if (!StringUtils.hasText(accessToken)) {
				log.error("获取企业微信 access_token 失败: {}", tokenResult);
				throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
			}
			redisTemplate.opsForValue().set(accessTokenKey, accessToken, ACCESS_TOKEN_EXPIRE_SECONDS,
					TimeUnit.SECONDS);
		}

		// 用 access_token + code 获取企业微信 userId
		String userInfoResult = HttpUtil.get(String.format(GET_USER_INFO_URL, accessToken, code));
		log.debug("获取企业微信 userId 响应: {}", userInfoResult);
		String userId = JSONUtil.parseObj(userInfoResult).getStr("userid");
		if (!StringUtils.hasText(userId)) {
			log.warn("企业微信 userId 为空，code={}", code);
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
		}

		// 以 wxCpUserid 作为用户名，交由 UserDetailsService 按 wxCpUserid 字段查找用户
		return new UsernamePasswordAuthenticationToken(userId, null);
	}

}
