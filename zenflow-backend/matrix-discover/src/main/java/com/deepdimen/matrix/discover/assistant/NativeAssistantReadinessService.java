package com.deepdimen.matrix.discover.assistant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.discover.entity.AiAgentEntity;
import com.deepdimen.matrix.discover.entity.AiAssistantNativeReadinessEntity;
import com.deepdimen.matrix.discover.mapper.AiAssistantNativeReadinessMapper;
import com.deepdimen.matrix.discover.service.AiAgentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Fail-closed cutover gate backed by an auditable golden-question decision. */
@Service
public class NativeAssistantReadinessService implements NativeAssistantCutoverGate {

	private static final Pattern SHA_256 = Pattern.compile("[a-fA-F0-9]{64}");

	private static final Set<String> REQUIRED_SCENARIOS = Set.of("KNOWLEDGE", "WORK", "INFORMATION", "CAPSULE",
			"MIXED");

	private final AiAssistantNativeReadinessMapper mapper;

	private final AiAgentService agentService;

	private final ObjectMapper objectMapper;

	private final Clock clock;

	@Autowired
	public NativeAssistantReadinessService(AiAssistantNativeReadinessMapper mapper, AiAgentService agentService,
			ObjectMapper objectMapper) {
		this(mapper, agentService, objectMapper, Clock.systemUTC());
	}

