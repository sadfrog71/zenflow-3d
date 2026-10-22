package com.deepdimen.matrix.assistant.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType;

import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.deepdimen.matrix.assistant.api.AssistantContextContract;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.Citation;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ContextFragment;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.FactType;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderDescriptor;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderId;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderLimits;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderQuery;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderResult;
import com.deepdimen.matrix.assistant.api.AssistantContextContract.ProviderStatus;
import com.deepdimen.matrix.assistant.api.AssistantContextProvider;
import com.deepdimen.matrix.assistant.api.AssistantModelContract;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantEgress;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.AssistantModelRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DataClassification;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.DeploymentDomain;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelCapabilities;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelChunk;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ModelResolutionRequest;
import com.deepdimen.matrix.assistant.api.AssistantModelContract.ResolvedAssistantModel;
import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AssistantAttachment;
import com.deepdimen.matrix.assistant.api.AssistantAttachmentContract.AttachmentKind;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.DispatchStatus;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.SourceMode;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClarificationSelection;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepStatus;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.UsageSource;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.WorkflowCapabilitySnapshot;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.AssistantSession;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.MessageExchange;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.ScopeHint;
import com.deepdimen.matrix.assistant.api.AssistantSessionContract.SessionStatus;
import com.deepdimen.matrix.assistant.capability.AssistantPromptSkillCatalog;
import com.deepdimen.matrix.assistant.capability.AssistantAnalysisSkillCatalog;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory;
import com.deepdimen.matrix.assistant.capability.AssistantWorkflowCatalog;
import com.deepdimen.matrix.assistant.capability.AssistantWorkflowCatalog.PublishedWorkflowCapability;
import com.deepdimen.matrix.assistant.capability.AssistantWorkflowCatalog.SideEffectClass;
import com.deepdimen.matrix.assistant.capability.AssistantPromptSkillCatalog.PromptSkillDescriptor;
import com.deepdimen.matrix.assistant.capability.AssistantPromptSkillCatalog.PromptSkillSnapshot;
import com.deepdimen.matrix.assistant.generation.AssistantModelGateway;
import com.deepdimen.matrix.assistant.generation.AssistantNativeProfileResolver;
import com.deepdimen.matrix.assistant.orchestrator.AssistantContextOrchestrator;
import com.deepdimen.matrix.assistant.orchestrator.AssistantExecutionPlanValidator;
import com.deepdimen.matrix.assistant.orchestrator.AssistantSemanticPlanner;
import com.deepdimen.matrix.assistant.orchestrator.AssistantStructuredPlanningGateway;
import com.deepdimen.matrix.assistant.capability.AssistantAnalysisSkillCatalog.AnalysisSkillSnapshot;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;
import com.deepdimen.matrix.assistant.policy.AssistantIntentPolicy;
import com.deepdimen.matrix.assistant.provider.AssistantProviderDirectory;
import com.deepdimen.matrix.assistant.provider.AssistantProviderRegistry;
import com.deepdimen.matrix.assistant.session.InMemoryAssistantSessionRepository;
import com.deepdimen.matrix.assistant.workflow.UnavailableAssistantWorkflowExecutor;
import com.deepdimen.matrix.assistant.workflow.AssistantWorkflowExecutor;
import com.deepdimen.matrix.assistant.workflow.AssistantWorkflowExecutor.WorkflowExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;

class AssistantRunEngineTest {

	@Test
	void fastGreetingStreamsWithoutProviderOrWorkflowSteps() throws Exception {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
		var plannerField = AssistantRunEngine.class.getDeclaredField("semanticPlanner");
		plannerField.setAccessible(true);
		var planner = (AssistantSemanticPlanner) plannerField.get(fixture.engine);
		var enabled = AssistantSemanticPlanner.class.getDeclaredField("fastPathEnabled");
		enabled.setAccessible(true);
		enabled.setBoolean(planner, true);
		var candidates = AssistantSemanticPlanner.class.getDeclaredField("candidateRetrievalEnabled");
		candidates.setAccessible(true);
		candidates.setBoolean(planner, true);
		String runId = fixture.engine
			.startOrGet(fixture.identity, fixture.session, "greeting", "你好", List.of(), false, null, List.of(), null,
					null, null, List.of(), SourceMode.AUTO)
			.runId();
		fixture.engine.executePending(runId);
		var run = fixture.runs.findById(runId).orElseThrow();
		assertEquals(RunStatus.COMPLETED, run.status());
		assertEquals(com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode.DIRECT_CHAT,
				run.routeDecision().executionMode());
		assertTrue(fixture.engine.stepTraces(runId).isEmpty());
		assertTrue(fixture.runs.findAfter(runId, 0, 100)
			.stream()
			.anyMatch(event -> event.type() == RunEventType.MODEL_FIRST_CHUNK));
		assertFalse(fixture.runs.findAfter(runId, 0, 100)
			.stream()
			.anyMatch(event -> event.type() == RunEventType.PROVIDER_STARTED));
	}

	@Test
	void explicitNoSourceModeUsesDirectChatInsteadOfEvidenceBoundAnalysis() throws Exception {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
		String runId = fixture.engine
			.startOrGet(fixture.identity, fixture.session, "source-free-chat", "用一句话介绍你能做什么", List.of(), false,
					null, List.of(), null, null, null, List.of(), SourceMode.NONE)
			.runId();

		fixture.engine.executePending(runId);

		var run = fixture.runs.findById(runId).orElseThrow();
		assertEquals(RunStatus.COMPLETED, run.status());
		assertEquals(com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode.DIRECT_CHAT,
				run.routeDecision().executionMode());
		assertTrue(fixture.engine.stepTraces(runId).isEmpty());
		assertEquals("native answer", fixture.engine
			.awaitExchange(fixture.session.assistantSessionId(), "source-free-chat", Duration.ofSeconds(1))
			.responseMessage()
			.content());
		assertTrue(fixture.gateway.lastRequest.get().systemPrompt().contains("没有读取企业或实时数据"));
	}

	@Test
	void clarificationCompletesWithoutModelOrResourceExecutionAndIsReplayable() throws Exception {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
		installClarificationPlanner(fixture, false, false);
		String id = fixture.engine
			.startOrGet(fixture.identity, fixture.session, "clarify", "查询情况", List.of(), false, null, List.of(), null,
					null, null, List.of(), SourceMode.AUTO)
			.runId();
		fixture.engine.executePending(id);
		var run = fixture.runs.findById(id).orElseThrow();
		assertEquals(RunStatus.COMPLETED, run.status());
		assertEquals(0, fixture.gateway.invocations.get());
		assertTrue(fixture.engine.stepTraces(id).isEmpty());
		assertNotNull(run.executionPlan().clarification());
		assertNotNull(fixture.engine.visibleClarification(fixture.identity, run));
		var changedOrg = new IdentityEnvelope("tenant-1", "user-1", "org-2", List.of("org-1"), List.of(), List.of(),
				"new-corr", "assistant");
		assertNull(fixture.engine.visibleClarification(changedOrg, run));
		var changedRole = new IdentityEnvelope("tenant-1", "user-1", "org-1", List.of("org-1"), List.of("new-role"),
				List.of(), "new-corr", "assistant");
		assertNull(fixture.engine.visibleClarification(changedRole, run));
		var foreign = new IdentityEnvelope("other", "user-1", "org-1", List.of(), List.of(), List.of(), "new-corr",
				"assistant");
		assertThrows(ResponseStatusException.class, () -> fixture.engine.visibleClarification(foreign, run));
		assertEquals(1,
				fixture.runs.findAfter(id, 0, 100)
					.stream()
					.filter(e -> e.type() == RunEventType.CLARIFICATION_REQUESTED)
					.count());
		assertFalse(fixture.runs.findAfter(id, 0, 100)
			.stream()
			.anyMatch(e -> e.type() == RunEventType.PROVIDER_STARTED || e.type() == RunEventType.MODEL_FIRST_CHUNK));
		var exchange = fixture.engine.awaitExchange(fixture.session.assistantSessionId(), "clarify",
				Duration.ofSeconds(1));
		assertEquals("需要查询哪类资料？", exchange.responseMessage().content());
		fixture.engine.executePending(id);
		assertEquals(1,
				fixture.runs.findAfter(id, 0, 100)
					.stream()
					.filter(e -> e.type() == RunEventType.CLARIFICATION_REQUESTED)
					.count());
	}

	@Test
	void expiredOrUnauthorizedClarificationNeverDisplaysCandidateQuestion() throws Exception {
		for (boolean expired : List.of(true, false)) {
			Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
			installClarificationPlanner(fixture, expired, !expired);
			String id = fixture.engine
				.startOrGet(fixture.identity, fixture.session, "invalid-clarify", "查询情况", List.of(), false, null,
						List.of(), null, null, null, List.of(), SourceMode.AUTO)
				.runId();
			fixture.engine.executePending(id);
			var run = fixture.runs.findById(id).orElseThrow();
			assertEquals(RunStatus.FAILED, run.status());
			assertEquals(expired ? "CLARIFICATION_EXPIRED" : "CLARIFICATION_UNAVAILABLE", run.errorCode());
			assertNull(fixture.engine.visibleClarification(fixture.identity, run));
			assertEquals(0, fixture.gateway.invocations.get());
			assertTrue(fixture.engine.stepTraces(id).isEmpty());
			assertFalse(fixture.runs.findAfter(id, 0, 100)
				.stream()
				.anyMatch(e -> e.type() == RunEventType.CLARIFICATION_REQUESTED));
		}
	}

