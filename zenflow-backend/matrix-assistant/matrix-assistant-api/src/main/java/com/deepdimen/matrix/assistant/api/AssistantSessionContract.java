package com.deepdimen.matrix.assistant.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantAttachment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.SourceMode;
import com.deepdimen.matrix.assistant.api.AssistantProposalContract.AssistantProposal;

public final class AssistantSessionContract {

	private AssistantSessionContract() {
	}

	public enum SessionStatus {

		ACTIVE, ARCHIVED

	}

	public enum TitleSource {

		DEFAULT, AUTO, MANUAL

	}

	public enum MessageRole {

		USER, ASSISTANT, SYSTEM_NOTICE, PROPOSAL, TOOL_STATUS

	}

	public enum MessageStatus {

		ACCEPTED, COMPLETED, FAILED, CANCELLED

	}

	public enum IntentDisposition {

		READ_ONLY, PROPOSAL_ONLY_DISABLED, ACTION_NOT_SUPPORTED

	}

	public enum ExecutionPlan {

		PRIMARY_ONLY, WORKFLOW_FINAL, WORKFLOW_THEN_COMPOSE, SEMANTIC_DAG, UNAVAILABLE

	}

	public record AssistantSession(String assistantSessionId, String tenantId, String ownerUserId, String title,
			SessionStatus status, List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute,
			TitleSource titleSource, Instant lastMessageAt, Instant archivedAt, long version, Instant createdAt,
			Instant updatedAt, String ownerSubjectType) {

		public AssistantSession {
			activeScopes = activeScopes == null ? List.of() : List.copyOf(activeScopes);
			activeSkillCode = activeSkillCode == null || activeSkillCode.isBlank() ? null : activeSkillCode;
			titleSource = titleSource == null ? defaultTitleSource(title) : titleSource;
			ownerSubjectType = ownerSubjectType == null ? "ADMIN_USER" : ownerSubjectType;
			if (!List.of("ADMIN_USER", "APP_USER").contains(ownerSubjectType)) throw new IllegalArgumentException("Invalid owner subject type");
		}
		public AssistantSession(String assistantSessionId, String tenantId, String ownerUserId, String title,
				SessionStatus status, List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute,
				TitleSource titleSource, Instant lastMessageAt, Instant archivedAt, long version, Instant createdAt,
				Instant updatedAt) {
			this(assistantSessionId,tenantId,ownerUserId,title,status,activeScopes,activeSkillCode,lastSourceRoute,
				titleSource,lastMessageAt,archivedAt,version,createdAt,updatedAt,"ADMIN_USER");
		}

		public AssistantSession(String assistantSessionId, String tenantId, String ownerUserId, String title,
				SessionStatus status, List<ProviderId> activeScopes, String lastSourceRoute, long version,
				Instant createdAt, Instant updatedAt) {
			this(assistantSessionId, tenantId, ownerUserId, title, status, activeScopes, null, lastSourceRoute,
					defaultTitleSource(title), null, null, version, createdAt, updatedAt);
		}
	}

	public record AssistantSessionSummary(String assistantSessionId, String title, SessionStatus status,
			List<ProviderId> activeScopes, String activeSkillCode, String lastSourceRoute, long messageCount,
			Instant lastMessageAt, Instant archivedAt, long version, Instant createdAt, Instant updatedAt) {

		public AssistantSessionSummary {
			activeScopes = activeScopes == null ? List.of() : List.copyOf(activeScopes);
		}
	}

	public record AssistantSessionPage(List<AssistantSessionSummary> items, String nextCursor, boolean hasMore) {

		public AssistantSessionPage {
			items = items == null ? List.of() : List.copyOf(items);
		}
	}

	public record AssistantMessage(String messageId, String assistantSessionId, MessageRole role, String content,
			MessageStatus status, long sequenceNo, String contextPackId, Instant createdAt,
			List<AssistantAttachment> attachments) {

		public AssistantMessage(String messageId, String assistantSessionId, MessageRole role, String content,
				MessageStatus status, long sequenceNo, String contextPackId, Instant createdAt) {
			this(messageId, assistantSessionId, role, content, status, sequenceNo, contextPackId, createdAt, List.of());
		}

		public AssistantMessage {
			attachments = attachments == null ? List.of() : List.copyOf(attachments);
		}
	}

	public record AssistantMessagePage(List<AssistantMessage> items, Long nextBeforeSequence, boolean hasMore) {

		public AssistantMessagePage {
			items = items == null ? List.of() : List.copyOf(items);
		}
	}

	public record ScopeHint(String sourceRoute, String objectType, List<String> objectIds,
			Map<String, String> filters) {

		public ScopeHint {
			objectIds = objectIds == null ? List.of() : List.copyOf(objectIds);
			filters = filters == null ? Map.of() : Map.copyOf(filters);
		}
	}

	public record AssistantContextPack(String contextPackId, String assistantSessionId, String messageId,
			Instant createdAt, Instant expiresAt, String identityFingerprint, List<ProviderResult> providerResults,
			List<AssistantContextContract.ContextFragment> fragments,
			List<AssistantContextContract.ProviderWarning> warnings) {

		public AssistantContextPack {
			providerResults = providerResults == null ? List.of() : List.copyOf(providerResults);
			fragments = fragments == null ? List.of() : List.copyOf(fragments);
			warnings = warnings == null ? List.of() : List.copyOf(warnings);
		}
	}

