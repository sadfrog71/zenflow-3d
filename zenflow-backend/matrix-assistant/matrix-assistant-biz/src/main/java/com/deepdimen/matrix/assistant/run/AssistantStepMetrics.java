package com.deepdimen.matrix.assistant.run;

import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepStatus;
import com.deepdimen.matrix.assistant.api.AssistantExecutionContract.ExecutionStepType;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

/**
 * Individual recorded step durations; overlapping steps must never be summed as run
 * latency.
 */
public final class AssistantStepMetrics {

	private AssistantStepMetrics() {
	}

	public record Step(String runId, String stepId, ExecutionStepType type, String targetId, ExecutionStepStatus status,
			Instant startedAt, Instant completedAt) {
	}

	public record Stage(ExecutionStepType type, int stepCount, Map<ExecutionStepStatus, Long> statuses,
			AssistantRunMetricsService.Distribution duration, int invalidClockCount) {
	}

	public record SlowStep(String runId, String stepId, ExecutionStepType type, String targetId, long durationMillis) {
	}

	public record Summary(List<Stage> stages, List<SlowStep> slowSteps, int runsWithoutStepRecords,
			int runsWithTruncatedSteps) {
	}

	public static Summary summarize(List<AssistantRunMetricsRepository.Sample> samples) {
		var grouped = new EnumMap<ExecutionStepType, List<Step>>(ExecutionStepType.class);
		var seen = new HashSet<List<String>>();
		int missing = 0, truncated = 0;
		for (var sample : samples) {
			if (sample.steps().isEmpty())
				missing++;
			if (sample.stepsTruncated())
				truncated++;
			for (Step step : sample.steps()) {
				if (!sample.run().runId().equals(step.runId()) || step.stepId() == null || step.status() == null
						|| (step.type() != ExecutionStepType.MATRIX_PROVIDER
								&& step.type() != ExecutionStepType.DIFY_WORKFLOW)
						|| !seen.add(List.of(step.runId(), step.stepId())))
					throw new IllegalArgumentException("METRICS_STEP_SCOPE_INVALID");
				grouped.computeIfAbsent(step.type(), ignored -> new ArrayList<>()).add(step);
			}
		}
		var stages = new ArrayList<Stage>();
		var slow = new ArrayList<SlowStep>();
		grouped.forEach((type, steps) -> {
			var statuses = new EnumMap<ExecutionStepStatus, Long>(ExecutionStepStatus.class);
			var values = new ArrayList<Long>();
			int skipped = 0, invalid = 0;
			for (Step step : steps) {
				statuses.merge(step.status(), 1L, Long::sum);
				if (step.status() == ExecutionStepStatus.SKIPPED) {
					skipped++;
					continue;
				}
				if (step.status() == ExecutionStepStatus.READY || step.status() == ExecutionStepStatus.RUNNING
						|| step.startedAt() == null || step.completedAt() == null)
					continue;
				if (step.completedAt().isBefore(step.startedAt())) {
					invalid++;
					continue;
				}
				try {
					long millis = Duration.between(step.startedAt(), step.completedAt()).toMillis();
					values.add(millis);
					slow.add(new SlowStep(step.runId(), step.stepId(), step.type(), step.targetId(), millis));
				}
				catch (ArithmeticException ex) {
					invalid++;
				}
			}
			Collections.sort(values);
			stages.add(new Stage(type, steps.size(), Map.copyOf(statuses),
					new AssistantRunMetricsService.Distribution(values.size(), steps.size() - values.size() - skipped,
							skipped, percentile(values, 50), percentile(values, 95), percentile(values, 99)),
					invalid));
		});
		slow.sort(Comparator.comparingLong(SlowStep::durationMillis)
			.reversed()
			.thenComparing(SlowStep::runId)
			.thenComparing(SlowStep::stepId));
		return new Summary(List.copyOf(stages), slow.stream().limit(20).toList(), missing, truncated);
	}

	private static Long percentile(List<Long> values, int p) {
		return values.isEmpty() ? null : values.get((int) Math.ceil(values.size() * p / 100.0) - 1);
	}

}