	private void installClarificationPlanner(Fixture fixture, boolean expired, boolean foreign) throws Exception {
		var planner = new AssistantSemanticPlanner(new AssistantExecutionPlanValidator(new ObjectMapper())) {
			@Override
			public com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionPlan plan(String runId,
					IdentityEnvelope identity, String content, SourceMode sourceMode, List<ProviderId> capped,
					ScopeHint scope, List<String> history, AssistantCapabilityDirectory.FrozenCapabilityCatalog catalog,
					AssistantAnalysisSkillCatalog.AnalysisSkillSnapshot skill) {
				var provider = catalog.providers().get(0);
				var state = new com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClarificationState(runId,
						1, false, "需要查询哪类资料？",
						List.of(new com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ClarificationCandidate(
								com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType.MATRIX_PROVIDER,
								foreign ? "FOREIGN" : provider.providerCode(), null, provider.displayName())),
						Instant.now().plusSeconds(expired ? -1 : 600).toEpochMilli());
				var route = new com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RouteDecision(
						com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.AMBIGUOUS,
						com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode.CLARIFICATION,
						List.of(), List.of(),
						com.deepdimen.matrix.assistant.api.AssistantExecutionContract.RouteTriggerType.PLANNER, null,
						null, true, false, "CLARIFICATION_REQUIRED");
				return new com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionPlan(null, "plan",
						runId, SourceMode.AUTO,
						com.deepdimen.matrix.assistant.api.AssistantExecutionContract.PlanningDisposition.AMBIGUOUS,
						List.of(), null, List.of(), catalog.digest(), "fixture-plan-digest", Instant.now(), route,
						state);
			}
		};
		var field = AssistantRunEngine.class.getDeclaredField("semanticPlanner");
		field.setAccessible(true);
		field.set(fixture.engine, planner);
	}

	@Test
	void ambiguousPlanningAsksOnceThenOffersManualChoiceAndNewTopicStartsFresh() throws Exception {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
		enableAmbiguousPlanning(fixture, true);
		var first = runClarificationTurn(fixture, "first", "查询 Knowledge 资料");
		assertEquals(RunStatus.COMPLETED, first.status());
		assertNotNull(first.executionPlan().clarification());
		assertEquals(1, first.executionPlan().clarification().clarificationCount());
		var second = runClarificationTurn(fixture, "second", "都看看");
		assertEquals(RunStatus.COMPLETED, second.status());
		assertEquals(2, second.executionPlan().clarification().clarificationCount());
		assertTrue(second.executionPlan().clarification().manualSelectionRequired());
		assertEquals(first.runId(), second.executionPlan().clarification().originRunId());
		assertEquals(first.executionPlan().clarification().expiresAtEpochMillis(),
				second.executionPlan().clarification().expiresAtEpochMillis());
		var changed = runClarificationTurn(fixture, "changed", "另外查询 Knowledge 资料");
		assertEquals(1, changed.executionPlan().clarification().clarificationCount());
		assertEquals(changed.runId(), changed.executionPlan().clarification().originRunId());
		assertEquals(0, fixture.gateway.invocations.get());
		assertTrue(fixture.engine.stepTraces(first.runId()).isEmpty());
		assertTrue(fixture.engine.stepTraces(second.runId()).isEmpty());
	}

	@Test
	void disabledClarificationPreservesLegacyAmbiguousPlan() throws Exception {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
		enableAmbiguousPlanning(fixture, false);
		var run = runClarificationTurn(fixture, "disabled", "查询 Knowledge 资料");
		assertNull(run.executionPlan().clarification());
		assertTrue(run.executionPlan()
			.steps()
			.stream()
			.anyMatch(step -> step
				.type() == com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType.MATRIX_ANALYSIS));
		assertFalse(fixture.runs.findAfter(run.runId(), 0, 100)
			.stream()
			.anyMatch(event -> event.type() == RunEventType.CLARIFICATION_REQUESTED));
	}

	@Test
	void clarifiedTimeReachesProviderOnceWithNoOldSearchTerms() throws Exception {
		var queries = new CopyOnWriteArrayList<ProviderQuery>();
		var delegate = okProvider();
		var provider = new AssistantContextProvider() {
			public ProviderDescriptor describe() {
				return delegate.describe();
			}

			public ProviderResult query(ProviderQuery query) {
				queries.add(query);
				return delegate.query(query);
			}
		};
		Fixture fixture = new Fixture(provider, new DroppingDispatch(), true);
		fixture.gateway.semanticResponse = true;
		enableAmbiguousPlanning(fixture, true);
		var field = AssistantRunEngine.class.getDeclaredField("semanticPlanner");
		field.setAccessible(true);
		var planner = (AssistantSemanticPlanner) field.get(fixture.engine);
		var gatewayField = AssistantSemanticPlanner.class.getDeclaredField("structuredPlanningGateway");
		gatewayField.setAccessible(true);
		var firstGateway = (AssistantStructuredPlanningGateway) gatewayField.get(planner);
		var calls = new AtomicInteger();
		AssistantStructuredPlanningGateway gateway = new AssistantStructuredPlanningGateway() {
			public java.util.Optional<PlanningSuggestion> suggest(String id, IdentityEnvelope identity, String question,
					SourceMode mode, List<String> cap, FrozenCapabilityCatalog catalog, AnalysisSkillSnapshot skill) {
				calls.incrementAndGet();
				return firstGateway.suggest(id, identity, question, mode, cap, catalog, skill);
			}

			public java.util.Optional<PlanningSuggestion> suggest(String id, IdentityEnvelope identity, String question,
					SourceMode mode, List<String> cap, FrozenCapabilityCatalog catalog, AnalysisSkillSnapshot skill,
					ClarificationContext context) {
				if (context == null)
					return suggest(id, identity, question, mode, cap, catalog, skill);
				calls.incrementAndGet();
				assertEquals("查询 Knowledge 昨天的资料", context.originalQuestion());
				assertEquals("改为今天", question);
				return java.util.Optional.of(new PlanningSuggestion(
						com.deepdimen.matrix.assistant.api.AssistantExecutionContract.PlanningDisposition.MATCH,
						List.of(new ProviderSuggestion("KNOWLEDGE", "Knowledge 今天的资料")), List.of(), List.of(), null,
						new RoutingSuggestion(
								com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.BUSINESS_QUERY,
								com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode.PROVIDER_QUERY,
								0.99, 0.9)));
			}
		};
		gatewayField.set(planner, gateway);
		var first = runClarificationTurn(fixture, "time-first", "查询 Knowledge 昨天的资料");
		assertNotNull(first.executionPlan().clarification());
		assertTrue(queries.isEmpty());
		var second = runClarificationTurn(fixture, "time-second", "改为今天");
		assertEquals(RunStatus.COMPLETED, second.status());
		assertNull(second.executionPlan().clarification());
		assertEquals(2, calls.get());
		assertEquals(1, queries.size());
		assertEquals("Knowledge 今天的资料", queries.get(0).query());
		assertTrue(
				queries.get(0).queryTerms().stream().noneMatch(term -> term.contains("昨天") || term.contains("原始请求")));
		assertTrue(second.draftContent().contains("Evidence"));
		fixture.engine.executePending(second.runId());
		assertEquals(1, queries.size());
		assertEquals(2, calls.get());
	}

	@Test
	void manualSelectionBindsToLatestAuthorizedPlanAndRejectsStaleOrForeignChoices() throws Exception {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
		enableAmbiguousPlanning(fixture, true);
		var first = runClarificationTurn(fixture, "choose-first", "查询 Knowledge 资料");
		var second = runClarificationTurn(fixture, "choose-second", "都看看");
		var directoryField = AssistantRunEngine.class.getDeclaredField("capabilityDirectory");
		directoryField.setAccessible(true);
		var directory = (AssistantCapabilityDirectory) directoryField.get(fixture.engine);
		var catalog = directory.freeze(fixture.identity);
		var policy = new AssistantClarificationSelectionPolicy(new AssistantExecutionPlanValidator(new ObjectMapper()));
		var choice = new ClarificationSelection(second.runId(), second.executionPlan().planDigest(),
				ExecutionStepType.MATRIX_PROVIDER, "KNOWLEDGE", null);
		var history = fixture.sessions.findMessages(fixture.session.assistantSessionId());
		assertEquals("KNOWLEDGE",
				policy
					.validate(fixture.identity, fixture.session.assistantSessionId(), history, second, choice, catalog,
							Instant.now())
					.targetId());
		for (var invalid : List.of(
				new ClarificationSelection(second.runId(), "0".repeat(64), ExecutionStepType.MATRIX_PROVIDER,
						"KNOWLEDGE", null),
				new ClarificationSelection(second.runId(), second.executionPlan().planDigest(),
						ExecutionStepType.MATRIX_PROVIDER, "FOREIGN", null),
				new ClarificationSelection(first.runId(), second.executionPlan().planDigest(),
						ExecutionStepType.MATRIX_PROVIDER, "KNOWLEDGE", null)))
			assertThrows(IllegalArgumentException.class, () -> policy.validate(fixture.identity,
					fixture.session.assistantSessionId(), history, second, invalid, catalog, Instant.now()));
		assertThrows(IllegalArgumentException.class,
				() -> policy.validate(fixture.identity, fixture.session.assistantSessionId(), history, second, choice,
						catalog, Instant.now().plusSeconds(700)));
		var foreign = new IdentityEnvelope("other", "user-1", "org-1", List.of("org-1"), List.of(), List.of(), "corr",
				"assistant");
		assertThrows(IllegalArgumentException.class, () -> policy.validate(foreign,
				fixture.session.assistantSessionId(), history, second, choice, catalog, Instant.now()));
		var beforeManual = new ClarificationSelection(first.runId(), first.executionPlan().planDigest(),
				ExecutionStepType.MATRIX_PROVIDER, "KNOWLEDGE", null);
		assertThrows(IllegalArgumentException.class, () -> policy.validate(fixture.identity,
				fixture.session.assistantSessionId(), history, first, beforeManual, catalog, Instant.now()));
		runClarificationTurn(fixture, "changed-topic", "另外查询 Knowledge 资料");
		assertThrows(IllegalArgumentException.class,
				() -> policy.validate(fixture.identity, fixture.session.assistantSessionId(),
						fixture.sessions.findMessages(fixture.session.assistantSessionId()), second, choice, catalog,
						Instant.now()));
	}

