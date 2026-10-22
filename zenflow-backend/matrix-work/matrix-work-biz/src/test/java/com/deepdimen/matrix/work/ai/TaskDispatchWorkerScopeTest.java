package com.deepdimen.matrix.work.ai;

import com.deepdimen.matrix.common.data.tenant.TenantContextHolder;
import com.deepdimen.matrix.work.entity.WorkTaskDispatchRunEntity;
import com.deepdimen.matrix.work.mapper.WorkTaskDispatchRunMapper;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class TaskDispatchWorkerScopeTest {

	private final WorkTaskDispatchRunMapper runs = mock(WorkTaskDispatchRunMapper.class);

	private final TaskDispatchCreatorAuthorization creators = mock(TaskDispatchCreatorAuthorization.class);

	private final Instant now = Instant.parse("2026-09-26T08:00:00Z");

	private final TaskDispatchWorkerScope scope = new TaskDispatchWorkerScope(runs, creators,
			Clock.fixed(now, ZoneOffset.UTC));

	private final WorkTaskDispatchRunEntity run = new WorkTaskDispatchRunEntity();

	@BeforeEach
	void setup() {
		TenantContextHolder.setTenantId(42L);
		SecurityContextHolder.clearContext();
		run.setId(8L);
		run.setTenantId(42L);
		run.setCreatorId(3L);
		run.setStatus("RUNNING");
		run.setLeaseOwner("owner");
		run.setLeaseUntil(LocalDateTime.ofInstant(now.plusSeconds(60), ZoneId.of("Asia/Shanghai")));
		when(runs.selectOne(any())).thenReturn(run);
		when(creators.authorized(42L, 3L)).thenReturn(true);
	}

	@AfterEach
	void clear() {
		TenantContextHolder.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	void scopeRequiresLiveDatabaseLeaseAndRechecksEveryUse() {
		assertThrows(IllegalArgumentException.class, scope::require);
		try (var lease = scope.open(42L, 8L, "owner")) {
			assertSame(run, scope.require());
			run.setLeaseOwner("another-worker");
			assertThrows(IllegalArgumentException.class, scope::require);
		}
		assertThrows(IllegalArgumentException.class, scope::require);
	}

	@Test
	void expiredCompletedMissingCrossTenantOrRevokedCreatorCannotOpenOrRemainAuthorized() {
		run.setLeaseUntil(LocalDateTime.ofInstant(now, ZoneId.of("Asia/Shanghai")));
		assertThrows(IllegalArgumentException.class, () -> scope.open(42L, 8L, "owner"));
		run.setLeaseUntil(LocalDateTime.ofInstant(now.plusSeconds(60), ZoneId.of("Asia/Shanghai")));
		run.setStatus("SUCCEEDED");
		assertThrows(IllegalArgumentException.class, () -> scope.open(42L, 8L, "owner"));
		run.setStatus("RUNNING");
		run.setTenantId(43L);
		assertThrows(IllegalArgumentException.class, () -> scope.open(42L, 8L, "owner"));
		run.setTenantId(42L);
		try (var lease = scope.open(42L, 8L, "owner")) {
			when(creators.authorized(42L, 3L)).thenReturn(false);
			assertThrows(IllegalArgumentException.class, scope::require);
		}
		when(runs.selectOne(any())).thenReturn(null);
		assertThrows(IllegalArgumentException.class, () -> scope.open(42L, 8L, "owner"));
	}

	@Test
	void scopeCannotNestLeakAcrossThreadsOrSurviveExceptionalExit() {
		assertThrows(IllegalStateException.class, () -> {
			try (var lease = scope.open(42L, 8L, "owner")) {
				assertThrows(IllegalArgumentException.class, () -> scope.open(42L, 8L, "owner"));
				java.util.concurrent.CompletableFuture.runAsync(() -> {
					assertThrows(IllegalArgumentException.class, scope::require);
					assertThrows(IllegalArgumentException.class, lease::close);
				}).join();
				assertSame(run, scope.require());
				throw new IllegalStateException("stop");
			}
		});
		assertThrows(IllegalArgumentException.class, scope::require);
		try (var lease = scope.open(42L, 8L, "owner")) {
			assertSame(run, scope.require());
		}
	}

	@Test
	void loggedInSessionOrChangedTenantCannotBecomeAWorker() {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", "n/a"));
		assertThrows(IllegalArgumentException.class, () -> scope.open(42L, 8L, "owner"));
		SecurityContextHolder.clearContext();
		try (var lease = scope.open(42L, 8L, "owner")) {
			TenantContextHolder.setTenantId(43L);
			assertThrows(IllegalArgumentException.class, scope::require);
		}
	}

}
