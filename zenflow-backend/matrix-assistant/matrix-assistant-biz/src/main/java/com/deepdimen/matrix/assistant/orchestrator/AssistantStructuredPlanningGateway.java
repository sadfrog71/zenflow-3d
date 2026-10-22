package com.deepdimen.matrix.assistant.orchestrator;

import java.util.List;
import java.util.Optional;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.PlanningDisposition;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.SourceMode;
import com.deepdimen.matrix.assistant.capability.AssistantAnalysisSkillCatalog.AnalysisSkillSnapshot;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;

/** One-shot, tool-free semantic suggestion. Code remains the execution authority. */
public interface AssistantStructuredPlanningGateway {

	Optional<PlanningSuggestion> suggest(String runId, IdentityEnvelope identity, String question,
			SourceMode sourceMode, List<String> cappedProviderCodes, FrozenCapabilityCatalog catalog,
			AnalysisSkillSnapshot analysisSkill);

	default Optional<PlanningSuggestion> suggest(String runId, IdentityEnvelope identity, String question,
			SourceMode sourceMode, List<String> cappedProviderCodes, FrozenCapabilityCatalog catalog,
			AnalysisSkillSnapshot analysisSkill, ClarificationContext clarification) {
		// Legacy adapters must not silently lose clarification and execute an unrelated
		// plan.
		return clarification == null
				? suggest(runId, identity, question, sourceMode, cappedProviderCodes, catalog, analysisSkill)
				: Optional.empty();
	}

	record ClarificationContext(String originRunId, String originalQuestion, String clarificationQuestion,
			List<com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClarificationCandidate> candidates) {
		public ClarificationContext {
			if (originRunId == null || originRunId.isBlank() || originalQuestion == null || originalQuestion.isBlank()
					|| originalQuestion.length() > 2000 || clarificationQuestion == null
					|| clarificationQuestion.isBlank() || clarificationQuestion.length() > 512 || candidates == null
					|| candidates.isEmpty() || candidates.size() > 8)
				throw new IllegalArgumentException("CLARIFICATION_CONTEXT_INVALID");
			candidates = List.copyOf(candidates);
		}
	}

	record ProviderSuggestion(String providerCode, String query) {
	}

	record WorkflowSuggestion(String capabilityId, Long releaseId, List<String> dependsOnCapabilityIds) {

		public WorkflowSuggestion {
			dependsOnCapabilityIds = dependsOnCapabilityIds == null ? List.of() : List.copyOf(dependsOnCapabilityIds);
		}
	}

	record PlanningSuggestion(PlanningDisposition disposition, List<ProviderSuggestion> providers,
			List<WorkflowSuggestion> workflows, List<String> reasonCodes, String clarificationQuestion,
			RoutingSuggestion routing) {

		public PlanningSuggestion(PlanningDisposition disposition, List<ProviderSuggestion> providers,
				List<WorkflowSuggestion> workflows, List<String> reasonCodes, String clarificationQuestion) {
			this(disposition, providers, workflows, reasonCodes, clarificationQuestion, null);
		}

		public PlanningSuggestion {
			providers = providers == null ? List.of() : List.copyOf(providers);
			workflows = workflows == null ? List.of() : List.copyOf(workflows);
			reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
		}
	}

	record RoutingSuggestion(
			com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent conversationIntent,
			com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode executionMode,
			Double confidence, Double runnerUpMargin) {
	}

}
