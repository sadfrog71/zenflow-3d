package com.deepdimen.matrix.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.admin.api.dto.EmbedAuthorizeDTO;
import com.deepdimen.matrix.admin.api.entity.SysUser;
import com.deepdimen.matrix.admin.api.feign.RemoteAgentService;
import com.deepdimen.matrix.admin.api.vo.AgentShareValidationVO;
import com.deepdimen.matrix.admin.api.vo.EmbedAuthorizeVO;
import com.deepdimen.matrix.admin.api.vo.EmbedCodeContext;
import com.deepdimen.matrix.admin.service.EmbedAuthorizationService;
import com.deepdimen.matrix.admin.service.SysUserService;
import com.deepdimen.matrix.common.core.constant.CommonConstants;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.component.ApiTokenValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Collections;

/**
 * 嵌入授权服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbedAuthorizationServiceImpl implements EmbedAuthorizationService {

	private static final int CODE_BYTES = 32;

	private static final long RATE_WINDOW_SECONDS = 60L;

	private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
			"local c = redis.call('INCR', KEYS[1])\n" + "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n"
					+ "return c",
			Long.class);

	private final ApiTokenValidator apiTokenValidator;

	private final SysUserService sysUserService;

	private final RemoteAgentService remoteAgentService;

	private final RedisTemplate<String, Object> redisTemplate;

	private final SecureRandom secureRandom = new SecureRandom();

	@Value("${matrix.embed-auth.code-ttl-seconds:60}")
	private long codeTtlSeconds;

	@Value("${matrix.embed-auth.base-url:http://localhost:8889}")
	private String baseUrl;

	@Override
	public EmbedAuthorizeVO authorize(String apiToken, EmbedAuthorizeDTO dto, String clientIp, String userAgent) {
		ApiTokenValidator.ApiTokenInfo tokenInfo = apiTokenValidator.findValidToken(apiToken);
		if (tokenInfo == null) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "接入凭证无效");
		}

		apiTokenValidator.setTenantContext(tokenInfo.getTenantId());
		try {
			checkRateLimit("token:" + tokenInfo.getId(), 120);
			checkRateLimit("ip:" + sha256(clientIp), 60);
			checkRateLimit("user:" + tokenInfo.getTenantId() + ":" + dto.username(), 10);

			SysUser user = sysUserService.getOne(Wrappers.<SysUser>lambdaQuery()
				.eq(SysUser::getUsername, dto.username())
				.eq(SysUser::getLockFlag, CommonConstants.STATUS_NORMAL));
			if (user == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户不可用");
			}

			R<AgentShareValidationVO> agentResult = remoteAgentService.validateShareAgent(dto.agentId());
			AgentShareValidationVO agent = agentResult == null ? null : agentResult.getData();
			if (agent == null || !agent.isAvailable() || !tokenInfo.getTenantId().equals(agent.getTenantId())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "智能体不可用");
			}

			byte[] randomBytes = new byte[CODE_BYTES];
			secureRandom.nextBytes(randomBytes);
			String code = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
			String key = SecurityConstants.EMBED_CODE_KEY_PREFIX + sha256(code);
			EmbedCodeContext context = new EmbedCodeContext(tokenInfo.getTenantId(), user.getUsername(), dto.agentId(),
					tokenInfo.getId(), LocalDateTime.now());
			redisTemplate.opsForValue().set(key, context, Duration.ofSeconds(codeTtlSeconds));
			apiTokenValidator.updateLastUsedTime(tokenInfo.getId());
			log.info("生成嵌入授权码, apiTokenId={}, tenantId={}, username={}, agentId={}, clientIp={}",
					tokenInfo.getId(), tokenInfo.getTenantId(), user.getUsername(), dto.agentId(), clientIp);
			String embedUrl = baseUrl.replaceAll("/$", "") + "/#/embed/chat?code=" + code;
			return new EmbedAuthorizeVO(code, codeTtlSeconds, embedUrl);
		}
		finally {
			apiTokenValidator.clearTenantContext();
		}
	}

	private void checkRateLimit(String dimension, int limit) {
		String key = "matrix:embed:rate:" + dimension;
		Long count = redisTemplate.execute(RATE_LIMIT_SCRIPT, Collections.singletonList(key),
				String.valueOf(RATE_WINDOW_SECONDS));
		if (count != null && count > limit) {
			log.warn("嵌入授权码申请触发限流, dimension={}", dimension);
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁");
		}
	}

	private String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256算法不可用", e);
		}
	}

}
