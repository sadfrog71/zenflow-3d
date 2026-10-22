package com.deepdimen.matrix.admin.controller;

import com.deepdimen.matrix.admin.api.dto.UserDTO;
import com.deepdimen.matrix.admin.service.SysMenuService;
import com.deepdimen.matrix.admin.service.SysTenantService;
import com.deepdimen.matrix.common.core.util.R;
import com.deepdimen.matrix.common.security.service.MatrixUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysTenantControllerTenantSwitchTest {

	private static final long USER_ID = 1L;
	private static final long SOURCE_TENANT_ID = 2056583997744693249L;
	private static final long TARGET_TENANT_ID = 2052188107445968897L;

	private final SysTenantService tenantService = mock(SysTenantService.class);
	private final OAuth2AuthorizationService authorizationService = mock(OAuth2AuthorizationService.class);
	private final SysTenantController controller = new SysTenantController(tenantService, mock(SysMenuService.class),
			authorizationService);

	@BeforeEach
	void setUpSecurityContext() {
		MatrixUser user = new MatrixUser(USER_ID, "admin", List.of(), null, List.of(), List.of(), null, null, null,
				"管理员", null, SOURCE_TENANT_ID, "", true, true, null, true, null, true, List.of());
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
	}

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void successfulTenantUpdateRevokesSourceAuthorization() {
		OAuth2Authorization sourceAuthorization = mock(OAuth2Authorization.class);
		when(tenantService.updateUserTenant(any(UserDTO.class))).thenReturn(R.ok(true));
		when(authorizationService.findByToken("source-token", OAuth2TokenType.ACCESS_TOKEN))
				.thenReturn(sourceAuthorization);

		UserDTO request = new UserDTO();
		request.setTenantId(TARGET_TENANT_ID);
		R result = controller.updateUserTenant(request, "Bearer source-token");

		assertThat(result.isOk()).isTrue();
		assertThat(request.getUserId()).isEqualTo(USER_ID);
		assertThat(request.getUsername()).isEqualTo("admin");
		verify(authorizationService).remove(sourceAuthorization);
	}

	@Test
	void failedTenantUpdateKeepsSourceAuthorization() {
		when(tenantService.updateUserTenant(any(UserDTO.class))).thenReturn(R.failed("switch denied"));

		UserDTO request = new UserDTO();
		request.setTenantId(TARGET_TENANT_ID);
		R result = controller.updateUserTenant(request, "Bearer source-token");

		assertThat(result.isOk()).isFalse();
		verify(authorizationService, never()).findByToken(any(), any());
	}
}
