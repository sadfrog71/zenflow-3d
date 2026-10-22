package com.deepdimen.matrix.auth.endpoint;

import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.core.util.R;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 企业微信登录 state 管理接口（防 CSRF）
 *
 * @author matrix
 * @date 2026-03-12
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/wechat-work")
public class WechatWorkStateEndpoint {

	private static final long STATE_EXPIRE_MINUTES = 5L;

	private final RedisTemplate<String, Object> redisTemplate;

	/**
	 * 生成 state 并存入 Redis，前端发起企业微信授权时携带
	 *
	 * @return state 值
	 */
	@GetMapping("/state")
	public R<String> generateState() {
		String state = UUID.randomUUID().toString().replace("-", "");
		String key = String.format(SecurityConstants.WECHAT_WORK_REDIS_STATE_KEY, state);
		redisTemplate.opsForValue().set(key, state, STATE_EXPIRE_MINUTES, TimeUnit.MINUTES);
		return R.ok(state);
	}

	/**
	 * 企业微信内置浏览器静默授权入口
	 * 识别 User-Agent 后重定向到企业微信 snsapi_base 授权页
	 *
	 * @param corpId      企业ID
	 * @param agentId     应用AgentId
	 * @param redirectUri 授权后回调地址（已 URL encode）
	 * @param state       CSRF 防护 state
	 * @return 重定向响应
	 */
	@GetMapping("/silent-auth")
	public R<String> buildSilentAuthUrl(@RequestParam String corpId, @RequestParam String agentId,
			@RequestParam String redirectUri, @RequestParam(required = false) String state) {
		if (state == null) {
			state = UUID.randomUUID().toString().replace("-", "");
			String key = String.format(SecurityConstants.WECHAT_WORK_REDIS_STATE_KEY, state);
			redisTemplate.opsForValue().set(key, state, STATE_EXPIRE_MINUTES, TimeUnit.MINUTES);
		}
		// 构造企业微信 snsapi_base 授权 URL
		String authUrl = String.format(
				"https://open.weixin.qq.com/connect/oauth2/authorize?appid=%s&redirect_uri=%s&response_type=code&scope=snsapi_base&agentid=%s&state=%s#wechat_redirect",
				corpId, redirectUri, agentId, state);
		return R.ok(authUrl);
	}

}
