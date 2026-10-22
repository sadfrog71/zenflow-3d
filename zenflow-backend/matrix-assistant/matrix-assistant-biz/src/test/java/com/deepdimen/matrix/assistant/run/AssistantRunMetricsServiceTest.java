package com.deepdimen.matrix.assistant.run;

import static org.junit.jupiter.api.Assertions.*;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ConversationIntent;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionMode;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventEnvelope;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunEventType;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import com.deepdimen.matrix.assistant.run.AssistantRunMetricsService.Metric;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistantRunMetricsServiceTest {

	private final AssistantRunMetricsService service = new AssistantRunMetricsService();

	private final Instant origin = Instant.parse("2026-09-26T00:00:00Z");

	private AssistantRunMetricsService.Input run(String id, long total, long seq, ExecutionMode mode) {
		return new AssistantRunMetricsService.Input(id, RunStatus.COMPLETED, ConversationIntent.BUSINESS_ANALYSIS, mode,
				origin, origin.plusMillis(100), origin.plusMillis(total), seq);
	}

	private RunEventEnvelope event(long seq, RunEventType type, long millis) {
		return new RunEventEnvelope(1, "r", seq, type, origin.plusMillis(millis), "private-correlation",
				Map.of("delta", "PRIVATE ANSWER", "prompt", "PRIVATE QUESTION"));
	}

	private List<RunEventEnvelope> facts() {
		return List.of(event(1, RunEventType.ROUTE_DECIDED, 200), event(2, RunEventType.CONTEXT_COMPLETED, 300),
				event(3, RunEventType.WORKFLOW_STARTED, 350), event(4, RunEventType.WORKFLOW_COMPLETED, 500),
				event(5, RunEventType.ANALYSIS_STARTED, 550), event(6, RunEventType.MODEL_FIRST_CHUNK, 900),
				event(7, RunEventType.RESPONSE_DELTA, 1200), event(8, RunEventType.RESPONSE_DELTA, 1300));
	}

	@Test
	void directContextIsNotApplicableWhileUnrecordedBusinessContextIsUnavailable() {
		var direct = service.calculate(run("direct", 1000, 0, ExecutionMode.DIRECT_CHAT), List.of());
		var business = service.calculate(run("business", 1000, 0, ExecutionMode.PROVIDER_QUERY), List.of());
		var stat = service.summarize(List.of(direct, business)).metrics().get(Metric.CONTEXT_TIME);
		assertEquals(0, stat.sampleCount());
		assertEquals(1, stat.notApplicableCount());
		assertEquals(1, stat.unavailableCount());
		assertNull(stat.p50Millis());
		assertEquals(2, stat.sampleCount() + stat.notApplicableCount() + stat.unavailableCount());
	}

	@Test
	void queryGroupsByIntentAndProviderModelPairIncludingUnknownHistory() {
		var identity = new com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope("t", "u", null,
				List.of(), List.of(), List.of(), "", "");
		var inputs = List
			.of(new AssistantRunMetricsService.Input("r1", RunStatus.COMPLETED, ConversationIntent.GENERAL_QA,
					ExecutionMode.DIRECT_CHAT, origin, origin, origin.plusMillis(100), 0, null, null, null, "p", "a"),
					new AssistantRunMetricsService.Input("r2", RunStatus.COMPLETED, ConversationIntent.GENERAL_QA,
							ExecutionMode.DIRECT_CHAT, origin, origin, origin.plusMillis(300), 0, null, null, null, "p",
							"b"),
					new AssistantRunMetricsService.Input("r3", RunStatus.COMPLETED,
							ConversationIntent.BUSINESS_ANALYSIS, ExecutionMode.PROVIDER_QUERY, origin, origin,
							origin.plusMillis(500), 0, null, null, null, "p", "a"),
					new AssistantRunMetricsService.Input("r4", RunStatus.FAILED, null, null, origin, null, null, 0,
							null, null, null, null, null));
		AssistantRunMetricsRepository repository = (tenant, from, to, limit) -> inputs.stream()
			.map(input -> new AssistantRunMetricsRepository.Sample(tenant, input, List.of()))
			.toList();
		var result = service.query(identity, repository, origin, origin.plusSeconds(1), 100);
		assertEquals(4, result.summary().runCount());
		assertEquals(3, result.byIntent().size());
		assertEquals(3, result.byModel().size());
		var general = result.byIntent()
			.stream()
			.filter(group -> group.intent() == ConversationIntent.GENERAL_QA)
			.findFirst()
			.orElseThrow();
		assertEquals(2, general.summary().runCount());
		assertEquals(300L, general.summary().metrics().get(Metric.TOTAL).p95Millis());
		var modelA = result.byModel().stream().filter(group -> "a".equals(group.model())).findFirst().orElseThrow();
		assertEquals("p", modelA.provider());
		assertEquals(2, modelA.summary().runCount());
		assertEquals(500L, modelA.summary().metrics().get(Metric.TOTAL).p95Millis());
		var unknown = result.byModel().stream().filter(group -> group.model() == null).findFirst().orElseThrow();
		assertEquals(1, unknown.summary().metrics().get(Metric.TOTAL).unavailableCount());
	}

	@Test
	void durableProjectionPreservesFirstVisibleTimingAfterEventRetention() {
		var input = new AssistantRunMetricsService.Input("r", RunStatus.COMPLETED, ConversationIntent.GENERAL_QA,
				ExecutionMode.DIRECT_CHAT, origin, origin.plusMillis(100), origin.plusMillis(1000), 20,
				origin.plusMillis(200), origin.plusMillis(400), origin.plusMillis(500));
		var timing = service.calculate(input, List.of());
		assertFalse(timing.eventHistoryComplete());
		assertEquals(origin.plusMillis(500), timing.t5());
		assertEquals(500L, timing.durationsMillis().get(Metric.FIRST_VISIBLE));
		assertEquals(100L, timing.durationsMillis().get(Metric.VALIDATION_HOLD));
	}

	@Test
	void queryUsesIdentityTenantAndReportsTruncation() {
		var identity = new com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope("tenant-a",
				"user", null, List.of(), List.of(), List.of(), "", "");
		AssistantRunMetricsRepository repository = (tenant, from, to, limit) -> {
			assertEquals("tenant-a", tenant);
			assertEquals(2, limit);
			return List.of(
					new AssistantRunMetricsRepository.Sample(tenant, run("r1", 100, 0, ExecutionMode.DIRECT_CHAT),
							List.of()),
					new AssistantRunMetricsRepository.Sample(tenant, run("r2", 200, 0, ExecutionMode.DIRECT_CHAT),
							List.of()));
		};
		var result = service.query(identity, repository, origin, origin.plusSeconds(1), 1);
		assertTrue(result.truncated());
		assertEquals(1, result.summary().runCount());
	}

	@Test
	void queryRejectsCrossTenantRowsAndInvalidBounds() {
		var identity = new com.deepdimen.matrix.assistant.api.AssistantContextContract.IdentityEnvelope("tenant-a",
				"user", null, List.of(), List.of(), List.of(), "", "");
		AssistantRunMetricsRepository repository = (tenant, from, to, limit) -> List
			.of(new AssistantRunMetricsRepository.Sample("tenant-b", run("r", 100, 0, ExecutionMode.DIRECT_CHAT),
					List.of()));
		assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> service.query(identity, repository, origin, origin.plusSeconds(1), 1));
		AssistantRunMetricsRepository never = (tenant, from, to, limit) -> {
			throw new AssertionError("must reject before read");
		};
		assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> service.query(identity, never, origin, origin.plusSeconds(8 * 86400), 1));
		assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> service.query(identity, never, origin, origin.plusSeconds(1), 201));
		assertThrows(org.springframework.web.server.ResponseStatusException.class,
				() -> service.query(null, never, origin, origin.plusSeconds(1), 1));
	}

	@Test
	void reconstructsAllStagesAndUsesSourceReadyAfterWorkflows() {
		var timing = service.calculate(run("r", 2000, 8, ExecutionMode.WORKFLOW_EXECUTION), facts());
		assertEquals(100L, timing.durationsMillis().get(Metric.QUEUE_WAIT));
		assertEquals(100L, timing.durationsMillis().get(Metric.ROUTE_TIME));
		assertEquals(350L, timing.durationsMillis().get(Metric.CONTEXT_TIME));
		assertEquals(350L, timing.durationsMillis().get(Metric.MODEL_WAIT));
		assertEquals(300L, timing.durationsMillis().get(Metric.VALIDATION_HOLD));
		assertEquals(800L, timing.durationsMillis().get(Metric.REMAINING_GENERATION));
		assertEquals(1200L, timing.durationsMillis().get(Metric.FIRST_VISIBLE));
		assertEquals(2000L, timing.durationsMillis().get(Metric.TOTAL));
	}

	@Test
	void replayOrderAndDuplicatesCannotChangeFirstTimestamps() {
		var original = service.calculate(run("r", 2000, 8, ExecutionMode.WORKFLOW_EXECUTION), facts());
		var replay = new ArrayList<>(facts());
		java.util.Collections.reverse(replay);
		replay.addAll(facts());
		assertEquals(original, service.calculate(run("r", 2000, 8, ExecutionMode.WORKFLOW_EXECUTION), replay));
	}

	@Test
	void historicalGapsCannotPretendToIdentifyFirstAnswer() {
		var timing = service.calculate(run("r", 2000, 8, ExecutionMode.WORKFLOW_EXECUTION),
				List.of(event(8, RunEventType.RESPONSE_DELTA, 1300)));
		assertFalse(timing.eventHistoryComplete());
		assertNull(timing.t5());
		assertNull(timing.durationsMillis().get(Metric.FIRST_VISIBLE));
		assertEquals(2000L, timing.durationsMillis().get(Metric.TOTAL));
		var summary = service.summarize(List.of(timing));
		assertEquals(1, summary.metrics().get(Metric.FIRST_VISIBLE).unavailableCount());
		assertNull(summary.metrics().get(Metric.FIRST_VISIBLE).p50Millis());
		assertEquals(1, summary.statusCounts().get(RunStatus.COMPLETED));
	}

	@Test
	void directConversationSkipsContextStageWithoutInventingZero() {
		var timing = service.calculate(run("r", 2000, 3, ExecutionMode.DIRECT_CHAT),
				List.of(event(1, RunEventType.ROUTE_DECIDED, 200), event(2, RunEventType.MODEL_FIRST_CHUNK, 900),
						event(3, RunEventType.RESPONSE_DELTA, 910)));
		assertNull(timing.t3());
		assertNull(timing.durationsMillis().get(Metric.CONTEXT_TIME));
		assertEquals(700L, timing.durationsMillis().get(Metric.MODEL_WAIT));
	}

	@Test
	void clockReversalIsUnavailableNotZeroAndMissingTimestampIsAccepted() {
		var timing = service.calculate(run("r", 2000, 2, ExecutionMode.DIRECT_CHAT),
				List.of(event(1, RunEventType.ROUTE_DECIDED, 50),
						new RunEventEnvelope(1, "r", 2, RunEventType.MODEL_FIRST_CHUNK, null, "", Map.of())));
		assertNull(timing.durationsMillis().get(Metric.ROUTE_TIME));
		assertTrue(timing.invalidClockOrder().contains(Metric.ROUTE_TIME));
		assertNull(timing.t4());
	}

	@Test
	void percentileCountsIncludeOnlyAvailableSamplesAndRejectRepeatedRuns() {
		var samples = new ArrayList<AssistantRunMetricsService.Timing>();
		for (int i = 1; i <= 100; i++)
			samples.add(service.calculate(run("r" + i, i * 100, 0, ExecutionMode.PROVIDER_QUERY), List.of()));
		var summary = service.summarize(samples);
		var total = summary.metrics().get(Metric.TOTAL);
		assertEquals(100, total.sampleCount());
		assertEquals(5000L, total.p50Millis());
		assertEquals(9500L, total.p95Millis());
		assertEquals(9900L, total.p99Millis());
		assertEquals("r100", summary.slowRunIds().get(0));
		assertEquals(20, summary.slowRunIds().size());
		assertThrows(IllegalArgumentException.class, () -> service.summarize(List.of(samples.get(0), samples.get(0))));
	}

	@Test
	void mismatchedRunAndConflictingSeqAreRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> service.calculate(run("other", 1000, 1, ExecutionMode.PROVIDER_QUERY),
						List.of(event(1, RunEventType.ROUTE_DECIDED, 100))));
		assertThrows(IllegalArgumentException.class,
				() -> service.calculate(run("r", 1000, 1, ExecutionMode.PROVIDER_QUERY),
						List.of(event(1, RunEventType.ROUTE_DECIDED, 100), event(1, RunEventType.ROUTE_DECIDED, 200))));
	}

	@Test
	void serializationCannotCarryPromptAnswerOrCorrelationPayload() throws Exception {
		var timing = service.calculate(run("r", 2000, 8, ExecutionMode.WORKFLOW_EXECUTION), facts());
		var module = new com.fasterxml.jackson.databind.module.SimpleModule();
		module.addSerializer(Instant.class, com.fasterxml.jackson.databind.ser.std.ToStringSerializer.instance);
		var mapper = new ObjectMapper().registerModule(module);
		String json = mapper.writeValueAsString(timing) + mapper.writeValueAsString(service.summarize(List.of(timing)));
		assertFalse(json.contains("PRIVATE"));
		assertFalse(json.contains("private-correlation"));
		assertFalse(json.contains("prompt"));
		assertFalse(json.contains("delta"));
	}

}
