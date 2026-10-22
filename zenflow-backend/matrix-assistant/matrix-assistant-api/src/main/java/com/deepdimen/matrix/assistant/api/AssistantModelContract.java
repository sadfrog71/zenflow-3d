package com.deepdimen.matrix.assistant.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantAttachment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;

/** Neutral contracts shared by local and remote native model gateways. */
public final class AssistantModelContract {

	public static final String VERSION = "assistant-model/v1";

	private AssistantModelContract() {
	}

	public enum DataClassification {

		PUBLIC, INTERNAL, SENSITIVE;

		public boolean allows(DataClassification actual) {
			return actual == null || ordinal() >= actual.ordinal();
		}

	}

	public enum DeploymentDomain {

		INTERNAL, EXTERNAL

	}

	public enum MessageRole {

		SYSTEM, USER, ASSISTANT

	}

	public record ModelCapabilities(boolean supportsNativeStreaming, int contextWindow, int maxOutputTokens,
			boolean supportsSystemMessage, boolean supportsVision) {

		public ModelCapabilities(boolean supportsNativeStreaming, int contextWindow, int maxOutputTokens,
				boolean supportsSystemMessage) {
			this(supportsNativeStreaming, contextWindow, maxOutputTokens, supportsSystemMessage, false);
		}

		public ModelCapabilities {
			contextWindow = contextWindow <= 0 ? 8192 : contextWindow;
			maxOutputTokens = maxOutputTokens <= 0 ? 2048 : Math.min(maxOutputTokens, contextWindow);
		}
	}

	public record AssistantEgress(DeploymentDomain deployment, String region, boolean allowBusinessContext,
			DataClassification maxDataClassification, String retentionPolicyRef) {

		public AssistantEgress {
			deployment = deployment == null ? DeploymentDomain.EXTERNAL : deployment;
			region = region == null ? "" : region;
			maxDataClassification = maxDataClassification == null ? DataClassification.PUBLIC : maxDataClassification;
			retentionPolicyRef = retentionPolicyRef == null ? "" : retentionPolicyRef;
		}

		public boolean allows(DataClassification classification, boolean containsBusinessContext) {
			return (!containsBusinessContext || allowBusinessContext) && maxDataClassification.allows(classification);
		}
	}

	public record ModelResolutionRequest(String contractVersion, String runId, IdentityEnvelope identity,
			String requestedModelId, boolean requireNativeStreaming, DataClassification contextClassification,
			boolean containsBusinessContext) {

		public ModelResolutionRequest {
			contractVersion = requireText(contractVersion, "contractVersion");
			runId = requireText(runId, "runId");
			identity = Objects.requireNonNull(identity, "identity");
			requestedModelId = normalizeOptional(requestedModelId);
			contextClassification = contextClassification == null ? DataClassification.PUBLIC : contextClassification;
		}
	}

	public record ResolvedAssistantModel(String contractVersion, String modelId, String modelName, String provider,
			ModelCapabilities capabilities, AssistantEgress assistantEgress) {

		public ResolvedAssistantModel {
			contractVersion = requireText(contractVersion, "contractVersion");
			modelId = requireText(modelId, "modelId");
			modelName = requireText(modelName, "modelName");
			provider = provider == null ? "" : provider;
			capabilities = Objects.requireNonNull(capabilities, "capabilities");
			assistantEgress = Objects.requireNonNull(assistantEgress, "assistantEgress");
		}
	}

	public record ModelMessage(MessageRole role, String content, List<AssistantAttachment> attachments) {

		public ModelMessage(MessageRole role, String content) {
			this(role, content, List.of());
		}

		public ModelMessage {
			role = Objects.requireNonNull(role, "role");
			content = requireText(content, "content");
			attachments = attachments == null ? List.of() : List.copyOf(attachments);
		}
	}

	public record ModelContextFragment(String citationKey, String title, String content,
			DataClassification dataClassification, Map<String, String> resourceRef) {

		public ModelContextFragment {
			citationKey = requireText(citationKey, "citationKey");
			title = title == null ? "" : title;
			content = requireText(content, "content");
			dataClassification = dataClassification == null ? DataClassification.INTERNAL : dataClassification;
			resourceRef = resourceRef == null ? Map.of() : Map.copyOf(resourceRef);
		}
	}

	public record AssistantModelRequest(String contractVersion, String runId, String correlationId,
			IdentityEnvelope identity, ResolvedAssistantModel model, String systemPrompt, List<ModelMessage> history,
			List<ModelContextFragment> contextFragments, String userMessage, Double temperature, Double topP,
			Integer maxOutputTokens, boolean requireNativeStreaming, List<AssistantAttachment> attachments) {

		public AssistantModelRequest(String contractVersion, String runId, String correlationId,
				IdentityEnvelope identity, ResolvedAssistantModel model, String systemPrompt,
				List<ModelMessage> history, List<ModelContextFragment> contextFragments, String userMessage,
				Double temperature, Double topP, Integer maxOutputTokens, boolean requireNativeStreaming) {
			this(contractVersion, runId, correlationId, identity, model, systemPrompt, history, contextFragments,
					userMessage, temperature, topP, maxOutputTokens, requireNativeStreaming, List.of());
		}

		public AssistantModelRequest {
			contractVersion = requireText(contractVersion, "contractVersion");
			runId = requireText(runId, "runId");
			correlationId = requireText(correlationId, "correlationId");
			identity = Objects.requireNonNull(identity, "identity");
			model = Objects.requireNonNull(model, "model");
			systemPrompt = requireText(systemPrompt, "systemPrompt");
			history = history == null ? List.of() : List.copyOf(history);
			contextFragments = contextFragments == null ? List.of() : List.copyOf(contextFragments);
			attachments = attachments == null ? List.of() : List.copyOf(attachments);
			userMessage = requireText(userMessage, "userMessage");
			maxOutputTokens = maxOutputTokens == null || maxOutputTokens <= 0 ? model.capabilities().maxOutputTokens()
					: Math.min(maxOutputTokens, model.capabilities().maxOutputTokens());
		}
	}

	public record ModelChunk(String runId, long sequence, String delta, boolean terminal, String finishReason) {

		public ModelChunk {
			runId = requireText(runId, "runId");
			if (sequence < 0) {
				throw new IllegalArgumentException("sequence must not be negative");
			}
			delta = delta == null ? "" : delta;
			finishReason = finishReason == null ? "" : finishReason;
		}
	}

	private static String normalizeOptional(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static String requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return value;
	}

}
