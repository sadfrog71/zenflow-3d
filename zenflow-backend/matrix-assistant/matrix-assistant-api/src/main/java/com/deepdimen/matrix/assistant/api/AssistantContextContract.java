package com.deepdimen.matrix.assistant.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;

public final class AssistantContextContract {

	public static final String VERSION = "assistant-context/v2";

	private static final Pattern PROVIDER_ID_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

	private AssistantContextContract() {
	}

	public record ProviderId(String value) {

		public static final ProviderId KNOWLEDGE = new ProviderId("KNOWLEDGE");

		public static final ProviderId WORK = new ProviderId("WORK");

		public static final ProviderId CAPSULE = new ProviderId("CAPSULE");

		public static final ProviderId INFORMATION = new ProviderId("INFORMATION");

		@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
		public ProviderId {
			if (value == null || !PROVIDER_ID_PATTERN.matcher(value).matches()) {
				throw new IllegalArgumentException("ProviderId must match [A-Z][A-Z0-9_]{0,63}");
			}
		}

		public static ProviderId of(String value) {
			return new ProviderId(value);
		}

		@Override
		@JsonValue
		public String value() {
			return value;
		}

		@Override
		public String toString() {
			return value;
		}

	}

	public enum ProviderStatus {

		OK, NO_MATCH, PARTIAL, UNAVAILABLE, FORBIDDEN_SCOPE

	}

	public enum FactType {

		DOMAIN_FACT, SOURCE_EVIDENCE, AI_SUMMARY, MODEL_INFERENCE

	}

	public enum ErrorCode {

		INVALID_SCOPE, PROVIDER_TIMEOUT, PROVIDER_UNAVAILABLE, PROVIDER_NOT_REGISTERED, FORBIDDEN_SCOPE,
		STALE_SOURCE_VERSION, VECTOR_INDEX_UNAVAILABLE, INTERNAL_ERROR

	}

	public record IdentityEnvelope(String tenantId, String userId, String currentOrgId, List<String> accessibleOrgIds,
			List<String> roleIds, List<String> deptIds, String correlationId, String purpose, String subjectType) {

		public IdentityEnvelope {
			accessibleOrgIds = immutableList(accessibleOrgIds);
			roleIds = immutableList(roleIds);
			deptIds = immutableList(deptIds);
			subjectType = subjectType == null ? "ADMIN_USER" : subjectType;
			if (!List.of("ADMIN_USER", "APP_USER").contains(subjectType)) throw new IllegalArgumentException("Invalid subject type");
		}
		public IdentityEnvelope(String tenantId, String userId, String currentOrgId, List<String> accessibleOrgIds,
				List<String> roleIds, List<String> deptIds, String correlationId, String purpose) {
			this(tenantId,userId,currentOrgId,accessibleOrgIds,roleIds,deptIds,correlationId,purpose,"ADMIN_USER");
		}
	}

	public record ProviderCapabilities(boolean readOnly, boolean supportsSearch, boolean supportsExplicitIds,
			boolean supportsTimeRange, boolean supportsCitations, boolean supportsFilters,
			boolean supportsStreamingProgress, List<String> supportedSourceTypes, List<String> supportedFilters) {

		public ProviderCapabilities {
			supportedSourceTypes = immutableList(supportedSourceTypes);
			supportedFilters = immutableList(supportedFilters);
		}
	}

	public record ProviderDescriptor(String contractVersion, ProviderId providerId, String displayName,
			String description, String iconKey, ProviderCapabilities capabilities, ProviderLimits defaultLimits) {

		public ProviderDescriptor {
			contractVersion = requireText(contractVersion, "contractVersion");
			providerId = Objects.requireNonNull(providerId, "providerId");
			displayName = displayName == null || displayName.isBlank() ? providerId.value() : displayName;
			description = description == null ? "" : description;
			iconKey = iconKey == null || iconKey.isBlank() ? "generic" : iconKey;
			capabilities = Objects.requireNonNull(capabilities, "capabilities");
			defaultLimits = Objects.requireNonNull(defaultLimits, "defaultLimits");
		}
	}

