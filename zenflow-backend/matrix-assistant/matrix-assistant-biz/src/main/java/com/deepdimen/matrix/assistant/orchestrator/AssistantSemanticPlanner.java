package com.deepdimen.matrix.assistant.orchestrator;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderScope;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionPlan;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStep;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.PlanningDisposition;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ProviderCapabilitySnapshot;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.SourceMode;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.WorkflowCapabilitySnapshot;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.ScopeHint;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;
import com.deepdimen.matrix.assistant.capability.AssistantAnalysisSkillCatalog.AnalysisSkillSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.deepdimen.matrix.assistant.orchestrator.AssistantStructuredPlanningGateway.PlanningSuggestion;
import com.deepdimen.matrix.assistant.orchestrator.AssistantStructuredPlanningGateway.ProviderSuggestion;
import com.deepdimen.matrix.assistant.orchestrator.AssistantStructuredPlanningGateway.WorkflowSuggestion;

/**
 * Bounded semantic candidate planner; every proposed ID is rechecked by the validator.
 */
@Service
public class AssistantSemanticPlanner {

	private static final Pattern SEMANTIC_TOKEN = Pattern.compile("[\\p{IsHan}]{2,}|[a-zA-Z][a-zA-Z0-9_-]{2,}");

	private static final Pattern HAN_RUN = Pattern.compile("[\\p{IsHan}]{2,}");

	private static final Pattern GENERATED_SOURCE_MARKER = Pattern
		.compile("\\[(?:工作流|来源|artifact)\\s*:[^\\]]{1,200}\\]", Pattern.CASE_INSENSITIVE);

	private static final Set<String> GENERIC = Set.of("总结", "分析", "情况", "内容", "信息", "这些", "当前", "一下", "进行");

	private static final Pattern WORKFLOW_NAME_SUFFIX = Pattern.compile("(?:智能体|助手|工作流|监测|分析)+$");

	private static final Map<String, List<String>> PROVIDER_TERMS = Map.of("KNOWLEDGE",
			List.of("知识", "知识库", "制度", "法规", "规范", "文档", "资料", "要求"), "WORK", List.of("待办", "任务", "工作任务", "事项", "进度"),
			"CAPSULE", List.of("闪念", "胶囊", "笔记", "想法"), "INFORMATION", List.of("资讯", "新闻", "行业动态", "媒体报道"));

	@org.springframework.beans.factory.annotation.Value("${matrix.assistant.routing.candidate-retrieval-enabled:false}")
	private boolean candidateRetrievalEnabled;

	@org.springframework.beans.factory.annotation.Value("${matrix.assistant.routing.fast-path-enabled:false}")
	private boolean fastPathEnabled;

	@org.springframework.beans.factory.annotation.Value("${matrix.assistant.routing.semantic-routing-enabled:false}")
	private boolean semanticRoutingEnabled;

	@org.springframework.beans.factory.annotation.Value("${matrix.assistant.routing.minimum-confidence:0.85}")
	private double minimumConfidence = 0.85;

	@org.springframework.beans.factory.annotation.Value("${matrix.assistant.routing.minimum-margin:0.15}")
	private double minimumMargin = 0.15;

	private final AssistantCapabilityCandidateRetriever candidateRetriever = new AssistantCapabilityCandidateRetriever();

	private final AssistantExecutionPlanValidator validator;

	private final AssistantStructuredPlanningGateway structuredPlanningGateway;

	public AssistantSemanticPlanner(AssistantExecutionPlanValidator validator) {
		this(validator, (AssistantStructuredPlanningGateway) null);
	}

	@Autowired
	public AssistantSemanticPlanner(AssistantExecutionPlanValidator validator,
			ObjectProvider<AssistantStructuredPlanningGateway> structuredPlanningGatewayProvider) {
		this(validator, structuredPlanningGatewayProvider.getIfAvailable());
	}

	AssistantSemanticPlanner(AssistantExecutionPlanValidator validator,
			AssistantStructuredPlanningGateway structuredPlanningGateway) {
		this.validator = validator;
		this.structuredPlanningGateway = structuredPlanningGateway;
	}

	public ExecutionPlan plan(String runId, IdentityEnvelope identity, String content, SourceMode sourceMode,
			List<ProviderId> cappedProviders, ScopeHint scopeHint, List<String> previousQueries,
			FrozenCapabilityCatalog catalog, AnalysisSkillSnapshot analysisSkill) {
		return plan(runId, identity, content, sourceMode, cappedProviders, scopeHint, previousQueries, catalog,
				analysisSkill, null);
	}