	@Test
	void submittedManualSelectionPersistsAndIdempotentRetryCannotSwitchCandidate() throws Exception {
		var queries = new AtomicInteger();
		Fixture fixture = new Fixture(provider(ProviderStatus.OK, queries), new DroppingDispatch(), true);
		fixture.gateway.semanticResponse = true;
		enableAmbiguousPlanning(fixture, true);
		runClarificationTurn(fixture, "manual-first", "查询 Knowledge 资料");
		var source = runClarificationTurn(fixture, "manual-second", "都看看");
		var choice = new ClarificationSelection(source.runId(), source.executionPlan().planDigest(),
				ExecutionStepType.MATRIX_PROVIDER, "KNOWLEDGE", null);
		var accepted = fixture.engine.startOrGet(fixture.identity, fixture.session, "manual-submit",
				"查询 Knowledge 今天的资料", List.of(), false, null, List.of(), null, null, null, List.of(), SourceMode.AUTO,
				choice);
		assertEquals(choice, fixture.runs.findById(accepted.runId()).orElseThrow().input().clarificationSelection());
		var winner = fixture.runs.findById(accepted.runId()).orElseThrow();
		var stale = new AssistantRunRecord("competing-run", winner.sessionId(), winner.tenantId(), winner.ownerUserId(),
				"competing-client", winner.requestHash(), "competing-user", "competing-answer", RunStatus.ACCEPTED,
				DispatchStatus.PENDING, winner.input(), null, "", 0, null, null, null, null, null, Instant.now(), null,
				null);
		assertThrows(ResponseStatusException.class, () -> fixture.runs.startAtomic(stale, null, null));
		assertTrue(fixture.runs.findById("competing-run").isEmpty());
		var retry = fixture.engine.startOrGet(fixture.identity, fixture.session, "manual-submit", "查询 Knowledge 今天的资料",
				List.of(), false, null, List.of(), null, null, null, List.of(), SourceMode.AUTO, choice);
		assertEquals(accepted.runId(), retry.runId());
		var other = new ClarificationSelection(source.runId(), source.executionPlan().planDigest(),
				ExecutionStepType.MATRIX_PROVIDER, "WORK", null);
		var conflict = assertThrows(ResponseStatusException.class,
				() -> fixture.engine.startOrGet(fixture.identity, fixture.session, "manual-submit",
						"查询 Knowledge 今天的资料", List.of(), false, null, List.of(), null, null, null, List.of(),
						SourceMode.AUTO, other));
		assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
		fixture.engine.executePending(accepted.runId());
		var completed = fixture.runs.findById(accepted.runId()).orElseThrow();
		assertEquals(RunStatus.COMPLETED, completed.status());
		assertEquals("MANUAL_CLARIFICATION_SELECTION", completed.routeDecision().reasonCode());
		assertFalse(completed.routeDecision().plannerUsed());
		assertEquals(1, queries.get());
		fixture.engine.executePending(accepted.runId());
		assertEquals(1, queries.get());
	}

	@Test
	void queuedManualChoiceHonorsDisabledFlagBeforeExecuting() throws Exception {
		var queries = new AtomicInteger();
		Fixture fixture = new Fixture(provider(ProviderStatus.OK, queries), new DroppingDispatch(), true);
		enableAmbiguousPlanning(fixture, true);
		runClarificationTurn(fixture, "disabled-choice-first", "查询 Knowledge 资料");
		var source = runClarificationTurn(fixture, "disabled-choice-second", "都看看");
		var choice = new ClarificationSelection(source.runId(), source.executionPlan().planDigest(),
				ExecutionStepType.MATRIX_PROVIDER, "KNOWLEDGE", null);
		var accepted = fixture.engine.startOrGet(fixture.identity, fixture.session, "disabled-choice-submit",
				"查询 Knowledge 今天资料", List.of(), false, null, List.of(), null, null, null, List.of(), SourceMode.AUTO,
				choice);
		var flag = AssistantRunEngine.class.getDeclaredField("clarificationEnabled");
		flag.setAccessible(true);
		flag.setBoolean(fixture.engine, false);
		fixture.engine.executePending(accepted.runId());
		var failed = fixture.runs.findById(accepted.runId()).orElseThrow();
		assertEquals(RunStatus.FAILED, failed.status());
		assertEquals("CLARIFICATION_SELECTION_UNAVAILABLE", failed.errorCode());
		assertEquals(0, queries.get());
	}

	@Test
	void clarificationFindsLatestRunAfterMoreThanTwentyCompletedTurns() throws Exception {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
		enableAmbiguousPlanning(fixture, true);
		AssistantRunRecord latest = null;
		for (int i = 0; i < 25; i++)
			latest = runClarificationTurn(fixture, "long-" + i, "另外查询 Knowledge 资料");
		var result = runClarificationTurn(fixture, "long-followup", "都看看");
		assertNotNull(result.executionPlan().clarification());
		assertEquals(latest.runId(), result.executionPlan().clarification().originRunId());
		assertEquals(2, result.executionPlan().clarification().clarificationCount());
		assertEquals(0, fixture.gateway.invocations.get());
	}

	private AssistantRunRecord runClarificationTurn(Fixture fixture, String client, String question) {
		String id = fixture.engine
			.startOrGet(fixture.identity, fixture.session, client, question, List.of(), false, null, List.of(), null,
					null, null, List.of(), SourceMode.AUTO)
			.runId();
		fixture.engine.executePending(id);
		return fixture.runs.findById(id).orElseThrow();
	}

	private void enableAmbiguousPlanning(Fixture fixture, boolean enabled) throws Exception {
		var flag = AssistantRunEngine.class.getDeclaredField("clarificationEnabled");
		flag.setAccessible(true);
		flag.setBoolean(fixture.engine, enabled);
		var field = AssistantRunEngine.class.getDeclaredField("semanticPlanner");
		field.setAccessible(true);
		var planner = (AssistantSemanticPlanner) field.get(fixture.engine);
		for (String name : List.of("candidateRetrievalEnabled", "semanticRoutingEnabled")) {
			var option = AssistantSemanticPlanner.class.getDeclaredField(name);
			option.setAccessible(true);
			option.setBoolean(planner, true);
		}
		com.deepdimen.matrix.assistant.orchestrator.AssistantStructuredPlanningGateway gateway = (id, identity,
				question, mode, cap, catalog, skill) -> java.util.Optional
					.of(new com.deepdimen.matrix.assistant.orchestrator.AssistantStructuredPlanningGateway.PlanningSuggestion(
							com.deepdimen.matrix.assistant.api.AssistantExecutionContract.PlanningDisposition.MATCH,
							List.of(new com.deepdimen.matrix.assistant.orchestrator.AssistantStructuredPlanningGateway.ProviderSuggestion(
									"KNOWLEDGE", question)),
							List.of(), List.of(), null,
							new com.deepdimen.matrix.assistant.orchestrator.AssistantStructuredPlanningGateway.RoutingSuggestion(
									com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent.BUSINESS_QUERY,
									com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode.PROVIDER_QUERY,
									0.4, 0.05)));
		var gatewayField = AssistantSemanticPlanner.class.getDeclaredField("structuredPlanningGateway");
		gatewayField.setAccessible(true);
		gatewayField.set(planner, gateway);
	}

	private void enableAnalysisStream(Fixture fixture) throws Exception {
		var field = AssistantRunEngine.class.getDeclaredField("analysisStreamEnabled");
		field.setAccessible(true);
		field.setBoolean(fixture.engine, true);
	}

	private String startAnalysis(Fixture fixture, String clientId) {
		return fixture.engine
			.startOrGet(fixture.identity, fixture.session, clientId, "总结知识库内容", List.of(), false, null, List.of(), null,
					null, null, List.of(), SourceMode.AUTO)
			.runId();
	}

	private String streamMeta(String runId) {
		return "{\"type\":\"analysis_meta\",\"protocol\":\"assistant-analysis-stream/v1\",\"runId\":\"" + runId
				+ "\",\"analysisType\":\"business\",\"plannedSections\":2}\n";
	}

	private String streamSection(String id, String citation) {
		return "{\"type\":\"section\",\"section\":{\"sectionId\":\"" + id + "\",\"title\":\"" + id
				+ "\",\"content\":\"\",\"claimIds\":[\"" + id + "\"]},\"claims\":[{\"claimId\":\"" + id
				+ "\",\"type\":\"FACT\",\"text\":\"Evidence " + id + "\",\"citationKeys\":[\"" + citation
				+ "\"],\"artifactIds\":[]}]}\n";
	}

	private String streamTail() {
		return "{\"type\":\"summary\",\"claim\":{\"claimId\":\"sum\",\"type\":\"FACT\",\"text\":\"Final summary\",\"citationKeys\":[\"C1\"],\"artifactIds\":[]},\"warnings\":[],\"sourceCoverage\":{}}\n"
				+ "{\"type\":\"final\",\"resultDigest\":null,\"sectionCount\":2,\"claimCount\":3,\"complete\":true}\n";
	}

	@Test
	void validatedSectionIsPersistedBeforeTheModelFinishesAndReplayHasNoDuplicates() throws Exception {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
		enableAnalysisStream(fixture);
		String runId = startAnalysis(fixture, "sections");
		var release = reactor.core.publisher.Sinks.<String>one();
		fixture.gateway.response = Flux
			.just(new ModelChunk(runId, 0, streamMeta(runId) + streamSection("s1", "C1"), false, ""))
			.concatWith(release.asMono().map(text -> new ModelChunk(runId, 1, text, false, "")))
			.concatWithValues(new ModelChunk(runId, 2, "", true, "STOP"));
		Thread worker = new Thread(() -> fixture.engine.executePending(runId));
		worker.setDaemon(true);
		worker.start();
		try {
			awaitDraft(fixture.runs, runId, "### s1\n\n- Evidence s1", Duration.ofSeconds(2));
			assertEquals(RunStatus.MODEL_STREAMING, fixture.runs.findById(runId).orElseThrow().status());
			release.tryEmitValue(streamSection("s2", "C1") + streamTail());
			worker.join(2000);
			assertFalse(worker.isAlive());
			var run = fixture.runs.findById(runId).orElseThrow();
			assertEquals(RunStatus.COMPLETED, run.status());
			var events = fixture.runs.findAfter(runId, 0, 100);
			String replay = events.stream()
				.filter(event -> event.type() == RunEventType.RESPONSE_DELTA)
				.map(event -> String.valueOf(event.data().get("delta")))
				.collect(java.util.stream.Collectors.joining());
			assertEquals(run.draftContent(), replay);
			assertEquals(1, replay.split("### s1", -1).length - 1);
			assertEquals(1, replay.split("Final summary", -1).length - 1);
			assertEquals(2,
					events.stream().filter(event -> event.type() == RunEventType.ANSWER_SECTION_VALIDATED).count());
			assertTrue(fixture.gateway.lastRequest.get().systemPrompt().contains("assistant-analysis-stream/v1"));
		}
		finally {
			release.tryEmitEmpty();
			if (worker.isAlive())
				fixture.engine.requestCancel(fixture.identity, fixture.session.assistantSessionId(), runId);
			worker.join(2000);
		}
	}

