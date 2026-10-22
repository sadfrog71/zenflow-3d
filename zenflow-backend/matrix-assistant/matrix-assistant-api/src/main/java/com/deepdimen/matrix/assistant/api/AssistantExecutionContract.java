package com.deepdimen.matrix.assistant.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;

/**
 * Additive, client-neutral contracts for semantic planning, durable execution and
 * evidence-backed analysis.
 */
public final class AssistantExecutionContract {

	public static final String PLAN_VERSION = "assistant-execution-plan/v1";

	public static final String ARTIFACT_VERSION = "assistant-analysis-artifact/v1";

	public static final String ANALYSIS_VERSION = "assistant-analysis-result/v1";

	private AssistantExecutionContract() {
	}

	/** LEGACY is an internal compatibility value; new clients use AUTO, CAP or NONE. */
	public enum SourceMode {

		LEGACY, AUTO, CAP, NONE;

		public static SourceMode effective(SourceMode value) {
			return value == null ? LEGACY : value;
		}

	}

	/** Semantic intent is separate from the policy decision authorizing execution. */
	public enum ConversationIntent {

		CASUAL_CHAT, GENERAL_QA, CAPABILITY_HELP, BUSINESS_QUERY, BUSINESS_ANALYSIS, ACTION_REQUEST, AMBIGUOUS

	}

	public enum ExecutionMode {

		DIRECT_CHAT, CAPABILITY_HELP, PROVIDER_QUERY, WORKFLOW_EXECUTION, CLARIFICATION, REJECTED

	}

	public enum RouteTriggerType {

		EXPLICIT_SOURCE, EXACT_AGENT_NAME, EXACT_AGENT_ALIAS, CASUAL_RULE, DESCRIPTION, CONTEXT, PLANNER

	}

	/**
	 * No prompt, hidden reasoning or unfiltered candidate metadata belongs in this
	 * snapshot.
	 */
	public record RouteDecision(ConversationIntent conversationIntent, ExecutionMode executionMode,
			List<String> selectedProviderIds, List<String> selectedWorkflowIds, RouteTriggerType triggerType,
			Double confidence, Double runnerUpMargin, boolean plannerUsed, boolean contextInherited,
			String reasonCode) {

		public RouteDecision {
			selectedProviderIds = selectedProviderIds == null ? List.of() : List.copyOf(selectedProviderIds);
			selectedWorkflowIds = selectedWorkflowIds == null ? List.of() : List.copyOf(selectedWorkflowIds);
			validateScore(confidence);
			validateScore(runnerUpMargin);
		}

		private static void validateScore(Double value) {
			if (value != null && (!Double.isFinite(value) || value < 0 || value > 1)) {
				throw new IllegalArgumentException("Routing scores must be finite values between zero and one");
			}
		}

	}

	public enum PlanningDisposition {

		MATCH, NO_MATCH, AMBIGUOUS, REJECTED

	}

	public enum RunPhase {

		ACCEPTED, PLANNING, PROVIDER_QUERY, WORKFLOW_EXECUTION, ANALYSIS, VALIDATION, COMPLETED

	}

	public enum ExecutionStepType {

		MATRIX_PROVIDER, DIFY_WORKFLOW, MATRIX_ANALYSIS

	}

	public enum ExecutionStepStatus {

		READY, RUNNING, SUCCEEDED, FAILED, SKIPPED, CANCELLED, UNKNOWN;

		public boolean terminal() {
			return this == SUCCEEDED || this == FAILED || this == SKIPPED || this == CANCELLED || this == UNKNOWN;
		}

		public boolean canTransitionTo(ExecutionStepStatus target) {
			if (target == null) {
				return false;
			}
			return switch (this) {
				case READY -> target == RUNNING || target == SKIPPED || target == CANCELLED;
				case RUNNING -> target == SUCCEEDED || target == FAILED || target == CANCELLED || target == UNKNOWN;
				default -> false;
			};
		}

	}

	public enum ArtifactKind {

		PROVIDER_CONTEXT, WORKFLOW_RESULT

	}

	public enum ClaimType {

		FACT, INFERENCE, OPINION

	}

	public enum UsageSource {

		MATRIX_PROVIDER, DIFY_RESPONSE, MATRIX_MODEL, NOT_REPORTED

	}

