package com.deepdimen.matrix.assistant.orchestrator;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionPlan;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStep;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ProviderCapabilitySnapshot;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.WorkflowCapabilitySnapshot;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Deterministic security boundary between semantic planning and execution. */
@Component
public class AssistantExecutionPlanValidator {

	private static final int MAX_STEPS = 16;

	private static final int MAX_WORKFLOWS = 8;

	private static final int MAX_DEPTH = 4;

	private final ObjectMapper objectMapper;

	public AssistantExecutionPlanValidator(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public ExecutionPlan validateAndDigest(ExecutionPlan proposed, FrozenCapabilityCatalog catalog) {
		if (proposed == null || catalog == null || !catalog.digest().equals(proposed.catalogDigest())) {
			throw invalid("CATALOG_DIGEST_MISMATCH");
		}
		if (proposed.createdAt() == null) {
			throw invalid("CREATED_AT_REQUIRED");
		}
		if (proposed.clarification() != null) {
			validateClarification(proposed, catalog);
			return digestPlan(proposed);
		}
		if (proposed.routeDecision() != null && proposed.steps().isEmpty()) {
			var route = proposed.routeDecision();
			boolean direct = route.executionMode() == AssistantExecutionContract.ExecutionMode.DIRECT_CHAT
					&& route.selectedWorkflowIds().isEmpty()
					&& (route.conversationIntent() == AssistantExecutionContract.ConversationIntent.CASUAL_CHAT
							|| route.conversationIntent() == AssistantExecutionContract.ConversationIntent.GENERAL_QA);
			boolean help = route.executionMode() == AssistantExecutionContract.ExecutionMode.CAPABILITY_HELP
					&& route.conversationIntent() == AssistantExecutionContract.ConversationIntent.CAPABILITY_HELP
					&& route.selectedWorkflowIds().size() == 1
					&& catalog.workflows()
						.stream()
						.anyMatch(item -> item.capabilityId().equals(route.selectedWorkflowIds().get(0)));
			boolean validSourceMode = proposed.sourceMode() == AssistantExecutionContract.SourceMode.AUTO
					|| direct && proposed.sourceMode() == AssistantExecutionContract.SourceMode.NONE;
			if ((!direct && !help) || !route.selectedProviderIds().isEmpty()
					|| !validSourceMode
					|| proposed.disposition() != AssistantExecutionContract.PlanningDisposition.MATCH) {
				throw invalid("FAST_ROUTE_INVALID");
			}
			return digestPlan(proposed);
		}
		if (proposed.steps().isEmpty() || proposed.steps().size() > MAX_STEPS) {
			throw invalid("STEP_LIMIT_EXCEEDED");
		}
		Map<String, ProviderCapabilitySnapshot> providers = catalog.providers()
			.stream()
			.collect(java.util.stream.Collectors.toMap(ProviderCapabilitySnapshot::providerCode, item -> item));
		Map<Long, WorkflowCapabilitySnapshot> workflows = catalog.workflows()
			.stream()
			.collect(java.util.stream.Collectors.toMap(WorkflowCapabilitySnapshot::releaseId, item -> item));
		if (proposed.routeDecision() != null) {
			var route = proposed.routeDecision();
			Set<String> workflowIds = catalog.workflows()
				.stream()
				.map(WorkflowCapabilitySnapshot::capabilityId)
				.collect(java.util.stream.Collectors.toSet());
			if (!providers.keySet().containsAll(route.selectedProviderIds())
					|| !workflowIds.containsAll(route.selectedWorkflowIds())) {
				throw invalid("ROUTE_TARGET_NOT_AUTHORIZED");
			}
		}
		Map<String, ExecutionStep> byId = new HashMap<>();
		Set<String> targets = new HashSet<>();
		int analysisCount = 0;
		int workflowCount = 0;
		for (ExecutionStep step : proposed.steps()) {
			if (step == null || step.stepId() == null || step.stepId().isBlank()
					|| byId.put(step.stepId(), step) != null) {
				throw invalid("DUPLICATE_OR_EMPTY_STEP_ID");
			}
			if (step.type() != ExecutionStepType.MATRIX_ANALYSIS && !targets.add(step.type() + ":" + step.targetId())) {
				throw invalid("DUPLICATE_EXECUTION_TARGET");
			}
			switch (step.type()) {
				case MATRIX_PROVIDER -> validateProvider(proposed.runId(), step, providers);
				case DIFY_WORKFLOW -> {
					workflowCount++;
					validateWorkflow(step, workflows);
				}
				case MATRIX_ANALYSIS -> {
					analysisCount++;
					validateAnalysisSkill(step);
				}
			}
		}
		if (workflowCount > MAX_WORKFLOWS || analysisCount != 1 || !byId.containsKey(proposed.analysisStepId())
				|| byId.get(proposed.analysisStepId()).type() != ExecutionStepType.MATRIX_ANALYSIS) {
			throw invalid("ANALYSIS_OR_WORKFLOW_LIMIT_INVALID");
		}
		if (proposed.sourceMode() == AssistantExecutionContract.SourceMode.NONE
				&& proposed.steps().stream().anyMatch(step -> step.type() != ExecutionStepType.MATRIX_ANALYSIS)) {
			throw invalid("SOURCE_MODE_NONE_EXPANDED");
		}
		if (proposed.disposition() != AssistantExecutionContract.PlanningDisposition.MATCH
				&& proposed.steps().stream().anyMatch(step -> step.type() != ExecutionStepType.MATRIX_ANALYSIS)) {
			throw invalid("NON_MATCH_PLAN_MUST_NOT_EXECUTE_SOURCES");
		}
		for (ExecutionStep step : proposed.steps()) {
			if (step.type() == ExecutionStepType.MATRIX_PROVIDER && !step.dependsOn().isEmpty()) {
				throw invalid("PROVIDER_DEPENDENCY_NOT_ALLOWED");
			}
			for (String dependency : step.dependsOn()) {
				if (step.stepId().equals(dependency) || !byId.containsKey(dependency)) {
					throw invalid("UNKNOWN_OR_SELF_DEPENDENCY");
				}
				ExecutionStep dependencyStep = byId.get(dependency);
				if (dependencyStep.type() == ExecutionStepType.MATRIX_ANALYSIS
						|| step.type() == ExecutionStepType.MATRIX_PROVIDER) {
					throw invalid("DEPENDENCY_DIRECTION_INVALID");
				}
			}
		}
		Map<String, Integer> depths = new HashMap<>();
		for (String stepId : byId.keySet()) {
			if (depth(stepId, byId, depths, new HashSet<>()) > MAX_DEPTH) {
				throw invalid("DEPENDENCY_DEPTH_EXCEEDED");
			}
		}
		return digestPlan(proposed);
	}

	private void validateClarification(ExecutionPlan plan, FrozenCapabilityCatalog catalog) {
		var state = plan.clarification();
		var route = plan.routeDecision();
		if (plan.sourceMode() != AssistantExecutionContract.SourceMode.AUTO
				|| plan.disposition() != AssistantExecutionContract.PlanningDisposition.AMBIGUOUS
				|| !plan.steps().isEmpty() || plan.analysisStepId() != null || route == null
				|| route.conversationIntent() != AssistantExecutionContract.ConversationIntent.AMBIGUOUS
				|| route.executionMode() != AssistantExecutionContract.ExecutionMode.CLARIFICATION
				|| !route.selectedProviderIds().isEmpty() || !route.selectedWorkflowIds().isEmpty())
			throw invalid("CLARIFICATION_MUST_NOT_EXECUTE");
		if (state.originRunId() == null || state.originRunId().isBlank() || state.originRunId().length() > 128
				|| state.clarificationCount() < 1 || state.clarificationCount() > 2
				|| state.manualSelectionRequired() != (state.clarificationCount() == 2) || state.question() == null
				|| state.question().isBlank() || state.question().length() > 512 || state.candidates().isEmpty()
				|| state.candidates().size() > 8)
			throw invalid("CLARIFICATION_STATE_INVALID");
		Instant expiry;
		try {
			expiry = Instant.ofEpochMilli(state.expiresAtEpochMillis());
		}
		catch (RuntimeException ex) {
			throw invalid("CLARIFICATION_EXPIRY_INVALID");
		}
		if (!expiry.isAfter(plan.createdAt()) || expiry.isAfter(plan.createdAt().plusSeconds(1800)))
			throw invalid("CLARIFICATION_EXPIRY_INVALID");
		Set<String> seen = new HashSet<>();
		for (var candidate : state.candidates()) {
			boolean authorized = false;
			if (candidate.type() == ExecutionStepType.MATRIX_PROVIDER)
				authorized = candidate.releaseId() == null && catalog.providers()
					.stream()
					.anyMatch(p -> p.providerCode().equals(candidate.targetId())
							&& java.util.Objects.equals(p.displayName(), candidate.displayName()));
			if (candidate.type() == ExecutionStepType.DIFY_WORKFLOW)
				authorized = catalog.workflows()
					.stream()
					.anyMatch(w -> w.capabilityId().equals(candidate.targetId())
							&& java.util.Objects.equals(w.releaseId(), candidate.releaseId())
							&& java.util.Objects.equals(w.displayName(), candidate.displayName()));
			if (!authorized || !seen.add(candidate.type() + ":" + candidate.targetId()))
				throw invalid("CLARIFICATION_CANDIDATE_NOT_AUTHORIZED");
		}
	}

	private ExecutionPlan digestPlan(ExecutionPlan proposed) {
		String digest = sha256(write(new DigestMaterial(proposed.contractVersion(), proposed.planId(), proposed.runId(),
				proposed.sourceMode(), proposed.disposition(), proposed.steps(), proposed.analysisStepId(),
				proposed.reasonCodes(), proposed.catalogDigest(), proposed.createdAt().toString(),
				proposed.routeDecision(), proposed.clarification())));
		return new ExecutionPlan(AssistantExecutionContract.PLAN_VERSION, proposed.planId(), proposed.runId(),
				proposed.sourceMode(), proposed.disposition(), proposed.steps(), proposed.analysisStepId(),
				proposed.reasonCodes(), proposed.catalogDigest(), digest, proposed.createdAt(),
				proposed.routeDecision(), proposed.clarification());
	}

	private void validateProvider(String runId, ExecutionStep step, Map<String, ProviderCapabilitySnapshot> providers) {
		if (!providers.containsKey(step.targetId()) || step.providerQuery() == null
				|| !runId.equals(step.providerQuery().runId()) || step.providerQuery().query() == null
				|| step.providerQuery().query().length() > 2000) {
			throw invalid("PROVIDER_STEP_NOT_ALLOWED");
		}
	}

	private void validateWorkflow(ExecutionStep step, Map<Long, WorkflowCapabilitySnapshot> workflows) {
		WorkflowCapabilitySnapshot workflow = step.targetReleaseId() == null ? null
				: workflows.get(step.targetReleaseId());
		if (workflow == null || !workflow.capabilityId().equals(step.targetId())
				|| !workflow.outputSchemaId().equals(step.outputSchemaId())) {
			throw invalid("WORKFLOW_RELEASE_NOT_ALLOWED");
		}
	}

	private void validateAnalysisSkill(ExecutionStep step) {
		Object instruction = step.inputProjection().get("instruction");
		Object instructionDigest = step.inputProjection().get("instructionDigest");
		Object releaseVersion = step.inputProjection().get("releaseVersion");
		Object packageSha256 = step.inputProjection().get("packageSha256");
		Object policy = step.inputProjection().get("policy");
		Object policyDigest = step.inputProjection().get("policyDigest");
		Object resultSchema = step.inputProjection().get("resultSchema");
		if (step.targetReleaseId() == null || step.outputSchemaId() == null || instruction == null
				|| instructionDigest == null || releaseVersion == null || packageSha256 == null || policy == null
				|| policyDigest == null || resultSchema == null) {
			throw invalid("ANALYSIS_SKILL_SNAPSHOT_INVALID");
		}
		String instructionValue = String.valueOf(instruction);
		String packageDigest = String.valueOf(packageSha256);
		String expectedPolicyDigest = sha256(canonical(objectMapper.valueToTree(policy)));
		String expectedSchemaId = "sha256:" + sha256(canonical(objectMapper.valueToTree(resultSchema)));
		if (instructionValue.isBlank() || String.valueOf(releaseVersion).isBlank()
				|| !sha256(instructionValue).equals(String.valueOf(instructionDigest))
				|| !packageDigest.matches("[0-9a-f]{64}") || !expectedPolicyDigest.equals(String.valueOf(policyDigest))
				|| !expectedSchemaId.equals(step.outputSchemaId())) {
			throw invalid("ANALYSIS_SKILL_SNAPSHOT_DIGEST_MISMATCH");
		}
	}

	private int depth(String stepId, Map<String, ExecutionStep> byId, Map<String, Integer> memo, Set<String> visiting) {
		Integer known = memo.get(stepId);
		if (known != null) {
			return known;
		}
		if (!visiting.add(stepId)) {
			throw invalid("PLAN_CYCLE_DETECTED");
		}
		int result = 1;
		for (String dependency : byId.get(stepId).dependsOn()) {
			result = Math.max(result, 1 + depth(dependency, byId, memo, visiting));
		}
		visiting.remove(stepId);
		memo.put(stepId, result);
		return result;
	}

	private IllegalArgumentException invalid(String reason) {
		return new IllegalArgumentException("EXECUTION_PLAN_INVALID:" + reason);
	}

	private String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalArgumentException("EXECUTION_PLAN_INVALID:SERIALIZATION_FAILED", ex);
		}
	}

	private String canonical(JsonNode node) {
		if (node.isObject()) {
			Map<String, String> fields = new TreeMap<>();
			node.fields().forEachRemaining(field -> fields.put(field.getKey(), canonical(field.getValue())));
			return fields.entrySet()
				.stream()
				.map(field -> objectMapper.getNodeFactory().textNode(field.getKey()) + ":" + field.getValue())
				.collect(java.util.stream.Collectors.joining(",", "{", "}"));
		}
		if (node.isArray()) {
			List<String> values = new ArrayList<>();
			node.forEach(value -> values.add(canonical(value)));
			return "[" + String.join(",", values) + "]";
		}
		return node.toString();
	}

	private String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}

	private record DigestMaterial(String contractVersion, String planId, String runId,
			AssistantExecutionContract.SourceMode sourceMode,
			AssistantExecutionContract.PlanningDisposition disposition, List<ExecutionStep> steps,
			String analysisStepId, List<String> reasonCodes, String catalogDigest, String createdAt,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) AssistantExecutionContract.RouteDecision routeDecision,
			@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) AssistantExecutionContract.ClarificationState clarification) {
	}

}
