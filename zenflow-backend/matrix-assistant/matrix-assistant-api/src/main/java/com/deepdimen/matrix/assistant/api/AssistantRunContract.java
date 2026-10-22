package com.deepdimen.matrix.assistant.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RouteDecision;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantAttachment;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RunPhase;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.SourceMode;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.StepTrace;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.ScopeHint;

/** Stable API and persistence vocabulary for the native assistant run lifecycle. */
public final class AssistantRunContract {

	public static final int EVENT_VERSION = 1;

	private AssistantRunContract() {
	}

	public enum RunStatus {

		ACCEPTED, CONTEXT_LOADING, MODEL_STREAMING, COMPLETED, FAILED, CANCEL_REQUESTED, CANCELLED;

		public boolean terminal() {
			return this == COMPLETED || this == FAILED || this == CANCELLED;
		}

	}

	public enum DispatchStatus {

		PENDING, CLAIMED, FINISHED, LEGACY

	}

	public enum RunEventType {

		RUN_ACCEPTED, CONTEXT_STARTED, PROVIDER_STARTED, PROVIDER_COMPLETED, CONTEXT_COMPLETED, SKILL_APPLIED,
		MODEL_STARTED, RESPONSE_DELTA, CITATION_AVAILABLE, RUN_CANCEL_REQUESTED, RUN_COMPLETED, RUN_FAILED,
		RUN_CANCELLED, PLANNING_STARTED, PLAN_READY, PLAN_FALLBACK, STEP_STARTED, STEP_COMPLETED, STEP_FAILED,
		WORKFLOW_STARTED, WORKFLOW_COMPLETED, ANALYSIS_STARTED, ANALYSIS_VALIDATED, ROUTE_DECIDED,
		CLARIFICATION_REQUESTED, MODEL_FIRST_CHUNK, ANSWER_SECTION_VALIDATED

	}

	/**
	 * providerCodes is deliberately nullable: null means legacy/default selection; []
	 * means explicitly no sources.
	 */
	public record CreateRunRequest(String clientMessageId, String content, List<ProviderId> providerCodes,
			ScopeHint scopeHint, String skillId, Double temperature, Double topP, Integer maxOutputTokens,
			List<String> attachmentUploadIds, SourceMode sourceMode,
			@JsonInclude(JsonInclude.Include.NON_NULL) AssistantExecutionContract.ClarificationSelection clarificationSelection) {

		public CreateRunRequest(String clientMessageId, String content, List<ProviderId> providerCodes,
				ScopeHint scopeHint, String skillId, Double temperature, Double topP, Integer maxOutputTokens,
				List<String> attachmentUploadIds, SourceMode sourceMode) {
			this(clientMessageId, content, providerCodes, scopeHint, skillId, temperature, topP, maxOutputTokens,
					attachmentUploadIds, sourceMode, null);
		}

		public CreateRunRequest(String clientMessageId, String content, List<ProviderId> providerCodes,
				ScopeHint scopeHint, String skillId, Double temperature, Double topP, Integer maxOutputTokens,
				List<String> attachmentUploadIds) {
			this(clientMessageId, content, providerCodes, scopeHint, skillId, temperature, topP, maxOutputTokens,
					attachmentUploadIds, null);
		}

		public CreateRunRequest(String clientMessageId, String content, List<ProviderId> providerCodes,
				ScopeHint scopeHint, String skillId, Double temperature, Double topP, Integer maxOutputTokens) {
			this(clientMessageId, content, providerCodes, scopeHint, skillId, temperature, topP, maxOutputTokens,
					List.of(), null);
		}

		public CreateRunRequest(String clientMessageId, String content, List<ProviderId> providerCodes, String skillId,
				Double temperature, Double topP, Integer maxOutputTokens) {
			this(clientMessageId, content, providerCodes, null, skillId, temperature, topP, maxOutputTokens);
		}

		public CreateRunRequest {
			providerCodes = providerCodes == null ? null : List.copyOf(providerCodes);
			attachmentUploadIds = attachmentUploadIds == null ? List.of() : List.copyOf(attachmentUploadIds);
		}
	}

	public record RunAcceptedResponse(String runId, String userMessageId, String responseMessageId, RunStatus status,
			String eventsUrl) {
	}

	public record RunEventEnvelope(int version, String runId, long seq, RunEventType type, Instant occurredAt,
			String correlationId, Map<String, Object> data) {

		public RunEventEnvelope {
			data = data == null ? Map.of() : Map.copyOf(data);
		}
	}

