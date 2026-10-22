package com.deepdimen.matrix.assistant.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.WorkflowCapabilitySnapshot;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;
import com.deepdimen.matrix.assistant.capability.AssistantWorkflowCatalog.PublishedWorkflowCapability;
import com.deepdimen.matrix.assistant.capability.AssistantWorkflowCatalog.SideEffectClass;
import com.deepdimen.matrix.assistant.provider.AssistantProviderDirectory;
import com.deepdimen.matrix.assistant.provider.AssistantProviderRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssistantCapabilityDirectoryTest {

	private static final IdentityEnvelope IDENTITY = new IdentityEnvelope("11", "7", "3", List.of("3"), List.of(),
			List.of(), "catalog-test", "ASSISTANT_CONTEXT");

	@Test
	void freezesOnlyTenantAuthorizedReadOnlyCapabilitiesWithStableDigest() {
		AssistantWorkflowCatalog workflows = identity -> List.of(capability("11", "water", 2, true, false, true,
				SideEffectClass.READ_ONLY), capability("other", "foreign", 1, true, false, true,
				SideEffectClass.READ_ONLY), capability("11", "write", 1, true, false, true, SideEffectClass.WRITE),
				capability("11", "revoked", 1, true, true, true, SideEffectClass.READ_ONLY));
		AssistantCapabilityDirectory directory = new AssistantCapabilityDirectory(providerDirectory(),
				provider(workflows), new ObjectMapper());

		FrozenCapabilityCatalog first = directory.freeze(IDENTITY);
		FrozenCapabilityCatalog second = directory.freeze(IDENTITY);

		assertEquals(List.of("KNOWLEDGE"), first.providers().stream().map(item -> item.providerCode()).toList());
		assertEquals(List.of("water"), first.workflows().stream().map(item -> item.capabilityId()).toList());
		assertEquals(first.digest(), second.digest());
	}

	@Test
	void publishedReleaseChangeProducesANewCatalogDigest() {
		AssistantCapabilityDirectory first = new AssistantCapabilityDirectory(providerDirectory(),
				provider(identity -> List.of(capability("11", "water", 1, true, false, true,
						SideEffectClass.READ_ONLY))), new ObjectMapper());
		AssistantCapabilityDirectory second = new AssistantCapabilityDirectory(providerDirectory(),
				provider(identity -> List.of(capability("11", "water", 2, true, false, true,
						SideEffectClass.READ_ONLY))), new ObjectMapper());

		assertNotEquals(first.freeze(IDENTITY).digest(), second.freeze(IDENTITY).digest());
	}

	private PublishedWorkflowCapability capability(String tenantId, String capabilityId, long releaseId,
			boolean enabled, boolean revoked, boolean audited, SideEffectClass sideEffectClass) {
		WorkflowCapabilitySnapshot snapshot = new WorkflowCapabilitySnapshot(capabilityId, releaseId, "v" + releaseId,
				"Water", "Water flow", List.of("water quality"), List.of("read only"), "input-v1", "output-v1",
				"workflow-" + releaseId, "digest-" + releaseId);
		return new PublishedWorkflowCapability(tenantId, snapshot, enabled, revoked, true, audited, sideEffectClass,
				DataClassification.INTERNAL, List.of("KNOWLEDGE"));
	}

	private AssistantProviderDirectory providerDirectory() {
		ProviderId knowledge = ProviderId.KNOWLEDGE;
		ProviderDescriptor descriptor = new ProviderDescriptor(AssistantContextContract.VERSION, knowledge, "Knowledge",
				"Knowledge docs", "book", new ProviderCapabilities(true, true, true, true, true, true, false,
						List.of("document"), List.of()), new ProviderLimits(5, 1000, 1000));
		var provider = new com.deepdimen.matrix.assistant.api.AssistantContextProvider() {
			@Override
			public ProviderDescriptor describe() {
				return descriptor;
			}

			@Override
			public com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult query(
					com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery query) {
				throw new UnsupportedOperationException();
			}
		};
		return new AssistantProviderDirectory(new AssistantProviderRegistry(List.of(provider)));
	}

	private ObjectProvider<AssistantWorkflowCatalog> provider(AssistantWorkflowCatalog catalog) {
		return new ObjectProvider<>() {
			@Override
			public AssistantWorkflowCatalog getObject(Object... args) {
				return catalog;
			}

			@Override
			public AssistantWorkflowCatalog getIfAvailable() {
				return catalog;
			}

			@Override
			public AssistantWorkflowCatalog getIfUnique() {
				return catalog;
			}

			@Override
			public AssistantWorkflowCatalog getObject() {
				return catalog;
			}
		};
	}

}
