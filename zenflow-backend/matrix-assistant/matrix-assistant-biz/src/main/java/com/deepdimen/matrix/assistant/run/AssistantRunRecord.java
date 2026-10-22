package com.deepdimen.matrix.assistant.run;

import java.time.Instant;
import java.util.List;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RouteDecision;

import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantAttachment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.SourceMode;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.AnalysisResult;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionPlan;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RunPhase;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.DispatchStatus;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.ScopeHint;
import com.deepdimen.matrix.assistant.capability.AssistantPromptSkillCatalog.PromptSkillSnapshot;

public record AssistantRunRecord(String runId, String sessionId, String tenantId, String ownerUserId,
		String clientMessageId, String requestHash, String userMessageId, String responseMessageId, RunStatus status,
		DispatchStatus dispatchStatus, RunInputSnapshot input, String contextPackId, String draftContent,
		long lastEventSeq, String ownerInstanceId, Instant leaseExpiresAt, Instant modelInvokedAt, String errorCode,
		String errorMessageSafe, Instant createdAt, Instant startedAt, Instant completedAt, RunPhase phase,
		String catalogDigest, ExecutionPlan executionPlan, AnalysisResult analysisResult) {

	public AssistantRunRecord(String runId, String sessionId, String tenantId, String ownerUserId,
			String clientMessageId, String requestHash, String userMessageId, String responseMessageId,
			RunStatus status, DispatchStatus dispatchStatus, RunInputSnapshot input, String contextPackId,
			String draftContent, long lastEventSeq, String ownerInstanceId, Instant leaseExpiresAt,
			Instant modelInvokedAt, String errorCode, String errorMessageSafe, Instant createdAt, Instant startedAt,
			Instant completedAt) {
		this(runId, sessionId, tenantId, ownerUserId, clientMessageId, requestHash, userMessageId, responseMessageId,
				status, dispatchStatus, input, contextPackId, draftContent, lastEventSeq, ownerInstanceId,
				leaseExpiresAt, modelInvokedAt, errorCode, errorMessageSafe, createdAt, startedAt, completedAt, null,
				null, null, null);
	}

	public AssistantRunRecord {
		draftContent = draftContent == null ? "" : draftContent;
	}

	public RouteDecision routeDecision() {
		return executionPlan == null ? null : executionPlan.routeDecision();
	}

	public RunPhase effectivePhase() {
		if (phase != null) {
			return phase;
		}
		if (status == RunStatus.COMPLETED || status == RunStatus.FAILED || status == RunStatus.CANCELLED) {
			return RunPhase.COMPLETED;
		}
		if (status == RunStatus.MODEL_STREAMING) {
			return RunPhase.ANALYSIS;
		}
		if (status == RunStatus.CONTEXT_LOADING) {
			return RunPhase.PROVIDER_QUERY;
		}
		return RunPhase.ACCEPTED;
	}

	public record RunInputSnapshot(IdentityEnvelope identity, String content, List<ProviderId> providerIds,
			boolean providerSelectionExplicit, ScopeHint scopeHint, String selectedSkillId,
			PromptSkillSnapshot promptSkill, List<AssistantMessage> history, Double temperature, Double topP,
			Integer maxOutputTokens, List<AssistantAttachment> attachments, SourceMode sourceMode,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClarificationSelection clarificationSelection) {

		public RunInputSnapshot(IdentityEnvelope identity, String content, List<ProviderId> providerIds,
				boolean providerSelectionExplicit, ScopeHint scopeHint, String selectedSkillId,
				PromptSkillSnapshot promptSkill, List<AssistantMessage> history, Double temperature, Double topP,
				Integer maxOutputTokens, List<AssistantAttachment> attachments, SourceMode sourceMode) {
			this(identity, content, providerIds, providerSelectionExplicit, scopeHint, selectedSkillId, promptSkill,
					history, temperature, topP, maxOutputTokens, attachments, sourceMode, null);
		}

		public RunInputSnapshot(IdentityEnvelope identity, String content, List<ProviderId> providerIds,
				boolean providerSelectionExplicit, ScopeHint scopeHint, String selectedSkillId,
				PromptSkillSnapshot promptSkill, List<AssistantMessage> history, Double temperature, Double topP,
				Integer maxOutputTokens, List<AssistantAttachment> attachments) {
			this(identity, content, providerIds, providerSelectionExplicit, scopeHint, selectedSkillId, promptSkill,
					history, temperature, topP, maxOutputTokens, attachments, null);
		}

		public RunInputSnapshot(IdentityEnvelope identity, String content, List<ProviderId> providerIds,
				boolean providerSelectionExplicit, ScopeHint scopeHint, String selectedSkillId,
				PromptSkillSnapshot promptSkill, List<AssistantMessage> history, Double temperature, Double topP,
				Integer maxOutputTokens) {
			this(identity, content, providerIds, providerSelectionExplicit, scopeHint, selectedSkillId, promptSkill,
					history, temperature, topP, maxOutputTokens, List.of(), null);
		}

		public RunInputSnapshot {
			providerIds = providerIds == null ? List.of() : List.copyOf(providerIds);
			history = history == null ? List.of() : List.copyOf(history);
			attachments = attachments == null ? List.of() : List.copyOf(attachments);
		}

		public SourceMode effectiveSourceMode() {
			return SourceMode.effective(sourceMode);
		}
	}

}
