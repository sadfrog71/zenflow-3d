package com.deepdimen.matrix.assistant.provider;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.CatalogEntry;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.PlatformStatus;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderChangeAudit;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderDefinition;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.TenantProviderSetting;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Control-plane operations. It never invokes provider search or returns business
 * fragments.
 */
@Service
public class AssistantProviderCatalogService {

	private static final Pattern SERVICE_ID = Pattern.compile("[a-z][a-z0-9-]{0,62}");

	private final AssistantProviderDirectory directory;

	private final AssistantProviderRepository repository;

	private final ObjectMapper objectMapper;

	public AssistantProviderCatalogService(AssistantProviderDirectory directory,
			ObjectProvider<AssistantProviderRepository> repositoryProvider, ObjectMapper objectMapper) {
		this.directory = directory;
		this.repository = repositoryProvider.getIfAvailable();
		this.objectMapper = objectMapper;
	}

	public List<CatalogEntry> catalog(IdentityEnvelope identity) {
		return directory.catalog(identity);
	}

	public ProviderDiagnostic diagnose(IdentityEnvelope identity, ProviderId providerId) {
		CatalogEntry entry = directory.catalog(identity)
			.stream()
			.filter(candidate -> providerId.equals(candidate.providerId()))
			.findFirst()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider Definition 不存在"));
		return new ProviderDiagnostic(entry.providerId(), entry.status(), entry.descriptor().contractVersion(),
				entry.descriptor().capabilities(), entry.local(), entry.safeMessage());
	}

	public List<ProviderDefinition> definitions() {
		return requireRepository().findDefinitions();
	}

	@Transactional
	public ProviderDefinition saveDefinition(IdentityEnvelope actor, ProviderDefinitionRequest request) {
		Objects.requireNonNull(request, "request");
		ProviderId providerId = ProviderId.of(request.providerCode());
		if (request.serviceId() == null || !SERVICE_ID.matcher(request.serviceId()).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "serviceId 必须是受信逻辑服务名");
		}
		AssistantProviderRepository controlPlane = requireRepository();
		ProviderDefinition current = controlPlane.findDefinition(providerId).orElse(null);
		if (request.defaultSelected() && !request.defaultEnabled()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "默认选中的 Provider 必须同时启用");
		}
		if (current == null && (request.defaultEnabled() || request.defaultSelected())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新 Provider 必须默认关闭");
		}
		Instant now = Instant.now();
		ProviderDefinition candidate = new ProviderDefinition(providerId, request.serviceId(),
				request.platformStatus() == null ? PlatformStatus.DISABLED : request.platformStatus(),
				request.defaultEnabled(), request.defaultSelected(), request.defaultDataClassification(),
				safeJson(request.governanceJson()), current == null ? null : current.descriptorSnapshot(),
				current == null ? null : current.reconciledAt(), current == null ? 0 : current.version(),
				current == null ? now : current.createdAt(), now);
		ProviderDefinition saved;
		try {
			saved = controlPlane.saveDefinition(candidate, request.expectedVersion());
		}
		catch (OptimisticLockingFailureException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Provider Definition 版本已变更", ex);
		}
		controlPlane.appendAudit(new ProviderChangeAudit(null, "PLATFORM_DEFINITION", actor.tenantId(), actor.userId(),
				providerId, current == null ? "CREATE" : "UPDATE", current == null ? null : current.version(),
				saved.version(), actor.correlationId(), now));
		return saved;
	}

	public List<TenantProviderSetting> tenantSettings(IdentityEnvelope identity) {
		return requireRepository().findTenantSettings(identity.tenantId());
	}

	@Transactional
	public TenantProviderSetting saveTenantSetting(IdentityEnvelope actor, ProviderId providerId,
			TenantProviderSettingRequest request) {
		AssistantProviderRepository controlPlane = requireRepository();
		if (controlPlane.findDefinition(providerId).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider Definition 不存在");
		}
		if (request.defaultSelected() && !request.enabled()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "默认选中的 Provider 必须同时启用");
		}
		ProviderLimits limits = request.limits() == null ? new ProviderLimits(0, 0, 0) : request.limits();
		TenantProviderSetting current = controlPlane.findTenantSetting(actor.tenantId(), providerId).orElse(null);
		TenantProviderSetting candidate = new TenantProviderSetting(actor.tenantId(), providerId, request.enabled(),
				request.defaultSelected(), limits, current == null ? 0 : current.version(), Instant.now());
		TenantProviderSetting saved;
		try {
			saved = controlPlane.saveTenantSetting(candidate, request.expectedVersion());
		}
		catch (OptimisticLockingFailureException ex) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "租户 Provider 设置版本已变更", ex);
		}
		controlPlane.appendAudit(new ProviderChangeAudit(null, "TENANT_SETTING", actor.tenantId(), actor.userId(),
				providerId, current == null ? "CREATE" : "UPDATE", current == null ? null : current.version(),
				saved.version(), actor.correlationId(), Instant.now()));
		return saved;
	}

	private String safeJson(String value) {
		if (value == null || value.isBlank()) {
			return "{}";
		}
		try {
			JsonNode root = objectMapper.readTree(value);
			if (root == null || !root.isObject() || containsCredentialOrEndpoint(root)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "governanceJson 不允许保存 URL 或凭证");
			}
			return objectMapper.writeValueAsString(root);
		}
		catch (JsonProcessingException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "governanceJson 必须是有效 JSON 对象", ex);
		}
	}

	private boolean containsCredentialOrEndpoint(JsonNode node) {
		if (node.isObject()) {
			var fields = node.fields();
			while (fields.hasNext()) {
				var field = fields.next();
				String name = field.getKey().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
				if (name.contains("token") || name.contains("secret") || name.contains("password")
						|| name.contains("apikey") || name.contains("credential") || name.contains("endpoint")
						|| name.equals("url") || containsCredentialOrEndpoint(field.getValue())) {
					return true;
				}
			}
			return false;
		}
		if (node.isArray()) {
			for (JsonNode item : node) {
				if (containsCredentialOrEndpoint(item)) {
					return true;
				}
			}
		}
		return node.isTextual() && (node.textValue().startsWith("http://") || node.textValue().startsWith("https://"));
	}

	private AssistantProviderRepository requireRepository() {
		if (repository == null) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Provider 控制面尚未配置");
		}
		return repository;
	}

	public record ProviderDefinitionRequest(String providerCode, String serviceId, PlatformStatus platformStatus,
			boolean defaultEnabled, boolean defaultSelected, DataClassification defaultDataClassification,
			String governanceJson, Long expectedVersion) {
	}

	public record TenantProviderSettingRequest(boolean enabled, boolean defaultSelected, ProviderLimits limits,
			Long expectedVersion) {
	}

	public record ProviderDiagnostic(ProviderId providerId, AssistantProviderControlPlane.RuntimeStatus status,
			String contractVersion,
			com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderCapabilities capabilities,
			boolean local, String safeMessage) {
	}

}
