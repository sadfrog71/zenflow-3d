package com.deepdimen.matrix.assistant.provider;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;

/**
 * Persistent control-plane vocabulary; provider business payloads never enter these
 * records.
 */
public final class AssistantProviderControlPlane {

	private static final Pattern SERVICE_ID = Pattern.compile("[a-z][a-z0-9-]{0,62}");

	private AssistantProviderControlPlane() {
	}

	public enum PlatformStatus {

		ACTIVE, DISABLED

	}

	public enum RuntimeStatus {

		AVAILABLE, DEGRADED, UNAVAILABLE, INCOMPATIBLE, DISABLED

	}

	public record ProviderDefinition(ProviderId providerId, String serviceId, PlatformStatus platformStatus,
			boolean defaultEnabled, boolean defaultSelected, DataClassification defaultDataClassification,
			String governanceJson, ProviderDescriptor descriptorSnapshot, Instant reconciledAt, long version,
			Instant createdAt, Instant updatedAt) {

		public ProviderDefinition {
			providerId = Objects.requireNonNull(providerId, "providerId");
			if (serviceId == null || !SERVICE_ID.matcher(serviceId).matches()) {
				throw new IllegalArgumentException("Provider serviceId is not trusted");
			}
			platformStatus = Objects.requireNonNull(platformStatus, "platformStatus");
			defaultDataClassification = defaultDataClassification == null ? DataClassification.SENSITIVE
					: defaultDataClassification;
			governanceJson = governanceJson == null || governanceJson.isBlank() ? "{}" : governanceJson;
			if (defaultSelected && !defaultEnabled) {
				throw new IllegalArgumentException("A default-selected Provider must also be enabled");
			}
		}
	}

	public record TenantProviderSetting(String tenantId, ProviderId providerId, boolean enabled,
			boolean defaultSelected, ProviderLimits limits, long version, Instant updatedAt) {

		public TenantProviderSetting {
			if (tenantId == null || tenantId.isBlank()) {
				throw new IllegalArgumentException("tenantId must not be blank");
			}
			providerId = Objects.requireNonNull(providerId, "providerId");
			limits = limits == null ? new ProviderLimits(0, 0, 0) : limits;
			if (defaultSelected && !enabled) {
				throw new IllegalArgumentException("A default-selected Provider must also be enabled");
			}
		}
	}

	public record CatalogEntry(ProviderId providerId, ProviderDescriptor descriptor, RuntimeStatus status,
			boolean enabled, boolean defaultSelected, boolean local, String safeMessage, long settingVersion) {
	}

	public record ProviderChangeAudit(Long id, String scopeType, String tenantId, String actorUserId,
			ProviderId providerId, String operation, Long previousVersion, long newVersion, String correlationId,
			Instant createdAt) {
	}

}
