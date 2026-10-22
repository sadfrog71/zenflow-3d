package com.deepdimen.matrix.assistant.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;

public final class AssistantProposalContract {

	private AssistantProposalContract() {
	}

	public enum ProposalType {

		TASK_CREATE, TASK_STATUS_CHANGE, KNOWLEDGE_DRAFT_CREATE, CAPSULE_TO_TASK, INFORMATION_TO_TASK

	}

	public enum ProposalStatus {

		DRAFT, PENDING_CONFIRMATION, CONFIRMED, EXECUTING, COMPLETED, REJECTED, EXPIRED, STALE, FAILED, DISABLED

	}

	public record SourceRef(ProviderId provider, String sourceId, String sourceVersion) {
	}

	public record AssistantProposal(String proposalId, ProposalType proposalType, String targetDomain,
			List<SourceRef> sourceRefs, Map<String, Object> payload, String riskLevel, ProposalStatus status,
			String disabledReason, Instant expiresAt, long version) {

		public AssistantProposal {
			sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
			payload = payload == null ? Map.of() : Map.copyOf(payload);
		}
	}

}
