package com.deepdimen.matrix.common.mcp.utils;

import com.deepdimen.matrix.common.core.util.SpringContextHolder;
import com.deepdimen.matrix.common.mcp.core.McpContextHolder;
import com.deepdimen.matrix.common.security.annotation.HasPermission;
import com.deepdimen.matrix.common.security.component.PermissionService;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import java.nio.file.AccessDeniedException;
import java.time.Instant;

/**
 * Mcp 安全工具类
 *
 * @author caoyuan
 * @date 2025/07/22
 */
@UtilityClass
public class McpSecurityUtils {

	/**
	 * 检查访问令牌并设置安全上下文
	 * @param accessToken 访问令牌
	 * @return 用户名或标识
	 */
	public MatrixUser init(String accessToken) {
		OpaqueTokenIntrospector tokenIntrospector = SpringContextHolder.getBean(OpaqueTokenIntrospector.class);
		OAuth2AuthenticatedPrincipal authenticatedPrincipal = tokenIntrospector.introspect(accessToken);

		if (authenticatedPrincipal == null) {
			throw new IllegalArgumentException("Invalid access token");
		}

		SecurityContext mcpContext = SecurityContextHolder.createEmptyContext();
		BearerTokenAuthentication bearerTokenAuthentication = new BearerTokenAuthentication(authenticatedPrincipal,
				new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, accessToken, Instant.now(), Instant.MAX),
				authenticatedPrincipal.getAuthorities());
		mcpContext.setAuthentication(bearerTokenAuthentication);
		SecurityContextHolder.setContext(mcpContext);
		return (MatrixUser) authenticatedPrincipal;
	}

	/**
	 * 检查当前用户是否具有指定权限
	 * @param accessToken 用户访问令牌
	 * @return 如果具有权限返回true，否则返回false
	 * @throws Exception 初始化或权限检查过程中可能抛出的异常
	 */
	@SneakyThrows
	public boolean hasPermission(String accessToken) {
		HasPermission annotation = McpContextHolder.getAnnotation();
		if (annotation == null) {
			return true; // 没有注解则默认允许
		}

		init(accessToken);

		boolean hasPermission = SpringContextHolder.getBean(PermissionService.class).hasPermission(annotation.value());

		if (!hasPermission) {
			throw new AccessDeniedException("权限不足，无法访问该资源");
		}

		return true;
	}

}