	@Test
	void invalidLaterSectionKeepsVerifiedDraftAndMarksIncompleteWithoutSummary() throws Exception {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
		enableAnalysisStream(fixture);
		String runId = startAnalysis(fixture, "invalid-section");
		CountDownLatch stopped = new CountDownLatch(1);
		fixture.gateway.response = Flux
			.just(new ModelChunk(runId, 0, streamMeta(runId) + streamSection("s1", "C1"), false, ""),
					new ModelChunk(runId, 1, streamSection("s2", "unknown") + streamTail(), false, ""),
					new ModelChunk(runId, 2, "", true, "STOP"))
			.concatWith(Flux.never())
			.doOnCancel(stopped::countDown);
		fixture.engine.executePending(runId);
		assertEquals(0, stopped.getCount());
		var run = fixture.runs.findById(runId).orElseThrow();
		assertEquals(RunStatus.FAILED, run.status());
		assertTrue(run.draftContent().contains("Evidence s1"));
		assertTrue(run.draftContent().contains("回答未完整"));
		assertFalse(run.draftContent().contains("Evidence s2"));
		assertFalse(run.draftContent().contains("Final summary"));
		var exchange = fixture.engine.awaitExchange(fixture.session.assistantSessionId(), "invalid-section",
				Duration.ofSeconds(1));
		assertEquals(run.draftContent(), exchange.responseMessage().content());
	}

	@Test
	void modelDisconnectAfterFirstSectionPersistsIncompleteAnswer() throws Exception {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
		enableAnalysisStream(fixture);
		String runId = startAnalysis(fixture, "section-disconnect");
		fixture.gateway.response = Flux
			.just(new ModelChunk(runId, 0, streamMeta(runId) + streamSection("s1", "C1"), false, ""));
		fixture.engine.executePending(runId);
		var run = fixture.runs.findById(runId).orElseThrow();
		assertEquals(RunStatus.FAILED, run.status());
		assertEquals("ANALYSIS_STREAM_INCOMPLETE", run.errorCode());
		assertTrue(run.draftContent().contains("Evidence s1"));
		assertTrue(run.draftContent().contains("回答未完整"));
		assertNull(run.analysisResult());
	}

	@Test
	void timeoutAndExpiredExecutorKeepValidatedAnalysisWithoutModelRetry() throws Exception {
		for (String code : List.of("RUN_TIMEOUT", "EXECUTOR_LOST")) {
			Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
			enableAnalysisStream(fixture);
			String runId = startAnalysis(fixture, code);
			var release = reactor.core.publisher.Sinks.<ModelChunk>one();
			fixture.gateway.response = Flux
				.just(new ModelChunk(runId, 0, streamMeta(runId) + streamSection("s1", "C1"), false, ""))
				.concatWith(release.asMono());
			Thread worker = new Thread(() -> fixture.engine.executePending(runId));
			worker.setDaemon(true);
			worker.start();
			try {
				awaitDraft(fixture.runs, runId, "### s1\n\n- Evidence s1", Duration.ofSeconds(2));
				if ("EXECUTOR_LOST".equals(code)) {
					var ownerField = AssistantRunEngine.class.getDeclaredField("ownerInstanceId");
					ownerField.setAccessible(true);
					assertTrue(fixture.runs.renewLease(runId, (String) ownerField.get(fixture.engine),
							Instant.now().minusSeconds(1)));
					fixture.engine.recoverExpired(Instant.now(), 20);
				}
				else {
					var fail = AssistantRunEngine.class.getDeclaredMethod("fail", AssistantRunRecord.class,
							String.class, String.class);
					fail.setAccessible(true);
					fail.invoke(fixture.engine, fixture.runs.findById(runId).orElseThrow(), code, "运行超时，已停止");
				}
				var failed = fixture.runs.findById(runId).orElseThrow();
				assertEquals(RunStatus.FAILED, failed.status());
				assertEquals(code, failed.errorCode());
				assertTrue(failed.draftContent().contains("Evidence s1"));
				assertTrue(failed.draftContent().contains("回答未完整"));
				assertFalse(failed.draftContent().contains("分析总结"));
				assertEquals(failed.draftContent(),
						fixture.engine.awaitExchange(fixture.session.assistantSessionId(), code, Duration.ofSeconds(1))
							.responseMessage()
							.content());
				fixture.engine.recoverExpired(Instant.now(), 20);
				assertEquals(1, fixture.gateway.invocations.get());
			}
			finally {
				release.tryEmitEmpty();
				worker.join(2000);
			}
			assertFalse(worker.isAlive());
		}
	}

	@Test
	void streamFlagAllowsOldJsonToFallBackWithoutDuplicatingOutput() throws Exception {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
		enableAnalysisStream(fixture);
		fixture.gateway.semanticResponse = true;
		String runId = startAnalysis(fixture, "legacy-stream");
		fixture.engine.executePending(runId);
		assertEquals(RunStatus.COMPLETED, fixture.runs.findById(runId).orElseThrow().status());
		var events = fixture.runs.findAfter(runId, 0, 100);
		assertEquals(1, events.stream().filter(event -> event.type() == RunEventType.RESPONSE_DELTA).count());
		assertEquals(0, events.stream().filter(event -> event.type() == RunEventType.ANSWER_SECTION_VALIDATED).count());
	}

