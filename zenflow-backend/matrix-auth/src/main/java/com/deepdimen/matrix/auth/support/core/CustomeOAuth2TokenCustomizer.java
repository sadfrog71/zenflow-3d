package com.deepdimen.matrix.auth.support.core;

import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenClaimsSet;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/**
 * token 输出增强
 *
 * @author caoyuan
 * @date 2022/6/3
 */
public class CustomeOAuth2TokenCustomizer implements OAuth2TokenCustomizer<OAuth2TokenClaimsContext> {

	/**
	 * Customize the OAuth 2.0 Token attributes.
	 * @param context the context containing the OAuth 2.0 Token attributes
	 */
	@Override
	public void customize(OAuth2TokenClaimsContext context) {
		OAuth2TokenClaimsSet.Builder claims = context.getClaims();
		claims.claim(SecurityConstants.DETAILS_LICENSE, SecurityConstants.MATRIX_LICENSE);
		String clientId = context.getAuthorizationGrant().getName();
		claims.claim(SecurityConstants.CLIENT_ID, clientId);
		claims.claim(SecurityConstants.ACTIVE, Boolean.TRUE);

		// 客户端模式不返回具体用户信息
		if (SecurityConstants.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType().getValue())) {
			return;
		}

		MatrixUser matrixUser = (MatrixUser) context.getPrincipal().getPrincipal();
		claims.claim(SecurityConstants.DETAILS_USER_ID, matrixUser.getId().toString());
		claims.claim(SecurityConstants.DETAILS_USERNAME, matrixUser.getUsername());
	}

}