	public record ProviderScope(Instant from, Instant to, List<String> explicitObjectIds, String sourceRoute,
			Map<String, String> filters) {

		public ProviderScope {
			explicitObjectIds = immutableList(explicitObjectIds);
			filters = filters == null ? Map.of() : Map.copyOf(filters);
		}
	}

	public record ProviderLimits(int maxItems, int maxCharacters, long timeoutMs) {

		public ProviderLimits {
			maxItems = maxItems <= 0 ? 20 : Math.min(maxItems, 100);
			maxCharacters = maxCharacters <= 0 ? 12000 : Math.min(maxCharacters, 50000);
			timeoutMs = timeoutMs <= 0 ? 3000 : Math.min(timeoutMs, 10000);
		}
	}

	public record ProviderQuery(String contractVersion, String requestId, String runId, String correlationId,
			IdentityEnvelope identity, String query, ProviderScope scope, ProviderLimits limits,
			List<String> queryTerms) {

		public ProviderQuery(String contractVersion, String requestId, String correlationId, IdentityEnvelope identity,
				String query, ProviderScope scope, ProviderLimits limits) {
			this(contractVersion, requestId, requestId, correlationId, identity, query, scope, limits, List.of());
		}

		public ProviderQuery(String contractVersion, String requestId, String correlationId, IdentityEnvelope identity,
				String query, ProviderScope scope, ProviderLimits limits, List<String> queryTerms) {
			this(contractVersion, requestId, requestId, correlationId, identity, query, scope, limits, queryTerms);
		}

		public ProviderQuery {
			contractVersion = requireText(contractVersion, "contractVersion");
			requestId = requireText(requestId, "requestId");
			runId = runId == null || runId.isBlank() ? requestId : runId;
			identity = Objects.requireNonNull(identity, "identity");
			scope = scope == null ? new ProviderScope(null, null, null, null, null) : scope;
			limits = limits == null ? new ProviderLimits(0, 0, 0) : limits;
			queryTerms = immutableList(queryTerms);
		}
	}

	public record Citation(String citationId, ProviderId provider, String sourceType, String sourceId,
			String sourceVersion, String routeType, String routeName, Map<String, String> routeParams) {

		public Citation {
			routeParams = routeParams == null ? Map.of() : Map.copyOf(routeParams);
		}
	}

	public record ContextFragment(String fragmentId, ProviderId provider, String sourceType, String sourceId,
			String sourceVersion, FactType factType, String title, String excerpt, Instant occurredAt,
			Instant updatedAt, String contentHash, Citation citation, DataClassification dataClassification,
			int tokenEstimate) {

		public ContextFragment(String fragmentId, ProviderId provider, String sourceType, String sourceId,
				String sourceVersion, FactType factType, String title, String excerpt, Instant occurredAt,
				Instant updatedAt, String contentHash, Citation citation) {
			this(fragmentId, provider, sourceType, sourceId, sourceVersion, factType, title, excerpt, occurredAt,
					updatedAt, contentHash, citation, DataClassification.SENSITIVE, estimateTokens(excerpt));
		}

		public ContextFragment {
			fragmentId = requireText(fragmentId, "fragmentId");
			provider = Objects.requireNonNull(provider, "provider");
			if (citation != null && citation.provider() == null) {
				citation = new Citation(citation.citationId(), provider,
						citation.sourceType() == null ? sourceType : citation.sourceType(),
						citation.sourceId() == null ? sourceId : citation.sourceId(),
						citation.sourceVersion() == null ? sourceVersion : citation.sourceVersion(),
						citation.routeType(), citation.routeName(), citation.routeParams());
			}
			if (citation != null) {
				requireStableCitation(citation);
				if (!provider.equals(citation.provider()) || !Objects.equals(sourceType, citation.sourceType())
						|| !Objects.equals(sourceId, citation.sourceId())) {
					throw new IllegalArgumentException("Citation stable resource must match its context fragment");
				}
			}
			dataClassification = dataClassification == null ? DataClassification.SENSITIVE : dataClassification;
			tokenEstimate = tokenEstimate <= 0 ? estimateTokens(excerpt) : tokenEstimate;
		}
	}

