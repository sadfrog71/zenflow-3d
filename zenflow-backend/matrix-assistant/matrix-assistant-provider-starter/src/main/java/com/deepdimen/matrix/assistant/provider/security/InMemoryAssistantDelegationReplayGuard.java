package com.deepdimen.matrix.assistant.provider.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.DelegationReplayGuard;

/** Single-instance replay protection. Replace this bean with a shared guard for Cloud. */
public class InMemoryAssistantDelegationReplayGuard implements DelegationReplayGuard {

	private final Map<String, Instant> consumedDelegations = new ConcurrentHashMap<>();

	@Override
	public boolean consumeOnce(String jti, Instant expiresAt, Instant now) {
		if (jti == null || jti.isBlank() || expiresAt == null || now == null || !expiresAt.isAfter(now)) {
			return false;
		}
		consumedDelegations.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
		return consumedDelegations.putIfAbsent(jti, expiresAt) == null;
	}

}
