package com.deepdimen.matrix.assistant.matrix;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.PlatformStatus;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderChangeAudit;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderDefinition;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.TenantProviderSetting;
import com.deepdimen.matrix.assistant.provider.AssistantProviderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
@ConditionalOnProperty(prefix = "matrix.assistant", name = "repository", havingValue = "jdbc")
public class JdbcAssistantProviderRepository implements AssistantProviderRepository {

	private final JdbcTemplate jdbcTemplate;

	private final ObjectMapper objectMapper;

	public JdbcAssistantProviderRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public List<ProviderDefinition> findDefinitions() {
		return jdbcTemplate.query("SELECT * FROM ai_assistant_provider_definition ORDER BY provider_code",
				this::mapDefinition);
	}

	@Override
	public Optional<ProviderDefinition> findDefinition(ProviderId providerId) {
		return jdbcTemplate
			.query("SELECT * FROM ai_assistant_provider_definition WHERE provider_code = ?", this::mapDefinition,
					providerId.value())
			.stream()
			.findFirst();
	}

	@Override
	public ProviderDefinition saveDefinition(ProviderDefinition definition, Long expectedVersion) {
		Instant now = Instant.now();
		if (expectedVersion == null) {
			try {
				jdbcTemplate.update(
						"""
								INSERT INTO ai_assistant_provider_definition
								(provider_code, service_id, platform_status, default_enabled, default_selected, default_data_classification,
								 governance_json, descriptor_snapshot_json, reconciled_at, version, created_at, updated_at)
								VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
								""",
						definition.providerId().value(), definition.serviceId(), definition.platformStatus().name(),
						definition.defaultEnabled(), definition.defaultSelected(),
						definition.defaultDataClassification().name(), definition.governanceJson(),
						toJsonOrNull(definition.descriptorSnapshot()), timestamp(definition.reconciledAt()),
						Timestamp.from(now), Timestamp.from(now));
			}
			catch (DuplicateKeyException ex) {
				throw new OptimisticLockingFailureException("Provider Definition already exists", ex);
			}
		}
		else {
			int updated = jdbcTemplate.update("""
					UPDATE ai_assistant_provider_definition
					SET service_id = ?, platform_status = ?, default_enabled = ?, default_selected = ?,
					    default_data_classification = ?, governance_json = ?, version = version + 1, updated_at = ?
					WHERE provider_code = ? AND version = ?
					""", definition.serviceId(), definition.platformStatus().name(), definition.defaultEnabled(),
					definition.defaultSelected(), definition.defaultDataClassification().name(),
					definition.governanceJson(), Timestamp.from(now), definition.providerId().value(), expectedVersion);
			if (updated != 1) {
				throw new OptimisticLockingFailureException("Provider Definition version conflict");
			}
		}
		return findDefinition(definition.providerId()).orElseThrow();
	}

	@Override
	public void saveDescriptorSnapshot(ProviderId providerId, ProviderDescriptor descriptor, Instant reconciledAt) {
		jdbcTemplate.update("""
				UPDATE ai_assistant_provider_definition
				SET descriptor_snapshot_json = ?, reconciled_at = ?, updated_at = ?
				WHERE provider_code = ?
				""", toJsonOrNull(descriptor), Timestamp.from(reconciledAt), Timestamp.from(reconciledAt),
				providerId.value());
	}

	@Override
	public List<TenantProviderSetting> findTenantSettings(String tenantId) {
		return jdbcTemplate.query(
				"SELECT * FROM ai_assistant_tenant_provider WHERE tenant_id = ? ORDER BY provider_code",
				this::mapTenantSetting, tenantId);
	}

	@Override
	public Optional<TenantProviderSetting> findTenantSetting(String tenantId, ProviderId providerId) {
		return jdbcTemplate
			.query("SELECT * FROM ai_assistant_tenant_provider WHERE tenant_id = ? AND provider_code = ?",
					this::mapTenantSetting, tenantId, providerId.value())
			.stream()
			.findFirst();
	}

	@Override
	public TenantProviderSetting saveTenantSetting(TenantProviderSetting setting, Long expectedVersion) {
		Instant now = Instant.now();
		if (expectedVersion == null) {
			try {
				jdbcTemplate.update(
						"""
								INSERT INTO ai_assistant_tenant_provider
								(tenant_id, provider_code, enabled, default_selected, limits_json, version, created_at, updated_at)
								VALUES (?, ?, ?, ?, ?, 1, ?, ?)
								""",
						setting.tenantId(), setting.providerId().value(), setting.enabled(), setting.defaultSelected(),
						toJsonOrNull(setting.limits()), Timestamp.from(now), Timestamp.from(now));
			}
			catch (DuplicateKeyException ex) {
				throw new OptimisticLockingFailureException("Tenant Provider setting already exists", ex);
			}
		}
		else {
			int updated = jdbcTemplate.update("""
					UPDATE ai_assistant_tenant_provider
					SET enabled = ?, default_selected = ?, limits_json = ?, version = version + 1, updated_at = ?
					WHERE tenant_id = ? AND provider_code = ? AND version = ?
					""", setting.enabled(), setting.defaultSelected(), toJsonOrNull(setting.limits()),
					Timestamp.from(now), setting.tenantId(), setting.providerId().value(), expectedVersion);
			if (updated != 1) {
				throw new OptimisticLockingFailureException("Tenant Provider setting version conflict");
			}
		}
		return findTenantSetting(setting.tenantId(), setting.providerId()).orElseThrow();
	}

	@Override
	public void appendAudit(ProviderChangeAudit audit) {
		jdbcTemplate.update("""
				INSERT INTO ai_assistant_provider_change_audit
				(scope_type, tenant_id, actor_user_id, provider_code, operation, previous_version,
				 new_version, correlation_id, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", audit.scopeType(), audit.tenantId(), audit.actorUserId(), audit.providerId().value(),
				audit.operation(), audit.previousVersion(), audit.newVersion(), audit.correlationId(),
				Timestamp.from(audit.createdAt()));
	}

	private ProviderDefinition mapDefinition(ResultSet resultSet, int rowNumber) throws SQLException {
		return new ProviderDefinition(ProviderId.of(resultSet.getString("provider_code")),
				resultSet.getString("service_id"), PlatformStatus.valueOf(resultSet.getString("platform_status")),
				resultSet.getBoolean("default_enabled"), resultSet.getBoolean("default_selected"),
				DataClassification.valueOf(resultSet.getString("default_data_classification")),
				resultSet.getString("governance_json"),
				fromJson(resultSet.getString("descriptor_snapshot_json"), ProviderDescriptor.class),
				instant(resultSet.getTimestamp("reconciled_at")), resultSet.getLong("version"),
				instant(resultSet.getTimestamp("created_at")), instant(resultSet.getTimestamp("updated_at")));
	}

	private TenantProviderSetting mapTenantSetting(ResultSet resultSet, int rowNumber) throws SQLException {
		return new TenantProviderSetting(resultSet.getString("tenant_id"),
				ProviderId.of(resultSet.getString("provider_code")), resultSet.getBoolean("enabled"),
				resultSet.getBoolean("default_selected"),
				fromJson(resultSet.getString("limits_json"), ProviderLimits.class), resultSet.getLong("version"),
				instant(resultSet.getTimestamp("updated_at")));
	}

	private String toJsonOrNull(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to serialize Provider control-plane data", ex);
		}
	}

	private <T> T fromJson(String value, Class<T> type) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readValue(value, type);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Unable to deserialize Provider control-plane data", ex);
		}
	}

	private Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private Instant instant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

}
