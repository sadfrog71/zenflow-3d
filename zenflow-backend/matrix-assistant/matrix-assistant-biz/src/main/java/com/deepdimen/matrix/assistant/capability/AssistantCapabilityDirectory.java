package com.deepdimen.matrix.assistant.capability;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ProviderCapabilitySnapshot;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.WorkflowCapabilitySnapshot;
import com.deepdimen.matrix.assistant.capability.AssistantWorkflowCatalog.PublishedWorkflowCapability;
import com.deepdimen.matrix.assistant.capability.AssistantWorkflowCatalog.SideEffectClass;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.CatalogEntry;
import com.deepdimen.matrix.assistant.provider.AssistantProviderControlPlane.RuntimeStatus;
import com.deepdimen.matrix.assistant.provider.AssistantProviderDirectory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Freezes the tenant-authorized Provider and Workflow catalog for one Run. */
@Service
public class AssistantCapabilityDirectory {

	private final AssistantProviderDirectory providerDirectory;

	private final AssistantWorkflowCatalog workflowCatalog;

	private final ObjectMapper objectMapper;

	public AssistantCapabilityDirectory(AssistantProviderDirectory providerDirectory,
			ObjectProvider<AssistantWorkflowCatalog> workflowCatalogProvider, ObjectMapper objectMapper) {
		this.providerDirectory = providerDirectory;
		this.workflowCatalog = workflowCatalogProvider.getIfAvailable(() -> identity -> List.of());
		this.objectMapper = objectMapper;
	}

	public FrozenCapabilityCatalog freeze(IdentityEnvelope identity) {
		List<ProviderCapabilitySnapshot> providers = providerDirectory.catalog(identity)
			.stream()
			.filter(this::availableProvider)
			.map(this::providerSnapshot)
			.sorted(Comparator.comparing(ProviderCapabilitySnapshot::providerCode))
			.toList();
		List<WorkflowCapabilitySnapshot> workflows = workflowCatalog.listPublished(identity)
			.stream()
			.filter(capability -> eligible(identity, capability))
			.map(PublishedWorkflowCapability::snapshot)
			.sorted(Comparator.comparing(WorkflowCapabilitySnapshot::capabilityId)
				.thenComparingLong(WorkflowCapabilitySnapshot::releaseId))
			.toList();
		String digest = digest(providers, workflows);
		return new FrozenCapabilityCatalog(providers, workflows, digest, Instant.now());
	}

	private boolean availableProvider(CatalogEntry entry) {
		return entry.enabled() && entry.descriptor().capabilities().readOnly()
				&& (entry.status() == RuntimeStatus.AVAILABLE || entry.status() == RuntimeStatus.DEGRADED);
	}

	private ProviderCapabilitySnapshot providerSnapshot(CatalogEntry entry) {
		String descriptorDigest = sha256(write(entry.descriptor()));
		return new ProviderCapabilitySnapshot(entry.providerId().value(), entry.descriptor().displayName(),
				entry.descriptor().description(), entry.settingVersion(), descriptorDigest);
	}

	private boolean eligible(IdentityEnvelope identity, PublishedWorkflowCapability capability) {
		return capability != null && capability.snapshot() != null && identity.tenantId().equals(capability.tenantId())
				&& capability.enabled() && !capability.revoked() && capability.authorized()
				&& capability.policyAuditPassed() && capability.sideEffectClass() == SideEffectClass.READ_ONLY;
	}

	private String digest(List<ProviderCapabilitySnapshot> providers, List<WorkflowCapabilitySnapshot> workflows) {
		return sha256(write(new CatalogDigestMaterial(providers, workflows)));
	}

	private String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("CAPABILITY_CATALOG_SERIALIZATION_FAILED", ex);
		}
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	private record CatalogDigestMaterial(List<ProviderCapabilitySnapshot> providers,
			List<WorkflowCapabilitySnapshot> workflows) {
	}

	public record FrozenCapabilityCatalog(List<ProviderCapabilitySnapshot> providers,
			List<WorkflowCapabilitySnapshot> workflows, String digest, Instant frozenAt) {

		public FrozenCapabilityCatalog {
			providers = providers == null ? List.of() : List.copyOf(providers);
			workflows = workflows == null ? List.of() : List.copyOf(workflows);
		}

	}

}
