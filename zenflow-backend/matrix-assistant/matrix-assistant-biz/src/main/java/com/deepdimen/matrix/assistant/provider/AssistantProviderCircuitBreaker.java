package com.deepdimen.matrix.assistant.provider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;

/** Small per-provider breaker that isolates repeated local and remote failures. */
@Component
public class AssistantProviderCircuitBreaker {

	private static final int FAILURE_THRESHOLD = 3;

	private static final Duration OPEN_DURATION = Duration.ofSeconds(30);

	private final Map<ProviderId, State> states = new ConcurrentHashMap<>();

	private final Clock clock;

	public AssistantProviderCircuitBreaker() {
		this(Clock.systemUTC());
	}

	AssistantProviderCircuitBreaker(Clock clock) {
		this.clock = clock;
	}

	public boolean allow(ProviderId providerId) {
		return states.computeIfAbsent(providerId, ignored -> new State()).allow(clock.instant());
	}

	public void recordSuccess(ProviderId providerId) {
		states.computeIfAbsent(providerId, ignored -> new State()).success();
	}

	public void recordFailure(ProviderId providerId) {
		states.computeIfAbsent(providerId, ignored -> new State()).failure(clock.instant());
	}

	private static final class State {

		private int consecutiveFailures;

		private Instant openUntil;

		private boolean probeInFlight;

		private synchronized boolean allow(Instant now) {
			if (openUntil == null) {
				return true;
			}
			if (now.isBefore(openUntil) || probeInFlight) {
				return false;
			}
			probeInFlight = true;
			return true;
		}

		private synchronized void success() {
			consecutiveFailures = 0;
			openUntil = null;
			probeInFlight = false;
		}

		private synchronized void failure(Instant now) {
			probeInFlight = false;
			consecutiveFailures++;
			if (consecutiveFailures >= FAILURE_THRESHOLD) {
				openUntil = now.plus(OPEN_DURATION);
			}
		}

	}

}
