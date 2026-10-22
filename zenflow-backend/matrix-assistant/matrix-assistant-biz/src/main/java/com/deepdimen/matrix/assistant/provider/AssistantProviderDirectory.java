package com.deepdimen.matrix.assistant.provider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.CatalogEntry;

/** Tenant-aware directory and selection policy above the transport registry. */
@Service
public class AssistantProviderDirectory {

	private final AssistantProviderRegistry registry;

	public AssistantProviderDirectory(AssistantProviderRegistry registry) {
		this.registry = registry;
	}

	public List<CatalogEntry> catalog(IdentityEnvelope identity) {
		return registry.catalog(identity);
	}

	public List<ProviderId> defaultProviderIds(IdentityEnvelope identity) {
		return registry.defaultProviderIds(identity);
	}

	public List<ProviderId> validateSelection(IdentityEnvelope identity, List<ProviderId> selected) {
		if (selected == null) {
			return defaultProviderIds(identity);
		}
		Map<ProviderId, CatalogEntry> entries = new LinkedHashMap<>();
		catalog(identity).forEach(entry -> entries.put(entry.providerId(), entry));
		for (ProviderId providerId : selected) {
			CatalogEntry entry = entries.get(providerId);
			if (entry == null) {
				throw new ProviderSelectionException("PROVIDER_NOT_REGISTERED", providerId);
			}
			if (!entry.enabled()) {
				throw new ProviderSelectionException("PROVIDER_NOT_ENABLED", providerId);
			}
		}
		return List.copyOf(selected);
	}

	public static final class ProviderSelectionException extends IllegalArgumentException {

		private static final long serialVersionUID = 1L;

		private final String code;

		private final ProviderId providerId;

		public ProviderSelectionException(String code, ProviderId providerId) {
			super(code + ": " + providerId);
			this.code = code;
			this.providerId = providerId;
		}

		public String code() {
			return code;
		}

		public ProviderId providerId() {
			return providerId;
		}

	}

}
