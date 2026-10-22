package com.deepdimen.matrix.admin.mobilepush;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.util.SecurityUtils;
import java.security.Principal;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Component;

@Component
public class PushSessionGuard {

	private final OAuth2AuthorizationService authorizations;

	public PushSessionGuard(OAuth2AuthorizationService authorizations) {
		this.authorizations = authorizations;
	}

	public PushIdentity current() {
		MatrixUser user = SecurityUtils.getUser();
		Long tenant = TenantContextHolder.getTenantId();
		String token = SecurityUtils.getToken();
		if (user == null || tenant == null || !Objects.equals(tenant, user.getTenantId()) || token == null)
			throw new AccessDeniedException("请先登录当前租户");
		OAuth2Authorization session = authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
		if (!matches(session, tenant, user.getId()))
			throw new AccessDeniedException("当前会话已失效");
		// Older sessions gain the stable ID index on their first registration after this
		// deployment.
		if (authorizations.findById(session.getId()) == null)
			authorizations.save(session);
		return new PushIdentity(tenant, user.getId(), session.getId());
	}

	public boolean active(String sessionId, long tenantId, long userId) {
		return matches(authorizations.findById(sessionId), tenantId, userId);
	}

	static boolean matches(OAuth2Authorization session, long tenantId, long userId) {
		if (session == null)
			return false;
		boolean active = (session.getRefreshToken() != null && session.getRefreshToken().isActive())
				|| (session.getAccessToken() != null && session.getAccessToken().isActive());
		Authentication authentication = session.getAttribute(Principal.class.getName());
		return active && authentication != null && authentication.getPrincipal() instanceof MatrixUser user
				&& Objects.equals(user.getId(), userId) && Objects.equals(user.getTenantId(), tenantId)
				&& user.isEnabled() && user.isAccountNonLocked();
	}

}
