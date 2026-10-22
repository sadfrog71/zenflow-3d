package com.deepdimen.matrix.assistant.matrix;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.access.AccessDeniedException;
import com.deepdimen.matrix.assistant.controller.AssistantRunMetricsController;
import com.deepdimen.matrix.assistant.identity.AssistantIdentityResolver;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.run.AssistantRunMetricsService;
import com.deepdimen.matrix.assistant.run.AssistantRunMetricsRepository;

class AssistantRunMetricsAuthorizationTest {

	@Test
	void sharedExecutorMetricsRequirePlatformRoleRatherThanTenantPermission() {
		try (var context = new AnnotationConfigApplicationContext(Config.class)) {
			var controller = context
				.getBean(com.deepdimen.matrix.assistant.controller.AssistantExecutorMetricsController.class);
			context.getBean(Gate.class).allowed = true;
			org.springframework.security.core.context.SecurityContextHolder.getContext()
				.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						"tenant-manager", "",
						List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
								"assistant_provider_tenant_manage"))));
			assertThrows(AccessDeniedException.class, controller::query);
			org.springframework.security.core.context.SecurityContextHolder.getContext()
				.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
						"platform-manager", "",
						List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
								"ROLE_SUPER_ADMIN"))));
			assertEquals("INSTANCE", controller.query().scope());
		}
		finally {
			org.springframework.security.core.context.SecurityContextHolder.clearContext();
		}
	}

	@Test
	void permissionProxyRejectsBeforeReadingAndAuthorizedQueryUsesResolvedTenant() {
		try (var context = new AnnotationConfigApplicationContext(Config.class)) {
			var controller = context.getBean(AssistantRunMetricsController.class);
			var gate = context.getBean(Gate.class);
			Instant from = Instant.parse("2026-09-26T00:00:00Z");
			assertThrows(AccessDeniedException.class, () -> controller.query(from, from.plusSeconds(10), 100));
			assertEquals(0, gate.reads.get());
			gate.allowed = true;
			assertEquals(0, controller.query(from, from.plusSeconds(10), 100).summary().runCount());
			assertEquals(1, gate.reads.get());
		}
	}

	public static class Gate {

		boolean allowed;

		final AtomicInteger reads = new AtomicInteger();

		public boolean hasPermission(String permission) {
			assertEquals("assistant_provider_tenant_manage", permission);
			return allowed;
		}

	}

	@Configuration
	@EnableMethodSecurity
	static class Config {

		@Bean
		com.deepdimen.matrix.assistant.run.AssistantRunDispatchService dispatch() {
			return new com.deepdimen.matrix.assistant.run.AssistantRunDispatchService(1, 1, 1, 1);
		}

		@Bean
		com.deepdimen.matrix.assistant.controller.AssistantExecutorMetricsController executors(
				com.deepdimen.matrix.assistant.run.AssistantRunDispatchService dispatch) {
			return new com.deepdimen.matrix.assistant.controller.AssistantExecutorMetricsController(dispatch);
		}

		@Bean(name = "pms")
		Gate gate() {
			return new Gate();
		}

		@Bean
		AssistantRunMetricsService metrics() {
			return new AssistantRunMetricsService();
		}

		@Bean
		AssistantIdentityResolver identity() {
			return () -> new IdentityEnvelope("tenant-a", "manager", null, List.of(), List.of(), List.of(), "", "");
		}

		@Bean
		AssistantRunMetricsRepository repository(Gate gate) {
			return (tenant, from, to, limit) -> {
				assertEquals("tenant-a", tenant);
				gate.reads.incrementAndGet();
				return List.of();
			};
		}

		@Bean
		AssistantRunMetricsController controller(AssistantIdentityResolver identity, AssistantRunMetricsService metrics,
				ObjectProvider<AssistantRunMetricsRepository> repository) {
			return new AssistantRunMetricsController(identity, metrics, repository);
		}

	}

}
