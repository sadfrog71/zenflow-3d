package com.deepdimen.matrix.admin.mobilepush;

import com.deepdimen.matrix.admin.api.service.TenantUserDirectory;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "matrix.mobile-push.enabled", havingValue = "true")
public class PushDispatchWorker {

	private static final Set<String> TERMINAL = Set.of("ACCEPTED", "INVALID_TOKEN", "FAILED_FINAL");

	private final PushDispatchRepository repository;

	private final MobilePushServiceImpl service;

	private final PushSessionGuard sessions;

	private final PushSecrets secrets;

	private final TenantUserDirectory users;

	private final List<PushProvider> providers;

	public PushDispatchWorker(PushDispatchRepository repository, MobilePushServiceImpl service,
			PushSessionGuard sessions, PushSecrets secrets, TenantUserDirectory users, List<PushProvider> providers) {
		this.repository = repository;
		this.service = service;
		this.sessions = sessions;
		this.secrets = secrets;
		this.users = users;
		this.providers = providers;
	}

	@Scheduled(fixedDelayString = "${matrix.mobile-push.poll-interval-ms:15000}")
	public void dispatch() {
		for (String id : repository.ready()) {
			String owner = UUID.randomUUID().toString();
			PushDispatchRepository.Outbox outbox = repository.claim(id, owner);
			if (outbox == null)
				continue;
			try {
				deliver(outbox, owner);
			}
			catch (Exception ignored) {
				repository.finish(outbox, owner, true, "DISPATCH_UNAVAILABLE");
			}
		}
	}

	void deliver(PushDispatchRepository.Outbox outbox, String owner) {
		PushDispatchRepository.Message message = repository.message(outbox);
		if (message == null || !service.preferences(outbox.tenantId(), outbox.userId()).allows(message.sourceType())
				|| !users.listAssignableUserIds(outbox.tenantId(), List.of(outbox.userId()))
					.contains(outbox.userId())) {
			repository.finish(outbox, owner, false, "MESSAGE_OR_PREFERENCE_UNAVAILABLE");
			return;
		}
		boolean retry = false;
		List<PushDispatchRepository.Target> targets = repository.targets(outbox);
		for (PushDispatchRepository.Target target : targets) {
			PushDispatchRepository.Delivery delivery = repository.delivery(outbox, target);
			if (TERMINAL.contains(delivery.status()) || !target.bindingId().equals(delivery.bindingId()))
				continue;
			if (!sessions.active(target.sessionId(), outbox.tenantId(), outbox.userId())
					|| !repository.stillCurrent(outbox, target, owner))
				continue;
			PushProvider provider = providers.stream()
				.filter(item -> item.channel().equals(target.channel()))
				.findFirst()
				.orElse(null);
			if (provider == null) {
				repository.result(delivery, target,
						new PushProvider.Result("FAILED_FINAL", null, "CHANNEL_UNSUPPORTED"));
				continue;
			}
			if (!repository.reserveRateSlot(outbox)) {
				retry = true;
				continue;
			}
			// Recheck after rate reservation. Payloads remain generic if revocation races
			// a provider call.
			if (repository.message(outbox) == null
					|| !service.preferences(outbox.tenantId(), outbox.userId()).allows(message.sourceType())
					|| !repository.stillCurrent(outbox, target, owner)
					|| !sessions.active(target.sessionId(), outbox.tenantId(), outbox.userId()))
				continue;
			String token = secrets.decrypt(target.tokenCiphertext(), service.context(target.installationId()));
			PushProvider.Result result = provider.send(token, Long.toString(outbox.messageId()), target.bindingId(),
					delivery.id());
			repository.result(delivery, target, result);
			retry |= "RETRY".equals(result.status());
		}
		repository.finish(outbox, owner, retry,
				targets.isEmpty() ? "NO_ACTIVE_INSTALLATION" : retry ? "RETRY_PENDING" : null);
	}

}
