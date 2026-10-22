package com.deepdimen.matrix.discover.controller;

import com.deepdimen.matrix.discover.service.impl.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedSkillPermissionTest {

	@Test
	void actualMethodSecurityDeniesBeforeMutationAndAllowsExplicitPermission() {
		try (var context = new AnnotationConfigApplicationContext(Config.class)) {
			SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken("manager", "n/a", List.of()));
			var controller = context.getBean(ManagedSkillController.class);
			ManagedSkillPackageService packages = org.springframework.test.util.AopTestUtils
				.getUltimateTargetObject(context.getBean(ManagedSkillPackageService.class));
			assertThrows(AccessDeniedException.class, () -> controller.approve(7L));
			verifyZeroInteractions(packages);
			context.getBean(Permission.class).allowed = true;
			controller.approve(7L);
			verify(packages).approve(7L);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	public static class Permission {

		boolean allowed;

		public boolean hasPermission(String... names) {
			return allowed;
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class Config {

		@Bean(name = "pms")
		Permission permission() {
			return new Permission();
		}

		@Bean
		ManagedSkillPackageService packages() {
			return mock(ManagedSkillPackageService.class);
		}

		@Bean
		ManagedSkillController controller(ManagedSkillPackageService packages) {
			return new ManagedSkillController(packages, mock(ManagedSkillReleaseService.class),
					mock(ManagedSkillBindingService.class), mock(ManagedSkillCatalogService.class));
		}

	}

}
