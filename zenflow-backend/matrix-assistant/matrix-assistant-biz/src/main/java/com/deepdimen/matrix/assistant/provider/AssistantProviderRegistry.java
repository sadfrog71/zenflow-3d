package com.deepdimen.matrix.assistant.provider;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolveRequest;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.CitationResolution;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.CatalogEntry;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.PlatformStatus;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderDefinition;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.RuntimeStatus;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.TenantProviderSetting;

/**
 * Dynamic local-first directory. Definitions select transport; runtime descriptors remain
 * authoritative.
 */
@Component
public class AssistantProviderRegistry {

	private static final Pattern TRUSTED_SERVICE_ID = Pattern.compile("[a-z][a-z0-9-]{0,62}");

	private final Map<ProviderId, AssistantContextProvider> localProviders;

	private final AssistantProviderRepository repository;

	private final AssistantRemoteProviderFactory remoteFactory;

	private final AssistantProviderCircuitBreaker circuitBreaker;

	public AssistantProviderRegistry(List<AssistantContextProvider> providerList) {
		this(providerList, null, null, new AssistantProviderCircuitBreaker());
	}

	@Autowired
	public AssistantProviderRegistry(List<AssistantContextProvider> providerList,
			ObjectProvider<AssistantProviderRepository> repositoryProvider,
			ObjectProvider<AssistantRemoteProviderFactory> remoteFactoryProvider,
			ObjectProvider<AssistantProviderCircuitBreaker> circuitBreakerProvider) {
		this(providerList, repositoryProvider.getIfAvailable(), remoteFactoryProvider.getIfAvailable(),
				circuitBreakerProvider.getIfAvailable(AssistantProviderCircuitBreaker::new));
	}

	AssistantProviderRegistry(List<AssistantContextProvider> providerList, AssistantProviderRepository repository,
			AssistantRemoteProviderFactory remoteFactory) {
		this(providerList, repository, remoteFactory, new AssistantProviderCircuitBreaker());
	}

	AssistantProviderRegistry(List<AssistantContextProvider> providerList, AssistantProviderRepository repository,
			AssistantRemoteProviderFactory remoteFactory, AssistantProviderCircuitBreaker circuitBreaker) {
		Map<ProviderId, AssistantContextProvider> indexed = new LinkedHashMap<>();
		for (AssistantContextProvider provider : providerList) {
			ProviderId providerId = provider.describe().providerId();
			AssistantContextProvider previous = indexed.put(providerId, provider);
			if (previous != null) {
				throw new IllegalStateException("Duplicate assistant provider: " + providerId);
			}
		}
		this.localProviders = Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
		this.repository = repository;
		this.remoteFactory = remoteFactory;
		this.circuitBreaker = circuitBreaker;
	}

	public List<ProviderId> providerIds() {
		return List.copyOf(localProviders.keySet());
	}

	public List<ProviderId> defaultProviderIds(IdentityEnvelope identity) {
		if (repository == null) {
			return providerIds();
		}
		return catalog(identity).stream()
			.filter(CatalogEntry::enabled)
			.filter(CatalogEntry::defaultSelected)
			.map(CatalogEntry::providerId)
			.toList();
	}

	public List<CatalogEntry> catalog(IdentityEnvelope identity) {
		if (repository == null) {
			return localProviders.entrySet()
				.stream()
				.map(entry -> new CatalogEntry(entry.getKey(), entry.getValue().describe(), RuntimeStatus.AVAILABLE,
						true, true, true, "", 0))
				.toList();
		}
		Map<ProviderId, TenantProviderSetting> settings = new LinkedHashMap<>();
		repository.findTenantSettings(identity.tenantId())
			.forEach(setting -> settings.put(setting.providerId(), setting));
		return repository.findDefinitions()
			.stream()
			.filter(definition -> definition.defaultEnabled() || settings.containsKey(definition.providerId()))
			.map(definition -> catalogEntry(identity, definition, settings.get(definition.providerId())))
			.toList();
	}

