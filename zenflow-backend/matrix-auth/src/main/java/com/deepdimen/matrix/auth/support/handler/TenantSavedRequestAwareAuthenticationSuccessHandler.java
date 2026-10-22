package com.deepdimen.matrix.auth.support.handler;

import com.deepdimen.matrix.common.core.util.KeyStrResolver;
import com.deepdimen.matrix.common.core.util.SpringContextHolder;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import java.io.IOException;

/**
 * @author caoyuan
 * @date 2021/2/7
 *
 * 增强成功回调增加租户上下文避免极端情况下丢失问题
 * @see SavedRequestAwareAuthenticationSuccessHandler
 */
@Slf4j
public class TenantSavedRequestAwareAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private final RequestCache requestCache = new HttpSessionRequestCache();

	public TenantSavedRequestAwareAuthenticationSuccessHandler() {
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws ServletException, IOException {
		SavedRequest savedRequest = this.requestCache.getRequest(request, response);
		if (savedRequest == null) {
			super.onAuthenticationSuccess(request, response, authentication);
			return;
		}

		if (isAlwaysUseDefaultTargetUrl()) {
			this.requestCache.removeRequest(request, response);
			super.onAuthenticationSuccess(request, response, authentication);
			return;
		}
		else {
			this.clearAuthenticationAttributes(request);
			// 增加租户信息
			String redirectUrl = savedRequest.getRedirectUrl();
			String separator = redirectUrl.contains("?") ? "&" : "?";
			String targetUrl = redirectUrl + separator + "TENANT-ID="
					+ SpringContextHolder.getBean(KeyStrResolver.class).key();

			this.getRedirectStrategy().sendRedirect(request, response, targetUrl);
		}
	}

}
