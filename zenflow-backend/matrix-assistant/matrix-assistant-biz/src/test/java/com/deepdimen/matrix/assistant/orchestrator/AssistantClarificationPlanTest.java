package com.deepdimen.matrix.assistant.orchestrator;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.*;
import com.deepdimen.matrix.assistant.capability.AssistantCapabilityDirectory.FrozenCapabilityCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantClarificationPlanTest {

	private final Instant now = Instant.parse("2026-09-26T00:00:00Z");

	private final ObjectMapper mapper = new ObjectMapper();

	private final AssistantExecutionPlanValidator validator = new AssistantExecutionPlanValidator(mapper);

	private final ClarificationCandidate candidate = new ClarificationCandidate(ExecutionStepType.DIFY_WORKFLOW,
			"water", 1L, "水质分析");

	private FrozenCapabilityCatalog catalog() {
		return new FrozenCapabilityCatalog(List.of(), List.of(new WorkflowCapabilitySnapshot("water", 1L, "v1", "水质分析",
				"水质", List.of(), List.of(), "in", "out", "app", "digest")), "catalog", now);
	}

	private ClarificationState state(int count, boolean manual, List<ClarificationCandidate> candidates, long expiry) {
		return new ClarificationState("origin", count, manual, "需要分析哪个对象？", candidates, expiry);
	}

	private ExecutionPlan plan(ClarificationState state) {
		return new ExecutionPlan(null, "plan", "run", SourceMode.AUTO, PlanningDisposition.AMBIGUOUS, List.of(), null,
				List.of("NEEDS_CLARIFICATION"), "catalog", null, now,
				new RouteDecision(ConversationIntent.AMBIGUOUS, ExecutionMode.CLARIFICATION, List.of(), List.of(),
						RouteTriggerType.PLANNER, null, null, true, false, "NEEDS_CLARIFICATION"),
				state);
	}

	@Test
	void generatorAsksOnceThenKeepsManualChoiceWithoutExtendingExpiry() {
		var generator = new AssistantClarificationPlanner(validator);
		var first = generator.create("one", "水质分析", catalog(), null, now).orElseThrow();
		assertEquals(1, first.clarification().clarificationCount());
		assertFalse(first.clarification().manualSelectionRequired());
		assertTrue(first.clarification().question().contains("水质分析"));
		var second = generator.create("two", "都看看", catalog(), first, now.plusSeconds(1)).orElseThrow();
		var third = generator.create("three", "还是都看看", catalog(), second, now.plusSeconds(2)).orElseThrow();
		assertEquals(2, third.clarification().clarificationCount());
		assertTrue(third.clarification().manualSelectionRequired());
		assertEquals("one", third.clarification().originRunId());
		assertEquals(first.clarification().expiresAtEpochMillis(), third.clarification().expiresAtEpochMillis());
		assertTrue(third.steps().isEmpty());
		assertThrows(IllegalArgumentException.class,
				() -> generator.create("expired", "继续", catalog(), first, now.plusSeconds(600)));
	}

	@Test
	void generatorNeverInventsUnmatchedCandidates() {
		var generator = new AssistantClarificationPlanner(validator);
		assertTrue(generator.create("missing", "秘密工资", catalog(), null, now).isEmpty());
		var first = generator.create("one", "水质分析", catalog(), null, now).orElseThrow();
		var revoked = new FrozenCapabilityCatalog(List.of(), List.of(), "changed", now);
		assertThrows(IllegalArgumentException.class,
				() -> generator.create("two", "继续", revoked, first, now.plusSeconds(1)));
	}

	@Test
	void validClarificationPersistsAndStateChangesDigest() throws Exception {
		var first = validator.validateAndDigest(
				plan(state(1, false, List.of(candidate), now.plusSeconds(600).toEpochMilli())), catalog());
		var second = validator.validateAndDigest(
				plan(state(2, true, List.of(candidate), now.plusSeconds(600).toEpochMilli())), catalog());
		assertTrue(first.steps().isEmpty());
		assertEquals(1, first.clarification().clarificationCount());
		assertNotEquals(first.planDigest(), second.planDigest());
		var restored = mapper.readValue(mapper.writeValueAsBytes(first.clarification()), ClarificationState.class);
		assertEquals(first.clarification(), restored);
	}

	@Test
	void rejectsForeignStaleAndForgedDisplayCandidates() {
		for (var choice : List.of(new ClarificationCandidate(ExecutionStepType.DIFY_WORKFLOW, "secret", 1L, "水质分析"),
				new ClarificationCandidate(ExecutionStepType.DIFY_WORKFLOW, "water", 2L, "水质分析"),
				new ClarificationCandidate(ExecutionStepType.DIFY_WORKFLOW, "water", 1L, "伪造介绍"),
				new ClarificationCandidate(ExecutionStepType.MATRIX_ANALYSIS, "water", 1L, "水质分析"))) {
			assertThrows(IllegalArgumentException.class, () -> validator.validateAndDigest(
					plan(state(1, false, List.of(choice), now.plusSeconds(600).toEpochMilli())), catalog()));
		}
	}

	@Test
	void enforcesBoundedCountCandidatesAndExpiry() {
		for (var state : List.of(state(0, false, List.of(candidate), now.plusSeconds(600).toEpochMilli()),
				state(2, false, List.of(candidate), now.plusSeconds(600).toEpochMilli()),
				state(3, true, List.of(candidate), now.plusSeconds(600).toEpochMilli()),
				state(1, false, List.of(), now.plusSeconds(600).toEpochMilli()),
				state(1, false, List.of(candidate, candidate), now.plusSeconds(600).toEpochMilli()),
				state(1, false, List.of(candidate), now.toEpochMilli()),
				state(1, false, List.of(candidate), now.plusSeconds(1801).toEpochMilli())))
			assertThrows(IllegalArgumentException.class, () -> validator.validateAndDigest(plan(state), catalog()));
	}

	@Test
	void clarificationCannotCarryExecutableSteps() {
		var original = plan(state(1, false, List.of(candidate), now.plusSeconds(600).toEpochMilli()));
		var executable = new ExecutionPlan(original.contractVersion(), original.planId(), original.runId(),
				original.sourceMode(), original.disposition(),
				List.of(new ExecutionStep("workflow", ExecutionStepType.DIFY_WORKFLOW, "water", 1L, true, List.of(),
						null, null, "out")),
				null, List.of(), original.catalogDigest(), null, now, original.routeDecision(),
				original.clarification());
		assertThrows(IllegalArgumentException.class, () -> validator.validateAndDigest(executable, catalog()));
	}

	@Test
	void legacyConstructorKeepsClarificationAbsent() {
		var old = new ExecutionPlan(null, "plan", "run", SourceMode.AUTO, PlanningDisposition.MATCH, List.of(), null,
				List.of(), "catalog", null, now);
		assertNull(old.clarification());
	}

}