	public ProviderResult query(ProviderId providerId, ProviderQuery request) {
		if (!circuitBreaker.allow(providerId)) {
			return ProviderResult.unavailable(providerId, "Provider 暂时熔断，请稍后重试。");
		}
		ResolvedTarget target;
		try {
			target = resolveTarget(providerId, request.identity(), request.runId());
		}
		catch (RuntimeException ex) {
			circuitBreaker.recordFailure(providerId);
			return ProviderResult.unavailable(providerId, "Provider 当前不可用。");
		}
		if (!target.enabled()) {
			circuitBreaker.recordSuccess(providerId);
			return "PROVIDER_NOT_REGISTERED".equals(target.safeMessage()) ? ProviderResult.notRegistered(providerId)
					: ProviderResult.unavailable(providerId, target.safeMessage());
		}
		try {
			ProviderDescriptor descriptor = target.provider().describe();
			requireCompatible(providerId, descriptor);
			reconcile(providerId, descriptor);
			ProviderLimits limits = effectiveLimits(request.limits(), target.tenantLimits(), descriptor.defaultLimits());
			ProviderQuery boundedRequest = new ProviderQuery(request.contractVersion(), request.requestId(), request.runId(),
					request.correlationId(), request.identity(), request.query(), request.scope(), limits,
					request.queryTerms());
			ProviderResult result = target.provider().query(boundedRequest);
			if (!providerId.equals(result.provider())) {
				return ProviderResult.unavailable(providerId, "Provider 返回了不一致的来源代码。");
			}
			ProviderResult governed = applyLimits(applyGovernance(result, target.definition()), limits);
			if (governed.status() == AssistantContextContract.ProviderStatus.UNAVAILABLE) {
				circuitBreaker.recordFailure(providerId);
			}
			else {
				circuitBreaker.recordSuccess(providerId);
			}
			return governed;
		}
		catch (RuntimeException ex) {
			circuitBreaker.recordFailure(providerId);
			return ProviderResult.unavailable(providerId, "Provider 当前不可用。");
		}
	}

	public long queryTimeoutMs(ProviderId providerId, IdentityEnvelope identity, ProviderLimits requested) {
		ProviderLimits baseline = requested == null ? new ProviderLimits(20, 12000, 3000) : requested;
		ProviderLimits providerDefault = baseline;
		ProviderLimits tenantLimits = null;
		AssistantContextProvider local = localProviders.get(providerId);
		if (local != null) {
			try {
				providerDefault = local.describe().defaultLimits();
			}
			catch (RuntimeException ignored) {
				// The query path will normalize the provider failure; timeout remains bounded.
			}
		}
		if (repository != null) {
			ProviderDefinition definition = repository.findDefinition(providerId).orElse(null);
			if (local == null && definition != null && definition.descriptorSnapshot() != null) {
				providerDefault = definition.descriptorSnapshot().defaultLimits();
			}
			tenantLimits = repository.findTenantSetting(identity.tenantId(), providerId)
				.map(TenantProviderSetting::limits)
				.orElse(null);
		}
		return effectiveLimits(baseline, tenantLimits, providerDefault).timeoutMs();
	}

	public CitationResolution resolve(CitationResolveRequest request) {
		ProviderId providerId = request.citation().provider();
		ResolvedTarget target = resolveTarget(providerId, request.identity(), request.runId());
		if (!target.enabled()) {
			throw new ProviderNotRegisteredException(providerId);
		}
		ProviderDescriptor descriptor = target.provider().describe();
		requireCompatible(providerId, descriptor);
		return target.provider().resolve(request);
	}

	public AssistantContextProvider require(ProviderId providerId) {
		AssistantContextProvider provider = localProviders.get(providerId);
		if (provider == null) {
			throw new ProviderNotRegisteredException(providerId);
		}
		return provider;
	}

	public Optional<AssistantContextProvider> local(ProviderId providerId) {
		return Optional.ofNullable(localProviders.get(providerId));
	}