	@Test
	void autoModeBuildsDurablePlanQueriesMatchedProviderAndValidatesSkillOutput() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true);
		fixture.gateway.semanticResponse = true;
		String runId = fixture.engine
			.startOrGet(fixture.identity, fixture.session, "semantic", "总结知识库内容", List.of(), false, null, List.of(),
					null, null, null, List.of(), SourceMode.AUTO)
			.runId();

		fixture.engine.executePending(runId);

		AssistantRunRecord run = fixture.runs.findById(runId).orElseThrow();
		assertEquals(RunStatus.COMPLETED, run.status());
		assertNotNull(run.executionPlan());
		assertNotNull(run.analysisResult());
		assertEquals(2, fixture.engine.stepTraces(runId).size());
		assertTrue(fixture.engine.stepTraces(runId)
			.stream()
			.allMatch(step -> step.status() == ExecutionStepStatus.SUCCEEDED));
		assertEquals(1, fixture.runs.findByRunId(runId).size());
		String content = fixture.engine
			.awaitExchange(fixture.session.assistantSessionId(), "semantic", Duration.ofSeconds(1))
			.responseMessage()
			.content();
		assertTrue(content.contains("Evidence"));
		assertFalse(content.contains("[C1]"));
	}

	@Test
	void singleMarketplaceWorkflowUsesItsAnalysisDirectlyWithoutSecondModelCall() {
		AssistantWorkflowCatalog workflowCatalog = identity -> List.of(marketplaceWorkflow(identity.tenantId()));
		AssistantWorkflowExecutor workflowExecutor = (identity, runId, step,
				dependencies) -> new WorkflowExecutionResult(
						Map.of("status", "success", "text",
								"""
										# 水源水质日报

										## 监测概况

										| 指标 | 当前值 |
										| --- | --- |
										| pH | 8.75 |

										```artifact
										{"title":"浊度趋势","type":"chart"}
										<canvas id="trend"></canvas>
										<script>
										var trendConfig={"title":"浊度趋势","labels":["09-06","09-07"],"datasets":[{"label":"浊度","data":[2.1,2.3]}]};
										new Chart(document.getElementById('trend'), {type:'line', data:{}});
										</script>
										```

										## 综合结论

										水质总体稳定，建议继续关注高锰酸盐指数。[工作流:source-water-quality]
										""",
								"_matrixMarketplaceAgentId", "agent-1", "_matrixCapabilityId", "market-agent-1",
								"_matrixCapabilityName", "水源水质监测智能体"),
						List.of(), "task-1", "workflow-run-1", UsageSource.DIFY_RESPONSE);
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true, workflowCatalog, workflowExecutor);
		String runId = fixture.engine
			.startOrGet(fixture.identity, fixture.session, "direct-workflow", "今天的水源水质如何", List.of(), false, null,
					List.of(), null, null, null, List.of(), SourceMode.AUTO)
			.runId();

		fixture.engine.executePending(runId);

		AssistantRunRecord run = fixture.runs.findById(runId).orElseThrow();
		assertEquals(RunStatus.COMPLETED, run.status());
		assertNotNull(run.analysisResult());
		assertEquals(0, fixture.gateway.invocations.get());
		assertEquals(UsageSource.DIFY_RESPONSE,
				fixture.engine.stepTraces(runId)
					.stream()
					.filter(step -> "analysis-final".equals(step.stepId()))
					.findFirst()
					.orElseThrow()
					.usageSource());
		String content = fixture.engine
			.awaitExchange(fixture.session.assistantSessionId(), "direct-workflow", Duration.ofSeconds(1))
			.responseMessage()
			.content();
		assertTrue(content.contains("### 分析总结"));
		assertTrue(content.contains("| pH | 8.75 |"));
		assertTrue(content.contains("水质总体稳定"));
		assertTrue(content.contains("业务分析：水源水质监测智能体"));
		assertFalse(content.contains("```artifact"));
		assertTrue(content.contains("```chart-spec"));
		assertTrue(content.contains("\"title\":\"浊度趋势\""));
		assertFalse(content.contains("<script>"));
		assertFalse(content.contains("[工作流:"));
	}

	@Test
	void singleMarketplaceWorkflowUsesNamedMarkdownOutputDirectly() {
		AssistantWorkflowCatalog workflowCatalog = identity -> List.of(marketplaceWorkflow(identity.tenantId()));
		AssistantWorkflowExecutor workflowExecutor = (identity, runId, step,
				dependencies) -> new WorkflowExecutionResult(
						Map.of("customer_report_markdown", "## 客服工单结论\n\n今日水质类工单 3 件。", "_matrixMarketplaceAgentId",
								"agent-1", "_matrixCapabilityName", "客服智能体"),
						List.of(), "task-1", "workflow-run-1", UsageSource.DIFY_RESPONSE);
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true, workflowCatalog, workflowExecutor);
		String runId = fixture.engine
			.startOrGet(fixture.identity, fixture.session, "named-markdown", "今天的客服工单有水质投诉吗", List.of(), false, null,
					List.of(), null, null, null, List.of(), SourceMode.AUTO)
			.runId();

		fixture.engine.executePending(runId);

		assertEquals(RunStatus.COMPLETED, fixture.runs.findById(runId).orElseThrow().status());
		assertEquals(0, fixture.gateway.invocations.get());
		String content = fixture.engine
			.awaitExchange(fixture.session.assistantSessionId(), "named-markdown", Duration.ofSeconds(1))
			.responseMessage()
			.content();
		assertTrue(content.contains("今日水质类工单 3 件"));
		assertTrue(content.contains("业务分析：水源水质监测智能体"));
	}

	@Test
	void mixedProviderAndMarketplaceWorkflowStillUsesSkillAnalysisModel() {
		AssistantWorkflowCatalog workflowCatalog = identity -> List.of(marketplaceWorkflow(identity.tenantId()));
		AssistantWorkflowExecutor workflowExecutor = (identity, runId, step,
				dependencies) -> new WorkflowExecutionResult(
						Map.of("status", "success", "text", "水源水质智能体结论", "_matrixMarketplaceAgentId", "agent-1",
								"_matrixCapabilityName", "水源水质监测智能体"),
						List.of(), "task-1", "workflow-run-1", UsageSource.DIFY_RESPONSE);
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true, workflowCatalog, workflowExecutor);
		fixture.gateway.semanticResponse = true;
		String runId = fixture.engine
			.startOrGet(fixture.identity, fixture.session, "mixed-workflow", "结合知识库分析今天的水源水质", List.of(), false, null,
					List.of(), null, null, null, List.of(), SourceMode.AUTO)
			.runId();

		fixture.engine.executePending(runId);

		assertEquals(RunStatus.COMPLETED, fixture.runs.findById(runId).orElseThrow().status());
		assertEquals(1, fixture.gateway.invocations.get());
		assertEquals(3, fixture.engine.stepTraces(runId).size());
	}

	@Test
	void invalidMixedAnalysisKeepsVerifiedMarketplaceWorkflowResult() {
		AssistantWorkflowCatalog workflowCatalog = identity -> List.of(marketplaceWorkflow(identity.tenantId()));
		AssistantWorkflowExecutor workflowExecutor = (identity, runId, step,
				dependencies) -> new WorkflowExecutionResult(
						Map.of("status", "success", "text", "水源水质智能体结论：pH 稳定，建议持续观察浊度趋势。", "_matrixMarketplaceAgentId",
								"agent-1", "_matrixCapabilityName", "水源水质监测智能体"),
						List.of(), "task-1", "workflow-run-1", UsageSource.DIFY_RESPONSE);
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch(), true, workflowCatalog, workflowExecutor);
		fixture.gateway.response = Flux
			.just(new ModelChunk("ignored", 0, "not-json", false, ""), new ModelChunk("ignored", 1, "", true, "STOP"))
			.transformDeferred(flux -> flux.map(chunk -> new ModelChunk(fixture.gateway.lastRequest.get().runId(),
					chunk.sequence(), chunk.delta(), chunk.terminal(), chunk.finishReason())));
		String runId = fixture.engine
			.startOrGet(fixture.identity, fixture.session, "mixed-invalid", "结合知识库分析今天的水源水质", List.of(), false, null,
					List.of(), null, null, null, List.of(), SourceMode.AUTO)
			.runId();

		fixture.engine.executePending(runId);

		assertEquals(RunStatus.COMPLETED, fixture.runs.findById(runId).orElseThrow().status());
		String content = fixture.engine
			.awaitExchange(fixture.session.assistantSessionId(), "mixed-invalid", Duration.ofSeconds(1))
			.responseMessage()
			.content();
		assertTrue(content.contains("水源水质智能体结论"));
		assertTrue(content.contains("跨来源自动综合未通过证据校验"));
		assertEquals(UsageSource.DIFY_RESPONSE,
				fixture.engine.stepTraces(runId)
					.stream()
					.filter(step -> "analysis-final".equals(step.stepId()))
					.findFirst()
					.orElseThrow()
					.usageSource());
	}

	@Test
	void concurrentSameClientMessageCreatesOneRunAndInvokesModelOnce() throws Exception {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<String> first = new AtomicReference<>();
		AtomicReference<String> second = new AtomicReference<>();
		Thread left = new Thread(() -> first.set(startAfter(start, fixture, "same", "hello")));
		Thread right = new Thread(() -> second.set(startAfter(start, fixture, "same", "hello")));
		left.start();
		right.start();
		start.countDown();
		left.join();
		right.join();

		assertEquals(first.get(), second.get());
		assertEquals(2, fixture.sessions.findMessages(fixture.session.assistantSessionId()).size());
		fixture.engine.executePending(first.get());
		fixture.engine.executePending(first.get());
		assertEquals(1, fixture.gateway.invocations.get());
		assertEquals("native answer",
				fixture.engine.awaitExchange(fixture.session.assistantSessionId(), "same", Duration.ofSeconds(1))
					.responseMessage()
					.content());
	}

	@Test
	void firstRunAutoTitlesDefaultSession() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		fixture.sessions.saveSession(new AssistantSession(fixture.session.assistantSessionId(),
				fixture.identity.tenantId(), fixture.identity.userId(), "新对话", SessionStatus.ACTIVE, List.of(), null, 1,
				Instant.now(), Instant.now()));

		fixture.start("auto-title", "  工作任务周报整理  ", List.of(), true, List.of());

		assertEquals("工作任务周报整理",
				fixture.sessions.findSession(fixture.session.assistantSessionId()).orElseThrow().title());
	}

	@Test
	void sameIdWithDifferentHashReturnsExplicitConflict() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		fixture.start("same", "first", List.of(ProviderId.KNOWLEDGE), true, List.of());

		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> fixture.start("same", "second", List.of(ProviderId.KNOWLEDGE), true, List.of()));

		assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
		assertEquals(AssistantRunEngine.IDEMPOTENCY_KEY_REUSED, error.getReason());
	}

	@Test
	void legacySourceModeKeepsTheExistingRequestHashWhileExplicitModesAreSeparated() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		String legacyRunId = fixture.start("legacy-1", "hello", List.of(ProviderId.KNOWLEDGE), true, List.of());
		String explicitLegacyRunId = fixture.engine
			.startOrGet(fixture.identity, fixture.session, "legacy-2", "hello", List.of(ProviderId.KNOWLEDGE), true,
					null, List.of(), null, null, null, List.of(), SourceMode.LEGACY)
			.runId();
		String capRunId = fixture.engine
			.startOrGet(fixture.identity, fixture.session, "cap-1", "hello", List.of(ProviderId.KNOWLEDGE), true, null,
					List.of(), null, null, null, List.of(), SourceMode.CAP)
			.runId();

		String legacyHash = fixture.runs.findById(legacyRunId).orElseThrow().requestHash();
		assertEquals(legacyHash, fixture.runs.findById(explicitLegacyRunId).orElseThrow().requestHash());
		assertFalse(legacyHash.equals(fixture.runs.findById(capRunId).orElseThrow().requestHash()));
		assertNull(fixture.runs.findById(legacyRunId).orElseThrow().input().sourceMode());
		assertEquals(SourceMode.CAP, fixture.runs.findById(capRunId).orElseThrow().input().sourceMode());
	}

	@Test
	void idempotencyIncludesScopeAndOrderedProviderSelection() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		ScopeHint firstScope = new ScopeHint("knowledge", "DOCUMENT", List.of("doc-1"), Map.of("folder", "a"));
		String runId = fixture.engine
			.startOrGet(fixture.identity, fixture.session, "scoped", "hello",
					List.of(ProviderId.WORK, ProviderId.KNOWLEDGE), true, firstScope, List.of(), null, null, null)
			.runId();
		assertEquals(runId,
				fixture.engine.startOrGet(fixture.identity, fixture.session, "scoped", "hello",
						List.of(ProviderId.WORK, ProviderId.KNOWLEDGE), true, firstScope, List.of(), null, null, null)
					.runId());

		ScopeHint differentScope = new ScopeHint("knowledge", "DOCUMENT", List.of("doc-2"), Map.of("folder", "a"));
		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> fixture.engine.startOrGet(fixture.identity, fixture.session, "scoped", "hello",
						List.of(ProviderId.WORK, ProviderId.KNOWLEDGE), true, differentScope, List.of(), null, null,
						null));
		assertEquals(AssistantRunEngine.IDEMPOTENCY_KEY_REUSED, error.getReason());
	}

	@Test
	void explicitEmptyProviderSelectionAllowsPureModelAnswer() {
		AtomicInteger queries = new AtomicInteger();
		Fixture fixture = new Fixture(provider(ProviderStatus.OK, queries), new DroppingDispatch());
		String runId = fixture.start("pure", "hello", List.of(), true, List.of());

		fixture.engine.executePending(runId);

		assertEquals(0, queries.get());
		assertEquals(1, fixture.gateway.invocations.get());
		AssistantRunRecord run = fixture.runs.findById(runId).orElseThrow();
		assertEquals(RunStatus.COMPLETED, run.status());
		assertEquals(DispatchStatus.FINISHED, run.dispatchStatus());
		var events = fixture.runs.findAfter(runId, 0, 100);
		assertEquals("RUN_COMPLETED", events.get(events.size() - 1).type().name());
		assertEquals(run.lastEventSeq(), events.get(events.size() - 1).seq());
	}

	@Test
	void attachmentsArePersistedForwardedAndIncludedInIdempotency() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		AssistantAttachment first = new AssistantAttachment("upload-1", "tenant/image.png", "image.png", "image/png",
				128, AttachmentKind.IMAGE);
		String runId = fixture.engine
			.startOrGet(fixture.identity, fixture.session, "with-file", "describe it", List.of(), true, null, List.of(),
					null, null, null, List.of(first))
			.runId();

		fixture.engine.executePending(runId);

		assertEquals(List.of(first), fixture.gateway.lastRequest.get().attachments());
		assertEquals(List.of(first),
				fixture.sessions.findMessages(fixture.session.assistantSessionId()).get(0).attachments());
		AssistantAttachment different = new AssistantAttachment("upload-2", "tenant/other.png", "other.png",
				"image/png", 128, AttachmentKind.IMAGE);
		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> fixture.engine.startOrGet(fixture.identity, fixture.session, "with-file", "describe it",
						List.of(), true, null, List.of(), null, null, null, List.of(different)));
		assertEquals(AssistantRunEngine.IDEMPOTENCY_KEY_REUSED, error.getReason());
	}

	@Test
	void followUpRunCarriesHistoricalAttachmentsToTheModel() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		AssistantAttachment attachment = new AssistantAttachment("upload-1", "tenant/report.md", "report.md",
				"text/markdown", 128, AttachmentKind.DOCUMENT);
		String firstRun = fixture.engine
			.startOrGet(fixture.identity, fixture.session, "with-history-file", "summarize", List.of(), true, null,
					List.of(), null, null, null, List.of(attachment))
			.runId();
		fixture.engine.executePending(firstRun);

		String secondRun = fixture.start("follow-up", "what was the second item?", List.of(), true, List.of());
		fixture.engine.executePending(secondRun);

		assertEquals(List.of(attachment), fixture.gateway.lastRequest.get().history().get(0).attachments());
	}

	@Test
	void allSelectedProvidersUnavailableFailsBeforeModelInvocation() {
		Fixture fixture = new Fixture(provider(ProviderStatus.UNAVAILABLE, new AtomicInteger()),
				new DroppingDispatch());
		String runId = fixture.start("unavailable", "hello", List.of(ProviderId.KNOWLEDGE), true, List.of());

		fixture.engine.executePending(runId);

		assertEquals(0, fixture.gateway.invocations.get());
		AssistantRunRecord run = fixture.runs.findById(runId).orElseThrow();
		assertEquals(RunStatus.FAILED, run.status());
		assertEquals("ALL_SELECTED_PROVIDERS_UNAVAILABLE", run.errorCode());
		MessageExchange exchange = fixture.engine.awaitExchange(fixture.session.assistantSessionId(), "unavailable",
				Duration.ofSeconds(1));
		assertNotNull(exchange.contextPack());
		assertEquals("ALL_SELECTED_PROVIDERS_UNAVAILABLE", exchange.executionTrace().errorCode());
	}

	@Test
	void frozenPromptSkillIsSystemLayerOnlyAndNeverExecutesTools() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		String runId = fixture.start("skill", "hello", List.of(ProviderId.KNOWLEDGE), true, List.of("skill-1"));

		fixture.engine.executePending(runId);
		assertEquals(runId, fixture.start("skill", "hello", List.of(ProviderId.KNOWLEDGE), true, List.of("skill-1")));

		AssistantModelRequest request = fixture.gateway.lastRequest.get();
		assertTrue(request.systemPrompt().contains("不得执行工具"));
		assertTrue(request.systemPrompt().contains("verified skill prompt"));
		assertFalse(request.userMessage().contains("verified skill prompt"));
		assertFalse(request.contextFragments().isEmpty());
		assertEquals("C1", request.contextFragments().get(0).citationKey());
		List<com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope> deltaEvents = fixture.runs
			.findAfter(runId, 0, 100)
			.stream()
			.filter(event -> event
				.type() == com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType.RESPONSE_DELTA)
			.toList();
		assertEquals(1, deltaEvents.size());
		assertEquals("native answer", deltaEvents.get(0).data().get("delta"));
		assertEquals(List.of("skill-1"),
				fixture.engine.awaitExchange(fixture.session.assistantSessionId(), "skill", Duration.ofSeconds(1))
					.executionTrace()
					.activatedSkillCodes());
		assertEquals(1, fixture.skills.resolutions.get());
	}

	@Test
	void droppedDispatchSignalIsRecoveredByPendingScan() {
		Fixture fixture = new Fixture(okProvider(), new DropFirstThenDirectDispatch());
		String runId = fixture.start("pending", "hello", List.of(), true, List.of());
		assertEquals(RunStatus.ACCEPTED, fixture.runs.findById(runId).orElseThrow().status());

		fixture.engine.scanPending(20);

		assertEquals(RunStatus.COMPLETED, fixture.runs.findById(runId).orElseThrow().status());
		assertEquals(1, fixture.gateway.invocations.get());
	}

	@Test
	void expiredClaimsRetryOnlyBeforeModelInvocation() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		String retryable = fixture.start("retryable", "hello", List.of(), true, List.of());
		assertTrue(fixture.runs.claimPending(retryable, "lost-owner", Instant.now().minusSeconds(1)));

		fixture.engine.recoverExpired(Instant.now(), 20);

		assertEquals(DispatchStatus.PENDING, fixture.runs.findById(retryable).orElseThrow().dispatchStatus());
		String charged = fixture.start("charged", "hello", List.of(), true, List.of());
		assertTrue(fixture.runs.claimPending(charged, "lost-owner", Instant.now().minusSeconds(1)));
		assertTrue(fixture.runs.markModelInvoked(charged, "lost-owner", Instant.now().minusSeconds(2)));

		fixture.engine.recoverExpired(Instant.now(), 20);

		AssistantRunRecord failed = fixture.runs.findById(charged).orElseThrow();
		assertEquals(RunStatus.FAILED, failed.status());
		assertEquals("EXECUTOR_LOST", failed.errorCode());
		assertEquals(0, fixture.gateway.invocations.get());
	}

	@Test
	void staleOwnerCannotMarkModelInvokedAndRenewedLeaseCannotBeFailedByOldRecoveryObservation() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		String runId = fixture.start("lease-cas", "hello", List.of(), true, List.of());
		Instant expiredLease = Instant.now().minusSeconds(1);
		assertTrue(fixture.runs.claimPending(runId, "current-owner", expiredLease));
		assertFalse(fixture.runs.markModelInvoked(runId, "stale-owner", Instant.now().minusSeconds(2)));
		assertTrue(fixture.runs.markModelInvoked(runId, "current-owner", Instant.now().minusSeconds(2)));

		Instant recoveryStartedAt = Instant.now();
		assertTrue(fixture.runs.renewLease(runId, "current-owner", recoveryStartedAt.plusSeconds(30)));
		assertFalse(fixture.runs.failExpiredInvokedAtomic(runId, expiredLease, recoveryStartedAt, null, null,
				"EXECUTOR_LOST", "lost", recoveryStartedAt));
		assertEquals(RunStatus.MODEL_STREAMING, fixture.runs.findById(runId).orElseThrow().status());
	}

	@Test
	void runSnapshotLookupRequiresTenantUserSessionAndRunToMatch() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		String runId = fixture.start("owned", "hello", List.of(), true, List.of());
		IdentityEnvelope otherUser = new IdentityEnvelope("tenant-1", "user-2", "org-1", List.of("org-1"), List.of(),
				List.of(), "corr-2", "assistant");

		ResponseStatusException error = assertThrows(ResponseStatusException.class,
				() -> fixture.engine.requireOwnedRun(otherUser, fixture.session.assistantSessionId(), runId));

		assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
	}

	@Test
	void pendingCancellationIsDurableImmediateAndIdempotent() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		String runId = fixture.start("cancel-pending", "hello", List.of(), true, List.of());

		var first = fixture.engine.requestCancel(fixture.identity, fixture.session.assistantSessionId(), runId);
		var second = fixture.engine.requestCancel(fixture.identity, fixture.session.assistantSessionId(), runId);

		assertTrue(first.newlyRequested());
		assertEquals(RunStatus.CANCELLED, first.status());
		assertFalse(second.newlyRequested());
		assertEquals(RunStatus.CANCELLED, second.status());
		assertEquals(List.of("RUN_ACCEPTED", "RUN_CANCEL_REQUESTED", "RUN_CANCELLED"),
				fixture.runs.findAfter(runId, 0, 20).stream().map(event -> event.type().name()).toList());
		assertEquals(0, fixture.gateway.invocations.get());
	}

	@Test
	void cancellationDuringContextLoadingConvergesAfterProviderReturns() throws Exception {
		CountDownLatch queryStarted = new CountDownLatch(1);
		CountDownLatch releaseQuery = new CountDownLatch(1);
		Fixture fixture = new Fixture(blockingProvider(queryStarted, releaseQuery), new DroppingDispatch());
		String runId = fixture.start("cancel-context", "hello", List.of(ProviderId.KNOWLEDGE), true, List.of());
		Thread worker = new Thread(() -> fixture.engine.executePending(runId));
		worker.start();
		assertTrue(queryStarted.await(2, TimeUnit.SECONDS));

		var cancellation = fixture.engine.requestCancel(fixture.identity, fixture.session.assistantSessionId(), runId);
		assertEquals(RunStatus.CANCEL_REQUESTED, cancellation.status());
		releaseQuery.countDown();
		worker.join(2000);

		assertFalse(worker.isAlive());
		assertEquals(RunStatus.CANCELLED, fixture.runs.findById(runId).orElseThrow().status());
		assertEquals("RUN_CANCELLED",
				fixture.runs.findAfter(runId, 0, 100)
					.get(fixture.runs.findAfter(runId, 0, 100).size() - 1)
					.type()
					.name());
		assertEquals(0, fixture.gateway.invocations.get());
	}

	@Test
	void expiredClaimWithCancellationRequestCompletesInsteadOfBeingRequeued() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		String runId = fixture.start("cancel-expired", "hello", List.of(), true, List.of());
		assertTrue(fixture.runs.claimPending(runId, "expired-owner", Instant.now().minusSeconds(1)));

		var request = fixture.engine.requestCancel(fixture.identity, fixture.session.assistantSessionId(), runId);
		assertEquals(RunStatus.CANCEL_REQUESTED, request.status());
		fixture.engine.recoverExpired(Instant.now(), 10);

		assertEquals(RunStatus.CANCELLED, fixture.runs.findById(runId).orElseThrow().status());
		assertEquals(DispatchStatus.FINISHED, fixture.runs.findById(runId).orElseThrow().dispatchStatus());
		assertEquals(0, fixture.gateway.invocations.get());
	}

	@Test
	void eventCleanupDeletesOnlyEventsForTerminalRuns() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		String completedRunId = fixture.start("cleanup-completed", "hello", List.of(), true, List.of());
		fixture.engine.executePending(completedRunId);
		String activeRunId = fixture.start("cleanup-active", "hello", List.of(), true, List.of());

		int deleted = fixture.runs.deleteTerminalEventsBefore(Instant.now().plusSeconds(1), 100);

		assertTrue(deleted > 0);
		assertTrue(fixture.runs.findAfter(completedRunId, 0, 100).isEmpty());
		assertEquals(List.of("RUN_ACCEPTED"),
				fixture.runs.findAfter(activeRunId, 0, 100).stream().map(event -> event.type().name()).toList());
	}

	@Test
	void streamingCancellationStopsUpstreamAndPreservesPartialDraft() throws Exception {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		CountDownLatch upstreamCancelled = new CountDownLatch(1);
		String partial = "p".repeat(600);
		fixture.gateway.response = Flux.just(new ModelChunk("placeholder", 0, partial, false, ""))
			.transformDeferred(flux -> flux.map(chunk -> new ModelChunk(fixture.gateway.lastRequest.get().runId(),
					chunk.sequence(), chunk.delta(), chunk.terminal(), chunk.finishReason())))
			.concatWith(Flux.never())
			.doOnCancel(upstreamCancelled::countDown);
		String runId = fixture.start("cancel-stream", "hello", List.of(), true, List.of());
		Thread worker = new Thread(() -> fixture.engine.executePending(runId));
		worker.start();
		awaitStatus(fixture.runs, runId, RunStatus.MODEL_STREAMING, Duration.ofSeconds(2));
		awaitDraft(fixture.runs, runId, partial, Duration.ofSeconds(2));

		fixture.engine.requestCancel(fixture.identity, fixture.session.assistantSessionId(), runId);
		worker.join(2000);

		assertFalse(worker.isAlive());
		assertTrue(upstreamCancelled.getCount() == 0);
		AssistantRunRecord cancelled = fixture.runs.findById(runId).orElseThrow();
		assertEquals(RunStatus.CANCELLED, cancelled.status());
		assertEquals(partial, cancelled.draftContent());
		var events = fixture.runs.findAfter(runId, 0, 100);
		assertEquals("RUN_CANCELLED", events.get(events.size() - 1).type().name());
	}

	private void awaitStatus(InMemoryAssistantRunStore runs, String runId, RunStatus expected, Duration timeout)
			throws InterruptedException {
		Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			if (runs.findById(runId).orElseThrow().status() == expected) {
				return;
			}
			Thread.sleep(5);
		}
		throw new AssertionError("Run did not reach " + expected);
	}

	private void awaitDraft(InMemoryAssistantRunStore runs, String runId, String expected, Duration timeout)
			throws InterruptedException {
		Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			if (expected.equals(runs.findById(runId).orElseThrow().draftContent())) {
				return;
			}
			Thread.sleep(5);
		}
		throw new AssertionError("Run did not persist the expected draft");
	}

	@Test
	void contextBudgetDropsWholeFragmentsInsteadOfOverflowingTheModelWindow() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		fixture.gateway.contextWindow = 600;
		fixture.gateway.maxOutputTokens = 512;
		String runId = fixture.start("budget", "hello", List.of(ProviderId.KNOWLEDGE), true, List.of());

		fixture.engine.executePending(runId);

		assertTrue(fixture.gateway.lastRequest.get().contextFragments().isEmpty());
		assertEquals(RunStatus.COMPLETED, fixture.runs.findById(runId).orElseThrow().status());
	}

	@Test
	void modelPolicyReceivesTheHighestActualFragmentClassification() {
		Fixture fixture = new Fixture(classifiedProvider(DataClassification.INTERNAL), new DroppingDispatch());
		String runId = fixture.start("classification", "hello", List.of(ProviderId.KNOWLEDGE), true, List.of());

		fixture.engine.executePending(runId);

		assertEquals(DataClassification.INTERNAL, fixture.gateway.lastResolution.get().contextClassification());
	}

	@Test
	void invalidModelStreamSequenceFailsInsteadOfPublishingCorruptOutput() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		fixture.gateway.response = Flux.just(new ModelChunk("wrong-run", 0, "bad", false, ""),
				new ModelChunk("wrong-run", 1, "", true, "STOP"));
		String runId = fixture.start("invalid-stream", "hello", List.of(), true, List.of());

		fixture.engine.executePending(runId);

		AssistantRunRecord failed = fixture.runs.findById(runId).orElseThrow();
		assertEquals(RunStatus.FAILED, failed.status());
		assertEquals("MODEL_STREAM_INVALID", failed.errorCode());
		assertTrue(fixture.runs.findAfter(runId, 0, 100)
			.stream()
			.noneMatch(event -> event
				.type() == com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType.RESPONSE_DELTA));
	}

	@Test
	void linkageErrorFailsRunInsteadOfLeavingItClaimedForRecovery() {
		Fixture fixture = new Fixture(okProvider(), new DroppingDispatch());
		fixture.gateway.resolutionError = new NoClassDefFoundError("missing-model-contract");
		String runId = fixture.start("linkage-error", "hello", List.of(), true, List.of());

		fixture.engine.executePending(runId);

		AssistantRunRecord failed = fixture.runs.findById(runId).orElseThrow();
		assertEquals(RunStatus.FAILED, failed.status());
		assertEquals(DispatchStatus.FINISHED, failed.dispatchStatus());
		assertEquals("NATIVE_RUNTIME_LINKAGE_FAILED", failed.errorCode());
	}

	private String startAfter(CountDownLatch latch, Fixture fixture, String clientId, String content) {
		try {
			latch.await();
			return fixture.start(clientId, content, List.of(), true, List.of());
		}
		catch (InterruptedException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static AssistantContextProvider okProvider() {
		return provider(ProviderStatus.OK, new AtomicInteger());
	}

	private static PublishedWorkflowCapability marketplaceWorkflow(String tenantId) {
		WorkflowCapabilitySnapshot snapshot = new WorkflowCapabilitySnapshot("market-agent-1", 101L, "v1", "水源水质监测智能体",
				"查询并分析水源水质监测数据", List.of("今天的水源水质如何", "水质如何"), List.of("用于水源水质指标、风险与建议分析"), "input-v1", "output-v1",
				"workflow-1", "digest-1");
		return new PublishedWorkflowCapability(tenantId, snapshot, true, false, true, true, SideEffectClass.READ_ONLY,
				DataClassification.INTERNAL, List.of());
	}

	private static AssistantContextProvider blockingProvider(CountDownLatch queryStarted, CountDownLatch releaseQuery) {
		return new AssistantContextProvider() {
			@Override
			public ProviderDescriptor describe() {
				return okProvider().describe();
			}

			@Override
			public ProviderResult query(ProviderQuery request) {
				queryStarted.countDown();
				try {
					if (!releaseQuery.await(2, TimeUnit.SECONDS)) {
						throw new IllegalStateException("provider release timed out");
					}
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("provider interrupted", ex);
				}
				return provider(ProviderStatus.OK, new AtomicInteger()).query(request);
			}
		};
	}

	private static AssistantContextProvider classifiedProvider(DataClassification classification) {
		return new AssistantContextProvider() {
			@Override
			public ProviderDescriptor describe() {
				return new ProviderDescriptor(AssistantContextContract.VERSION, ProviderId.KNOWLEDGE, "Knowledge", "",
						"knowledge", new ProviderCapabilities(true, true, true, false, true, false, false,
								List.of("DOCUMENT"), List.of()),
						new ProviderLimits(20, 12000, 3000));
			}

			@Override
			public ProviderResult query(ProviderQuery request) {
				Citation citation = new Citation("citation-1", ProviderId.KNOWLEDGE, "DOCUMENT", "doc-1", "v1", "route",
						"knowledge", Map.of());
				ContextFragment fragment = new ContextFragment("fragment-1", ProviderId.KNOWLEDGE, "DOCUMENT", "doc-1",
						"v1", FactType.SOURCE_EVIDENCE, "Title", "Evidence", Instant.now(), Instant.now(), "hash",
						citation, classification, 2);
				return new ProviderResult(AssistantContextContract.VERSION, ProviderId.KNOWLEDGE, ProviderStatus.OK,
						Instant.now(), 1, 0, false, List.of(fragment), List.of());
			}
		};
	}

	private static AssistantContextProvider provider(ProviderStatus status, AtomicInteger queries) {
		return new AssistantContextProvider() {
			@Override
			public ProviderDescriptor describe() {
				return new ProviderDescriptor(AssistantContextContract.VERSION, ProviderId.KNOWLEDGE, "Knowledge", "",
						"knowledge", new ProviderCapabilities(true, true, true, false, true, false, false,
								List.of("DOCUMENT"), List.of()),
						new ProviderLimits(20, 12000, 3000));
			}

			@Override
			public ProviderResult query(ProviderQuery request) {
				queries.incrementAndGet();
				if (status == ProviderStatus.UNAVAILABLE) {
					return ProviderResult.unavailable(ProviderId.KNOWLEDGE, "down");
				}
				Citation citation = new Citation("citation-1", ProviderId.KNOWLEDGE, "DOCUMENT", "doc-1", "v1", "route",
						"knowledge", Map.of());
				ContextFragment fragment = new ContextFragment("fragment-1", ProviderId.KNOWLEDGE, "DOCUMENT", "doc-1",
						"v1", FactType.SOURCE_EVIDENCE, "Title", "Evidence", Instant.now(), Instant.now(), "hash",
						citation);
				return new ProviderResult(AssistantContextContract.VERSION, ProviderId.KNOWLEDGE, status, Instant.now(),
						1, 0, false, List.of(fragment), List.of());
			}
		};
	}

	private static final class Fixture {

		private final IdentityEnvelope identity = new IdentityEnvelope("tenant-1", "user-1", "org-1", List.of("org-1"),
				List.of(), List.of(), "corr-1", "assistant");

		private final InMemoryAssistantSessionRepository sessions = new InMemoryAssistantSessionRepository();

		private final InMemoryAssistantRunStore runs = new InMemoryAssistantRunStore(sessions);

		private final RecordingGateway gateway = new RecordingGateway();

		private final SkillCatalog skills = new SkillCatalog();

		private final AssistantSession session;

		private final AssistantRunEngine engine;

		private final TestRunEventBus eventBus = new TestRunEventBus();

		private Fixture(AssistantContextProvider provider, AssistantRunDispatchService dispatch) {
			this(provider, dispatch, false);
		}

		private Fixture(AssistantContextProvider provider, AssistantRunDispatchService dispatch, boolean semantic) {
			this(provider, dispatch, semantic, identity -> List.of(), new UnavailableAssistantWorkflowExecutor());
		}

		private Fixture(AssistantContextProvider provider, AssistantRunDispatchService dispatch, boolean semantic,
				AssistantWorkflowCatalog workflowCatalog, AssistantWorkflowExecutor workflowExecutor) {
			session = sessions
				.saveSession(new AssistantSession("session-1", identity.tenantId(), identity.userId(), "Test",
						SessionStatus.ACTIVE, List.of(ProviderId.KNOWLEDGE), null, 1, Instant.now(), Instant.now()));
			AssistantProviderRegistry registry = new AssistantProviderRegistry(List.of(provider));
			AssistantContextOrchestrator orchestrator = new AssistantContextOrchestrator(registry);
			AssistantNativeProfileResolver resolver = (runId, currentIdentity, requireStreaming, classification,
					containsContext) -> new AssistantNativeProfileResolver.ResolvedNativeProfile("1", "Native", null,
							gateway.resolve(new ModelResolutionRequest(AssistantModelContract.VERSION, runId,
									currentIdentity, null, requireStreaming, classification, containsContext)));
			if (!semantic) {
				engine = new AssistantRunEngine(runs, sessions, new AssistantRunEventService(runs, eventBus), dispatch,
						orchestrator, skills, resolver, gateway, new AssistantIntentPolicy(),
						new AssistantPromptComposer());
			}
			else {
				ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
				StaticListableBeanFactory beanFactory = new StaticListableBeanFactory(
						Map.of("workflowCatalog", workflowCatalog));
				AssistantCapabilityDirectory directory = new AssistantCapabilityDirectory(
						new AssistantProviderDirectory(registry),
						beanFactory.getBeanProvider(AssistantWorkflowCatalog.class), objectMapper);
				AssistantExecutionPlanValidator validator = new AssistantExecutionPlanValidator(objectMapper);
				Map<String, Object> resultSchema = Map.of("type", "object", "properties",
						Map.of("sections", Map.of("type", "array", "items", Map.of("type", "object")), "claims",
								Map.of("type", "array", "items", Map.of("type", "object"))));
				String resultSchemaId = new AssistantAnalysisResultValidator(objectMapper,
						new AssistantJsonSchemaValidator())
					.schemaId(resultSchema);
				AssistantAnalysisSkillCatalog analysisCatalog = currentIdentity -> new AssistantAnalysisSkillCatalog.AnalysisSkillSnapshot(
						"analysis-skill", 99L, "1.0.0", "统一总结", "按证据总结", sha256("按证据总结"), "a".repeat(64), Map.of(),
						sha256("{}"), resultSchema, resultSchemaId, Set.of());
				engine = new AssistantRunEngine(runs, sessions, new AssistantRunEventService(runs, eventBus), dispatch,
						orchestrator, skills, resolver, gateway, new AssistantIntentPolicy(),
						new AssistantPromptComposer(), directory, new AssistantSemanticPlanner(validator),
						analysisCatalog, runs, runs, workflowExecutor,
						new AssistantAnalysisResultValidator(objectMapper, new AssistantJsonSchemaValidator()),
						objectMapper);
			}
		}

		private String start(String clientId, String content, List<ProviderId> providers, boolean explicit,
				List<String> skills) {
			return engine
				.startOrGet(identity, session, clientId, content, providers, explicit, null, skills, null, null, null)
				.runId();
		}

	}

	private static final class TestRunEventBus implements AssistantRunEventBus {

		private final Map<String, CopyOnWriteArrayList<Consumer<com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope>>> subscribers = new ConcurrentHashMap<>();

		@Override
		public void publish(com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope event) {
			subscribers.getOrDefault(event.runId(), new CopyOnWriteArrayList<>())
				.forEach(consumer -> consumer.accept(event));
		}

		@Override
		public Subscription subscribe(String runId,
				Consumer<com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope> consumer,
				Runnable overflowHandler) {
			subscribers.computeIfAbsent(runId, ignored -> new CopyOnWriteArrayList<>()).add(consumer);
			return () -> subscribers.getOrDefault(runId, new CopyOnWriteArrayList<>()).remove(consumer);
		}

	}

	private static final class RecordingGateway implements AssistantModelGateway {

		private final AtomicInteger invocations = new AtomicInteger();

		private final AtomicReference<AssistantModelRequest> lastRequest = new AtomicReference<>();

		private final AtomicReference<ModelResolutionRequest> lastResolution = new AtomicReference<>();

		private int contextWindow = 8192;

		private int maxOutputTokens = 1024;

		private Flux<ModelChunk> response;

		private LinkageError resolutionError;

		private boolean semanticResponse;

		@Override
		public ResolvedAssistantModel resolve(ModelResolutionRequest request) {
			if (resolutionError != null) {
				throw resolutionError;
			}
			lastResolution.set(request);
			return new ResolvedAssistantModel(AssistantModelContract.VERSION, "model-1", "Native Model", "local",
					new ModelCapabilities(false, contextWindow, maxOutputTokens, true), new AssistantEgress(
							DeploymentDomain.INTERNAL, "local", true, DataClassification.SENSITIVE, "test"));
		}

		@Override
		public Flux<ModelChunk> stream(AssistantModelRequest request) {
			invocations.incrementAndGet();
			lastRequest.set(request);
			if (response != null) {
				return response;
			}
			if (semanticResponse) {
				String json = "{\"contractVersion\":\"assistant-analysis-result/v1\",\"runId\":\"" + request.runId()
						+ "\",\"summary\":\"Evidence\",\"sections\":[{\"sectionId\":\"analysis-summary\",\"title\":\"分析总结\",\"content\":\"\",\"claimIds\":[\"c1\"]}],\"claims\":[{\"claimId\":\"c1\",\"type\":\"FACT\",\"text\":\"Evidence\",\"citationKeys\":[\"C1\"],\"artifactIds\":[]}],\"warnings\":[],\"sourceCoverage\":{\"KNOWLEDGE\":\"used\"},\"resultDigest\":null}";
				return Flux.just(new ModelChunk(request.runId(), 0, json, false, ""),
						new ModelChunk(request.runId(), 1, "", true, "STOP"));
			}
			return Flux.just(new ModelChunk(request.runId(), 0, "native ", false, ""),
					new ModelChunk(request.runId(), 1, "answer", false, ""),
					new ModelChunk(request.runId(), 2, "", true, "STOP"));
		}

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

	private static final class SkillCatalog implements AssistantPromptSkillCatalog {

		private final AtomicInteger resolutions = new AtomicInteger();

		private final PromptSkillSnapshot snapshot = new PromptSkillSnapshot("skill-1", "release-1", "1.0.0", "Skill",
				"icon", "verified skill prompt", "digest", "sha", "policy-v1", Set.of());

		@Override
		public List<PromptSkillDescriptor> listPromptSkills(IdentityEnvelope identity) {
			return List.of();
		}

		@Override
		public PromptSkillSnapshot resolvePromptSkill(IdentityEnvelope identity, String skillId) {
			resolutions.incrementAndGet();
			assertEquals("skill-1", skillId);
			return snapshot;
		}

		@Override
		public void savePromptSkillPreference(IdentityEnvelope identity, List<String> selectedSkillIds) {
		}

	}

	@Test
	void saturatedDispatchPersistsStableFailureWithoutInvokingModel() {
		var dispatch = new AssistantRunDispatchService() {
			@Override
			public void dispatch(String runId, Consumer<String> worker) {
				throw new java.util.concurrent.RejectedExecutionException("full");
			}
		};
		Fixture fixture = new Fixture(okProvider(), dispatch);
		String runId = fixture.start("busy", "你好", List.of(), false, List.of());
		var run = fixture.runs.findById(runId).orElseThrow();
		assertEquals(RunStatus.FAILED, run.status());
		assertEquals("ASSISTANT_QUEUE_FULL", run.errorCode());
		assertNull(run.modelInvokedAt());
		assertEquals(runId, fixture.start("busy", "你好", List.of(), false, List.of()));
		assertTrue(fixture.runs.findAfter(runId, 0, 100)
			.stream()
			.anyMatch(event -> event.type() == RunEventType.RUN_FAILED));
		dispatch.destroy();
	}

	private static class DroppingDispatch extends AssistantRunDispatchService {

		@Override
		public void dispatch(String runId, Consumer<String> worker) {
		}

	}

	private static final class DropFirstThenDirectDispatch extends AssistantRunDispatchService {

		private final AtomicInteger signals = new AtomicInteger();

		@Override
		public void dispatch(String runId, Consumer<String> worker) {
			if (signals.incrementAndGet() > 1) {
				worker.accept(runId);
			}
		}

	}

}
