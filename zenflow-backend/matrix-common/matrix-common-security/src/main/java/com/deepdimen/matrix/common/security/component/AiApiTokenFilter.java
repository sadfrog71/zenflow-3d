package com.deepdimen.matrix.common.security.component;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * API Token 鉴权过滤器
 * 从 X-API-Token 请求头提取 Token，验证有效性后构造 SecurityContext
 *
 * @author matrix
 * @date 2025-03-12
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(value = "security.api-token.enabled", havingValue = "true")
public class AiApiTokenFilter extends OncePerRequestFilter {

	@Value("${security.api-token.header-name:X-API-Token}")
	private String headerName;

	private final ApiTokenValidator apiTokenValidator;

	@Override
	protected void doFilterInternal(HttpServletRequest request, @NotNull HttpServletResponse response,
			@NotNull FilterChain filterChain) throws ServletException, IOException {
		String tokenValue = request.getHeader(headerName);
		if (StrUtil.isBlank(tokenValue)) {
			filterChain.doFilter(request, response);
			return;
		}

		ApiTokenValidator.ApiTokenInfo tokenInfo = apiTokenValidator.findValidToken(tokenValue);
		if (tokenInfo == null) {
			log.warn("API Token 验证失败，tokenValue 前缀：{}", StrUtil.subPre(tokenValue, 8));
			response.setStatus(HttpStatus.UNAUTHORIZED.value());
			response.setContentType("application/json;charset=UTF-8");
			response.getWriter().print("{\"code\":401,\"msg\":\"API Token 无效或已过期\"}");
			return;
		}

		// 设置租户上下文（由实现类调用 TenantContextHolder）
		apiTokenValidator.setTenantContext(tokenInfo.getTenantId());

		// 构造轻量级 Authentication，principal 为创建人用户名
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
				tokenInfo.getCreateBy(), null, List.of(new SimpleGrantedAuthority("ROLE_API_TOKEN")));
		SecurityContextHolder.getContext().setAuthentication(authentication);

		try {
			filterChain.doFilter(request, response);
		}
		finally {
			SecurityContextHolder.clearContext();
			apiTokenValidator.clearTenantContext();
			try {
				apiTokenValidator.updateLastUsedTime(tokenInfo.getId());
			}
			catch (Exception e) {
				log.warn("更新 API Token 最后使用时间失败，id：{}", tokenInfo.getId(), e);
			}
		}
	}

}
