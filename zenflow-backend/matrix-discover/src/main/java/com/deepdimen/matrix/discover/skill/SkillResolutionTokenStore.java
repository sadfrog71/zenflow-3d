package com.deepdimen.matrix.discover.skill;

import com.deepdimen.matrix.skill.api.SkillContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Opaque server-held tokens. Execution must still recheck revocation and current
 * authorization.
 */
@Component
@RequiredArgsConstructor
public class SkillResolutionTokenStore {

	private static final String PREFIX = "matrix:skill:resolution:";

	private static final Duration TTL = Duration.ofMinutes(5);

	private final StringRedisTemplate redis;

	private final ObjectMapper json;

    // Internal immutable snapshots must not pass through HTTP string sanitation (e.g. trim).
    private final ObjectMapper snapshotJson = new ObjectMapper();

	private final SecureRandom random = new SecureRandom();

	public Resolution issue(Identity identity, Consumer consumer, String businessRunId, String parametersJson,
			ResolvedSkillSnapshot snapshot) {
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		Instant expires = Instant.now().plus(TTL);
		try {
			var stored = new StoredResolution(identity, consumer, businessRunId, parametersJson, snapshot,
					expires.toEpochMilli());
			if (!Boolean.TRUE
				.equals(redis.opsForValue().setIfAbsent(PREFIX + token, snapshotJson.writeValueAsString(stored), TTL))) {
				throw new IllegalStateException("解析令牌保存失败");
			}
			return new Resolution(snapshot, token, expires);
		}
		catch (java.io.IOException ex) {
			throw new IllegalArgumentException("解析快照序列化失败", ex);
		}
	}

	public StoredResolution require(String token, Identity identity, Consumer consumer) {
		if (token == null || !token.matches("[A-Za-z0-9_-]{43}"))
			throw invalid();
		String value = redis.opsForValue().get(PREFIX + token);
		if (value == null)
			throw invalid();
		try {
			var stored = snapshotJson.readValue(value, StoredResolution.class);
			if (stored.expiresAtEpochMillis() <= Instant.now().toEpochMilli() || !stored.identity().equals(identity)
					|| !stored.consumer().equals(consumer))
				throw invalid();
			return stored;
		}
		catch (java.io.IOException ex) {
			throw invalid();
		}
	}

	private IllegalArgumentException invalid() {
		return new IllegalArgumentException("RESOLUTION_TOKEN_INVALID");
	}

	public record StoredResolution(Identity identity, Consumer consumer, String businessRunId, String parametersJson,
			ResolvedSkillSnapshot snapshot, long expiresAtEpochMillis) {
	}

}
