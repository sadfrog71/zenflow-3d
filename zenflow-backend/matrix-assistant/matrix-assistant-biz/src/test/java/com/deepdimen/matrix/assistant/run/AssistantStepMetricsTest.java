package com.deepdimen.matrix.assistant.run;

import static org.junit.jupiter.api.Assertions.*;
import static com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType.*;
import static com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepStatus.*;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepStatus;
import com.deepdimen.matrix.assistant.api.AssistantRunContract.RunStatus;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class AssistantStepMetricsTest {

	private final Instant origin = Instant.parse("2026-09-26T00:00:00Z");

	private AssistantStepMetrics.Step step(String id, ExecutionStepStatus status, long end) {
		return new AssistantStepMetrics.Step("r", id, MATRIX_PROVIDER, "provider", status, origin,
				origin.plusMillis(end));
	}

	private AssistantRunMetricsRepository.Sample sample(List<AssistantStepMetrics.Step> steps, boolean truncated) {
		return new AssistantRunMetricsRepository.Sample("tenant", new AssistantRunMetricsService.Input("r",
				RunStatus.COMPLETED, null, null, origin, origin, origin.plusMillis(100), 0), List.of(), steps,
				truncated);
	}

	@Test
	void overlappingStepsAreSeparateObservationsAndFailuresRetainDuration() {
		var result = AssistantStepMetrics
			.summarize(List.of(sample(List.of(step("a", SUCCEEDED, 100), step("b", FAILED, 100)), false)));
		var stage = result.stages().get(0);
		assertEquals(2, stage.duration().sampleCount());
		assertEquals(100L, stage.duration().p95Millis());
		assertEquals(1L, stage.statuses().get(FAILED));
		assertEquals(2, result.slowSteps().size());
	}

	@Test
	void skippedUnfinishedMissingAndInvalidTimesAreNotZeroLatency() {
		var missing = new AssistantStepMetrics.Step("r", "missing", DIFY_WORKFLOW, "workflow", FAILED, null, null);
		var result = AssistantStepMetrics.summarize(List.of(sample(List.of(step("zero", SUCCEEDED, 0),
				step("skip", SKIPPED, 0), step("running", RUNNING, 50), step("reverse", FAILED, -1), missing), false)));
		var provider = result.stages().stream().filter(s -> s.type() == MATRIX_PROVIDER).findFirst().orElseThrow();
		assertEquals(1, provider.duration().sampleCount());
		assertEquals(0L, provider.duration().p50Millis());
		assertEquals(2, provider.duration().unavailableCount());
		assertEquals(1, provider.duration().notApplicableCount());
		assertEquals(1, provider.invalidClockCount());
		var workflow = result.stages().stream().filter(s -> s.type() == DIFY_WORKFLOW).findFirst().orElseThrow();
		assertNull(workflow.duration().p50Millis());
		assertEquals(1, workflow.duration().unavailableCount());
	}

	@Test
	void missingHistoryAndTruncatedHistoryAreExplicitAndSlowListIsBounded() {
		var empty = AssistantStepMetrics.summarize(List.of(sample(List.of(), false)));
		assertEquals(1, empty.runsWithoutStepRecords());
		assertTrue(empty.stages().isEmpty());
		var result = AssistantStepMetrics.summarize(
				List.of(sample(IntStream.range(0, 30).mapToObj(i -> step("s" + i, SUCCEEDED, i)).toList(), true)));
		assertEquals(1, result.runsWithTruncatedSteps());
		assertEquals(20, result.slowSteps().size());
		assertEquals(29, result.slowSteps().get(0).durationMillis());
	}

	@Test
	void rejectsForeignDuplicateAndUnknownTypeSteps() {
		var foreign = new AssistantStepMetrics.Step("other", "x", MATRIX_PROVIDER, null, SUCCEEDED, origin, origin);
		var unknown = new AssistantStepMetrics.Step("r", "x", null, null, SUCCEEDED, origin, origin);
		for (var steps : List.of(List.of(foreign), List.of(unknown),
				List.of(step("x", SUCCEEDED, 1), step("x", SUCCEEDED, 1)))) {
			var ex = assertThrows(IllegalArgumentException.class,
					() -> AssistantStepMetrics.summarize(List.of(sample(steps, false))));
			assertEquals("METRICS_STEP_SCOPE_INVALID", ex.getMessage());
		}
	}

}