	private CatalogEntry catalogEntry(IdentityEnvelope identity, ProviderDefinition definition,
			TenantProviderSetting setting) {
		boolean enabled = setting == null ? definition.defaultEnabled() : setting.enabled();
		boolean selected = setting == null ? definition.defaultSelected() : setting.defaultSelected();
		long settingVersion = setting == null ? 0 : setting.version();
		if (definition.platformStatus() != PlatformStatus.ACTIVE || !enabled) {
			return new CatalogEntry(definition.providerId(), snapshotOrGeneric(definition), RuntimeStatus.DISABLED,
					false, false, localProviders.containsKey(definition.providerId()), "该来源未启用。", settingVersion);
		}
		try {
			ResolvedTarget target = targetForEnabledDefinition(definition, setting, identity, "catalog");
			ProviderDescriptor descriptor = target.provider().describe();
			requireCompatible(definition.providerId(), descriptor);
			boolean reconciled = reconcile(definition.providerId(), descriptor);
			return new CatalogEntry(definition.providerId(), descriptor,
					reconciled ? RuntimeStatus.AVAILABLE : RuntimeStatus.DEGRADED, true, selected, target.local(),
					reconciled ? "" : "Provider 描述快照暂未对账。", settingVersion);
		}
		catch (ProviderContractIncompatibleException ex) {
			return new CatalogEntry(definition.providerId(), snapshotOrGeneric(definition), RuntimeStatus.INCOMPATIBLE,
					true, selected, localProviders.containsKey(definition.providerId()), "Provider 契约不兼容。",
					settingVersion);
		}
		catch (RuntimeException ex) {
			return new CatalogEntry(definition.providerId(), snapshotOrGeneric(definition), RuntimeStatus.UNAVAILABLE,
					true, selected, localProviders.containsKey(definition.providerId()), "Provider 当前不可用。",
					settingVersion);
		}
	}

	private ResolvedTarget resolveTarget(ProviderId providerId, IdentityEnvelope identity, String runId) {
		if (repository == null) {
			AssistantContextProvider local = localProviders.get(providerId);
			return local == null ? ResolvedTarget.disabled("PROVIDER_NOT_REGISTERED") : ResolvedTarget.local(local);
		}
		ProviderDefinition definition = repository.findDefinition(providerId).orElse(null);
		if (definition == null) {
			return ResolvedTarget.disabled("PROVIDER_NOT_REGISTERED");
		}
		TenantProviderSetting setting = repository.findTenantSetting(identity.tenantId(), providerId).orElse(null);
		boolean enabled = definition.platformStatus() == PlatformStatus.ACTIVE
				&& (setting == null ? definition.defaultEnabled() : setting.enabled());
		return enabled ? targetForEnabledDefinition(definition, setting, identity, runId)
				: ResolvedTarget.disabled("Provider 未对当前租户启用。");
	}

	private ResolvedTarget targetForEnabledDefinition(ProviderDefinition definition, TenantProviderSetting setting,
			IdentityEnvelope identity, String runId) {
		AssistantContextProvider local = localProviders.get(definition.providerId());
		if (local != null) {
			return ResolvedTarget.local(local, definition, setting == null ? null : setting.limits());
		}
		if (remoteFactory == null || !trustedServiceId(definition.serviceId())) {
			throw new IllegalStateException("REMOTE_PROVIDER_NOT_CONFIGURED");
		}
		return ResolvedTarget.remote(remoteFactory.create(definition, identity, runId), definition,
				setting == null ? null : setting.limits());
	}

	private ProviderDescriptor snapshotOrGeneric(ProviderDefinition definition) {
		if (definition.descriptorSnapshot() != null) {
			return definition.descriptorSnapshot();
		}
		return new UnavailableAssistantContextProvider(definition.providerId()).describe();
	}

	private void requireCompatible(ProviderId expected, ProviderDescriptor descriptor) {
		if (descriptor == null || !expected.equals(descriptor.providerId())
				|| !AssistantContextContract.VERSION.equals(descriptor.contractVersion())) {
			throw new ProviderContractIncompatibleException();
		}
	}

	private boolean reconcile(ProviderId providerId, ProviderDescriptor descriptor) {
		if (repository == null) {
			return true;
		}
		try {
			repository.saveDescriptorSnapshot(providerId, descriptor, Instant.now());
			return true;
		}
		catch (RuntimeException ex) {
			return false;
		}
	}

	private boolean trustedServiceId(String serviceId) {
		return serviceId != null && TRUSTED_SERVICE_ID.matcher(serviceId).matches();
	}