	public record RunSnapshot(String runId, String sessionId, RunStatus status, String draftContent, long lastEventSeq,
			String responseMessageId, String inputContent, List<ProviderId> providerCodes, String skillId,
			String contextPackId, String errorCode, String errorMessageSafe, Instant createdAt, Instant startedAt,
			Instant completedAt, List<AssistantAttachment> attachments, SourceMode sourceMode, RunPhase phase,
			String planDigest, List<StepTrace> stepTraces, List<String> planningReasonCodes,
			@JsonInclude(JsonInclude.Include.NON_NULL) RouteDecision routeDecision,
			@JsonInclude(JsonInclude.Include.NON_NULL) AssistantExecutionContract.ClarificationState clarification,
			@JsonInclude(JsonInclude.Include.ALWAYS) ScopeHint scopeHint,
			@JsonInclude(JsonInclude.Include.NON_NULL) AssistantExecutionContract.ClarificationSelection clarificationSelection) {

		public RunSnapshot(String runId, String sessionId, RunStatus status, String draftContent, long lastEventSeq,
				String responseMessageId, String inputContent, List<ProviderId> providerCodes, String skillId,
				String contextPackId, String errorCode, String errorMessageSafe, Instant createdAt, Instant startedAt,
				Instant completedAt, List<AssistantAttachment> attachments, SourceMode sourceMode, RunPhase phase,
				String planDigest, List<StepTrace> stepTraces, List<String> planningReasonCodes,
				RouteDecision routeDecision, AssistantExecutionContract.ClarificationState clarification) {
			this(runId, sessionId, status, draftContent, lastEventSeq, responseMessageId, inputContent, providerCodes,
					skillId, contextPackId, errorCode, errorMessageSafe, createdAt, startedAt, completedAt, attachments,
					sourceMode, phase, planDigest, stepTraces, planningReasonCodes, routeDecision, clarification, null,
					null);
		}

		public RunSnapshot(String runId, String sessionId, RunStatus status, String draftContent, long lastEventSeq,
				String responseMessageId, String inputContent, List<ProviderId> providerCodes, String skillId,
				String contextPackId, String errorCode, String errorMessageSafe, Instant createdAt, Instant startedAt,
				Instant completedAt, List<AssistantAttachment> attachments, SourceMode sourceMode, RunPhase phase,
				String planDigest, List<StepTrace> stepTraces, List<String> planningReasonCodes,
				RouteDecision routeDecision) {
			this(runId, sessionId, status, draftContent, lastEventSeq, responseMessageId, inputContent, providerCodes,
					skillId, contextPackId, errorCode, errorMessageSafe, createdAt, startedAt, completedAt, attachments,
					sourceMode, phase, planDigest, stepTraces, planningReasonCodes, routeDecision, null);
		}

		public RunSnapshot(String runId, String sessionId, RunStatus status, String draftContent, long lastEventSeq,
				String responseMessageId, String inputContent, List<ProviderId> providerCodes, String skillId,
				String contextPackId, String errorCode, String errorMessageSafe, Instant createdAt, Instant startedAt,
				Instant completedAt, List<AssistantAttachment> attachments, SourceMode sourceMode, RunPhase phase,
				String planDigest, List<StepTrace> stepTraces, List<String> planningReasonCodes) {
			this(runId, sessionId, status, draftContent, lastEventSeq, responseMessageId, inputContent, providerCodes,
					skillId, contextPackId, errorCode, errorMessageSafe, createdAt, startedAt, completedAt, attachments,
					sourceMode, phase, planDigest, stepTraces, planningReasonCodes, null);
		}

		public RunSnapshot(String runId, String sessionId, RunStatus status, String draftContent, long lastEventSeq,
				String responseMessageId, String inputContent, List<ProviderId> providerCodes, String skillId,
				String contextPackId, String errorCode, String errorMessageSafe, Instant createdAt, Instant startedAt,
				Instant completedAt, List<AssistantAttachment> attachments) {
			this(runId, sessionId, status, draftContent, lastEventSeq, responseMessageId, inputContent, providerCodes,
					skillId, contextPackId, errorCode, errorMessageSafe, createdAt, startedAt, completedAt, attachments,
					SourceMode.LEGACY, defaultPhase(status), null, List.of(), List.of());
		}

		public RunSnapshot(String runId, String sessionId, RunStatus status, String draftContent, long lastEventSeq,
				String responseMessageId, String inputContent, List<ProviderId> providerCodes, String skillId,
				String contextPackId, String errorCode, String errorMessageSafe, Instant createdAt, Instant startedAt,
				Instant completedAt) {
			this(runId, sessionId, status, draftContent, lastEventSeq, responseMessageId, inputContent, providerCodes,
					skillId, contextPackId, errorCode, errorMessageSafe, createdAt, startedAt, completedAt, List.of(),
					SourceMode.LEGACY, defaultPhase(status), null, List.of(), List.of());
		}

		public RunSnapshot {
			providerCodes = providerCodes == null ? List.of() : List.copyOf(providerCodes);
			attachments = attachments == null ? List.of() : List.copyOf(attachments);
			sourceMode = SourceMode.effective(sourceMode);
			phase = phase == null ? defaultPhase(status) : phase;
			stepTraces = stepTraces == null ? List.of() : List.copyOf(stepTraces);
			planningReasonCodes = planningReasonCodes == null ? List.of() : List.copyOf(planningReasonCodes);
		}
	}

	public record CancelRunResponse(String runId, RunStatus status, boolean newlyRequested) {
	}

	private static RunPhase defaultPhase(RunStatus status) {
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

}
