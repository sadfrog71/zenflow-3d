package com.deepdimen.matrix.common.security.component;

import com.deepdimen.matrix.common.core.constant.SecurityConstants;
import com.deepdimen.matrix.common.core.util.SpringContextHolder;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import com.deepdimen.matrix.common.security.service.MatrixUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.security.Principal;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @author caoyuan
 * @date 2022/5/28
 */
@Slf4j
@RequiredArgsConstructor
public class MatrixCustomOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private final OAuth2AuthorizationService authorizationService;

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        OAuth2Authorization oldAuthorization = authorizationService.findByToken(token, OAuth2TokenType.ACCESS_TOKEN);
        if (Objects.isNull(oldAuthorization)) {
			throw new InvalidBearerTokenException("无效访问令牌");
        }

        // 客户端模式默认返回
        if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(oldAuthorization.getAuthorizationGrantType())) {
            return new MatrixClientCredentialsOAuth2AuthenticatedPrincipal(
                    Objects.requireNonNull(oldAuthorization.getAccessToken().getClaims()),
                    AuthorityUtils.NO_AUTHORITIES, oldAuthorization.getPrincipalName());
        }

        Map<String, MatrixUserDetailsService> userDetailsServiceMap = SpringContextHolder
                .getBeansOfType(MatrixUserDetailsService.class);

        Optional<MatrixUserDetailsService> optional = userDetailsServiceMap.values()
                .stream()
                .filter(service -> service.support(Objects.requireNonNull(oldAuthorization).getRegisteredClientId(),
                        oldAuthorization.getAuthorizationGrantType().getValue()))
                .max(Comparator.comparingInt(Ordered::getOrder));

		UserDetails userDetails;
		MatrixUser tokenUser;
        try {
            Object principal = Objects.requireNonNull(oldAuthorization).getAttributes().get(Principal.class.getName());
            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = (UsernamePasswordAuthenticationToken) principal;
            Object tokenPrincipal = usernamePasswordAuthenticationToken.getPrincipal();
			tokenUser = (MatrixUser) tokenPrincipal;
			verifyRequestedTenant(tokenUser);
			MatrixUserDetailsService detailsService = optional
				.orElseThrow(() -> new InvalidBearerTokenException("没有可用的用户身份加载器"));
			userDetails = detailsService.loadUserByUser(tokenUser);
        } catch (UsernameNotFoundException notFoundException) {
			log.warn("用户不存在 {}", notFoundException.getLocalizedMessage());
            throw notFoundException;
		} catch (InvalidBearerTokenException ex) {
			throw ex;
        } catch (Exception ex) {
            log.error("资源服务器 introspect Token error {}", ex.getLocalizedMessage());
			throw new InvalidBearerTokenException("访问令牌身份校验失败");
        }

        // 检查用户账号状态
        if (Objects.nonNull(userDetails)) {
            boolean isLocked = !userDetails.isAccountNonLocked();
            boolean isExpired = !userDetails.isAccountNonExpired();

            if (isLocked) {
                log.warn("用户账号 {} 已被锁定，拒绝访问", userDetails.getUsername());
                throw new InvalidBearerTokenException(token);
            }

            if (isExpired) {
                log.warn("用户账号 {} 已过期，拒绝访问", userDetails.getUsername());
                throw new InvalidBearerTokenException(token);
            }
        }

        // 注入客户端信息，方便上下文中获取
		if (!(userDetails instanceof MatrixUser matrixUser)
				|| !Objects.equals(matrixUser.getId(), tokenUser.getId())
				|| !Objects.equals(matrixUser.getTenantId(), tokenUser.getTenantId())) {
			throw new InvalidBearerTokenException("访问令牌身份已变化");
		}

		matrixUser.getAttributes()
                .put(SecurityConstants.CLIENT_ID, oldAuthorization.getRegisteredClientId());
        return matrixUser;
    }

	private void verifyRequestedTenant(MatrixUser tokenUser) {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		Object explicit = attributes == null ? null : attributes.getAttribute(
				"com.deepdimen.matrix.common.data.tenant.TenantContextHolderFilter.explicitTenant",
				RequestAttributes.SCOPE_REQUEST);
		if (explicit == null) {
			return;
		}
		try {
			if (!Objects.equals(Long.valueOf(explicit.toString()), tokenUser.getTenantId())) {
				throw new InvalidBearerTokenException("请求租户与访问令牌不一致");
			}
		}
		catch (NumberFormatException ex) {
			throw new InvalidBearerTokenException("非法租户上下文");
		}
	}

}
