package com.deepdimen.matrix.discover.service;

import java.util.List;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.capability.AssistantWorkflowCatalog.SideEffectClass;
import com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityEntity;
import com.deepdimen.matrix.discover.entity.AiAssistantWorkflowCapabilityReleaseEntity;

public interface AssistantWorkflowCapabilityService {

	AiAssistantWorkflowCapabilityEntity saveDraft(IdentityEnvelope identity, Long agentId,
			WorkflowCapabilityDraftRequest request);

	WorkflowCapabilityDetail get(IdentityEnvelope identity, Long capabilityId);

	List<WorkflowCapabilityDetail> list(IdentityEnvelope identity);

	WorkflowCapabilityInspection inspect(IdentityEnvelope identity, Long agentId);

	AiAssistantWorkflowCapabilityReleaseEntity publish(IdentityEnvelope identity, Long capabilityId);

	AiAssistantWorkflowCapabilityReleaseEntity rollback(IdentityEnvelope identity, Long capabilityId, Long releaseId);

	void revoke(IdentityEnvelope identity, Long capabilityId);

	record WorkflowCapabilityDraftRequest(String capabilityCode, List<String> intentExamples, String inputSchemaJson,
			String outputSchemaJson, List<String> requiredProviderCodes, DataClassification dataClassification,
			SideEffectClass sideEffectClass, List<String> egressAllowlist, Boolean allowCodeNode,
			List<String> toolAllowlist, Integer timeoutMs) {

		public WorkflowCapabilityDraftRequest {
			intentExamples = intentExamples == null ? List.of() : List.copyOf(intentExamples);
			requiredProviderCodes = requiredProviderCodes == null ? List.of() : List.copyOf(requiredProviderCodes);
			egressAllowlist = egressAllowlist == null ? List.of() : List.copyOf(egressAllowlist);
			toolAllowlist = toolAllowlist == null ? List.of() : List.copyOf(toolAllowlist);
		}

	}

	record WorkflowCapabilityDetail(AiAssistantWorkflowCapabilityEntity draft,
			AiAssistantWorkflowCapabilityReleaseEntity activeRelease, boolean stale,
			List<AiAssistantWorkflowCapabilityReleaseEntity> releases) {

		public WorkflowCapabilityDetail {
			releases = releases == null ? List.of() : List.copyOf(releases);
		}

	}

	record WorkflowCapabilityInspection(String definitionDigest, boolean codeNodePresent, List<String> toolIdentities,
			List<String> forbiddenNodeTypes, List<String> httpEgressHosts) {

		public WorkflowCapabilityInspection {
			toolIdentities = toolIdentities == null ? List.of() : List.copyOf(toolIdentities);
			forbiddenNodeTypes = forbiddenNodeTypes == null ? List.of() : List.copyOf(forbiddenNodeTypes);
			httpEgressHosts = httpEgressHosts == null ? List.of() : List.copyOf(httpEgressHosts);
		}

	}

}
