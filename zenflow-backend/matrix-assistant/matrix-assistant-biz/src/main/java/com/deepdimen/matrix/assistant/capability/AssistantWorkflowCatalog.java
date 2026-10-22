package com.deepdimen.matrix.assistant.capability;

import java.util.List;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.WorkflowCapabilitySnapshot;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;

/**
 * Runtime port for tenant-authorized Workflow capability snapshots. Implementations
 * must freeze the routing evidence for the current Run and must not return credentials,
 * mutable Agent rows or raw Dify DSL.
 */
public interface AssistantWorkflowCatalog {

	List<PublishedWorkflowCapability> listPublished(IdentityEnvelope identity);

	default List<PublishedWorkflowCapability> emptyCatalog() {
		return List.of();
	}

	enum SideEffectClass {

		READ_ONLY, PROPOSAL_ONLY, WRITE, UNKNOWN

	}

	record PublishedWorkflowCapability(String tenantId, WorkflowCapabilitySnapshot snapshot, boolean enabled,
			boolean revoked, boolean authorized, boolean policyAuditPassed, SideEffectClass sideEffectClass,
			DataClassification dataClassification, List<String> requiredProviderCodes) {

		public PublishedWorkflowCapability {
			requiredProviderCodes = requiredProviderCodes == null ? List.of() : List.copyOf(requiredProviderCodes);
			sideEffectClass = sideEffectClass == null ? SideEffectClass.UNKNOWN : sideEffectClass;
			dataClassification = dataClassification == null ? DataClassification.SENSITIVE : dataClassification;
		}

	}

}