	public ExecutionPlan plan(String runId, IdentityEnvelope identity, String content, SourceMode sourceMode,
			List<ProviderId> cappedProviders, ScopeHint scopeHint, List<String> previousQueries,
			FrozenCapabilityCatalog catalog, AnalysisSkillSnapshot analysisSkill,
			AssistantStructuredPlanningGateway.ClarificationContext clarification) {
		SourceMode mode = SourceMode.effective(sourceMode);
		String routingQuestion = routingQuestion(content);
		validateSkillSourceRequirements(mode, cappedProviders, catalog, analysisSkill);
		if (clarification == null && mode == SourceMode.NONE) {
			var route = new AssistantExecutionContract.RouteDecision(
					AssistantExecutionContract.ConversationIntent.GENERAL_QA,
					AssistantExecutionContract.ExecutionMode.DIRECT_CHAT, List.of(), List.of(),
					AssistantExecutionContract.RouteTriggerType.EXPLICIT_SOURCE, null, null, false, false,
					"EXPLICIT_NO_SOURCE_CHAT");
			return validator.validateAndDigest(new ExecutionPlan(null, UUID.randomUUID().toString(), runId, mode,
					PlanningDisposition.MATCH, List.of(), null, List.of("EXPLICIT_NO_SOURCE_CHAT"), catalog.digest(), null,
					Instant.now(), route), catalog);
		}
		if (clarification == null && fastPathEnabled && candidateRetrievalEnabled && mode == SourceMode.AUTO
				&& analysisSkill.requiredProviders().isEmpty()) {
			var fastRoute = new AssistantSemanticRouter().fastRoute(routingQuestion, catalog);
			if (fastRoute.isPresent()) {
				var route = fastRoute.get();
				if (route.executionMode() == AssistantExecutionContract.ExecutionMode.WORKFLOW_EXECUTION) {
					var workflow = catalog.workflows()
						.stream()
						.filter(item -> route.selectedWorkflowIds().contains(item.capabilityId()))
						.findFirst()
						.orElseThrow();
					var suggestion = new PlanningSuggestion(PlanningDisposition.MATCH,
							workflow.requiredProviderCodes()
								.stream()
								.map(code -> new ProviderSuggestion(code, routingQuestion))
								.toList(),
							List.of(new WorkflowSuggestion(workflow.capabilityId(), workflow.releaseId(), List.of())),
							List.of(route.reasonCode()), null);
					ExecutionPlan executable = fromSuggestion(runId, identity, routingQuestion, mode, cappedProviders,
							scopeHint, previousQueries, catalog, analysisSkill, suggestion);
					var actualRoute = new AssistantExecutionContract.RouteDecision(route.conversationIntent(),
							route.executionMode(),
							executable.steps()
								.stream()
								.filter(step -> step.type() == ExecutionStepType.MATRIX_PROVIDER)
								.map(ExecutionStep::targetId)
								.toList(),
							executable.steps()
								.stream()
								.filter(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW)
								.map(ExecutionStep::targetId)
								.toList(),
							route.triggerType(), null, null, false, false, route.reasonCode());
					return validator
						.validateAndDigest(new ExecutionPlan(executable.contractVersion(), executable.planId(),
								executable.runId(), executable.sourceMode(), executable.disposition(),
								executable.steps()
									.stream()
									.map(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW
											? new ExecutionStep(step.stepId(), step.type(), step.targetId(),
													step.targetReleaseId(), true, step.dependsOn(),
													step.providerQuery(), step.inputProjection(), step.outputSchemaId())
											: step)
									.toList(),
								executable.analysisStepId(), executable.reasonCodes(), executable.catalogDigest(), null,
								executable.createdAt(), actualRoute), catalog);
				}
				return validator.validateAndDigest(new ExecutionPlan(null, UUID.randomUUID().toString(), runId, mode,
						PlanningDisposition.MATCH, List.of(), null, List.of(fastRoute.get().reasonCode()),
						catalog.digest(), null, Instant.now(), fastRoute.get()), catalog);
			}
		}

		if (structuredPlanningGateway != null) {
			List<String> cap = (cappedProviders == null ? List.<ProviderId>of() : cappedProviders).stream()
				.map(ProviderId::value)
				.toList();
			FrozenCapabilityCatalog planningCatalog = clarification == null && candidateRetrievalEnabled
					&& mode == SourceMode.AUTO
							? candidateRetriever.shortlist(routingQuestion, catalog, 8,
									analysisSkill.requiredProviders())
							: catalog;
			var suggestion = structuredPlanningGateway.suggest(runId, identity, routingQuestion, mode, cap,
					planningCatalog, analysisSkill, clarification);
			if (suggestion.isPresent()) {
				try {
					var semantic = semanticDirectPlan(runId, routingQuestion, mode, scopeHint, catalog, analysisSkill,
							suggestion.get());
					if (semantic != null)
						return semantic;
					String effectiveQuestion = clarification == null ? routingQuestion
							: "原始请求：" + clarification.originalQuestion() + "\n本轮补充（以此修正原请求）：" + routingQuestion;
					PlanningSuggestion effectiveSuggestion = clarification == null
							? resolveNarrowWorkflowAmbiguity(routingQuestion, suggestion.get(), catalog.workflows())
							: suggestion.get();
					var executable = fromSuggestion(runId, identity, effectiveQuestion, mode, cappedProviders,
							scopeHint, previousQueries, catalog, analysisSkill, effectiveSuggestion);
					return clarification == null ? executable : clarifiedQueries(executable, catalog);
				}
				catch (RuntimeException ignored) {
					if (clarification != null)
						return finishPlan(runId, mode, PlanningDisposition.AMBIGUOUS, new ArrayList<>(),
								List.of("CLARIFICATION_PLAN_REJECTED"), catalog, analysisSkill);
					return heuristicPlan(runId, identity, routingQuestion, mode, cappedProviders, scopeHint,
							previousQueries, catalog, analysisSkill,
							List.of("PLANNER_OUTPUT_REJECTED", "CATALOG_HEURISTIC_FALLBACK"));
				}
			}
			if (clarification != null)
				return finishPlan(runId, mode, PlanningDisposition.AMBIGUOUS, new ArrayList<>(),
						List.of("CLARIFICATION_PLANNER_UNAVAILABLE"), catalog, analysisSkill);
			return heuristicPlan(runId, identity, routingQuestion, mode, cappedProviders, scopeHint, previousQueries,
					catalog, analysisSkill, List.of("PLANNER_UNAVAILABLE", "CATALOG_HEURISTIC_FALLBACK"));
		}
		if (clarification != null)
			return finishPlan(runId, mode, PlanningDisposition.AMBIGUOUS, new ArrayList<>(),
					List.of("CLARIFICATION_PLANNER_UNAVAILABLE"), catalog, analysisSkill);
		return heuristicPlan(runId, identity, routingQuestion, mode, cappedProviders, scopeHint, previousQueries,
				catalog, analysisSkill);
	}

