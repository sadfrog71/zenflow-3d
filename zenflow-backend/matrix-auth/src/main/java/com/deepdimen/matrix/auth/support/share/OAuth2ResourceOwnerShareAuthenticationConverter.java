package com.deepdimen.matrix.auth.support.share;

import com.deepdimen.matrix.auth.support.base.OAuth2ResourceOwnerBaseAuthenticationConverter;
import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.security.util.OAuth2EndpointUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * 第三方分享免密登录转换器
 *
 * @author matrix
 * @date 2026-06-29
 */
public class OAuth2ResourceOwnerShareAuthenticationConverter
		extends OAuth2ResourceOwnerBaseAuthenticationConverter<OAuth2ResourceOwnerShareAuthenticationToken> {

	@Override
	public boolean support(String grantType) {
		return SecurityConstants.SHARE.equals(grantType);
	}

	@Override
	public OAuth2ResourceOwnerShareAuthenticationToken buildToken(Authentication clientPrincipal,
			Set<String> requestedScopes, Map<String, Object> additionalParameters) {
		return new OAuth2ResourceOwnerShareAuthenticationToken(new AuthorizationGrantType(SecurityConstants.SHARE),
				clientPrincipal, requestedScopes, additionalParameters);
	}

	@Override
	public void checkParams(HttpServletRequest request) {
		MultiValueMap<String, String> parameters = OAuth2EndpointUtils.getParameters(request);
		String code = parameters.getFirst(SecurityConstants.SHARE_CODE);
		if (!StringUtils.hasText(code) || parameters.get(SecurityConstants.SHARE_CODE).size() != 1) {
			OAuth2EndpointUtils.throwError(OAuth2ErrorCodes.INVALID_REQUEST, SecurityConstants.SHARE_CODE,
					OAuth2EndpointUtils.ACCESS_TOKEN_REQUEST_ERROR_URI);
		}
	}

}
