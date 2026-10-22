package com.deepdimen.matrix.assistant.orchestrator;

import java.util.ArrayList;
import java.util.List;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.*;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AssistantRoutingContractTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	void historySnapshotPreservesScopeAndConsumedSelectionWithoutReplacingNullScope() throws Exception {
		var scope = new com.deepdimen.matrix.assistant.api.AssistantSessionContract.ScopeHint("/station", "STATION",
				List.of("station-1"), java.util.Map.of("period", "today"));
		var selection = new ClarificationSelection("source", "a".repeat(64), ExecutionStepType.DIFY_WORKFLOW, "quality",
				7L);
		var snapshot = new RunSnapshot("run", "session", RunStatus.COMPLETED, "answer", 8, "reply", "question",
				List.of(), "skill", null, null, null, null, null, null, List.of(), SourceMode.AUTO, RunPhase.COMPLETED,
				"digest", List.of(), List.of(), null, null, scope, selection);
		var restored = mapper.readValue(mapper.writeValueAsBytes(snapshot), RunSnapshot.class);
		assertEquals(scope, restored.scopeHint());
		assertEquals(selection, restored.clarificationSelection());
		var old = mapper.readValue("{\"runId\":\"old\",\"status\":\"ACCEPTED\"}", RunSnapshot.class);
		assertNull(old.scopeHint());
		assertNull(old.clarificationSelection());
		var json = mapper.readTree(mapper.writeValueAsBytes(old));
		assertTrue(json.has("scopeHint"));
		assertTrue(json.get("scopeHint").isNull());
		assertFalse(json.has("clarificationSelection"));
	}

	@Test
	void manualSelectionRoundTripsThroughRequestAndStoredInputWhileLegacyOmitsIt() throws Exception {
		var selection = new ClarificationSelection("source", "a".repeat(64), ExecutionStepType.MATRIX_PROVIDER,
				"KNOWLEDGE", null);
		var request = new CreateRunRequest("client", "查询今天资料", List.of(), null, null, null, null, null, List.of(),
				SourceMode.AUTO, selection);
		assertEquals(selection,
				mapper.readValue(mapper.writeValueAsBytes(request), CreateRunRequest.class).clarificationSelection());
		var input = new com.deepdimen.matrix.assistant.run.AssistantRunRecord.RunInputSnapshot(null, "查询今天资料",
				List.of(), false, null, null, null, List.of(), null, null, null, List.of(), SourceMode.AUTO, selection);
		assertEquals(selection,
				mapper
					.readValue(mapper.writeValueAsBytes(input),
							com.deepdimen.matrix.assistant.run.AssistantRunRecord.RunInputSnapshot.class)
					.clarificationSelection());
		var old = mapper.readValue("{\"content\":\"old\"}",
				com.deepdimen.matrix.assistant.run.AssistantRunRecord.RunInputSnapshot.class);
		assertNull(old.clarificationSelection());
		assertFalse(mapper.readTree(mapper.writeValueAsBytes(old)).has("clarificationSelection"));
	}

	@Test
	void oldPlanWithoutRoutingRemainsReadable() throws Exception {
		ExecutionPlan plan = mapper.readValue("{\"planId\":\"old\",\"disposition\":\"MATCH\"}", ExecutionPlan.class);
		assertNull(plan.routeDecision());
		assertFalse(mapper.readTree(mapper.writeValueAsString(plan)).has("routeDecision"));
		assertEquals(SourceMode.LEGACY, plan.sourceMode());
		assertTrue(plan.steps().isEmpty());
	}

	@Test
	void routingSnapshotRoundTripsWithoutInventingScores() throws Exception {
		RouteDecision route = new RouteDecision(ConversationIntent.CAPABILITY_HELP, ExecutionMode.CAPABILITY_HELP,
				List.of(), List.of("authorized-agent"), RouteTriggerType.EXACT_AGENT_NAME, null, null, false, false,
				"AGENT_DESCRIPTION_REQUESTED");
		ExecutionPlan plan = new ExecutionPlan(null, "plan", "run", SourceMode.AUTO, PlanningDisposition.MATCH,
				List.of(), null, List.of(), "catalog", "digest", null, route);
		ExecutionPlan restored = mapper.readValue(mapper.writeValueAsString(plan), ExecutionPlan.class);
		assertEquals(route, restored.routeDecision());
		assertNull(restored.routeDecision().confidence());
	}

	@Test
	void recoveredSnapshotKeepsRoutingAndOldSnapshotsRemainReadable() throws Exception {
		RunSnapshot old = mapper.readValue("{\"runId\":\"old\",\"status\":\"ACCEPTED\"}", RunSnapshot.class);
		assertNull(old.routeDecision());
		assertEquals(SourceMode.LEGACY, old.sourceMode());
		RouteDecision route = new RouteDecision(ConversationIntent.GENERAL_QA, ExecutionMode.DIRECT_CHAT, List.of(),
				List.of(), RouteTriggerType.CASUAL_RULE, null, null, false, false, "GENERAL_QA");
		RunSnapshot snapshot = new RunSnapshot("run", "session", RunStatus.MODEL_STREAMING, "partial", 4, "response",
				"question", List.of(), null, null, null, null, null, null, null, List.of(), SourceMode.AUTO,
				RunPhase.ANALYSIS, "digest", List.of(), List.of(), route);
		RunSnapshot recovered = mapper.readValue(mapper.writeValueAsString(snapshot), RunSnapshot.class);
		assertEquals(route, recovered.routeDecision());
		assertEquals(4, recovered.lastEventSeq());
		assertEquals("partial", recovered.draftContent());
	}

	@Test
	void routingSelectionCannotBeMutatedAfterFreeze() {
		List<String> ids = new ArrayList<>(List.of("allowed"));
		RouteDecision route = new RouteDecision(ConversationIntent.BUSINESS_QUERY, ExecutionMode.PROVIDER_QUERY, ids,
				null, RouteTriggerType.PLANNER, 0.9, 0.2, true, false, "MATCH");
		ids.add("later");
		assertEquals(List.of("allowed"), route.selectedProviderIds());
		assertThrows(UnsupportedOperationException.class, () -> route.selectedProviderIds().add("other"));
	}

	@Test
	void invalidScoresAreRejected() {
		for (double value : new double[] { Double.NaN, Double.POSITIVE_INFINITY, -0.1, 1.1 }) {
			assertThrows(IllegalArgumentException.class,
					() -> new RouteDecision(ConversationIntent.AMBIGUOUS, ExecutionMode.CLARIFICATION, null, null,
							RouteTriggerType.PLANNER, value, null, true, false, "AMBIGUOUS"));
		}
	}

	@Test
	void newFactEventsRoundTripOnExistingProtocolVersion() throws Exception {
		for (RunEventType type : List.of(RunEventType.ROUTE_DECIDED, RunEventType.CLARIFICATION_REQUESTED,
				RunEventType.MODEL_FIRST_CHUNK, RunEventType.ANSWER_SECTION_VALIDATED)) {
			RunEventEnvelope event = new RunEventEnvelope(1, "run", 3, type, null, "correlation", null);
			assertEquals(event, mapper.readValue(mapper.writeValueAsString(event), RunEventEnvelope.class));
		}
	}

}
