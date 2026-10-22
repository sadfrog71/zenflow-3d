package com.deepdimen.matrix.assistant.orchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.PlanningDisposition;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ProviderCapabilitySnapshot;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.SourceMode;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.WorkflowCapabilitySnapshot;
import com.deepdimen.matrix.assistant.capability.AssistantAnalysisSkillCatalog.AnalysisSkillSnapshot;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;
import com.deepdimen.matrix.assistant.orchestrator.AssistantStructuredPlanningGateway.PlanningSuggestion;
import com.deepdimen.matrix.assistant.orchestrator.AssistantStructuredPlanningGateway.ProviderSuggestion;
import com.deepdimen.matrix.assistant.orchestrator.AssistantStructuredPlanningGateway.WorkflowSuggestion;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssistantSemanticPlannerTest {

	private static final IdentityEnvelope IDENTITY = new IdentityEnvelope("tenant", "user", "org", List.of("org"),
			List.of(), List.of(), "corr", "assistant");

	@Test
	void explicitNoSourceModePlansDirectChatWithoutTenantResources() {
		var planner = planner((runId, identity, question, sourceMode, cap, catalog, skill) -> {
			throw new AssertionError("Explicit no-source chat must not invoke the business planner");
		});

		var plan = planner.plan("run", IDENTITY, "用一句话介绍你能做什么", SourceMode.NONE, List.of(), null, List.of(),
				catalog(), skill());

		assertEquals(SourceMode.NONE, plan.sourceMode());
		assertEquals(PlanningDisposition.MATCH, plan.disposition());
		assertTrue(plan.steps().isEmpty());
		assertEquals(com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.GENERAL_QA,
				plan.routeDecision().conversationIntent());
		assertEquals(com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode.DIRECT_CHAT,
				plan.routeDecision().executionMode());
		assertTrue(plan.routeDecision().selectedProviderIds().isEmpty());
		assertTrue(plan.routeDecision().selectedWorkflowIds().isEmpty());
	}

	@Test
	void structuredPlannerCanSelectMultipleWorkflowsUsingFrozenOperationRules() {
		AtomicReference<FrozenCapabilityCatalog> seen = new AtomicReference<>();
		AssistantStructuredPlanningGateway gateway = (runId, identity, question, sourceMode, cap, catalog, skill) -> {
			seen.set(catalog);
			return Optional.of(new PlanningSuggestion(PlanningDisposition.MATCH,
					List.of(new ProviderSuggestion("KNOWLEDGE", "水质标准")),
					List.of(new WorkflowSuggestion("raw-water", 11L, List.of()),
							new WorkflowSuggestion("process-water", 12L, List.of()),
							new WorkflowSuggestion("outlet-water", 13L, List.of())),
					List.of("FULL_PROCESS_MATCH"), null));
		};
		AssistantSemanticPlanner planner = planner(gateway);

		var plan = planner.plan("run", IDENTITY, "总结全流程水质情况", SourceMode.AUTO, List.of(), null, List.of(), catalog(),
				skill());

		assertEquals(3, plan.steps().stream().filter(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW).count());
		assertTrue(plan.steps()
			.stream()
			.filter(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW)
			.allMatch(step -> step.targetId().equals(step.inputProjection().get("displayName"))));
		assertEquals(1, plan.steps().stream().filter(step -> step.type() == ExecutionStepType.MATRIX_PROVIDER).count());
		assertTrue(seen.get().workflows().get(0).operationRules().contains("用于原水水质分析"));
		assertTrue(plan.planDigest().matches("[0-9a-f]{64}"));
	}

	@Test
	void currentWaterSourceQuestionPrefersDedicatedWorkflowAndKeepsItOptional() {
		AssistantStructuredPlanningGateway gateway = (runId, identity, question, sourceMode, cap, catalog,
				skill) -> Optional.of(new PlanningSuggestion(PlanningDisposition.MATCH, List.of(),
						List.of(new WorkflowSuggestion("daily-report", 21L, List.of())), List.of(), null));

		var plan = planner(gateway).plan("run", IDENTITY, "今天的水源水质如何", SourceMode.AUTO, List.of(), null, List.of(),
				routingCatalog(), skill());

		var workflow = plan.steps()
			.stream()
			.filter(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW)
			.findFirst()
			.orElseThrow();
		assertEquals("water-source-quality-analysis", workflow.targetId());
		assertEquals("今天的水源水质如何", workflow.inputProjection().get("query"));
		assertTrue(!workflow.required());
	}

	@Test
	void explicitReportGenerationKeepsWorkflowRequired() {
		AssistantStructuredPlanningGateway gateway = (runId, identity, question, sourceMode, cap, catalog,
				skill) -> Optional.of(new PlanningSuggestion(PlanningDisposition.MATCH, List.of(),
						List.of(new WorkflowSuggestion("water-source-quality-analysis", 22L, List.of())), List.of(),
						null));

		var plan = planner(gateway).plan("run", IDENTITY, "生成昨天的水源水质日报", SourceMode.AUTO, List.of(), null, List.of(),
				routingCatalog(), skill());

		var workflow = plan.steps()
			.stream()
			.filter(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW)
			.findFirst()
			.orElseThrow();
		assertEquals("water-source-quality-analysis", workflow.targetId());
		assertTrue(workflow.required());
	}

	@Test
	void generatedWorkflowMarkerCannotHijackFollowUpRouting() {
		AtomicReference<String> routedQuestion = new AtomicReference<>();
		AssistantStructuredPlanningGateway gateway = (runId, identity, question, sourceMode, cap, catalog, skill) -> {
			routedQuestion.set(question);
			return Optional.of(new PlanningSuggestion(PlanningDisposition.MATCH,
					List.of(new ProviderSuggestion("INFORMATION", "旧回答")), List.of(), List.of(), null));
		};

		var plan = planner(gateway).plan("run", IDENTITY, "可能关联：旧的水质结论。[工作流:three-minute-daily-report]\n这个知识库里应该有类似数据",
				SourceMode.AUTO, List.of(), null, List.of(), routingCatalog(), skill());

		assertEquals("这个知识库里应该有类似数据", routedQuestion.get());
		var providers = plan.steps().stream().filter(step -> step.type() == ExecutionStepType.MATRIX_PROVIDER).toList();
		assertEquals(1, providers.size());
		assertEquals("KNOWLEDGE", providers.get(0).targetId());
		assertEquals("这个知识库里应该有类似数据", providers.get(0).providerQuery().query());
	}

	@Test
	void foreignWorkflowSuggestionFallsBackToBoundedCatalogMatcher() {
		AssistantStructuredPlanningGateway gateway = (runId, identity, question, sourceMode, cap, catalog,
				skill) -> Optional.of(new PlanningSuggestion(PlanningDisposition.MATCH, List.of(),
						List.of(new WorkflowSuggestion("foreign", 999L, List.of())), List.of(), null));

		var plan = planner(gateway).plan("run", IDENTITY, "分析水质", SourceMode.AUTO, List.of(), null, List.of(),
				catalog(), skill());

		assertEquals(1, plan.steps().stream().filter(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW).count());
		assertEquals(List.of("KNOWLEDGE"),
				plan.steps()
					.stream()
					.filter(step -> step.type() == ExecutionStepType.MATRIX_PROVIDER)
					.map(step -> step.targetId())
					.toList());
		assertTrue(plan.reasonCodes().contains("PLANNER_OUTPUT_REJECTED"));
		assertTrue(plan.reasonCodes().contains("CATALOG_HEURISTIC_FALLBACK"));
	}

	@Test
	void unknownWorkflowDependencyFallsBackToBoundedCatalogMatcher() {
		AssistantStructuredPlanningGateway gateway = (runId, identity, question, sourceMode, cap, catalog,
				skill) -> Optional.of(new PlanningSuggestion(PlanningDisposition.MATCH, List.of(),
						List.of(new WorkflowSuggestion("raw-water", 11L, List.of("missing-workflow"))), List.of(),
						null));

		var plan = planner(gateway).plan("run", IDENTITY, "分析原水水质", SourceMode.AUTO, List.of(), null, List.of(),
				catalog(), skill());

		assertEquals(1, plan.steps().stream().filter(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW).count());
		assertTrue(plan.reasonCodes().contains("PLANNER_OUTPUT_REJECTED"));
		assertTrue(plan.reasonCodes().contains("CATALOG_HEURISTIC_FALLBACK"));
	}

	@Test
	void customerServiceOrderDoesNotSelectFrameworkWorkTasks() {
		AssistantStructuredPlanningGateway gateway = (runId, identity, question, sourceMode, cap, catalog,
				skill) -> Optional.of(new PlanningSuggestion(PlanningDisposition.MATCH,
						List.of(new ProviderSuggestion("WORK", question)), List.of(), List.of(), null));
		List<WorkflowCapabilitySnapshot> workflows = List
			.of(workflow("customer-service", 31L, "按统一有效工单口径查询客服工单，支持水质投诉、分类统计和趋势分析"));
		FrozenCapabilityCatalog customerCatalog = new FrozenCapabilityCatalog(catalog().providers(), workflows,
				"customer-catalog", Instant.EPOCH);

		var plan = planner(gateway).plan("run", IDENTITY, "今天的客服工单有水质投诉吗", SourceMode.AUTO, List.of(), null, List.of(),
				customerCatalog, skill());

		assertTrue(plan.steps()
			.stream()
			.filter(step -> step.type() == ExecutionStepType.MATRIX_PROVIDER)
			.noneMatch(step -> "WORK".equals(step.targetId())));
		assertEquals("customer-service",
				plan.steps()
					.stream()
					.filter(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW)
					.findFirst()
					.orElseThrow()
					.targetId());
	}

	@Test
	void narrowCustomerQuestionOverridesAggregateWorkflowThatOnlyMentionsCustomerAsADownstreamSource() {
		WorkflowCapabilitySnapshot customer = new WorkflowCapabilitySnapshot("market-agent-customer", 31L, "market",
				"客服智能体", "按统一有效工单口径查询客服工单", List.of("查询本月水质类投诉的区域分布"), List.of("支持工单类别统计、趋势分析和报告生成"), "input", "output",
				"app-customer", "release-customer", List.of());
		WorkflowCapabilitySnapshot aggregate = new WorkflowCapabilitySnapshot("market-agent-daily", 32L, "market",
				"三分钟日报", "五域调度日报", List.of("生成昨天五域日报"), List.of("汇总水源、生产水质、生产能耗、二供和客服五个领域", "汇总昨天客服工单的重点情况"), "input",
				"output", "app-daily", "release-daily", List.of());
		FrozenCapabilityCatalog customerCatalog = new FrozenCapabilityCatalog(catalog().providers(),
				List.of(customer, aggregate), "customer-catalog", Instant.EPOCH);
		AssistantStructuredPlanningGateway gateway = (runId, identity, question, sourceMode, cap, catalog,
				skill) -> Optional.of(new PlanningSuggestion(PlanningDisposition.MATCH, List.of(),
						List.of(new WorkflowSuggestion("market-agent-daily", 32L, List.of())),
						List.of("CUSTOMER_SERVICE_TICKET_DOMAIN"), null));

		var plan = planner(gateway).plan("run", IDENTITY, "今天的客服工单有水质投诉吗", SourceMode.AUTO, List.of(), null, List.of(),
				customerCatalog, skill());

		var selected = plan.steps().stream().filter(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW).toList();
		assertEquals(1, selected.size());
		assertEquals("market-agent-customer", selected.get(0).targetId());
	}

	@Test
	void capAggregateQuestionForcesEveryCappedProviderAndMarksThemRequired() {
		AssistantStructuredPlanningGateway gateway = (runId, identity, question, sourceMode, cap, catalog,
				skill) -> Optional
					.of(new PlanningSuggestion(PlanningDisposition.MATCH, List.of(), List.of(), List.of(), null));

		var plan = planner(gateway).plan("run", IDENTITY, "总结这些模块的内容", SourceMode.CAP,
				List.of(ProviderId.KNOWLEDGE, ProviderId.WORK), null, List.of(), catalog(), skill());

		var providers = plan.steps().stream().filter(step -> step.type() == ExecutionStepType.MATRIX_PROVIDER).toList();
		assertEquals(2, providers.size());
		assertTrue(providers.stream().allMatch(step -> step.required()));
		assertEquals("总结这些模块的内容",
				providers.stream()
					.filter(step -> "KNOWLEDGE".equals(step.targetId()))
					.findFirst()
					.orElseThrow()
					.providerQuery()
					.query());
		assertEquals(10000,
				providers.stream()
					.filter(step -> "KNOWLEDGE".equals(step.targetId()))
					.findFirst()
					.orElseThrow()
					.providerQuery()
					.limits()
					.timeoutMs());
		assertEquals("",
				providers.stream()
					.filter(step -> "WORK".equals(step.targetId()))
					.findFirst()
					.orElseThrow()
					.providerQuery()
					.query());
		assertEquals(3000,
				providers.stream()
					.filter(step -> "WORK".equals(step.targetId()))
					.findFirst()
					.orElseThrow()
					.providerQuery()
					.limits()
					.timeoutMs());
	}

	@Test
	void ambiguousPlanNeverSchedulesProvidersOrWorkflows() {
		AssistantStructuredPlanningGateway gateway = (runId, identity, question, sourceMode, cap, catalog,
				skill) -> Optional.of(new PlanningSuggestion(PlanningDisposition.AMBIGUOUS, List.of(), List.of(),
						List.of("MISSING_BUSINESS_OBJECT"), "请补充业务对象"));

		var plan = planner(gateway).plan("run", IDENTITY, "分析情况", SourceMode.AUTO, List.of(), null, List.of(),
				catalog(), skill());

		assertEquals(PlanningDisposition.AMBIGUOUS, plan.disposition());
		assertEquals(1, plan.steps().size());
		assertEquals(ExecutionStepType.MATRIX_ANALYSIS, plan.steps().get(0).type());
		assertTrue(plan.reasonCodes().contains("MISSING_BUSINESS_OBJECT"));
	}

	@Test
	void genericWaterQualityQuestionResolvesModelAmbiguityThroughSingleFullChainWorkflow() {
		WorkflowCapabilitySnapshot water = new WorkflowCapabilitySnapshot("market-agent-water", 41L, "market",
				"水源水质监测智能体", "查询当前水源水质", List.of("水质如何"), List.of("分析原水监测指标"), "input", "output", "app-water",
				"release-water", List.of());
		WorkflowCapabilitySnapshot fullChain = new WorkflowCapabilitySnapshot("market-agent-full-chain", 44L, "market",
				"全链路水质巡检", "汇总各环节水质", List.of("水质如何"), List.of("调用水源、生产和管网水质能力"), "input", "output", "app-full-chain",
				"release-full-chain", List.of());
		WorkflowCapabilitySnapshot legal = new WorkflowCapabilitySnapshot("market-agent-legal", 42L, "market", "法律法规助手",
				"查询供水和水质法规", List.of("水质管理依据哪些法规"), List.of("仅用于法规检索"), "input", "output", "app-legal", "release-legal",
				List.of());
		WorkflowCapabilitySnapshot emergency = new WorkflowCapabilitySnapshot("market-agent-emergency", 43L, "market",
				"应急处理预案助手", "查询水质突发事件预案", List.of("水质异常后如何应急处置"), List.of("仅用于突发事件处置流程"), "input", "output",
				"app-emergency", "release-emergency", List.of());
		FrozenCapabilityCatalog waterCatalog = new FrozenCapabilityCatalog(catalog().providers(),
				List.of(legal, emergency, fullChain, water), "water-catalog", Instant.EPOCH);
		AssistantStructuredPlanningGateway gateway = (runId, identity, question, sourceMode, cap, catalog,
				skill) -> Optional.of(new PlanningSuggestion(PlanningDisposition.AMBIGUOUS, List.of(), List.of(),
						List.of("QUESTION_AMBIGUOUS"), "请补充水质对象"));

		var plan = planner(gateway).plan("run", IDENTITY, "水质如何", SourceMode.AUTO, List.of(), null, List.of(),
				waterCatalog, skill());

		assertEquals(PlanningDisposition.MATCH, plan.disposition());
		var workflows = plan.steps().stream().filter(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW).toList();
		assertEquals(1, workflows.size());
		assertEquals("market-agent-full-chain", workflows.get(0).targetId());
		assertTrue(plan.reasonCodes().contains("DETERMINISTIC_MARKETPLACE_NAME_MATCH"));
	}

	@Test
	void conflictingBusinessObjectNamesKeepModelAmbiguity() {
		WorkflowCapabilitySnapshot water = new WorkflowCapabilitySnapshot("market-agent-water", 41L, "market",
				"水源水质监测智能体", "查询当前水源水质", List.of("水质如何"), List.of("分析原水监测指标"), "input", "output", "app-water",
				"release-water", List.of());
		WorkflowCapabilitySnapshot complaint = new WorkflowCapabilitySnapshot("market-agent-complaint", 42L, "market",
				"水质投诉智能体", "查询客服水质投诉", List.of("今天有哪些水质投诉"), List.of("仅用于投诉工单分析"), "input", "output", "app-complaint",
				"release-complaint", List.of());
		FrozenCapabilityCatalog waterCatalog = new FrozenCapabilityCatalog(catalog().providers(),
				List.of(complaint, water), "water-catalog", Instant.EPOCH);
		AssistantStructuredPlanningGateway gateway = (runId, identity, question, sourceMode, cap, catalog,
				skill) -> Optional.of(new PlanningSuggestion(PlanningDisposition.AMBIGUOUS, List.of(), List.of(),
						List.of("QUESTION_AMBIGUOUS"), "请补充查询目的"));

		var plan = planner(gateway).plan("run", IDENTITY, "水质情况", SourceMode.AUTO, List.of(), null, List.of(),
				waterCatalog, skill());

		assertEquals(PlanningDisposition.AMBIGUOUS, plan.disposition());
		assertEquals(1, plan.steps().size());
		assertEquals(ExecutionStepType.MATRIX_ANALYSIS, plan.steps().get(0).type());
	}

	@Test
	void explicitCapCannotBeExpandedByAnalysisSkillRequirements() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> planner(null).plan("run", IDENTITY, "总结知识", SourceMode.CAP, List.of(ProviderId.KNOWLEDGE), null,
						List.of(), catalog(), skill(Set.of("WORK"))));

		assertEquals("ANALYSIS_SKILL_REQUIRED_PROVIDER_OUTSIDE_CAP", failure.getMessage());
	}

	@Test
	void validatorRetainsRoutingInDigestAndRejectsUnauthorizedRouteTargets() {
		var catalog = catalog();
		var original = planner(null).plan("route-test", IDENTITY, "查询工作任务", SourceMode.AUTO, List.of(), null, List.of(),
				catalog, skill());
		var route = new com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RouteDecision(
				com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.BUSINESS_QUERY,
				com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode.PROVIDER_QUERY,
				List.of("WORK"), List.of(),
				com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RouteTriggerType.PLANNER, 0.9, 0.3, true,
				false, "MATCH");
		var routed = new com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionPlan(
				original.contractVersion(), original.planId(), original.runId(), original.sourceMode(),
				original.disposition(), original.steps(), original.analysisStepId(), original.reasonCodes(),
				original.catalogDigest(), null, original.createdAt(), route);
		var validator = new AssistantExecutionPlanValidator(new ObjectMapper());
		var validated = validator.validateAndDigest(routed, catalog);
		assertEquals(route, validated.routeDecision());
		org.junit.jupiter.api.Assertions.assertNotEquals(original.planDigest(), validated.planDigest());
		var forbiddenCatalog = new FrozenCapabilityCatalog(List.of(), catalog.workflows(), catalog.digest(),
				catalog.frozenAt());
		assertEquals("EXECUTION_PLAN_INVALID:ROUTE_TARGET_NOT_AUTHORIZED", assertThrows(IllegalArgumentException.class,
				() -> validator.validateAndDigest(routed, forbiddenCatalog))
			.getMessage());
	}

	@Test
	void exactInvocationUsesValidatedWorkflowPlanWithoutModelPlanning() throws Exception {
		var planner = planner((runId, identity, question, mode, cap, catalog, skill) -> {
			throw new AssertionError("Explicit invocation must not call planning model");
		});
		for (String flag : List.of("fastPathEnabled", "candidateRetrievalEnabled")) {
			var field = AssistantSemanticPlanner.class.getDeclaredField(flag);
			field.setAccessible(true);
			field.setBoolean(planner, true);
		}
		var result = planner.plan("explicit", IDENTITY, "用水源水质监测智能体查今天的水质", SourceMode.AUTO, List.of(), null, List.of(),
				routingCatalog(), skill());
		assertEquals(List.of("water-source-quality-analysis"), result.routeDecision().selectedWorkflowIds());
		assertEquals(1, result.steps().stream().filter(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW).count());
		assertTrue(!result.routeDecision().plannerUsed());
		assertTrue(result.steps()
			.stream()
			.filter(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW)
			.allMatch(step -> step.required()));
	}

	@Test
	void generalKnowledgeUsesSamePlanningCallAndSkipsBusinessExecution() throws Exception {
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var planner = planner((id, identity, question, mode, cap, catalog, skill) -> {
			calls.incrementAndGet();
			return Optional.of(dialogue(0.95, 0.4));
		});
		enableSemantic(planner);
		var result = planner.plan("general", IDENTITY, "为什么天空是蓝色的", SourceMode.AUTO, List.of(), null, List.of(),
				catalog(), skill());
		assertEquals(1, calls.get());
		assertTrue(result.steps().isEmpty());
		assertEquals(com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.GENERAL_QA,
				result.routeDecision().conversationIntent());
		assertTrue(result.routeDecision().plannerUsed());
	}

	@Test
	void uncertainOrExplicitlyScopedDialogueDoesNotFallThroughToWorkflowExecution() throws Exception {
		for (double confidence : List.of(0.4, Double.NaN, 1.1)) {
			var planner = planner(
					(id, identity, question, mode, cap, catalog, skill) -> Optional.of(dialogue(confidence, 0.4)));
			enableSemantic(planner);
			var result = planner.plan("uncertain", IDENTITY, "原水的颜色是什么", SourceMode.AUTO, List.of(), null, List.of(),
					catalog(), skill());
			assertEquals(PlanningDisposition.AMBIGUOUS, result.disposition());
			assertTrue(result.steps().stream().noneMatch(step -> step.type() != ExecutionStepType.MATRIX_ANALYSIS));
		}
		var planner = planner((id, identity, question, mode, cap, catalog, skill) -> Optional.of(dialogue(0.95, 0.4)));
		enableSemantic(planner);
		var scope = new com.deepdimen.matrix.assistant.api.AssistantSessionContract.ScopeHint(null, "asset",
				List.of("asset-1"), Map.of());
		var scoped = planner.plan("scoped", IDENTITY, "说明这个对象", SourceMode.AUTO, List.of(), scope, List.of(), catalog(),
				skill());
		assertEquals(PlanningDisposition.AMBIGUOUS, scoped.disposition());
		assertTrue(scoped.steps().stream().noneMatch(step -> step.type() != ExecutionStepType.MATRIX_ANALYSIS));
	}

	@Test
	void modelCapabilityDescriptionChecksReleaseAndNeverExecutesDescribedWorkflow() throws Exception {
		for (long release : List.of(11L, 999L)) {
			var suggestion = new PlanningSuggestion(PlanningDisposition.MATCH, List.of(),
					List.of(new WorkflowSuggestion("raw-water", release, List.of())), List.of(), null,
					new AssistantStructuredPlanningGateway.RoutingSuggestion(
							com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.CAPABILITY_HELP,
							com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode.CAPABILITY_HELP,
							0.96, 0.4));
			var planner = planner((id, identity, question, mode, cap, catalog, skill) -> Optional.of(suggestion));
			enableSemantic(planner);
			var result = planner.plan("help", IDENTITY, "这个原水助手适合什么场景", SourceMode.AUTO, List.of(), null, List.of(),
					catalog(), skill());
			assertTrue(result.steps().stream().noneMatch(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW));
			if (release == 11L)
				assertEquals(List.of("raw-water"), result.routeDecision().selectedWorkflowIds());
			else
				assertEquals(PlanningDisposition.AMBIGUOUS, result.disposition());
		}
	}

	@Test
	void oldPlanningJsonAndDisabledFlagKeepLegacyBehavior() throws Exception {
		var old = new ObjectMapper().readValue(
				"{\"disposition\":\"MATCH\",\"providers\":[],\"workflows\":[],\"reasonCodes\":[]}",
				PlanningSuggestion.class);
		org.junit.jupiter.api.Assertions.assertNull(old.routing());
		var planner = planner((id, identity, question, mode, cap, catalog, skill) -> Optional.of(dialogue(0.99, 0.5)));
		var result = planner.plan("old", IDENTITY, "为什么天空是蓝色的", SourceMode.AUTO, List.of(), null, List.of(), catalog(),
				skill());
		org.junit.jupiter.api.Assertions.assertNull(result.routeDecision());
		assertTrue(result.steps().stream().anyMatch(step -> step.type() == ExecutionStepType.MATRIX_ANALYSIS));
	}

	@Test
	void closeCandidatesAndInconsistentIntentCannotPromoteBusinessExecution() throws Exception {
		var badPair = new PlanningSuggestion(PlanningDisposition.MATCH, List.of(),
				List.of(new WorkflowSuggestion("raw-water", 11L, List.of())), List.of(), null,
				new AssistantStructuredPlanningGateway.RoutingSuggestion(
						com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.GENERAL_QA,
						com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode.WORKFLOW_EXECUTION,
						0.99, 0.9));
		for (var suggestion : List.of(dialogue(0.99, 0.05), badPair)) {
			var planner = planner((id, identity, question, mode, cap, catalog, skill) -> Optional.of(suggestion));
			enableSemantic(planner);
			var result = planner.plan("uncertain", IDENTITY, "解释原水的颜色", SourceMode.AUTO, List.of(), null, List.of(),
					catalog(), skill());
			assertEquals(PlanningDisposition.AMBIGUOUS, result.disposition());
			assertTrue(result.steps().stream().noneMatch(step -> step.type() != ExecutionStepType.MATRIX_ANALYSIS));
		}
	}

	@Test
	void businessSuggestionsRequireConfidenceAndMarginBeforeAnyExecutableStep() throws Exception {
		for (Double[] scores : List.of(new Double[] { 0.4, 0.9 }, new Double[] { 0.99, 0.05 },
				new Double[] { Double.NaN, 0.9 }, new Double[] { 0.99, null }, new Double[] { null, 0.9 })) {
			var planner = planner((id, identity, question, mode, cap, catalog, skill) -> Optional
				.of(businessSuggestion(scores[0], scores[1], PlanningDisposition.MATCH)));
			enableSemantic(planner);
			var result = planner.plan("uncertain-business", IDENTITY, "分析原水水质", SourceMode.AUTO, List.of(), null,
					List.of(), catalog(), skill());
			assertEquals(PlanningDisposition.AMBIGUOUS, result.disposition());
			assertTrue(result.steps().stream().allMatch(step -> step.type() == ExecutionStepType.MATRIX_ANALYSIS));
			assertTrue(result.reasonCodes().contains("SEMANTIC_ROUTE_UNCERTAIN"));
		}
	}

	@Test
	void ambiguousBusinessCannotBePromotedByNarrowWorkflowHeuristic() throws Exception {
		var planner = planner((id, identity, question, mode, cap, catalog, skill) -> Optional
			.of(businessSuggestion(0.99, 0.9, PlanningDisposition.AMBIGUOUS)));
		enableSemantic(planner);
		var result = planner.plan("ambiguous", IDENTITY, "分析原水水质", SourceMode.AUTO, List.of(), null, List.of(),
				catalog(), skill());
		assertEquals(PlanningDisposition.AMBIGUOUS, result.disposition());
		assertTrue(result.steps().stream().allMatch(step -> step.type() == ExecutionStepType.MATRIX_ANALYSIS));
	}

	@Test
	void confidentBusinessStillUsesAuthorizedWorkflow() throws Exception {
		var planner = planner((id, identity, question, mode, cap, catalog, skill) -> Optional
			.of(businessSuggestion(0.85, 0.15, PlanningDisposition.MATCH)));
		enableSemantic(planner);
		var result = planner.plan("confident", IDENTITY, "分析原水水质", SourceMode.AUTO, List.of(), null, List.of(),
				catalog(), skill());
		assertEquals(PlanningDisposition.MATCH, result.disposition());
		assertTrue(result.steps()
			.stream()
			.anyMatch(step -> step.type() == ExecutionStepType.DIFY_WORKFLOW && step.targetId().equals("raw-water")));
	}

	@Test
	void nonExecutingAndInconsistentBusinessMetadataNeverSchedulesResources() throws Exception {
		var intents = List.of(
				com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.BUSINESS_QUERY,
				com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.AMBIGUOUS,
				com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.ACTION_REQUEST);
		for (var intent : intents) {
			var suggestion = new PlanningSuggestion(PlanningDisposition.MATCH, List.of(),
					List.of(new WorkflowSuggestion("raw-water", 11L, List.of())), List.of(), null,
					new AssistantStructuredPlanningGateway.RoutingSuggestion(intent,
							com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode.DIRECT_CHAT,
							0.99, 0.9));
			var planner = planner((id, identity, question, mode, cap, catalog, skill) -> Optional.of(suggestion));
			enableSemantic(planner);
			var result = planner.plan("inconsistent", IDENTITY, "分析原水水质", SourceMode.AUTO, List.of(), null, List.of(),
					catalog(), skill());
			assertEquals(PlanningDisposition.AMBIGUOUS, result.disposition());
			assertTrue(result.steps().stream().allMatch(step -> step.type() == ExecutionStepType.MATRIX_ANALYSIS));
		}
	}

	@Test
	void clarificationUsesOneContextAwarePlanningCallAndValidatesNewProviderPlan() throws Exception {
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var captured = new AtomicReference<AssistantStructuredPlanningGateway.ClarificationContext>();
		AssistantStructuredPlanningGateway gateway = new AssistantStructuredPlanningGateway() {
			public Optional<PlanningSuggestion> suggest(String id, IdentityEnvelope identity, String question,
					SourceMode mode, List<String> cap, FrozenCapabilityCatalog catalog, AnalysisSkillSnapshot skill) {
				throw new AssertionError("Context must not be discarded");
			}

			public Optional<PlanningSuggestion> suggest(String id, IdentityEnvelope identity, String question,
					SourceMode mode, List<String> cap, FrozenCapabilityCatalog catalog, AnalysisSkillSnapshot skill,
					ClarificationContext context) {
				calls.incrementAndGet();
				captured.set(context);
				assertEquals("查询知识库中今天的资料", question);
				return Optional.of(new PlanningSuggestion(PlanningDisposition.MATCH,
						List.of(new ProviderSuggestion("KNOWLEDGE", "查询知识库中今天的资料")), List.of(), List.of(), null,
						new RoutingSuggestion(
								com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.BUSINESS_QUERY,
								com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode.PROVIDER_QUERY,
								0.99, 0.9)));
			}
		};
		var planner = planner(gateway);
		enableSemantic(planner);
		var context = clarificationContext();
		var result = planner.plan("followup", IDENTITY, "查询知识库中今天的资料", SourceMode.AUTO, List.of(), null, List.of(),
				catalog(), skill(), context);
		assertEquals(1, calls.get());
		assertEquals(context, captured.get());
		assertTrue(result.steps()
			.stream()
			.filter(step -> step.type() == ExecutionStepType.MATRIX_PROVIDER)
			.flatMap(step -> step.providerQuery().queryTerms().stream())
			.noneMatch(term -> term.contains("昨天") || term.contains("原始请求")));
		assertEquals(PlanningDisposition.MATCH, result.disposition());
		assertTrue(result.steps()
			.stream()
			.anyMatch(step -> step.type() == ExecutionStepType.MATRIX_PROVIDER && step.targetId().equals("KNOWLEDGE")
					&& step.providerQuery().query().contains("今天")));
	}

	@Test
	void legacyGatewayCannotSilentlyDropClarificationAndHeuristicallyExecute() throws Exception {
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var planner = planner((id, identity, question, mode, cap, catalog, skill) -> {
			calls.incrementAndGet();
			return Optional.of(businessSuggestion(0.99, 0.9, PlanningDisposition.MATCH));
		});
		enableSemantic(planner);
		var result = planner.plan("followup", IDENTITY, "查询知识库", SourceMode.AUTO, List.of(), null, List.of(), catalog(),
				skill(), clarificationContext());
		assertEquals(0, calls.get());
		assertEquals(PlanningDisposition.AMBIGUOUS, result.disposition());
		assertTrue(result.steps().stream().allMatch(step -> step.type() == ExecutionStepType.MATRIX_ANALYSIS));
	}

	private AssistantStructuredPlanningGateway.ClarificationContext clarificationContext() {
		return new AssistantStructuredPlanningGateway.ClarificationContext("origin", "查询知识库中昨天的资料", "需要哪类资料？",
				List.of(new com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClarificationCandidate(
						ExecutionStepType.MATRIX_PROVIDER, "KNOWLEDGE", null, "知识库")));
	}

	@Test
	void manualSelectionPinsWorkflowAndRequiredProvidersWithoutModelOrSemanticSubstitution() {
		var planner = planner((id, identity, question, mode, cap, catalog, skill) -> {
			throw new AssertionError("Manual choice must not invoke planner");
		});
		var choice = new com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClarificationCandidate(
				ExecutionStepType.DIFY_WORKFLOW, "raw-water", 11L, "raw-water");
		var result = planner.planSelected("manual", IDENTITY, "总结全流程水质情况", null, catalog(), skill(), choice);
		assertEquals(List.of("raw-water"), result.routeDecision().selectedWorkflowIds());
		assertEquals(List.of("KNOWLEDGE"), result.routeDecision().selectedProviderIds());
		assertEquals(1, result.steps().stream().filter(s -> s.type() == ExecutionStepType.DIFY_WORKFLOW).count());
		assertTrue(result.steps()
			.stream()
			.filter(s -> s.type() != ExecutionStepType.MATRIX_ANALYSIS)
			.allMatch(s -> s.required()));
		assertTrue(!result.routeDecision().plannerUsed());
	}

	@Test
	void manualProviderChoiceDoesNotRequireProviderNameInQuestionAndCannotExpandToOtherResources() {
		var result = planner(null).planSelected("manual", IDENTITY, "今天有哪些逾期事项", null, catalog(), skill(),
				new com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClarificationCandidate(
						ExecutionStepType.MATRIX_PROVIDER, "WORK", null, "工作任务"));
		assertEquals(List.of("WORK"), result.routeDecision().selectedProviderIds());
		assertTrue(result.routeDecision().selectedWorkflowIds().isEmpty());
		assertEquals(2, result.steps().size());
	}

	@Test
	void manualChoiceRejectsStaleVersionAndIncompatibleTimeRatherThanSelectingReplacement() {
		var planner = planner(null);
		assertThrows(IllegalArgumentException.class,
				() -> planner.planSelected("manual", IDENTITY, "原水水质", null, catalog(), skill(),
						new com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClarificationCandidate(
								ExecutionStepType.DIFY_WORKFLOW, "raw-water", 999L, "raw-water")));
		assertThrows(IllegalArgumentException.class,
				() -> planner.planSelected("manual", IDENTITY, "今天水源水质", null, routingCatalog(), skill(),
						new com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClarificationCandidate(
								ExecutionStepType.DIFY_WORKFLOW, "daily-report", 21L, "三分钟日报")));
		assertThrows(IllegalArgumentException.class,
				() -> planner.planSelected("manual", IDENTITY, "今天资料", null, catalog(), skill(Set.of("MISSING")),
						new com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClarificationCandidate(
								ExecutionStepType.MATRIX_PROVIDER, "KNOWLEDGE", null, "知识库")));
	}

	private PlanningSuggestion businessSuggestion(Double confidence, Double margin, PlanningDisposition disposition) {
		return new PlanningSuggestion(disposition, List.of(),
				List.of(new WorkflowSuggestion("raw-water", 11L, List.of())), List.of(), null,
				new AssistantStructuredPlanningGateway.RoutingSuggestion(
						com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.BUSINESS_QUERY,
						com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode.WORKFLOW_EXECUTION,
						confidence, margin));
	}

	private PlanningSuggestion dialogue(double confidence, double margin) {
		return new PlanningSuggestion(PlanningDisposition.MATCH, List.of(), List.of(), List.of(), null,
				new AssistantStructuredPlanningGateway.RoutingSuggestion(
						com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.GENERAL_QA,
						com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode.DIRECT_CHAT,
						confidence, margin));
	}

	private void enableSemantic(AssistantSemanticPlanner planner) throws Exception {
		for (String name : List.of("candidateRetrievalEnabled", "semanticRoutingEnabled")) {
			var field = AssistantSemanticPlanner.class.getDeclaredField(name);
			field.setAccessible(true);
			field.setBoolean(planner, true);
		}
	}

	private AssistantSemanticPlanner planner(AssistantStructuredPlanningGateway gateway) {
		return new AssistantSemanticPlanner(new AssistantExecutionPlanValidator(new ObjectMapper()), gateway);
	}

	private FrozenCapabilityCatalog catalog() {
		List<ProviderCapabilitySnapshot> providers = List.of(
				new ProviderCapabilitySnapshot("KNOWLEDGE", "知识库", "制度与资料", 1, "k"),
				new ProviderCapabilitySnapshot("WORK", "工作任务", "待办与任务", 1, "w"));
		List<WorkflowCapabilitySnapshot> workflows = List.of(workflow("raw-water", 11L, "用于原水水质分析"),
				workflow("process-water", 12L, "用于工艺段水质分析"), workflow("outlet-water", 13L, "用于出水水质分析"));
		return new FrozenCapabilityCatalog(providers, workflows, "catalog-digest", Instant.EPOCH);
	}

	private FrozenCapabilityCatalog routingCatalog() {
		List<ProviderCapabilitySnapshot> providers = List.of(
				new ProviderCapabilitySnapshot("KNOWLEDGE", "知识库", "制度与资料", 1, "k"),
				new ProviderCapabilitySnapshot("INFORMATION", "资讯信息", "新闻和媒体报道", 1, "i"));
		List<WorkflowCapabilitySnapshot> workflows = List.of(
				new WorkflowCapabilitySnapshot("daily-report", 21L, "v1", "三分钟日报", "五域昨日综合日报", List.of("生成昨天五域日报"),
						List.of("仅查询昨天，覆盖水源、生产、能耗、二供和客服"), "input", "daily-output", "published-daily", "release-daily",
						List.of()),
				new WorkflowCapabilitySnapshot("water-source-quality-analysis", 22L, "v1", "水源水质监测智能体",
						"查询当前或指定时间的水源水质和原水监测指标", List.of("今天的水源水质如何"), List.of("用于一水厂和二水厂原水水质分析"), "input",
						"water-output", "published-water", "release-water", List.of()));
		return new FrozenCapabilityCatalog(providers, workflows, "routing-catalog", Instant.EPOCH);
	}

	private WorkflowCapabilitySnapshot workflow(String id, long releaseId, String operationRule) {
		return new WorkflowCapabilitySnapshot(id, releaseId, "v1", id, "水质分析", List.of("全流程水质"), List.of(operationRule),
				"input", "output", "published-" + id, "release-" + id, List.of("KNOWLEDGE"));
	}

	private AnalysisSkillSnapshot skill() {
		return skill(Set.of());
	}

	private AnalysisSkillSnapshot skill(Set<String> requiredProviders) {
		return new AnalysisSkillSnapshot("skill", 9L, "v1", "总结", "按证据总结", sha256("按证据总结"), "a".repeat(64), Map.of(),
				sha256("{}"), Map.of(), "sha256:" + sha256("{}"), requiredProviders);
	}

	private static String sha256(String value) {
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