	public record CreateSessionRequest(String title, List<ProviderId> activeScopes, String sourceRoute) {

		public CreateSessionRequest {
			activeScopes = activeScopes == null ? List.of() : List.copyOf(activeScopes);
		}
	}

	public record SendMessageRequest(String clientMessageId, String content, List<ProviderId> enabledScopes,
			ScopeHint scopeHint, List<String> explicitSkillCodes, SourceMode sourceMode) {

		public SendMessageRequest(String clientMessageId, String content, List<ProviderId> enabledScopes,
				ScopeHint scopeHint, List<String> explicitSkillCodes) {
			this(clientMessageId, content, enabledScopes, scopeHint, explicitSkillCodes, null);
		}

		public SendMessageRequest {
			enabledScopes = enabledScopes == null ? null : List.copyOf(enabledScopes);
			explicitSkillCodes = explicitSkillCodes == null ? List.of() : List.copyOf(explicitSkillCodes);
		}
	}

	public record UpdateSessionTitleRequest(String title, long expectedVersion) {
	}

	public record UpdateSessionPreferencesRequest(List<ProviderId> activeScopes, String activeSkillCode,
			String lastSourceRoute, long expectedVersion) {

		public UpdateSessionPreferencesRequest {
			activeScopes = activeScopes == null ? null : List.copyOf(activeScopes);
			activeSkillCode = activeSkillCode == null || activeSkillCode.isBlank() ? null : activeSkillCode;
		}
	}

	public record UpdateSessionStatusRequest(long expectedVersion) {
	}

	public record AssistantExecutionTrace(String executionRunId, ExecutionPlan executionPlan, String runtimeMode,
			String runtimeId, String taskId, String workflowRunId, String conversationId,
			List<String> activatedSkillCodes, String errorCode, Long agentId, String agentVersion, String provider,
			String model, Long promptTokens, Long completionTokens, Long totalTokens, String usageSource,
			String requestId, String executionStatus, List<String> executedSkillCodes, Long skillReleaseId,
			String skillReleaseVersion, String skillPackageSha256, String skillInstructionDigest) {

		public AssistantExecutionTrace(String executionRunId, ExecutionPlan executionPlan, String runtimeMode,
				String runtimeId, String taskId, String workflowRunId, String conversationId,
				List<String> activatedSkillCodes, String errorCode) {
			this(executionRunId, executionPlan, runtimeMode, runtimeId, taskId, workflowRunId, conversationId,
					activatedSkillCodes, errorCode, null, null, null, null, null, null, null, null, null,
					defaultStatus(errorCode), List.of(), null, null, null, null);
		}

		public AssistantExecutionTrace(String executionRunId, ExecutionPlan executionPlan, String runtimeMode,
				String runtimeId, String taskId, String workflowRunId, String conversationId,
				List<String> activatedSkillCodes, String errorCode, Long agentId, String agentVersion, String provider,
				String model, Long promptTokens, Long completionTokens, Long totalTokens, String usageSource,
				String requestId) {
			this(executionRunId, executionPlan, runtimeMode, runtimeId, taskId, workflowRunId, conversationId,
					activatedSkillCodes, errorCode, agentId, agentVersion, provider, model, promptTokens,
					completionTokens, totalTokens, usageSource, requestId, defaultStatus(errorCode), List.of(), null,
					null, null, null);
		}

		public AssistantExecutionTrace {
			activatedSkillCodes = activatedSkillCodes == null ? List.of() : List.copyOf(activatedSkillCodes);
			executionStatus = executionStatus == null || executionStatus.isBlank() ? defaultStatus(errorCode)
					: executionStatus;
			executedSkillCodes = executedSkillCodes == null ? List.of() : List.copyOf(executedSkillCodes);
		}

		private static String defaultStatus(String errorCode) {
			return errorCode == null ? "SUCCEEDED" : "FAILED";
		}

	}

	public record AssistantSkillCapability(String skillCode, String displayName, String description, String icon,
			boolean defaultEnabled, boolean required, String executionStrategy) {
	}

	public record UpdateSkillPreferenceRequest(List<String> enabledSkillCodes) {

		public UpdateSkillPreferenceRequest {
			enabledSkillCodes = enabledSkillCodes == null ? List.of() : List.copyOf(enabledSkillCodes);
		}
	}

	public record MessageExchange(AssistantSession session, AssistantMessage userMessage,
			AssistantMessage responseMessage, AssistantContextPack contextPack, List<ProviderResult> providerResults,
			IntentDisposition disposition, AssistantProposal proposal, AssistantExecutionTrace executionTrace) {

		public MessageExchange {
			providerResults = providerResults == null ? List.of() : List.copyOf(providerResults);
		}
	}

	private static TitleSource defaultTitleSource(String title) {
		return title == null || title.isBlank() || "新对话".equals(title.trim()) ? TitleSource.DEFAULT
				: TitleSource.MANUAL;
	}

}
