package com.deepdimen.matrix.assistant.provider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderDefinition;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.ProviderChangeAudit;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.TenantProviderSetting;

public interface AssistantProviderRepository {

	List<ProviderDefinition> findDefinitions();

	Optional<ProviderDefinition> findDefinition(ProviderId providerId);

	ProviderDefinition saveDefinition(ProviderDefinition definition, Long expectedVersion);

	void saveDescriptorSnapshot(ProviderId providerId, ProviderDescriptor descriptor, Instant reconciledAt);

	List<TenantProviderSetting> findTenantSettings(String tenantId);

	Optional<TenantProviderSetting> findTenantSetting(String tenantId, ProviderId providerId);

	TenantProviderSetting saveTenantSetting(TenantProviderSetting setting, Long expectedVersion);

	default void appendAudit(ProviderChangeAudit audit) {
	}

}
