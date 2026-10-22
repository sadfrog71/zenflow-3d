package com.deepdimen.matrix.assistant.api;

import java.time.Instant;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;

/** Workload identity and short-lived end-user delegation for private Assistant RPCs. */
public final class AssistantInternalAuthContract {

	public static final String SERVICE_AUTHORIZATION_HEADER = "X-Assistant-Service-Authorization";

	public static final String DELEGATION_HEADER = "X-Assistant-Delegation";

	public static final String CONTRACT_VERSION_HEADER = "X-Assistant-Contract-Version";

	public static final String CORRELATION_HEADER = "X-Correlation-ID";

	public static final String RUN_ID_HEADER = "X-Assistant-Run-ID";

	private AssistantInternalAuthContract() {
	}

	public enum Purpose {

		PROVIDER_DESCRIBE, PROVIDER_SEARCH, PROVIDER_CITATION_RESOLVE, MODEL_RESOLVE, MODEL_STREAM

	}

	public record InternalCredentials(String serviceBearerToken, String delegationToken) {

		public InternalCredentials {
			if (serviceBearerToken == null || serviceBearerToken.isBlank() || delegationToken == null
					|| delegationToken.isBlank()) {
				throw new IllegalArgumentException("ASSISTANT_INTERNAL_CREDENTIALS_UNAVAILABLE");
			}
		}
	}

	public record DelegationClaims(String issuer, String workloadId, String audience, Purpose purpose, String runId,
			String requestHash, IdentityEnvelope identity, Instant issuedAt, Instant expiresAt, String jti) {
	}

	public interface AssistantInternalCredentialService {

		InternalCredentials issue(IdentityEnvelope identity, String runId, String audience, Purpose purpose,
				String requestHash);

		DelegationClaims verify(String serviceAuthorization, String delegationToken, String audience, Purpose purpose,
				String runId, String requestHash);

	}

	/**
	 * Atomically records a delegation token identifier until it expires. Distributed
	 * deployments should replace the local implementation with a shared store such as
	 * Redis SET-NX with TTL.
	 */
	public interface DelegationReplayGuard {

		boolean consumeOnce(String jti, Instant expiresAt, Instant now);

	}

	public static final class InternalAuthenticationException extends SecurityException {

		private static final long serialVersionUID = 1L;

		public InternalAuthenticationException() {
			super("ASSISTANT_INTERNAL_UNAUTHORIZED");
		}

	}

}