	/** A frozen, tenant-authorized Provider descriptor included in a Run catalog. */
	public record ProviderCapabilitySnapshot(String providerCode, String displayName, String description,
			long descriptorVersion, String descriptorDigest) {
	}

	/**
	 * A frozen, tenant-authorized Workflow descriptor; never contains credentials or raw
	 * DSL.
	 */
	public record WorkflowCapabilitySnapshot(String capabilityId, long releaseId, String releaseVersion,
			String displayName, String description, List<String> intentExamples, List<String> operationRules,
			String inputSchemaId, String outputSchemaId, String publishedWorkflowId, String releaseDigest,
			List<String> requiredProviderCodes, @JsonInclude(JsonInclude.Include.NON_EMPTY) List<String> aliases) {

		public WorkflowCapabilitySnapshot(String capabilityId, long releaseId, String releaseVersion,
				String displayName, String description, List<String> intentExamples, List<String> operationRules,
				String inputSchemaId, String outputSchemaId, String publishedWorkflowId, String releaseDigest,
				List<String> requiredProviderCodes) {
			this(capabilityId, releaseId, releaseVersion, displayName, description, intentExamples, operationRules,
					inputSchemaId, outputSchemaId, publishedWorkflowId, releaseDigest, requiredProviderCodes,
					List.of());
		}

		public WorkflowCapabilitySnapshot(String capabilityId, long releaseId, String releaseVersion,
				String displayName, String description, List<String> intentExamples, List<String> operationRules,
				String inputSchemaId, String outputSchemaId, String publishedWorkflowId, String releaseDigest) {
			this(capabilityId, releaseId, releaseVersion, displayName, description, intentExamples, operationRules,
					inputSchemaId, outputSchemaId, publishedWorkflowId, releaseDigest, List.of());
		}

		public WorkflowCapabilitySnapshot {
			aliases = aliases == null ? List.of()
					: aliases.stream()
						.filter(java.util.Objects::nonNull)
						.map(String::trim)
						.filter(value -> !value.isEmpty())
						.distinct()
						.toList();
			intentExamples = intentExamples == null ? List.of() : List.copyOf(intentExamples);
			operationRules = operationRules == null ? List.of() : List.copyOf(operationRules);
			requiredProviderCodes = requiredProviderCodes == null ? List.of() : List.copyOf(requiredProviderCodes);
		}

	}

	/**
	 * One immutable node in the validated Run DAG. Provider nodes reuse ProviderQuery.
	 */
	public record ExecutionStep(String stepId, ExecutionStepType type, String targetId, Long targetReleaseId,
			boolean required, List<String> dependsOn, ProviderQuery providerQuery, Map<String, Object> inputProjection,
			String outputSchemaId) {

		public ExecutionStep {
			dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
			inputProjection = inputProjection == null ? Map.of() : Map.copyOf(inputProjection);
		}

	}

	/**
	 * Authorized choices frozen with the plan; never a replacement for execution
	 * authorization.
	 */
	public record ClarificationCandidate(ExecutionStepType type, String targetId, Long releaseId, String displayName) {
	}

	public record ClarificationSelection(String sourceRunId, String planDigest, ExecutionStepType type, String targetId,
			Long releaseId) {
		public ClarificationSelection {
			if (sourceRunId == null || sourceRunId.isBlank() || sourceRunId.length() > 128 || planDigest == null
					|| !planDigest.matches("[0-9a-f]{64}") || targetId == null || targetId.isBlank()
					|| targetId.length() > 256
					|| type != ExecutionStepType.MATRIX_PROVIDER && type != ExecutionStepType.DIFY_WORKFLOW
					|| type == ExecutionStepType.MATRIX_PROVIDER && releaseId != null
					|| type == ExecutionStepType.DIFY_WORKFLOW && (releaseId == null || releaseId <= 0))
				throw new IllegalArgumentException("CLARIFICATION_SELECTION_INVALID");
		}
	}

	public record ClarificationState(String originRunId, int clarificationCount, boolean manualSelectionRequired,
			String question, List<ClarificationCandidate> candidates, long expiresAtEpochMillis) {
		public ClarificationState {
			candidates = candidates == null ? List.of() : List.copyOf(candidates);
		}
	}