	public record ProviderWarning(ErrorCode code, String message) {
	}

	public record ProviderResult(String contractVersion, ProviderId provider, ProviderStatus status, Instant dataAsOf,
			int returned, int permissionFiltered, boolean truncated, List<ContextFragment> fragments,
			List<ProviderWarning> warnings) {

		public ProviderResult {
			contractVersion = defaultVersion(contractVersion);
			provider = Objects.requireNonNull(provider, "provider");
			fragments = immutableList(fragments);
			warnings = immutableList(warnings);
			for (ContextFragment fragment : fragments) {
				if (!provider.equals(fragment.provider())) {
					throw new IllegalArgumentException("ProviderResult fragment provider must match result provider");
				}
			}
		}

		public static ProviderResult unavailable(ProviderId provider, String message) {
			return new ProviderResult(VERSION, provider, ProviderStatus.UNAVAILABLE, Instant.now(), 0, 0, false,
					List.of(), List.of(new ProviderWarning(ErrorCode.PROVIDER_UNAVAILABLE, message)));
		}

		public static ProviderResult notRegistered(ProviderId provider) {
			return new ProviderResult(VERSION, provider, ProviderStatus.UNAVAILABLE, Instant.now(), 0, 0, false,
					List.of(), List.of(new ProviderWarning(ErrorCode.PROVIDER_NOT_REGISTERED,
							"Provider is not registered: " + provider.value())));
		}
	}

	public record CitationResolveRequest(String runId, Citation citation, IdentityEnvelope identity) {

		public CitationResolveRequest(Citation citation, IdentityEnvelope identity) {
			this(identity == null ? null : identity.correlationId(), citation, identity);
		}

		public CitationResolveRequest {
			citation = Objects.requireNonNull(citation, "citation");
			requireStableCitation(citation);
			identity = Objects.requireNonNull(identity, "identity");
			runId = runId == null || runId.isBlank() ? identity.correlationId() : runId;
		}

		public String citationId() {
			return citation.citationId();
		}
	}

	public record CitationResolution(String citationId, ProviderStatus status, Citation citation,
			ProviderWarning warning) {

		public CitationResolution {
			citationId = requireText(citationId, "citationId");
			status = Objects.requireNonNull(status, "status");
			if (status == ProviderStatus.OK) {
				citation = Objects.requireNonNull(citation, "citation");
				requireStableCitation(citation);
				if (!citationId.equals(citation.citationId())) {
					throw new IllegalArgumentException("Resolved citationId must match the requested citation");
				}
			}
		}

		public static CitationResolution forbidden(String citationId, String message) {
			return new CitationResolution(citationId, ProviderStatus.FORBIDDEN_SCOPE, null,
					new ProviderWarning(ErrorCode.FORBIDDEN_SCOPE, message));
		}
	}

	private static <T> List<T> immutableList(List<T> source) {
		return source == null ? List.of() : List.copyOf(source);
	}

	private static String defaultVersion(String version) {
		return version == null || version.isBlank() ? VERSION : version;
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return value;
	}

	private static int estimateTokens(String content) {
		return content == null || content.isBlank() ? 0 : Math.max(1, (content.length() + 3) / 4);
	}

	private static void requireStableCitation(Citation citation) {
		requireText(citation.citationId(), "citation.citationId");
		Objects.requireNonNull(citation.provider(), "citation.provider");
		requireText(citation.sourceType(), "citation.sourceType");
		requireText(citation.sourceId(), "citation.sourceId");
	}

}
