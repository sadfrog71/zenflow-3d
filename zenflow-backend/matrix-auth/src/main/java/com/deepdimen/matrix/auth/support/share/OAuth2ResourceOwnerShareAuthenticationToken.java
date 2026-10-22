package com.deepdimen.matrix.auth.support.share;

import com.deepdimen.matrix.auth.support.base.OAuth2ResourceOwnerBaseAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.Map;
import java.util.Set;

/**
 * 第三方分享免密登录 Token
 *
 * @author matrix
 * @date 2026-06-29
 */
public class OAuth2ResourceOwnerShareAuthenticationToken extends OAuth2ResourceOwnerBaseAuthenticationToken {

	private Long agentId;

	public OAuth2ResourceOwnerShareAuthenticationToken(AuthorizationGrantType authorizationGrantType,
			Authentication clientPrincipal, Set<String> scopes, Map<String, Object> additionalParameters) {
		super(authorizationGrantType, clientPrincipal, scopes, additionalParameters);
	}

	public Long getAgentId() {
		return agentId;
	}

	public void setAgentId(Long agentId) {
		this.agentId = agentId;
	}

}