	/** Planner output after deterministic validation and digesting. */
	public record ExecutionPlan(String contractVersion, String planId, String runId, SourceMode sourceMode,
			PlanningDisposition disposition, List<ExecutionStep> steps, String analysisStepId, List<String> reasonCodes,
			String catalogDigest, String planDigest, Instant createdAt,
			@JsonInclude(JsonInclude.Include.NON_NULL) RouteDecision routeDecision,
			@JsonInclude(JsonInclude.Include.NON_NULL) ClarificationState clarification) {

		public ExecutionPlan(String contractVersion, String planId, String runId, SourceMode sourceMode,
				PlanningDisposition disposition, List<ExecutionStep> steps, String analysisStepId,
				List<String> reasonCodes, String catalogDigest, String planDigest, Instant createdAt,
				RouteDecision routeDecision) {
			this(contractVersion, planId, runId, sourceMode, disposition, steps, analysisStepId, reasonCodes,
					catalogDigest, planDigest, createdAt, routeDecision, null);
		}

		public ExecutionPlan(String contractVersion, String planId, String runId, SourceMode sourceMode,
				PlanningDisposition disposition, List<ExecutionStep> steps, String analysisStepId,
				List<String> reasonCodes, String catalogDigest, String planDigest, Instant createdAt) {
			this(contractVersion, planId, runId, sourceMode, disposition, steps, analysisStepId, reasonCodes,
					catalogDigest, planDigest, createdAt, null);
		}

		public ExecutionPlan {
			contractVersion = contractVersion == null || contractVersion.isBlank() ? PLAN_VERSION : contractVersion;
			sourceMode = SourceMode.effective(sourceMode);
			steps = steps == null ? List.of() : List.copyOf(steps);
			reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
		}

	}

	/** Validated intermediate output. It is evidence input, never final user prose. */
	public record AnalysisArtifact(String contractVersion, String artifactId, String runId, String stepId,
			ArtifactKind kind, Map<String, Object> payload, List<String> citationKeys, String artifactDigest,
			Instant createdAt) {

		public AnalysisArtifact {
			contractVersion = contractVersion == null || contractVersion.isBlank() ? ARTIFACT_VERSION : contractVersion;
			payload = payload == null ? Map.of() : Map.copyOf(payload);
			citationKeys = citationKeys == null ? List.of() : List.copyOf(citationKeys);
		}

	}

	public record AnalysisClaim(String claimId, ClaimType type, String text, List<String> citationKeys,
			List<String> artifactIds) {

		public AnalysisClaim {
			citationKeys = citationKeys == null ? List.of() : List.copyOf(citationKeys);
			artifactIds = artifactIds == null ? List.of() : List.copyOf(artifactIds);
		}

	}

	public record AnalysisSection(String sectionId, String title, String content, List<String> claimIds) {

		public AnalysisSection {
			claimIds = claimIds == null ? List.of() : List.copyOf(claimIds);
		}

	}

	/** The only structure allowed to feed the final Skill-governed renderer. */
	public record AnalysisResult(String contractVersion, String runId, String summary, List<AnalysisSection> sections,
			List<AnalysisClaim> claims, List<String> warnings, Map<String, String> sourceCoverage,
			String resultDigest) {

		public AnalysisResult {
			contractVersion = contractVersion == null || contractVersion.isBlank() ? ANALYSIS_VERSION : contractVersion;
			sections = sections == null ? List.of() : List.copyOf(sections);
			claims = claims == null ? List.of() : List.copyOf(claims);
			warnings = warnings == null ? List.of() : List.copyOf(warnings);
			sourceCoverage = sourceCoverage == null ? Map.of() : Map.copyOf(sourceCoverage);
		}

	}

	/** Additive trace shape consumed identically by Web and mobile clients. */
	public record StepTrace(String stepId, ExecutionStepType type, String targetId, ExecutionStepStatus status,
			boolean required, String invocationKey, String inputDigest, String outputDigest, String artifactId,
			String externalTaskId, String workflowRunId, UsageSource usageSource, String errorCode,
			String errorMessageSafe, Instant startedAt, Instant completedAt) {

		public StepTrace {
			usageSource = usageSource == null ? UsageSource.NOT_REPORTED : usageSource;
		}

	}

}
