package com.deepdimen.matrix.admin.mobilepush;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PushDispatchWorkerTest {

	private final PushDispatchRepository repository = mock(PushDispatchRepository.class);

	private final MobilePushServiceImpl service = mock(MobilePushServiceImpl.class);

	private final PushSessionGuard sessions = mock(PushSessionGuard.class);

	private final PushSecrets secrets = mock(PushSecrets.class);

	private final TenantUserDirectory users = mock(TenantUserDirectory.class);

	private final PushProvider provider = mock(PushProvider.class);

	private final PushDispatchRepository.Outbox outbox = new PushDispatchRepository.Outbox("o", 7, 9, 10, 8, 1,
			Instant.now().plusSeconds(1000));

	private final PushDispatchRepository.Target target = new PushDispatchRepository.Target("i", "binding", "session",
			"HMS_ANDROID", "encrypted", "hash");

	private final PushDispatchRepository.Delivery delivery = new PushDispatchRepository.Delivery("d", "binding",
			"QUEUED");

	private PushDispatchWorker worker;

	@BeforeEach
	void setup() {
		worker = new PushDispatchWorker(repository, service, sessions, secrets, users, List.of(provider));
		when(repository.message(outbox)).thenReturn(new PushDispatchRepository.Message("TASK"));
		when(service.preferences(7, 8)).thenReturn(new PushPreferencesDTO(true, true, false, false));
		when(users.listAssignableUserIds(7L, List.of(8L))).thenReturn(List.of(8L));
		when(repository.targets(outbox)).thenReturn(List.of(target));
		when(repository.delivery(outbox, target)).thenReturn(delivery);
		when(sessions.active("session", 7, 8)).thenReturn(true);
		when(repository.stillCurrent(outbox, target, "owner")).thenReturn(true);
		when(repository.reserveRateSlot(outbox)).thenReturn(true);
		when(provider.channel()).thenReturn("HMS_ANDROID");
		when(service.context("i")).thenReturn("context");
		when(secrets.decrypt("encrypted", "context")).thenReturn("token");
		when(provider.send(anyString(), anyString(), anyString(), anyString()))
			.thenReturn(new PushProvider.Result("ACCEPTED", "receipt", null));
	}

	@Test
	void sendsOnlyOpaqueRoutingMetadataAndDoesNotMarkRead() {
		worker.deliver(outbox, "owner");
		verify(provider).send("token", "9", "binding", "d");
		verify(repository).result(delivery, target, new PushProvider.Result("ACCEPTED", "receipt", null));
		verify(repository).finish(outbox, "owner", false, null);
	}

	@Test void revokedSessionsNeverReachAProvider() {
		when(sessions.active("session",7,8)).thenReturn(false); worker.deliver(outbox,"owner"); verify(provider,never()).send(anyString(),anyString(),anyString(),anyString());
	}

	@Test void readOrInaccessibleMessagesAreSuppressed() {
		when(repository.message(outbox)).thenReturn(null); worker.deliver(outbox,"owner"); verifyZeroInteractions(provider); verify(repository).finish(outbox,"owner",false,"MESSAGE_OR_PREFERENCE_UNAVAILABLE");
	}

	@Test void anAcceptedDeliveryIsNotSentAgain() {
		when(repository.delivery(outbox,target)).thenReturn(new PushDispatchRepository.Delivery("d","binding","ACCEPTED")); worker.deliver(outbox,"owner"); verify(provider,never()).send(anyString(),anyString(),anyString(),anyString());
	}

	@Test void rateLimitAndTemporaryFailuresWaitForBoundedRetry() {
		when(repository.reserveRateSlot(outbox)).thenReturn(false); worker.deliver(outbox,"owner"); verify(provider,never()).send(anyString(),anyString(),anyString(),anyString()); verify(repository).finish(outbox,"owner",true,"RETRY_PENDING");
	}

	@Test
	void providerTemporaryFailureIsPersistedAndScheduledForRetry() {
		PushProvider.Result retry = PushProvider.Result.retry("PROVIDER_TEMPORARY");
		when(provider.send(anyString(), anyString(), anyString(), anyString())).thenReturn(retry);
		worker.deliver(outbox, "owner");
		verify(repository).result(delivery, target, retry);
		verify(repository).finish(outbox, "owner", true, "RETRY_PENDING");
	}

	@Test void aBindingChangedDuringProcessingIsNotDelivered() {
		when(repository.stillCurrent(outbox,target,"owner")).thenReturn(true,false); worker.deliver(outbox,"owner"); verify(provider,never()).send(anyString(),anyString(),anyString(),anyString());
	}

}
