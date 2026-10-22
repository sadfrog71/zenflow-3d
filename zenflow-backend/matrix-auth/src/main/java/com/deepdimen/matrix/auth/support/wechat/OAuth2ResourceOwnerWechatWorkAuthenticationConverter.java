package com.deepdimen.matrix.auth.support.wechat;

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
 * 企业微信登录转换器
 *
 * @author matrix
 * @date 2026-03-12
 */
public class OAuth2ResourceOwnerWechatWorkAuthenticationConverter
		extends OAuth2ResourceOwnerBaseAuthenticationConverter<OAuth2ResourceOwnerWechatWorkAuthenticationToken> {

	@Override
	public boolean support(String grantType) {
		return SecurityConstants.WECHAT_WORK.equals(grantType);
	}

	@Override
	public OAuth2ResourceOwnerWechatWorkAuthenticationToken buildToken(Authentication clientPrincipal,
			Set requestedScopes, Map additionalParameters) {
		return new OAuth2ResourceOwnerWechatWorkAuthenticationToken(
				new AuthorizationGrantType(SecurityConstants.WECHAT_WORK), clientPrincipal, requestedScopes,
				additionalParameters);
	}

	@Override
	public void checkParams(HttpServletRequest request) {
		MultiValueMap<String, String> parameters = OAuth2EndpointUtils.getParameters(request);
		String code = parameters.getFirst(SecurityConstants.WECHAT_WORK_CODE);
		if (!StringUtils.hasText(code) || parameters.get(SecurityConstants.WECHAT_WORK_CODE).size() != 1) {
			OAuth2EndpointUtils.throwError(OAuth2ErrorCodes.INVALID_REQUEST, SecurityConstants.WECHAT_WORK_CODE,
					OAuth2EndpointUtils.ACCESS_TOKEN_REQUEST_ERROR_URI);
		}
	}

}