	NativeAssistantReadinessService(AiAssistantNativeReadinessMapper mapper, AiAgentService agentService,
			ObjectMapper objectMapper, Clock clock) {
		this.mapper = mapper;
		this.agentService = agentService;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	public ReadinessDecision approve(IdentityEnvelope identity, ReadinessApprovalRequest request) {
		requireIdentity(identity);
		AiAgentEntity profile = requireNativeProfile(identity, request == null ? null : request.nativeProfileId());
		validateApproval(request);
		AiAssistantNativeReadinessEntity decision = baseDecision(identity, profile, request.reportSha256(),
				request.businessOwner(), request.reason());
		decision.setDecisionStatus("APPROVED");
		decision.setMetricsJson(metricsJson(request));
		decision.setExpiresAt(LocalDateTime.ofInstant(request.expiresAt(), ZoneOffset.UTC));
		insert(decision);
		return toDecision(decision);
	}

	public ReadinessDecision revoke(IdentityEnvelope identity, Long nativeProfileId, String reason) {
		requireIdentity(identity);
		AiAgentEntity profile = requireNativeProfile(identity, nativeProfileId);
		if (!StringUtils.hasText(reason)) {
			throw new IllegalArgumentException("READINESS_REVOCATION_REASON_REQUIRED");
		}
		AiAssistantNativeReadinessEntity current = latest(identity.tenantId(), nativeProfileId);
		AiAssistantNativeReadinessEntity decision = baseDecision(identity, profile,
				current == null ? sha256("revoked") : current.getReportSha256(),
				current == null ? identity.userId() : current.getBusinessOwner(), reason);
		decision.setDecisionStatus("REVOKED");
		decision.setMetricsJson(current == null ? "{}" : current.getMetricsJson());
		decision.setExpiresAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
		insert(decision);
		return toDecision(decision);
	}

	public ReadinessDecision current(IdentityEnvelope identity, Long nativeProfileId) {
		requireIdentity(identity);
		requireNativeProfile(identity, nativeProfileId);
		AiAssistantNativeReadinessEntity current = latest(identity.tenantId(), nativeProfileId);
		return current == null ? null : toDecision(current);
	}

	@Override
	public void assertReady(IdentityEnvelope identity, Long nativeProfileId) {
		requireIdentity(identity);
		AiAgentEntity profile = requireNativeProfile(identity, nativeProfileId);
		AiAssistantNativeReadinessEntity decision = latest(identity.tenantId(), nativeProfileId);
		if (decision == null || !"APPROVED".equals(decision.getDecisionStatus())) {
			throw new IllegalStateException("NATIVE_CUTOVER_NOT_APPROVED");
		}
		LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
		if (decision.getExpiresAt() == null || !decision.getExpiresAt().isAfter(now)) {
			throw new IllegalStateException("NATIVE_CUTOVER_APPROVAL_EXPIRED");
		}
		if (!sha256(profileConfig(profile)).equals(decision.getProfileConfigSha256())) {
			throw new IllegalStateException("NATIVE_CUTOVER_PROFILE_CHANGED");
		}
	}

	private AiAssistantNativeReadinessEntity baseDecision(IdentityEnvelope identity, AiAgentEntity profile,
			String reportSha256, String businessOwner, String reason) {
		AiAssistantNativeReadinessEntity decision = new AiAssistantNativeReadinessEntity();
		decision.setTenantId(Long.valueOf(identity.tenantId()));
		decision.setNativeProfileId(profile.getId());
		decision.setReportSha256(reportSha256.toLowerCase());
		decision.setProfileConfigSha256(sha256(profileConfig(profile)));
		decision.setBusinessOwner(businessOwner.trim());
		decision.setDecidedBy(identity.userId());
		decision.setReason(reason.trim());
		decision.setCorrelationId(identity.correlationId());
		decision.setDecidedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
		return decision;
	}

	private void validateApproval(ReadinessApprovalRequest request) {
		if (request == null || request.nativeProfileId() == null) {
			throw new IllegalArgumentException("NATIVE_PROFILE_REQUIRED");
		}
		if (!StringUtils.hasText(request.reportSha256()) || !SHA_256.matcher(request.reportSha256()).matches()) {
			throw new IllegalArgumentException("GOLDEN_REPORT_SHA256_INVALID");
		}
		if (request.p0PermissionDefects() != 0 || request.p0FactDefects() != 0) {
			throw new IllegalArgumentException("GOLDEN_REPORT_P0_DEFECTS_PRESENT");
		}
		if (request.evaluatedQuestionCount() < REQUIRED_SCENARIOS.size()
				|| !coversRequiredScenarios(request.coveredScenarioCodes())) {
			throw new IllegalArgumentException("GOLDEN_REPORT_COVERAGE_INCOMPLETE");
		}
		requireRate(request.baselineCitationSupportRate(), "BASELINE_CITATION_RATE_INVALID");
		requireRate(request.candidateCitationSupportRate(), "CANDIDATE_CITATION_RATE_INVALID");
		requireRate(request.baselineUnsupportedClaimRate(), "BASELINE_UNSUPPORTED_RATE_INVALID");
		requireRate(request.candidateUnsupportedClaimRate(), "CANDIDATE_UNSUPPORTED_RATE_INVALID");
		if (request.candidateCitationSupportRate() < request.baselineCitationSupportRate()
				|| request.candidateUnsupportedClaimRate() > request.baselineUnsupportedClaimRate()) {
			throw new IllegalArgumentException("GOLDEN_REPORT_QUALITY_REGRESSION");
		}
		requireNonNegative(request.baselineFirstTokenMs(), "BASELINE_FIRST_TOKEN_INVALID");
		requireNonNegative(request.candidateFirstTokenMs(), "CANDIDATE_FIRST_TOKEN_INVALID");
		requireNonNegative(request.baselineTotalLatencyMs(), "BASELINE_TOTAL_LATENCY_INVALID");
		requireNonNegative(request.candidateTotalLatencyMs(), "CANDIDATE_TOTAL_LATENCY_INVALID");
		requireNonNegative(request.baselineCost(), "BASELINE_COST_INVALID");
		requireNonNegative(request.candidateCost(), "CANDIDATE_COST_INVALID");
		if (!StringUtils.hasText(request.businessOwner()) || !StringUtils.hasText(request.reason())
				|| !StringUtils.hasText(request.costUnit())) {
			throw new IllegalArgumentException("GOLDEN_REPORT_APPROVAL_METADATA_REQUIRED");
		}
		Instant now = clock.instant();
		if (request.expiresAt() == null || !request.expiresAt().isAfter(now)
				|| request.expiresAt().isAfter(now.plusSeconds(30L * 24 * 60 * 60))) {
			throw new IllegalArgumentException("GOLDEN_REPORT_APPROVAL_EXPIRY_INVALID");
		}
	}

	private String metricsJson(ReadinessApprovalRequest request) {
		Map<String, Object> metrics = new LinkedHashMap<>();
		metrics.put("p0PermissionDefects", request.p0PermissionDefects());
		metrics.put("p0FactDefects", request.p0FactDefects());
		metrics.put("evaluatedQuestionCount", request.evaluatedQuestionCount());
		metrics.put("coveredScenarioCodes", request.coveredScenarioCodes());
		metrics.put("baselineCitationSupportRate", request.baselineCitationSupportRate());
		metrics.put("candidateCitationSupportRate", request.candidateCitationSupportRate());
		metrics.put("baselineUnsupportedClaimRate", request.baselineUnsupportedClaimRate());
		metrics.put("candidateUnsupportedClaimRate", request.candidateUnsupportedClaimRate());
		metrics.put("baselineFirstTokenMs", request.baselineFirstTokenMs());
		metrics.put("candidateFirstTokenMs", request.candidateFirstTokenMs());
		metrics.put("baselineTotalLatencyMs", request.baselineTotalLatencyMs());
		metrics.put("candidateTotalLatencyMs", request.candidateTotalLatencyMs());
		metrics.put("baselineCost", request.baselineCost());
		metrics.put("candidateCost", request.candidateCost());
		metrics.put("costUnit", request.costUnit());
		try {
			return objectMapper.writeValueAsString(metrics);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("GOLDEN_REPORT_METRICS_INVALID", ex);
		}
	}

	private AiAgentEntity requireNativeProfile(IdentityEnvelope identity, Long nativeProfileId) {
		if (nativeProfileId == null) {
			throw new IllegalArgumentException("NATIVE_PROFILE_REQUIRED");
		}
		AiAgentEntity profile = agentService.getById(nativeProfileId);
		if (profile == null || !"native".equals(profile.getAgentType()) || !"published".equals(profile.getStatus())
				|| !identity.tenantId().equals(profile.getTenantId())) {
			throw new IllegalArgumentException("NATIVE_PROFILE_NOT_AVAILABLE");
		}
		return profile;
	}

	private AiAssistantNativeReadinessEntity latest(String tenantId, Long nativeProfileId) {
		List<AiAssistantNativeReadinessEntity> decisions = mapper
			.selectList(Wrappers.<AiAssistantNativeReadinessEntity>lambdaQuery()
				.eq(AiAssistantNativeReadinessEntity::getTenantId, Long.valueOf(tenantId))
				.eq(AiAssistantNativeReadinessEntity::getNativeProfileId, nativeProfileId)
				.orderByDesc(AiAssistantNativeReadinessEntity::getId)
				.last("LIMIT 1"));
		return decisions.isEmpty() ? null : decisions.get(0);
	}

	private void insert(AiAssistantNativeReadinessEntity decision) {
		if (mapper.insert(decision) != 1) {
			throw new IllegalStateException("NATIVE_READINESS_AUDIT_FAILED");
		}
	}

	private ReadinessDecision toDecision(AiAssistantNativeReadinessEntity entity) {
		return new ReadinessDecision(entity.getId(), entity.getTenantId(), entity.getNativeProfileId(),
				entity.getDecisionStatus(), entity.getReportSha256(), entity.getProfileConfigSha256(),
				entity.getMetricsJson(), entity.getBusinessOwner(), entity.getDecidedBy(), entity.getReason(),
				entity.getCorrelationId(), toInstant(entity.getDecidedAt()), toInstant(entity.getExpiresAt()));
	}

	private void requireIdentity(IdentityEnvelope identity) {
		if (identity == null || !StringUtils.hasText(identity.tenantId()) || !identity.tenantId().matches("[0-9]+")
				|| !StringUtils.hasText(identity.userId())) {
			throw new IllegalArgumentException("ASSISTANT_IDENTITY_REQUIRED");
		}
	}

	private static String profileConfig(AiAgentEntity profile) {
		return profile.getConfig() == null ? "" : profile.getConfig();
	}

	private static Instant toInstant(LocalDateTime value) {
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}

	private static void requireRate(double value, String code) {
		if (!Double.isFinite(value) || value < 0 || value > 1) {
			throw new IllegalArgumentException(code);
		}
	}

	private static boolean coversRequiredScenarios(List<String> scenarioCodes) {
		if (scenarioCodes == null) {
			return false;
		}
		return scenarioCodes.stream()
			.filter(StringUtils::hasText)
			.collect(java.util.stream.Collectors.toSet())
			.containsAll(REQUIRED_SCENARIOS);
	}

	private static void requireNonNegative(long value, String code) {
		if (value < 0) {
			throw new IllegalArgumentException(code);
		}
	}

	private static void requireNonNegative(double value, String code) {
		if (!Double.isFinite(value) || value < 0) {
			throw new IllegalArgumentException(code);
		}
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	public record ReadinessApprovalRequest(Long nativeProfileId, String reportSha256, int p0PermissionDefects,
			int p0FactDefects, int evaluatedQuestionCount, List<String> coveredScenarioCodes,
			double baselineCitationSupportRate, double candidateCitationSupportRate,
			double baselineUnsupportedClaimRate, double candidateUnsupportedClaimRate, long baselineFirstTokenMs,
			long candidateFirstTokenMs, long baselineTotalLatencyMs, long candidateTotalLatencyMs, double baselineCost,
			double candidateCost, String costUnit, String businessOwner, Instant expiresAt, String reason) {

		public ReadinessApprovalRequest {
			coveredScenarioCodes = coveredScenarioCodes == null ? List.of() : List.copyOf(coveredScenarioCodes);
		}
	}

	public record ReadinessDecision(Long id, Long tenantId, Long nativeProfileId, String decisionStatus,
			String reportSha256, String profileConfigSha256, String metricsJson, String businessOwner, String decidedBy,
			String reason, String correlationId, Instant decidedAt, Instant expiresAt) {
	}

}