	private ProviderResult applyGovernance(ProviderResult result, ProviderDefinition definition) {
		if (definition == null) {
			return result;
		}
		DataClassification floor = definition.defaultDataClassification();
		List<ContextFragment> governed = result.fragments().stream().map(fragment -> {
			DataClassification classification = fragment.dataClassification().ordinal() < floor.ordinal() ? floor
					: fragment.dataClassification();
			return new ContextFragment(fragment.fragmentId(), fragment.provider(), fragment.sourceType(),
					fragment.sourceId(), fragment.sourceVersion(), fragment.factType(), fragment.title(),
					fragment.excerpt(), fragment.occurredAt(), fragment.updatedAt(), fragment.contentHash(),
					fragment.citation(), classification, fragment.tokenEstimate());
		}).toList();
		return new ProviderResult(result.contractVersion(), result.provider(), result.status(), result.dataAsOf(),
				result.returned(), result.permissionFiltered(), result.truncated(), governed, result.warnings());
	}

	private ProviderLimits effectiveLimits(ProviderLimits requested, ProviderLimits tenant,
			ProviderLimits providerDefault) {
		ProviderLimits baseline = requested == null ? providerDefault : requested;
		int maxItems = Math.min(baseline.maxItems(), providerDefault.maxItems());
		int maxCharacters = Math.min(baseline.maxCharacters(), providerDefault.maxCharacters());
		long timeoutMs = Math.min(baseline.timeoutMs(), providerDefault.timeoutMs());
		if (tenant != null) {
			maxItems = Math.min(maxItems, tenant.maxItems());
			maxCharacters = Math.min(maxCharacters, tenant.maxCharacters());
			timeoutMs = Math.min(timeoutMs, tenant.timeoutMs());
		}
		return new ProviderLimits(maxItems, maxCharacters, timeoutMs);
	}

	private ProviderResult applyLimits(ProviderResult result, ProviderLimits limits) {
		List<ContextFragment> accepted = new java.util.ArrayList<>();
		int characters = 0;
		for (ContextFragment fragment : result.fragments()) {
			int fragmentCharacters = fragment.excerpt() == null ? 0 : fragment.excerpt().length();
			if (accepted.size() >= limits.maxItems() || characters + fragmentCharacters > limits.maxCharacters()) {
				break;
			}
			accepted.add(fragment);
			characters += fragmentCharacters;
		}
		boolean truncated = result.truncated() || accepted.size() < result.fragments().size();
		var status = truncated && result.status() == AssistantContextContract.ProviderStatus.OK
				? AssistantContextContract.ProviderStatus.PARTIAL : result.status();
		return new ProviderResult(result.contractVersion(), result.provider(), status, result.dataAsOf(),
				accepted.size(), result.permissionFiltered(), truncated, accepted, result.warnings());
	}

	private record ResolvedTarget(AssistantContextProvider provider, boolean enabled, boolean local, String safeMessage,
			ProviderDefinition definition, ProviderLimits tenantLimits) {

		private static ResolvedTarget local(AssistantContextProvider provider) {
			return new ResolvedTarget(provider, true, true, "", null, null);
		}

		private static ResolvedTarget local(AssistantContextProvider provider, ProviderDefinition definition,
				ProviderLimits tenantLimits) {
			return new ResolvedTarget(provider, true, true, "", definition, tenantLimits);
		}

		private static ResolvedTarget remote(AssistantContextProvider provider, ProviderDefinition definition,
				ProviderLimits tenantLimits) {
			return new ResolvedTarget(provider, true, false, "", definition, tenantLimits);
		}

		private static ResolvedTarget disabled(String message) {
			return new ResolvedTarget(null, false, false, message, null, null);
		}
	}

	private static final class ProviderContractIncompatibleException extends RuntimeException {

		private static final long serialVersionUID = 1L;

	}

	public static final class ProviderNotRegisteredException extends IllegalStateException {

		private static final long serialVersionUID = 1L;

		public ProviderNotRegisteredException(ProviderId providerId) {
			super("Assistant provider is not registered: " + providerId);
		}

	}

}