	/**
	 * Caller must first bind this candidate to a current, unexpired manual clarification.
	 */
	public ExecutionPlan planSelected(String runId, IdentityEnvelope identity, String effectiveQuestion,
			ScopeHint scopeHint, FrozenCapabilityCatalog catalog, AnalysisSkillSnapshot skill,
			AssistantExecutionContract.ClarificationCandidate selected) {
		if (effectiveQuestion == null || effectiveQuestion.isBlank() || effectiveQuestion.length() > 2000
				|| selected == null)
			throw new IllegalArgumentException("MANUAL_SELECTION_INPUT_INVALID");
		Set<String> providers = new LinkedHashSet<>(skill.requiredProviders());
		WorkflowCapabilitySnapshot workflow = null;
		if (selected.type() == ExecutionStepType.MATRIX_PROVIDER) {
			if (selected.releaseId() != null || catalog.providers()
				.stream()
				.noneMatch(p -> p.providerCode().equals(selected.targetId())
						&& java.util.Objects.equals(p.displayName(), selected.displayName())))
				throw new IllegalArgumentException("MANUAL_SELECTION_NOT_AUTHORIZED");
			providers.add(selected.targetId());
		}
		else if (selected.type() == ExecutionStepType.DIFY_WORKFLOW) {
			workflow = catalog.workflows()
				.stream()
				.filter(w -> w.capabilityId().equals(selected.targetId())
						&& java.util.Objects.equals(w.releaseId(), selected.releaseId())
						&& java.util.Objects.equals(w.displayName(), selected.displayName()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("MANUAL_SELECTION_NOT_AUTHORIZED"));
			if (!workflowCompatible(effectiveQuestion, workflow))
				throw new IllegalArgumentException("MANUAL_SELECTION_TIME_INCOMPATIBLE");
			providers.addAll(workflow.requiredProviderCodes());
		}
		else
			throw new IllegalArgumentException("MANUAL_SELECTION_TYPE_INVALID");
		Set<String> available = catalog.providers()
			.stream()
			.map(ProviderCapabilitySnapshot::providerCode)
			.collect(java.util.stream.Collectors.toSet());
		if (!available.containsAll(providers))
			throw new IllegalArgumentException("MANUAL_SELECTION_REQUIRED_PROVIDER_UNAVAILABLE");
		List<ExecutionStep> steps = providerSteps(runId, identity, effectiveQuestion, SourceMode.AUTO, scopeHint,
				List.of(), providers.stream().map(code -> new ProviderSuggestion(code, effectiveQuestion)).toList(),
				providers);
		if (workflow != null) {
			steps.add(new ExecutionStep("workflow-" + workflow.releaseId(), ExecutionStepType.DIFY_WORKFLOW,
					workflow.capabilityId(), workflow.releaseId(), true,
					workflow.requiredProviderCodes()
						.stream()
						.map(code -> "provider-" + code.toLowerCase(Locale.ROOT))
						.toList(),
					null, Map.of("query", effectiveQuestion, "includeDependencyArtifacts", true, "displayName",
							workflow.displayName()),
					workflow.outputSchemaId()));
		}
		var executable = finishPlan(runId, SourceMode.AUTO, PlanningDisposition.MATCH, steps,
				List.of("MANUAL_CLARIFICATION_SELECTION"), catalog, skill);
		var route = new AssistantExecutionContract.RouteDecision(
				AssistantExecutionContract.ConversationIntent.BUSINESS_QUERY,
				workflow == null ? AssistantExecutionContract.ExecutionMode.PROVIDER_QUERY
						: AssistantExecutionContract.ExecutionMode.WORKFLOW_EXECUTION,
				List.copyOf(providers), workflow == null ? List.of() : List.of(workflow.capabilityId()),
				AssistantExecutionContract.RouteTriggerType.EXPLICIT_SOURCE, null, null, false, true,
				"MANUAL_CLARIFICATION_SELECTION");
		return validator.validateAndDigest(
				new ExecutionPlan(executable.contractVersion(), executable.planId(), runId, SourceMode.AUTO,
						executable.disposition(), executable.steps(), executable.analysisStepId(),
						executable.reasonCodes(), executable.catalogDigest(), null, executable.createdAt(), route),
				catalog);
	}

	private ExecutionPlan clarifiedQueries(ExecutionPlan plan, FrozenCapabilityCatalog catalog) {
		var steps = plan.steps().stream().map(step -> {
			if (step.type() != ExecutionStepType.MATRIX_PROVIDER)
				return step;
			var query = step.providerQuery();
			var terms = query.scope().explicitObjectIds().isEmpty()
					? AssistantQueryPlanner.plan(query.query(), List.of()) : List.<String>of();
			var revised = new ProviderQuery(query.contractVersion(), query.requestId(), query.runId(),
					query.correlationId(), query.identity(), query.query(), query.scope(), query.limits(), terms);
			return new ExecutionStep(step.stepId(), step.type(), step.targetId(), step.targetReleaseId(),
					step.required(), step.dependsOn(), revised, step.inputProjection(), step.outputSchemaId());
		}).toList();
		return validator.validateAndDigest(new ExecutionPlan(plan.contractVersion(), plan.planId(), plan.runId(),
				plan.sourceMode(), plan.disposition(), steps, plan.analysisStepId(), plan.reasonCodes(),
				plan.catalogDigest(), null, plan.createdAt(), plan.routeDecision(), plan.clarification()), catalog);
	}

	private ExecutionPlan semanticDirectPlan(String runId, String question, SourceMode mode, ScopeHint scope,
			FrozenCapabilityCatalog catalog, AnalysisSkillSnapshot skill, PlanningSuggestion suggestion) {
		var route = suggestion.routing();
		if (!semanticRoutingEnabled || !candidateRetrievalEnabled || mode != SourceMode.AUTO || route == null)
			return null;
		var intent = route.conversationIntent();
		var execution = route.executionMode();
		boolean confident = validScore(route.confidence()) && validScore(route.runnerUpMargin())
				&& validScore(minimumConfidence) && validScore(minimumMargin) && route.confidence() >= minimumConfidence
				&& route.runnerUpMargin() >= minimumMargin;
		boolean business = intent == AssistantExecutionContract.ConversationIntent.BUSINESS_QUERY
				|| intent == AssistantExecutionContract.ConversationIntent.BUSINESS_ANALYSIS;
		if (business) {
			boolean consistent = execution == AssistantExecutionContract.ExecutionMode.WORKFLOW_EXECUTION
					&& !suggestion.workflows().isEmpty()
					|| execution == AssistantExecutionContract.ExecutionMode.PROVIDER_QUERY
							&& suggestion.workflows().isEmpty()
							&& (!suggestion.providers().isEmpty() || !skill.requiredProviders().isEmpty());
			if (!confident || !consistent || suggestion.disposition() != PlanningDisposition.MATCH)
				return finishPlan(runId, mode, PlanningDisposition.AMBIGUOUS, new ArrayList<>(),
						List.of(consistent ? "SEMANTIC_ROUTE_UNCERTAIN" : "SEMANTIC_ROUTE_INCONSISTENT"), catalog,
						skill);
			return null;
		}
		if (intent == null || intent == AssistantExecutionContract.ConversationIntent.AMBIGUOUS
				|| intent == AssistantExecutionContract.ConversationIntent.ACTION_REQUEST)
			return finishPlan(runId, mode, PlanningDisposition.AMBIGUOUS, new ArrayList<>(),
					List.of("SEMANTIC_ROUTE_NON_EXECUTING"), catalog, skill);
		if (!skill.requiredProviders().isEmpty())
			return finishPlan(runId, mode, PlanningDisposition.AMBIGUOUS, new ArrayList<>(),
					List.of("SEMANTIC_ROUTE_INCONSISTENT"), catalog, skill);
		boolean direct = execution == AssistantExecutionContract.ExecutionMode.DIRECT_CHAT
				&& (intent == AssistantExecutionContract.ConversationIntent.GENERAL_QA
						|| intent == AssistantExecutionContract.ConversationIntent.CASUAL_CHAT);
		boolean help = execution == AssistantExecutionContract.ExecutionMode.CAPABILITY_HELP
				&& intent == AssistantExecutionContract.ConversationIntent.CAPABILITY_HELP;
		if (!direct && !help) {
			if (intent == AssistantExecutionContract.ConversationIntent.GENERAL_QA
					|| intent == AssistantExecutionContract.ConversationIntent.CASUAL_CHAT
					|| intent == AssistantExecutionContract.ConversationIntent.CAPABILITY_HELP) {
				return finishPlan(runId, mode, PlanningDisposition.AMBIGUOUS, new ArrayList<>(),
						List.of("SEMANTIC_ROUTE_INCONSISTENT"), catalog, skill);
			}
			return null;
		}
		boolean constrained = scope != null
				&& (!scope.objectIds().isEmpty() || scope.sourceRoute() != null && !scope.sourceRoute().isBlank()
						|| scope.filters() != null && !scope.filters().isEmpty());
		var selected = new ArrayList<String>();
		boolean supported = !constrained && confident && suggestion.disposition() == PlanningDisposition.MATCH
				&& suggestion.providers().isEmpty();
		if (direct) {
			supported &= suggestion.workflows().isEmpty()
					&& candidateRetriever.exactWorkflowMatches(question, catalog).isEmpty()
					&& catalog.providers()
						.stream()
						.noneMatch(p -> explicitlyRequestsProvider(question, p.providerCode()));
		}
		else {
			supported &= suggestion.workflows().size() == 1;
			if (suggestion.workflows().size() == 1) {
				var proposed = suggestion.workflows().get(0);
				supported &= proposed.dependsOnCapabilityIds().isEmpty() && catalog.workflows()
					.stream()
					.anyMatch(w -> w.capabilityId().equals(proposed.capabilityId())
							&& java.util.Objects.equals(w.releaseId(), proposed.releaseId()));
				selected.add(proposed.capabilityId());
			}
		}
		if (!supported) {
			// Do not let a rejected non-executing suggestion become heuristic execution.
			return finishPlan(runId, mode, PlanningDisposition.AMBIGUOUS, new ArrayList<>(),
					List.of("SEMANTIC_ROUTE_UNCERTAIN"), catalog, skill);
		}
		var decision = new AssistantExecutionContract.RouteDecision(intent, execution, List.of(), selected,
				AssistantExecutionContract.RouteTriggerType.PLANNER, route.confidence(), route.runnerUpMargin(), true,
				false, direct ? "GENERAL_DIALOGUE_CLASSIFIED" : "CAPABILITY_DESCRIPTION_CLASSIFIED");
		return validator.validateAndDigest(
				new ExecutionPlan(null, UUID.randomUUID().toString(), runId, mode, PlanningDisposition.MATCH, List.of(),
						null, List.of(decision.reasonCode()), catalog.digest(), null, Instant.now(), decision),
				catalog);
	}

	private boolean validScore(Double score) {
		return score != null && Double.isFinite(score) && score >= 0 && score <= 1;
	}

	private ExecutionPlan heuristicPlan(String runId, IdentityEnvelope identity, String content, SourceMode sourceMode,
			List<ProviderId> cappedProviders, ScopeHint scopeHint, List<String> previousQueries,
			FrozenCapabilityCatalog catalog, AnalysisSkillSnapshot analysisSkill) {
		return heuristicPlan(runId, identity, content, sourceMode, cappedProviders, scopeHint, previousQueries, catalog,
				analysisSkill, List.of());
	}

	private ExecutionPlan heuristicPlan(String runId, IdentityEnvelope identity, String content, SourceMode sourceMode,
			List<ProviderId> cappedProviders, ScopeHint scopeHint, List<String> previousQueries,
			FrozenCapabilityCatalog catalog, AnalysisSkillSnapshot analysisSkill, List<String> fallbackReasons) {
		SourceMode mode = SourceMode.effective(sourceMode);
		List<ProviderCapabilitySnapshot> providers = new ArrayList<>(
				selectProviders(content, mode, cappedProviders, catalog.providers()));
		Set<String> cap = (cappedProviders == null ? List.<ProviderId>of() : cappedProviders).stream()
			.map(ProviderId::value)
			.collect(java.util.stream.Collectors.toSet());
		List<WorkflowCapabilitySnapshot> workflows = mode == SourceMode.AUTO || mode == SourceMode.CAP
				? selectWorkflows(content, catalog.workflows()).stream()
					.filter(workflow -> mode != SourceMode.CAP || cap.containsAll(workflow.requiredProviderCodes()))
					.toList()
				: List.of();
		if (mode == SourceMode.AUTO) {
			Set<String> required = workflows.stream()
				.flatMap(item -> item.requiredProviderCodes().stream())
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
			required.addAll(analysisSkill.requiredProviders());
			Set<String> alreadySelected = providers.stream()
				.map(ProviderCapabilitySnapshot::providerCode)
				.collect(java.util.stream.Collectors.toSet());
			catalog.providers()
				.stream()
				.filter(item -> required.contains(item.providerCode()))
				.filter(item -> !alreadySelected.contains(item.providerCode()))
				.forEach(providers::add);
		}
		List<ExecutionStep> steps = new ArrayList<>();
		ProviderScope scope = new ProviderScope(null, null, scopeHint == null ? List.of() : scopeHint.objectIds(),
				scopeHint == null ? null : scopeHint.sourceRoute(), scopeHint == null ? null : scopeHint.filters());
		List<String> queryTerms = scope.explicitObjectIds().isEmpty()
				? AssistantQueryPlanner.plan(content, previousQueries) : List.of();
		for (ProviderCapabilitySnapshot provider : providers) {
			ProviderQuery query = new ProviderQuery(AssistantContextContract.VERSION, UUID.randomUUID().toString(),
					runId, identity.correlationId(), identity,
					providerQuery(content, provider.providerCode(), queryTerms), scope,
					AssistantQueryPlanner.limitsFor(provider.providerCode()), queryTerms);
			steps.add(new ExecutionStep("provider-" + provider.providerCode().toLowerCase(Locale.ROOT),
					ExecutionStepType.MATRIX_PROVIDER, provider.providerCode(), null,
					analysisSkill.requiredProviders().contains(provider.providerCode())
							|| mode == SourceMode.CAP && broadAggregate(normalize(content)),
					List.of(), query, Map.of(), null));
		}
		for (WorkflowCapabilitySnapshot workflow : workflows) {
			String stepId = "workflow-" + workflow.releaseId();
			List<String> workflowDependencies = workflow.requiredProviderCodes()
				.stream()
				.map(code -> "provider-" + code.toLowerCase(Locale.ROOT))
				.filter(id -> steps.stream().anyMatch(step -> step.stepId().equals(id)))
				.toList();
			steps.add(new ExecutionStep(stepId, ExecutionStepType.DIFY_WORKFLOW, workflow.capabilityId(),
					workflow.releaseId(), workflowRequired(content, workflow), workflowDependencies, null,
					Map.of("query", content, "includeDependencyArtifacts", true, "displayName", workflow.displayName()),
					workflow.outputSchemaId()));
			// Candidate workflows are independent leaves in the safe fallback planner. A
			// structured planner may later introduce validated dependencies, but the
			// fallback
			// never invents an ordering relationship from names alone.
		}
		String analysisStepId = "analysis-final";
		List<String> analysisDependencies = steps.stream().map(ExecutionStep::stepId).toList();
		steps.add(new ExecutionStep(analysisStepId, ExecutionStepType.MATRIX_ANALYSIS, analysisSkill.skillId(),
				analysisSkill.releaseId(), true, analysisDependencies, null,
				Map.of("instruction", analysisSkill.instruction(), "instructionDigest",
						analysisSkill.instructionDigest(), "releaseVersion", analysisSkill.releaseVersion(),
						"packageSha256", analysisSkill.packageSha256(), "policy", analysisSkill.policy(),
						"policyDigest", analysisSkill.policyDigest(), "resultSchema", analysisSkill.resultSchema()),
				analysisSkill.resultSchemaId()));
		PlanningDisposition disposition = providers.isEmpty() && workflows.isEmpty() && mode != SourceMode.NONE
				? PlanningDisposition.NO_MATCH : PlanningDisposition.MATCH;
		List<String> reasons = new ArrayList<>(fallbackReasons == null ? List.of() : fallbackReasons);
		if (disposition == PlanningDisposition.NO_MATCH) {
			reasons.add("NO_SEMANTIC_SOURCE_MATCH");
		}
		else if (mode == SourceMode.NONE) {
			reasons.add("SOURCE_MODE_NONE");
		}
		ExecutionPlan proposed = new ExecutionPlan(AssistantExecutionContract.PLAN_VERSION,
				UUID.randomUUID().toString(), runId, mode, disposition, steps, analysisStepId,
				sanitizeReasonCodes(reasons), catalog.digest(), null, Instant.now());
		return validator.validateAndDigest(proposed, catalog);
	}

	private ExecutionPlan fromSuggestion(String runId, IdentityEnvelope identity, String content, SourceMode mode,
			List<ProviderId> cappedProviders, ScopeHint scopeHint, List<String> previousQueries,
			FrozenCapabilityCatalog catalog, AnalysisSkillSnapshot analysisSkill, PlanningSuggestion suggestion) {
		if (suggestion.disposition() == null) {
			throw new IllegalArgumentException("PLANNER_DISPOSITION_REQUIRED");
		}
		Set<String> cap = (cappedProviders == null ? List.<ProviderId>of() : cappedProviders).stream()
			.map(ProviderId::value)
			.collect(java.util.stream.Collectors.toSet());
		Map<String, ProviderCapabilitySnapshot> availableProviders = catalog.providers()
			.stream()
			.collect(java.util.stream.Collectors.toMap(ProviderCapabilitySnapshot::providerCode, item -> item));
		Map<Long, WorkflowCapabilitySnapshot> availableWorkflows = catalog.workflows()
			.stream()
			.collect(java.util.stream.Collectors.toMap(WorkflowCapabilitySnapshot::releaseId, item -> item));
		List<ProviderSuggestion> providerSuggestions = suggestion.disposition() == PlanningDisposition.MATCH
				? suggestion.providers() : List.of();
		if (mode == SourceMode.NONE && (!providerSuggestions.isEmpty() || !suggestion.workflows().isEmpty())) {
			throw new IllegalArgumentException("SOURCE_MODE_NONE_EXPANDED");
		}
		if (mode == SourceMode.CAP && broadAggregate(normalize(content))) {
			providerSuggestions = cap.stream().sorted().map(code -> new ProviderSuggestion(code, content)).toList();
		}
		Map<String, ProviderSuggestion> selectedProviders = new LinkedHashMap<>();
		for (ProviderSuggestion provider : providerSuggestions) {
			if (provider == null || !availableProviders.containsKey(provider.providerCode())
					|| mode == SourceMode.CAP && !cap.contains(provider.providerCode())) {
				throw new IllegalArgumentException("PLANNER_PROVIDER_NOT_ALLOWED");
			}
			if (!providerSuggestionAllowed(content, provider.providerCode(), mode)) {
				continue;
			}
			selectedProviders.putIfAbsent(provider.providerCode(), provider);
		}
		if (mode == SourceMode.AUTO) {
			availableProviders.values()
				.stream()
				.filter(provider -> explicitlyRequestsProvider(content, provider.providerCode()))
				.forEach(provider -> selectedProviders.putIfAbsent(provider.providerCode(),
						new ProviderSuggestion(provider.providerCode(), content)));
			analysisSkill.requiredProviders()
				.forEach(code -> selectedProviders.putIfAbsent(code, new ProviderSuggestion(code, content)));
		}
		Map<Long, WorkflowSuggestion> selectedWorkflows = new LinkedHashMap<>();
		List<WorkflowSuggestion> workflowSuggestions = suggestion.disposition() == PlanningDisposition.MATCH
				? refineWorkflowSuggestions(content, suggestion.workflows(), catalog.workflows()) : List.of();
		for (WorkflowSuggestion proposed : workflowSuggestions) {
			WorkflowCapabilitySnapshot workflow = proposed == null || proposed.releaseId() == null ? null
					: availableWorkflows.get(proposed.releaseId());
			if (workflow == null || !workflow.capabilityId().equals(proposed.capabilityId())
					|| mode == SourceMode.CAP && !cap.containsAll(workflow.requiredProviderCodes())) {
				throw new IllegalArgumentException("PLANNER_WORKFLOW_NOT_ALLOWED");
			}
			selectedWorkflows.putIfAbsent(proposed.releaseId(), proposed);
			if (mode == SourceMode.AUTO) {
				workflow.requiredProviderCodes()
					.forEach(code -> selectedProviders.putIfAbsent(code, new ProviderSuggestion(code, content)));
			}
		}
		if (!availableProviders.keySet().containsAll(selectedProviders.keySet())) {
			throw new IllegalArgumentException("PLANNER_REQUIRED_PROVIDER_NOT_AVAILABLE");
		}
		List<ExecutionStep> steps = providerSteps(runId, identity, content, mode, scopeHint, previousQueries,
				selectedProviders.values().stream().toList(), analysisSkill.requiredProviders());
		Map<String, String> workflowStepIds = selectedWorkflows.values()
			.stream()
			.collect(java.util.stream.Collectors.toMap(WorkflowSuggestion::capabilityId,
					item -> "workflow-" + item.releaseId(), (left, right) -> left));
		for (WorkflowSuggestion selected : selectedWorkflows.values()) {
			if (selected.dependsOnCapabilityIds()
				.stream()
				.anyMatch(dependencyId -> dependencyId == null || dependencyId.equals(selected.capabilityId())
						|| !workflowStepIds.containsKey(dependencyId))) {
				throw new IllegalArgumentException("PLANNER_WORKFLOW_DEPENDENCY_NOT_ALLOWED");
			}
			WorkflowCapabilitySnapshot workflow = availableWorkflows.get(selected.releaseId());
			List<String> dependencies = new ArrayList<>(workflow.requiredProviderCodes()
				.stream()
				.map(code -> "provider-" + code.toLowerCase(Locale.ROOT))
				.filter(id -> steps.stream().anyMatch(step -> step.stepId().equals(id)))
				.toList());
			selected.dependsOnCapabilityIds()
				.stream()
				.map(workflowStepIds::get)
				.filter(java.util.Objects::nonNull)
				.forEach(dependencies::add);
			steps.add(new ExecutionStep(workflowStepIds.get(selected.capabilityId()), ExecutionStepType.DIFY_WORKFLOW,
					selected.capabilityId(), selected.releaseId(), workflowRequired(content, workflow), dependencies,
					null,
					Map.of("query", content, "includeDependencyArtifacts", true, "displayName", workflow.displayName()),
					workflow.outputSchemaId()));
		}
		return finishPlan(runId, mode, suggestion.disposition(), steps, suggestion.reasonCodes(), catalog,
				analysisSkill);
	}

	private List<ExecutionStep> providerSteps(String runId, IdentityEnvelope identity, String content, SourceMode mode,
			ScopeHint scopeHint, List<String> previousQueries, List<ProviderSuggestion> providers,
			Set<String> skillRequiredProviders) {
		ProviderScope scope = new ProviderScope(null, null, scopeHint == null ? List.of() : scopeHint.objectIds(),
				scopeHint == null ? null : scopeHint.sourceRoute(), scopeHint == null ? null : scopeHint.filters());
		List<String> queryTerms = scope.explicitObjectIds().isEmpty()
				? AssistantQueryPlanner.plan(content, previousQueries) : List.of();
		List<ExecutionStep> steps = new ArrayList<>();
		for (ProviderSuggestion provider : providers) {
			String queryText = provider.query() == null || provider.query().isBlank() ? content
					: provider.query().trim();
			if (queryTerms.isEmpty()) {
				queryText = providerQuery(queryText, provider.providerCode(), queryTerms);
			}
			if (queryText.length() > 2000) {
				throw new IllegalArgumentException("PLANNER_PROVIDER_QUERY_TOO_LONG");
			}
			ProviderQuery query = new ProviderQuery(AssistantContextContract.VERSION, UUID.randomUUID().toString(),
					runId, identity.correlationId(), identity, queryText, scope,
					AssistantQueryPlanner.limitsFor(provider.providerCode()), queryTerms);
			steps.add(new ExecutionStep("provider-" + provider.providerCode().toLowerCase(Locale.ROOT),
					ExecutionStepType.MATRIX_PROVIDER, provider.providerCode(), null,
					skillRequiredProviders.contains(provider.providerCode())
							|| mode == SourceMode.CAP && broadAggregate(normalize(content)),
					List.of(), query, Map.of(), null));
		}
		return steps;
	}

	private void validateSkillSourceRequirements(SourceMode mode, List<ProviderId> cappedProviders,
			FrozenCapabilityCatalog catalog, AnalysisSkillSnapshot analysisSkill) {
		Set<String> available = catalog.providers()
			.stream()
			.map(ProviderCapabilitySnapshot::providerCode)
			.collect(java.util.stream.Collectors.toSet());
		if (!available.containsAll(analysisSkill.requiredProviders())) {
			throw new IllegalArgumentException("ANALYSIS_SKILL_REQUIRED_PROVIDER_NOT_AVAILABLE");
		}
		Set<String> cap = (cappedProviders == null ? List.<ProviderId>of() : cappedProviders).stream()
			.map(ProviderId::value)
			.collect(java.util.stream.Collectors.toSet());
		if (mode == SourceMode.CAP && !cap.containsAll(analysisSkill.requiredProviders())) {
			throw new IllegalArgumentException("ANALYSIS_SKILL_REQUIRED_PROVIDER_OUTSIDE_CAP");
		}
		if (mode == SourceMode.NONE && !analysisSkill.requiredProviders().isEmpty()) {
			throw new IllegalArgumentException("ANALYSIS_SKILL_REQUIRES_SOURCES");
		}
	}

	private ExecutionPlan finishPlan(String runId, SourceMode mode, PlanningDisposition disposition,
			List<ExecutionStep> steps, List<String> reasons, FrozenCapabilityCatalog catalog,
			AnalysisSkillSnapshot analysisSkill) {
		String analysisStepId = "analysis-final";
		List<String> dependencies = steps.stream().map(ExecutionStep::stepId).toList();
		steps.add(analysisStep(analysisStepId, dependencies, analysisSkill));
		ExecutionPlan proposed = new ExecutionPlan(AssistantExecutionContract.PLAN_VERSION,
				UUID.randomUUID().toString(), runId, mode, disposition, steps, analysisStepId,
				sanitizeReasonCodes(reasons), catalog.digest(), null, Instant.now());
		return validator.validateAndDigest(proposed, catalog);
	}

	private ExecutionStep analysisStep(String stepId, List<String> dependencies, AnalysisSkillSnapshot analysisSkill) {
		return new ExecutionStep(stepId, ExecutionStepType.MATRIX_ANALYSIS, analysisSkill.skillId(),
				analysisSkill.releaseId(), true, dependencies, null,
				Map.of("instruction", analysisSkill.instruction(), "instructionDigest",
						analysisSkill.instructionDigest(), "releaseVersion", analysisSkill.releaseVersion(),
						"packageSha256", analysisSkill.packageSha256(), "policy", analysisSkill.policy(),
						"policyDigest", analysisSkill.policyDigest(), "resultSchema", analysisSkill.resultSchema()),
				analysisSkill.resultSchemaId());
	}

	private List<String> sanitizeReasonCodes(List<String> reasons) {
		return (reasons == null ? List.<String>of() : reasons).stream()
			.filter(java.util.Objects::nonNull)
			.map(value -> value.trim().toUpperCase(Locale.ROOT))
			.filter(value -> value.matches("[A-Z0-9_:-]{3,64}"))
			.distinct()
			.limit(12)
			.toList();
	}

	private List<ProviderCapabilitySnapshot> selectProviders(String query, SourceMode mode, List<ProviderId> capped,
			List<ProviderCapabilitySnapshot> available) {
		if (mode == SourceMode.NONE) {
			return List.of();
		}
		Set<String> allowed = available.stream()
			.map(ProviderCapabilitySnapshot::providerCode)
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		String normalized = normalize(query);
		if (mode == SourceMode.CAP || mode == SourceMode.LEGACY) {
			Set<String> requested = (capped == null ? List.<ProviderId>of() : capped).stream()
				.map(ProviderId::value)
				.collect(java.util.stream.Collectors.toSet());
			return available.stream()
				.filter(item -> requested.contains(item.providerCode()))
				.filter(item -> broadAggregate(normalized) || scoreProvider(normalized, item) > 0)
				.toList();
		}
		if (broadAggregate(normalized)) {
			return available;
		}
		return available.stream()
			.filter(item -> scoreProvider(normalized, item) > 0)
			.filter(item -> allowed.contains(item.providerCode()))
			.toList();
	}

	private List<WorkflowCapabilitySnapshot> selectWorkflows(String query, List<WorkflowCapabilitySnapshot> available) {
		List<ScoredWorkflow> scored = available.stream()
			.filter(item -> workflowCompatible(query, item))
			.map(item -> new ScoredWorkflow(item, scoreWorkflow(query, item)))
			.filter(item -> item.score() > 0)
			.sorted(Comparator.comparingInt(ScoredWorkflow::score)
				.reversed()
				.thenComparing(item -> item.workflow().capabilityId()))
			.toList();
		if (scored.isEmpty()) {
			return List.of();
		}
		int limit = broadWorkflow(normalize(query)) ? Math.min(8, scored.size()) : 1;
		return scored.stream().limit(limit).map(ScoredWorkflow::workflow).toList();
	}

	private int scoreProvider(String query, ProviderCapabilitySnapshot provider) {
		int score = containsAny(query, PROVIDER_TERMS.getOrDefault(provider.providerCode(), List.of())) ? 8 : 0;
		return score + overlap(query, provider.displayName() + " " + provider.description());
	}

	private int scoreWorkflow(String query, WorkflowCapabilitySnapshot workflow) {
		String normalizedQuery = normalize(query);
		return directWorkflowNameScore(normalizedQuery, workflow) + 5 * overlap(normalizedQuery, workflow.displayName())
				+ 3 * overlap(normalizedQuery, workflow.description())
				+ 2 * overlap(normalizedQuery, String.join(" ", workflow.intentExamples()))
				+ overlap(normalizedQuery, String.join(" ", workflow.operationRules()));
	}

	private List<WorkflowSuggestion> refineWorkflowSuggestions(String query, List<WorkflowSuggestion> proposed,
			List<WorkflowCapabilitySnapshot> available) {
		Map<Long, WorkflowCapabilitySnapshot> byRelease = available.stream()
			.collect(java.util.stream.Collectors.toMap(WorkflowCapabilitySnapshot::releaseId, item -> item));
		List<WorkflowSuggestion> requested = proposed == null ? List.of() : proposed;
		for (WorkflowSuggestion item : requested) {
			WorkflowCapabilitySnapshot workflow = item == null || item.releaseId() == null ? null
					: byRelease.get(item.releaseId());
			if (workflow == null || !workflow.capabilityId().equals(item.capabilityId())) {
				throw new IllegalArgumentException("PLANNER_WORKFLOW_NOT_ALLOWED");
			}
		}
		List<WorkflowSuggestion> compatible = requested.stream()
			.filter(item -> item != null && item.releaseId() != null)
			.filter(item -> {
				WorkflowCapabilitySnapshot workflow = byRelease.get(item.releaseId());
				return workflow != null && workflowCompatible(query, workflow);
			})
			.toList();
		if (broadWorkflow(normalize(query))) {
			return compatible;
		}
		WorkflowCapabilitySnapshot preferred = selectWorkflows(query, available).stream().findFirst().orElse(null);
		if (preferred == null) {
			return compatible;
		}
		String normalizedQuery = normalize(query);
		int preferredDirectNameScore = directWorkflowNameScore(normalizedQuery, preferred);
		int currentDirectNameScore = compatible.stream()
			.map(item -> byRelease.get(item.releaseId()))
			.filter(java.util.Objects::nonNull)
			.mapToInt(item -> directWorkflowNameScore(normalizedQuery, item))
			.max()
			.orElse(0);
		int preferredNameScore = overlap(normalizedQuery, preferred.displayName() + " " + preferred.description());
		int currentNameScore = compatible.stream()
			.map(item -> byRelease.get(item.releaseId()))
			.filter(java.util.Objects::nonNull)
			.mapToInt(item -> overlap(normalizedQuery, item.displayName() + " " + item.description()))
			.max()
			.orElse(0);
		if (compatible.isEmpty() || preferredDirectNameScore > currentDirectNameScore
				|| preferredNameScore >= 4 && preferredNameScore > currentNameScore) {
			return List.of(new WorkflowSuggestion(preferred.capabilityId(), preferred.releaseId(), List.of()));
		}
		return compatible;
	}

	private PlanningSuggestion resolveNarrowWorkflowAmbiguity(String query, PlanningSuggestion suggestion,
			List<WorkflowCapabilitySnapshot> available) {
		if (suggestion.disposition() != PlanningDisposition.AMBIGUOUS || broadWorkflow(normalize(query))) {
			return suggestion;
		}
		WorkflowCapabilitySnapshot preferred = dominantNamedWorkflow(query, available);
		if (preferred == null) {
			return suggestion;
		}
		List<String> reasons = new ArrayList<>(suggestion.reasonCodes());
		reasons.add("DETERMINISTIC_MARKETPLACE_NAME_MATCH");
		return new PlanningSuggestion(PlanningDisposition.MATCH, suggestion.providers(),
				List.of(new WorkflowSuggestion(preferred.capabilityId(), preferred.releaseId(), List.of())), reasons,
				null);
	}

	private WorkflowCapabilitySnapshot dominantNamedWorkflow(String query, List<WorkflowCapabilitySnapshot> available) {
		String normalizedQuery = normalize(query);
		List<ScoredWorkflow> scored = available.stream()
			.filter(item -> workflowCompatible(query, item))
			.map(item -> new ScoredWorkflow(item, scoreWorkflow(query, item)))
			.filter(item -> item.score() > 0)
			.sorted(Comparator.comparingInt(ScoredWorkflow::score)
				.reversed()
				.thenComparing(item -> item.workflow().capabilityId()))
			.toList();
		if (scored.isEmpty()) {
			return null;
		}
		WorkflowCapabilitySnapshot top = scored.get(0).workflow();
		int topDirectScore = directWorkflowNameScore(normalizedQuery, top);
		int nextDirectScore = scored.stream()
			.skip(1)
			.mapToInt(item -> directWorkflowNameScore(normalizedQuery, item.workflow()))
			.max()
			.orElse(0);
		if (topDirectScore >= 100 && topDirectScore > nextDirectScore) {
			return top;
		}
		int topNameScore = businessObjectNameScore(normalizedQuery, top);
		int nextNameScore = scored.stream()
			.skip(1)
			.mapToInt(item -> businessObjectNameScore(normalizedQuery, item.workflow()))
			.max()
			.orElse(0);
		int nextOverallScore = scored.size() > 1 ? scored.get(1).score() : 0;
		if (topNameScore >= 2 && topNameScore > nextNameScore && scored.get(0).score() > nextOverallScore) {
			return top;
		}
		return sharedBusinessObjectAggregate(normalizedQuery, scored);
	}

	private int businessObjectNameScore(String normalizedQuery, WorkflowCapabilitySnapshot workflow) {
		return businessObjectNameTokens(normalizedQuery, workflow).stream().mapToInt(String::length).sum();
	}

	private Set<String> businessObjectNameTokens(String normalizedQuery, WorkflowCapabilitySnapshot workflow) {
		String coreName = WORKFLOW_NAME_SUFFIX.matcher(normalize(workflow.displayName())).replaceFirst("");
		return tokens(normalizedQuery).stream()
			.filter(token -> token.length() >= 2 && token.length() <= 4 && coreName.contains(token))
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
	}

	private WorkflowCapabilitySnapshot sharedBusinessObjectAggregate(String normalizedQuery,
			List<ScoredWorkflow> scored) {
		List<ScoredWorkflow> nameMatched = scored.stream()
			.filter(item -> !businessObjectNameTokens(normalizedQuery, item.workflow()).isEmpty())
			.toList();
		List<ScoredWorkflow> aggregates = nameMatched.stream()
			.filter(item -> containsAny(normalize(item.workflow().displayName()),
					List.of("全链路", "全流程", "全过程", "整体", "综合", "总览", "汇总")))
			.toList();
		if (aggregates.size() != 1) {
			return null;
		}
		WorkflowCapabilitySnapshot aggregate = aggregates.get(0).workflow();
		Set<String> aggregateTokens = businessObjectNameTokens(normalizedQuery, aggregate);
		boolean sameBusinessObject = nameMatched.stream()
			.filter(item -> item.workflow().releaseId() != aggregate.releaseId())
			.map(item -> businessObjectNameTokens(normalizedQuery, item.workflow()))
			.allMatch(tokens -> tokens.stream().anyMatch(aggregateTokens::contains));
		return sameBusinessObject ? aggregate : null;
	}

	private int directWorkflowNameScore(String normalizedQuery, WorkflowCapabilitySnapshot workflow) {
		String coreName = WORKFLOW_NAME_SUFFIX.matcher(normalize(workflow.displayName())).replaceFirst("");
		if (coreName.length() < 2 || !normalizedQuery.contains(coreName)) {
			return 0;
		}
		// A direct business-object match in the marketplace name outranks incidental
		// mentions inside an aggregate workflow's operation guide. The guide remains
		// routing evidence, but cannot hijack a narrow request merely because it lists
		// another agent as one of its downstream sources.
		return 100 + Math.min(coreName.length(), 16);
	}

	private boolean workflowCompatible(String query, WorkflowCapabilitySnapshot workflow) {
		String normalizedQuery = normalize(query);
		String evidence = normalize(workflow.description() + " " + String.join(" ", workflow.operationRules()));
		boolean currentQuestion = containsAny(normalizedQuery, List.of("今天", "今日", "当前", "现在", "实时", "最新"));
		boolean previousDayOnly = containsAny(evidence, List.of("仅查询昨天", "只查询昨天", "固定昨天", "仅查询昨日", "只查询昨日", "固定昨日"));
		return !currentQuestion || !previousDayOnly;
	}

	private boolean workflowRequired(String query, WorkflowCapabilitySnapshot workflow) {
		String normalized = normalize(query);
		String capabilityId = normalize(workflow.capabilityId());
		String displayName = normalize(workflow.displayName());
		if ((!capabilityId.isBlank() && normalized.contains(capabilityId))
				|| (!displayName.isBlank() && normalized.contains(displayName))) {
			return true;
		}
		return containsAny(normalized, List.of("生成", "执行", "运行", "调用", "启动", "导出"))
				&& containsAny(normalized, List.of("日报", "周报", "月报", "报告", "工作流", "智能体"));
	}

	private boolean providerSuggestionAllowed(String query, String providerCode, SourceMode mode) {
		return mode != SourceMode.AUTO || !Set.of("INFORMATION", "WORK").contains(providerCode)
				|| explicitlyRequestsProvider(query, providerCode) || broadAggregate(normalize(query));
	}

	private boolean explicitlyRequestsProvider(String query, String providerCode) {
		return containsAny(normalize(query), PROVIDER_TERMS.getOrDefault(providerCode, List.of()));
	}

	private String routingQuestion(String content) {
		String raw = content == null ? "" : content;
		Matcher marker = GENERATED_SOURCE_MARKER.matcher(raw);
		int lastMarkerEnd = -1;
		while (marker.find()) {
			lastMarkerEnd = marker.end();
		}
		String withoutMarkers = GENERATED_SOURCE_MARKER.matcher(raw).replaceAll(" ").trim();
		if (lastMarkerEnd >= 0 && lastMarkerEnd < raw.length()) {
			String trailing = GENERATED_SOURCE_MARKER.matcher(raw.substring(lastMarkerEnd)).replaceAll(" ").trim();
			if (trailing.length() >= 4) {
				return trailing;
			}
		}
		return withoutMarkers;
	}

	private int overlap(String normalizedQuery, String evidence) {
		Set<String> queryTokens = tokens(normalizedQuery);
		Set<String> evidenceTokens = tokens(normalize(evidence));
		int score = 0;
		for (String token : queryTokens) {
			if (evidenceTokens.contains(token) || evidenceTokens.stream().anyMatch(value -> value.contains(token))
					|| evidenceTokens.stream().anyMatch(token::contains)) {
				score += Math.min(token.length(), 8);
			}
		}
		return score;
	}

	private Set<String> tokens(String value) {
		Set<String> result = new LinkedHashSet<>();
		String normalized = value == null ? "" : value;
		Matcher matcher = SEMANTIC_TOKEN.matcher(normalized);
		while (matcher.find()) {
			String token = matcher.group().toLowerCase(Locale.ROOT);
			if (!GENERIC.contains(token)) {
				result.add(token);
			}
		}
		Matcher hanMatcher = HAN_RUN.matcher(normalized);
		while (hanMatcher.find()) {
			String run = hanMatcher.group();
			for (int width = 2; width <= Math.min(4, run.length()); width++) {
				for (int start = 0; start + width <= run.length(); start++) {
					String token = run.substring(start, start + width);
					if (!GENERIC.contains(token)) {
						result.add(token);
					}
				}
			}
		}
		return result;
	}

	private String providerQuery(String original, String providerCode, List<String> queryTerms) {
		if (queryTerms == null || queryTerms.isEmpty()) {
			return AssistantQueryPlanner.browseQuery(providerCode, original);
		}
		String domain = PROVIDER_TERMS.getOrDefault(providerCode, List.of()).stream().findFirst().orElse("");
		return String.join(" ", queryTerms) + (domain.isBlank() ? "" : " " + domain);
	}

	private boolean containsAny(String value, List<String> terms) {
		return terms.stream().anyMatch(value::contains);
	}

	private boolean broadAggregate(String query) {
		return containsAny(query, List.of("这些模块", "这些来源", "所选模块", "所有模块", "全部来源", "跨来源", "统一总结"));
	}

	private boolean broadWorkflow(String query) {
		return containsAny(query, List.of("全流程", "全过程", "整体", "综合", "各环节", "从原水", "端到端"));
	}

	private String normalize(String value) {
		return value == null ? "" : value.replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]", "").toLowerCase(Locale.ROOT);
	}

	private record ScoredWorkflow(WorkflowCapabilitySnapshot workflow, int score) {
	}

}
