package com.deepdimen.matrix.assistant.generation;

import java.util.List;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantMessage;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantContextPack;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantExecutionTrace;

public interface AssistantGenerationAdapter {

	GenerationResult generate(GenerationRequest request);

	record GenerationRequest(String executionRunId, String assistantSessionId, String clientMessageId,
			IdentityEnvelope identity, String query, AssistantContextPack contextPack,
			List<String> explicitSkillCodes, List<AssistantMessage> conversationHistory, String routeChannel) {

		public GenerationRequest(String executionRunId, String assistantSessionId, String clientMessageId,
				IdentityEnvelope identity, String query, AssistantContextPack contextPack,
				List<String> explicitSkillCodes) {
			this(executionRunId, assistantSessionId, clientMessageId, identity, query, contextPack, explicitSkillCodes,
				List.of(), "UNIFIED_ASSISTANT");
		}

		public GenerationRequest(String executionRunId, String assistantSessionId, String clientMessageId,
				IdentityEnvelope identity, String query, AssistantContextPack contextPack,
				List<String> explicitSkillCodes, List<AssistantMessage> conversationHistory) {
			this(executionRunId, assistantSessionId, clientMessageId, identity, query, contextPack,
					explicitSkillCodes, conversationHistory, "UNIFIED_ASSISTANT");
		}

		public GenerationRequest {
			explicitSkillCodes = explicitSkillCodes == null ? List.of() : List.copyOf(explicitSkillCodes);
			conversationHistory = conversationHistory == null ? List.of() : List.copyOf(conversationHistory);
			routeChannel = routeChannel == null || routeChannel.isBlank() ? "UNIFIED_ASSISTANT" : routeChannel;
			if (!List.of("UNIFIED_ASSISTANT", "WORK_BRIEF").contains(routeChannel)) {
				throw new IllegalArgumentException("UNSUPPORTED_ASSISTANT_ROUTE");
			}
		}
	}

	record GenerationResult(boolean available, String content, AssistantExecutionTrace trace) {
	}

}
