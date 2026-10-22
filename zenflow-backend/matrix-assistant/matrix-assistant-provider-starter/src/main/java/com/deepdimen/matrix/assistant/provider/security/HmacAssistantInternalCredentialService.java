package com.deepdimen.matrix.assistant.provider.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.AssistantInternalCredentialService;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.DelegationReplayGuard;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.DelegationClaims;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.InternalAuthenticationException;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.InternalCredentials;
import com.deepdimen.matrix.assistant.api.AssistantInternalAuthContract.Purpose;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HmacAssistantInternalCredentialService implements AssistantInternalCredentialService {

	private static final Logger log = LoggerFactory.getLogger(HmacAssistantInternalCredentialService.class);

	private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

	private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

	private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

	private static final Pattern HASH = Pattern.compile("[a-f0-9]{64}");

	private final ObjectMapper objectMapper;

	private final byte[] secret;

	private final String issuer;

	private final String workloadId;

	private final Set<String> trustedWorkloadIds;

	private final Duration ttl;

	private final Clock clock;

	private final DelegationReplayGuard replayGuard;

	public HmacAssistantInternalCredentialService(ObjectMapper objectMapper,
			AssistantInternalAuthProperties properties) {
		this(objectMapper, properties, Clock.systemUTC(), new InMemoryAssistantDelegationReplayGuard());
	}

	public HmacAssistantInternalCredentialService(ObjectMapper objectMapper, AssistantInternalAuthProperties properties,
			DelegationReplayGuard replayGuard) {
		this(objectMapper, properties, Clock.systemUTC(), replayGuard);
	}

	HmacAssistantInternalCredentialService(ObjectMapper objectMapper, AssistantInternalAuthProperties properties,
			Clock clock) {
		this(objectMapper, properties, clock, new InMemoryAssistantDelegationReplayGuard());
	}

	HmacAssistantInternalCredentialService(ObjectMapper objectMapper, AssistantInternalAuthProperties properties,
			Clock clock, DelegationReplayGuard replayGuard) {
		this.objectMapper = objectMapper;
		this.secret = requireSecret(properties.getSecret());
		this.issuer = requireSafe(properties.getIssuer());
		this.workloadId = requireSafe(properties.getWorkloadId());
		this.trustedWorkloadIds = properties.getTrustedWorkloadIds() == null ? Set.of()
				: properties.getTrustedWorkloadIds()
					.stream()
					.map(HmacAssistantInternalCredentialService::requireSafe)
					.collect(java.util.stream.Collectors.toUnmodifiableSet());
		this.ttl = requireTtl(properties.getTtl());
		this.clock = clock;
		this.replayGuard = Objects.requireNonNull(replayGuard, "replayGuard");
	}

	@Override
	public InternalCredentials issue(IdentityEnvelope identity, String runId, String audience, Purpose purpose,
			String requestHash) {
		Objects.requireNonNull(identity, "identity");
		Instant issuedAt = clock.instant();
		Instant expiresAt = issuedAt.plus(ttl);
		String normalizedRunId = requireSafe(runId);
		String normalizedAudience = requireSafe(audience);
		requireHash(requestHash);
		String serviceToken = sign(new WorkloadClaims("WORKLOAD", issuer, workloadId, normalizedAudience, purpose,
				normalizedRunId, requestHash, issuedAt, expiresAt, UUID.randomUUID().toString()));
		String delegationToken = sign(new DelegationTokenClaims("DELEGATION", issuer, workloadId, normalizedAudience,
				purpose, normalizedRunId, requestHash, identity, issuedAt, expiresAt, UUID.randomUUID().toString()));
		return new InternalCredentials(serviceToken, delegationToken);
	}

	@Override
	public DelegationClaims verify(String serviceAuthorization, String delegationToken, String audience,
			Purpose purpose, String runId, String requestHash) {
		try {
			String normalizedAudience = requireSafe(audience);
			String normalizedRunId = requireSafe(runId);
			requireHash(requestHash);
			WorkloadClaims workload = parse(bearer(serviceAuthorization), WorkloadClaims.class);
			DelegationTokenClaims delegation = parse(delegationToken, DelegationTokenClaims.class);
			Instant now = clock.instant();
			requireCommon(workload.type(), workload.issuer(), workload.workloadId(), workload.audience(),
					workload.purpose(), workload.runId(), workload.requestHash(), workload.issuedAt(),
					workload.expiresAt(), "WORKLOAD", normalizedAudience, purpose, normalizedRunId, requestHash, now);
			requireCommon(delegation.type(), delegation.issuer(), delegation.workloadId(), delegation.audience(),
					delegation.purpose(), delegation.runId(), delegation.requestHash(), delegation.issuedAt(),
					delegation.expiresAt(), "DELEGATION", normalizedAudience, purpose, normalizedRunId, requestHash,
					now);
			if (!Objects.equals(workload.workloadId(), delegation.workloadId()) || delegation.identity() == null) {
				throw new InternalAuthenticationException();
			}
			if (!replayGuard.consumeOnce(delegation.jti(), delegation.expiresAt(), now)) {
				throw new InternalAuthenticationException();
			}
			return new DelegationClaims(delegation.issuer(), delegation.workloadId(), delegation.audience(),
					delegation.purpose(), delegation.runId(), delegation.requestHash(), delegation.identity(),
					delegation.issuedAt(), delegation.expiresAt(), delegation.jti());
		}
		catch (InternalAuthenticationException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new InternalAuthenticationException();
		}
	}

	private void requireCommon(String type, String tokenIssuer, String tokenWorkload, String tokenAudience,
			Purpose tokenPurpose, String tokenRunId, String tokenHash, Instant issuedAt, Instant expiresAt,
			String expectedType, String audience, Purpose purpose, String runId, String requestHash, Instant now) {
		boolean typeMatches = expectedType.equals(type);
		boolean issuerMatches = issuer.equals(tokenIssuer);
		boolean workloadTrusted = trustedWorkloadIds.contains(tokenWorkload);
		boolean audienceMatches = audience.equals(tokenAudience);
		boolean purposeMatches = purpose == tokenPurpose;
		boolean runMatches = runId.equals(tokenRunId);
		boolean hashMatches = requestHash.equals(tokenHash);
		boolean timeWindowValid = issuedAt != null && expiresAt != null && !issuedAt.isAfter(now.plusSeconds(15))
				&& expiresAt.isAfter(now) && !expiresAt.isAfter(issuedAt.plus(Duration.ofMinutes(5)));
		if (!typeMatches || !issuerMatches || !workloadTrusted || !audienceMatches || !purposeMatches || !runMatches
				|| !hashMatches || !timeWindowValid) {
			log.warn(
					"Assistant internal credential claim mismatch: type={}, issuer={}, workload={}, audience={}, "
							+ "purpose={}, run={}, hash={}, timeWindow={}",
					typeMatches, issuerMatches, workloadTrusted, audienceMatches, purposeMatches, runMatches,
					hashMatches, timeWindowValid);
			throw new InternalAuthenticationException();
		}
	}

	private String sign(Object claims) {
		try {
			String payload = ENCODER.encodeToString(objectMapper.writeValueAsBytes(claims));
			return "v1." + payload + "." + ENCODER.encodeToString(hmac(payload));
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("ASSISTANT_INTERNAL_REQUEST_INVALID", ex);
		}
	}

	private <T> T parse(String token, Class<T> type) {
		if (token == null) {
			throw new InternalAuthenticationException();
		}
		String[] parts = token.split("\\.", -1);
		if (parts.length != 3 || !"v1".equals(parts[0])) {
			throw new InternalAuthenticationException();
		}
		byte[] supplied = decode(parts[2]);
		if (!MessageDigest.isEqual(hmac(parts[1]), supplied)) {
			log.warn("Assistant internal credential signature mismatch");
			throw new InternalAuthenticationException();
		}
		try {
			return objectMapper.readValue(DECODER.decode(parts[1]), type);
		}
		catch (IllegalArgumentException | IOException ex) {
			log.warn("Assistant internal credential payload is invalid: {}", ex.getClass().getSimpleName());
			throw new InternalAuthenticationException();
		}
	}

	private byte[] hmac(String payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret, "HmacSHA256"));
			return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
		}
		catch (Exception ex) {
			throw new IllegalStateException("HmacSHA256 unavailable", ex);
		}
	}

	private byte[] decode(String value) {
		try {
			return DECODER.decode(value);
		}
		catch (IllegalArgumentException ex) {
			throw new InternalAuthenticationException();
		}
	}

	private String bearer(String authorization) {
		if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
			throw new InternalAuthenticationException();
		}
		return authorization.substring(7);
	}

	private static byte[] requireSecret(String value) {
		if (value == null || value.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException("Assistant internal auth secret must contain at least 32 bytes");
		}
		return value.getBytes(StandardCharsets.UTF_8);
	}

	private static String requireSafe(String value) {
		if (value == null || !SAFE_ID.matcher(value).matches()) {
			throw new IllegalArgumentException("Assistant internal identifier is invalid");
		}
		return value;
	}

	private static void requireHash(String value) {
		if (value == null || !HASH.matcher(value).matches()) {
			throw new IllegalArgumentException("Assistant request hash is invalid");
		}
	}

	private static Duration requireTtl(Duration value) {
		if (value == null || value.isNegative() || value.isZero() || value.compareTo(Duration.ofMinutes(5)) > 0) {
			throw new IllegalArgumentException("Assistant delegation TTL must be in (0, 5m]");
		}
		return value;
	}

	private record WorkloadClaims(String type, String issuer, String workloadId, String audience, Purpose purpose,
			String runId, String requestHash, Instant issuedAt, Instant expiresAt, String jti) {
	}

	private record DelegationTokenClaims(String type, String issuer, String workloadId, String audience,
			Purpose purpose, String runId, String requestHash, IdentityEnvelope identity, Instant issuedAt,
			Instant expiresAt, String jti) {
	}

}
