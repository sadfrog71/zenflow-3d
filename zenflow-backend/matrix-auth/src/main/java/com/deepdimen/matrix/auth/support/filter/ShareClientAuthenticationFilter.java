package com.deepdimen.matrix.auth.support.filter;

import cn.hutool.core.util.StrUtil;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 分享免密登录只免用户密码，不要求第三方 iframe 携带 OAuth2 客户端认证头。
 */
@Component
public class ShareClientAuthenticationFilter extends OncePerRequestFilter {

	private static final String SHARE_CLIENT = "social:social";

	private static final String SHARE_BASIC_AUTH = "Basic "
			+ Base64.getEncoder().encodeToString(SHARE_CLIENT.getBytes(StandardCharsets.UTF_8));

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!isShareTokenRequest(request) || StrUtil.isNotBlank(request.getHeader(HttpHeaders.AUTHORIZATION))) {
			filterChain.doFilter(request, response);
			return;
		}

		filterChain.doFilter(new ShareClientAuthenticationRequestWrapper(request), response);
	}

	private boolean isShareTokenRequest(HttpServletRequest request) {
		return SecurityConstants.OAUTH_TOKEN_URL.equals(request.getServletPath())
				&& StrUtil.equals(SecurityConstants.SHARE, request.getParameter(OAuth2ParameterNames.GRANT_TYPE));
	}

	private static class ShareClientAuthenticationRequestWrapper extends HttpServletRequestWrapper {

		ShareClientAuthenticationRequestWrapper(HttpServletRequest request) {
			super(request);
		}

		@Override
		public String getHeader(String name) {
			if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
				return SHARE_BASIC_AUTH;
			}
			return super.getHeader(name);
		}

		@Override
		public Enumeration<String> getHeaders(String name) {
			if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
				return Collections.enumeration(Collections.singletonList(SHARE_BASIC_AUTH));
			}
			return super.getHeaders(name);
		}

		@Override
		public Enumeration<String> getHeaderNames() {
			Set<String> names = new LinkedHashSet<>();
			Enumeration<String> headerNames = super.getHeaderNames();
			while (headerNames.hasMoreElements()) {
				names.add(headerNames.nextElement());
			}
			names.add(HttpHeaders.AUTHORIZATION);
			return Collections.enumeration(names);
		}

	}

}
